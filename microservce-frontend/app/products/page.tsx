"use client";

import { FormEvent, MouseEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import CategoryMenu from "../components/CategoryMenu";
import Footer from "../components/Footer";
import CatalogToolbar from "../components/catalog/CatalogToolbar";
import { API_BASE } from "../../lib/constants";
import ProductFilterPanel from "../components/products/ProductFilterPanel";
import ProductSearchResults from "../components/products/ProductSearchResults";
import { useAuthSession } from "../../lib/authSession";
import { money, calcDiscount } from "../../lib/format";
import { resolveImageUrl } from "../../lib/image";
import type { ProductSummary, PagedResponse, SearchResponse } from "../../lib/types";
import type { Category } from "../../lib/types/category";
import type { SortKey } from "../../lib/types/product";
import { PAGE_SIZE_SMALL as PAGE_SIZE, AGGREGATE_PAGE_SIZE, AGGREGATE_MAX_PAGES } from "../../lib/constants";
import { useWishlistToggle } from "../../lib/hooks/useWishlistToggle";


function parsePrice(value: string): number | null {
  const normalized = value.trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return Number(parsed.toFixed(2));
}

function sortParams(sortBy: SortKey): { field: string; direction: "ASC" | "DESC" } {
  switch (sortBy) {
    case "priceAsc": return { field: "regularPrice", direction: "ASC" };
    case "priceDesc": return { field: "regularPrice", direction: "DESC" };
    case "nameAsc": return { field: "name", direction: "ASC" };
    default: return { field: "createdAt", direction: "DESC" };
  }
}

function normalizeLower(values: string[]): string[] {
  return values.map((v) => v.trim().toLowerCase()).filter(Boolean);
}

function matchesCategorySelection(product: ProductSummary, selectedParentNames: string[], selectedSubNames: string[]): boolean {
  const categories = new Set(normalizeLower(product.categories || []));
  const selectedParents = normalizeLower(selectedParentNames);
  const selectedSubs = normalizeLower(selectedSubNames);
  const parentMatch = selectedParents.length === 0 || selectedParents.some((n) => categories.has(n));
  const subMatch = selectedSubs.length === 0 || selectedSubs.some((n) => categories.has(n));
  return parentMatch && subMatch;
}

function sortProductsClient(products: ProductSummary[], sortBy: SortKey): ProductSummary[] {
  const copy = [...products];
  if (sortBy === "priceAsc") copy.sort((a, b) => a.sellingPrice - b.sellingPrice);
  else if (sortBy === "priceDesc") copy.sort((a, b) => b.sellingPrice - a.sellingPrice);
  else if (sortBy === "nameAsc") copy.sort((a, b) => a.name.localeCompare(b.name));
  return copy;
}

function ProductsPageContent() {
  const searchParams = useSearchParams();
  const {
    isAuthenticated,
    profile,
    logout,
    isSuperAdmin,
    isVendorAdmin,
    canViewAdmin,
    canManageAdminOrders,
    canManageAdminProducts,
    canManageAdminCategories,
    canManageAdminVendors,
    canManageAdminPosters,
    apiClient,
    emailVerified,
  } = useAuthSession();

  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [selectedParentNames, setSelectedParentNames] = useState<string[]>([]);
  const [selectedSubNames, setSelectedSubNames] = useState<string[]>([]);
  const [expandedParentIds, setExpandedParentIds] = useState<Record<string, boolean>>({});
  const [minPriceInput, setMinPriceInput] = useState("");
  const [maxPriceInput, setMaxPriceInput] = useState("");
  const [appliedMinPrice, setAppliedMinPrice] = useState<number | null>(null);
  const [appliedMaxPrice, setAppliedMaxPrice] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState<SortKey>("newest");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [productsLoading, setProductsLoading] = useState(false);
  const [status, setStatus] = useState("Loading products...");
  const { isWishlisted, isBusy: isWishlistBusy, toggle: toggleWishlist } = useWishlistToggle(apiClient, isAuthenticated);

  useEffect(() => {
    const q = (searchParams.get("q") || "").trim();
    const mainCategory = (searchParams.get("mainCategory") || "").trim();
    const subCategory = (searchParams.get("subCategory") || "").trim();
    const category = (searchParams.get("category") || "").trim();
    setQuery(q);
    setSearch(q);
    setSelectedParentNames(mainCategory ? [mainCategory] : (category ? [category] : []));
    setSelectedSubNames(subCategory ? [subCategory] : []);
    setPage(0);
  }, [searchParams]);

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = API_BASE;
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) return;
        const data = (await res.json()) as Category[];
        setAllCategories(data || []);
      } catch { setAllCategories([]); }
    };
    void run();
  }, []);

  const parents = useMemo(
    () => allCategories.filter((c) => c.type === "PARENT").sort((a, b) => a.name.localeCompare(b.name)),
    [allCategories]
  );

  const subsByParent = useMemo(() => {
    const map = new Map<string, Category[]>();
    for (const sub of allCategories.filter((c) => c.type === "SUB" && c.parentCategoryId)) {
      const key = sub.parentCategoryId as string;
      const existing = map.get(key) || [];
      existing.push(sub);
      map.set(key, existing);
    }
    for (const [, list] of map) list.sort((a, b) => a.name.localeCompare(b.name));
    return map;
  }, [allCategories]);

  const parentNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of parents) map.set(p.id, p.name);
    return map;
  }, [parents]);

  const parentBySubName = useMemo(() => {
    const map = new Map<string, string>();
    for (const sub of allCategories.filter((c) => c.type === "SUB" && c.parentCategoryId)) {
      const parentName = parentNameById.get(sub.parentCategoryId || "");
      if (parentName) map.set(sub.name.toLowerCase(), parentName);
    }
    return map;
  }, [allCategories, parentNameById]);

  useEffect(() => {
    if (parents.length === 0) return;
    setExpandedParentIds((prev) => {
      const next = { ...prev };
      for (const p of parents) { if (selectedParentNames.includes(p.name)) next[p.id] = true; }
      return next;
    });
  }, [parents, selectedParentNames]);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    const run = async () => {
      setProductsLoading(true);
      try {
        const apiBase = API_BASE;
        const sort = sortParams(sortBy);

        // Use search-service when a text query is present
        if (search.trim()) {
          const searchSortMap: Record<SortKey, string> = {
            newest: "newest",
            priceAsc: "price-low",
            priceDesc: "price-high",
            nameAsc: "relevance",
          };
          const params = new URLSearchParams();
          params.set("q", search.trim());
          params.set("page", String(page));
          params.set("size", String(PAGE_SIZE));
          params.set("sortBy", searchSortMap[sortBy] || "relevance");
          if (appliedMinPrice !== null) params.set("minPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) params.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) params.set("subCategory", selectedSubNames[0]);
          const res = await fetch(`${apiBase}/search/products?${params.toString()}`, { cache: "no-store", signal });
          if (!res.ok) throw new Error("Failed to fetch search results");
          const data = (await res.json()) as SearchResponse;
          if (signal.aborted) return;
          const mapped = (data.content || []).map((hit) => ({
            id: hit.id,
            slug: hit.slug,
            name: hit.name,
            shortDescription: hit.shortDescription,
            brandName: hit.brandName,
            mainImage: hit.mainImage,
            regularPrice: hit.regularPrice,
            discountedPrice: hit.discountedPrice,
            sellingPrice: hit.sellingPrice,
            sku: hit.sku,
            mainCategory: hit.mainCategory,
            subCategories: hit.subCategories,
            categories: hit.categories,
            vendorId: hit.vendorId ?? "",
            variations: hit.variations,
          } as ProductSummary));
          setProducts(mapped);
          setTotalPages(Math.max(data.totalPages || 1, 1));
          setStatus(`Showing ${mapped.length} of ${data.totalElements} results for "${search.trim()}"${data.tookMs ? ` (${data.tookMs}ms)` : ""}`);
          return;
        }

        // No text query: use product-service directly
        const canUseDirectCategoryQuery = selectedParentNames.length <= 1 && selectedSubNames.length <= 1;

        if (canUseDirectCategoryQuery) {
          const params = new URLSearchParams();
          params.set("page", String(page));
          params.set("size", String(PAGE_SIZE));
          params.set("sort", `${sort.field},${sort.direction}`);
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) params.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) params.set("subCategory", selectedSubNames[0]);
          const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store", signal });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as PagedResponse<ProductSummary>;
          if (signal.aborted) return;
          setProducts(data.content || []);
          setTotalPages(Math.max(data.totalPages ?? data.page?.totalPages ?? 1, 1));
          setStatus(`Showing ${data.content?.length || 0} products`);
          return;
        }

        const aggregated: ProductSummary[] = [];
        let totalServerPages = 1;
        let currentPage = 0;
        while (currentPage < totalServerPages && currentPage < AGGREGATE_MAX_PAGES) {
          const params = new URLSearchParams();
          params.set("page", String(currentPage));
          params.set("size", String(AGGREGATE_PAGE_SIZE));
          params.set("sort", `${sort.field},${sort.direction}`);
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());
          const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store", signal });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as PagedResponse<ProductSummary>;
          if (signal.aborted) return;
          aggregated.push(...(data.content || []));
          totalServerPages = Math.max(data.totalPages ?? data.page?.totalPages ?? 1, 1);
          currentPage += 1;
        }

        const dedupedMap = new Map<string, ProductSummary>();
        for (const p of aggregated) dedupedMap.set(p.id, p);
        const deduped = Array.from(dedupedMap.values());
        const filtered = sortProductsClient(
          deduped.filter((p) => matchesCategorySelection(p, selectedParentNames, selectedSubNames)),
          sortBy
        );
        const computedTotalPages = Math.max(Math.ceil(filtered.length / PAGE_SIZE), 1);
        const boundedPage = Math.min(page, computedTotalPages - 1);
        const start = boundedPage * PAGE_SIZE;
        const slice = filtered.slice(start, start + PAGE_SIZE);
        if (boundedPage !== page) setPage(boundedPage);
        setProducts(slice);
        setTotalPages(computedTotalPages);
        setStatus(`Showing ${slice.length} of ${filtered.length} products`);
      } catch (err) {
        if (signal.aborted) return;
        setProducts([]);
        setTotalPages(1);
        setStatus("Failed to load products.");
      } finally {
        if (!signal.aborted) setProductsLoading(false);
      }
    };
    void run();
    return () => controller.abort();
  }, [search, selectedParentNames, selectedSubNames, appliedMinPrice, appliedMaxPrice, sortBy, page]);

  const onSearch = useCallback((e: FormEvent) => {
    e.preventDefault();
    if (productsLoading) return;
    setPage(0);
    setSearch(query);
  }, [query, productsLoading]);

  const applyPriceFilter = (e: FormEvent) => {
    e.preventDefault();
    const min = parsePrice(minPriceInput);
    const max = parsePrice(maxPriceInput);
    if (min !== null && max !== null && min > max) { toast.error("Min price must be lower than max price"); return; }
    setAppliedMinPrice(min);
    setAppliedMaxPrice(max);
    setPage(0);
  };

  const clearAllFilters = () => {
    setQuery(""); setSearch(""); setSelectedParentNames([]); setSelectedSubNames([]);
    setMinPriceInput(""); setMaxPriceInput(""); setAppliedMinPrice(null); setAppliedMaxPrice(null);
    setSortBy("newest"); setPage(0);
  };

  const toggleParentSelection = (parent: Category) => {
    setSelectedParentNames((prev) => {
      const exists = prev.includes(parent.name);
      if (exists) {
        const subs = (subsByParent.get(parent.id) || []).map((s) => s.name);
        setSelectedSubNames((old) => old.filter((s) => !subs.includes(s)));
        return prev.filter((n) => n !== parent.name);
      }
      return [...prev, parent.name];
    });
    setPage(0);
  };

  const toggleSubSelection = (sub: Category) => {
    setSelectedSubNames((prev) => {
      const exists = prev.includes(sub.name);
      if (exists) return prev.filter((n) => n !== sub.name);
      return [...prev, sub.name];
    });
    const parentName = parentBySubName.get(sub.name.toLowerCase());
    if (parentName) setSelectedParentNames((prev) => prev.includes(parentName) ? prev : [...prev, parentName]);
    setPage(0);
  };

  const toggleParentExpanded = (parentId: string) => {
    setExpandedParentIds((prev) => ({ ...prev, [parentId]: !prev[parentId] }));
  };

  const activeFilterLabel = useMemo(() => {
    const parts: string[] = [];
    if (selectedParentNames.length > 0) parts.push(`Categories: ${selectedParentNames.join(", ")}`);
    if (selectedSubNames.length > 0) parts.push(`Subcategories: ${selectedSubNames.join(", ")}`);
    if (search.trim()) parts.push(`Search: "${search.trim()}"`);
    if (appliedMinPrice !== null || appliedMaxPrice !== null) parts.push(`Price: ${appliedMinPrice ?? 0} - ${appliedMaxPrice ?? "Any"}`);
    return parts.length === 0 ? "All Products" : parts.join(" | ");
  }, [selectedParentNames, selectedSubNames, search, appliedMinPrice, appliedMaxPrice]);

  return (
    <div className="min-h-screen bg-bg">
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          isSuperAdmin={isSuperAdmin}
          isVendorAdmin={isVendorAdmin}
          canViewAdmin={canViewAdmin}
          canManageAdminOrders={canManageAdminOrders}
          canManageAdminProducts={canManageAdminProducts}
          canManageAdminCategories={canManageAdminCategories}
          canManageAdminVendors={canManageAdminVendors}
          canManageAdminPosters={canManageAdminPosters}
          apiClient={apiClient}
          emailVerified={emailVerified}
          onLogout={() => { void logout(); }}
        />
      )}

      {!isAuthenticated && (
        <header className="sticky top-0 z-50 border-b border-line-bright bg-header-bg backdrop-blur-[20px]">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-3 no-underline">
              <div className="flex h-[34px] w-[34px] items-center justify-center rounded-md bg-[var(--gradient-brand)] text-xs font-black text-white shadow-[0_0_14px_var(--brand-glow)]">
                RS
              </div>
              <p className="m-0 font-[Syne,sans-serif] text-lg font-extrabold text-white">Rumal Store</p>
            </Link>
            <Link
              href="/"
              className="rounded-xl bg-[var(--gradient-brand)] px-5 py-2 text-sm font-bold text-white shadow-[0_0_14px_var(--line-bright)] no-underline transition"
            >
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-6">
        {/* Breadcrumb */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Products</span>
        </nav>

        <CategoryMenu />

        {/* Toolbar */}
        <CatalogToolbar
          title="Product Catalog"
          status={status}
          filterSummary={activeFilterLabel}
          sortBy={sortBy}
          loading={productsLoading}
          query={query}
          onSortChange={(value) => { setSortBy(value); setPage(0); }}
          onResetFilters={clearAllFilters}
          onQueryChange={setQuery}
          onSearchSubmit={onSearch}
          onClearSearch={() => { setQuery(""); setSearch(""); setPage(0); }}
        />

        {/* Sidebar + Grid Layout */}
        <div className="grid items-start gap-5" style={{ gridTemplateColumns: "260px 1fr" }}>
          {/* Left Filter Sidebar */}
          <ProductFilterPanel
            parents={parents}
            subsByParent={subsByParent}
            selectedParentNames={selectedParentNames}
            selectedSubNames={selectedSubNames}
            expandedParentIds={expandedParentIds}
            minPriceInput={minPriceInput}
            maxPriceInput={maxPriceInput}
            loading={productsLoading}
            onMinPriceChange={setMinPriceInput}
            onMaxPriceChange={setMaxPriceInput}
            onApplyPriceFilter={applyPriceFilter}
            onClearPriceFilter={() => {
              setMinPriceInput(""); setMaxPriceInput("");
              setAppliedMinPrice(null); setAppliedMaxPrice(null);
              setPage(0);
            }}
            onToggleParent={toggleParentSelection}
            onToggleSub={toggleSubSelection}
            onToggleParentExpanded={toggleParentExpanded}
          />

          {/* Product Grid */}
          <ProductSearchResults
            products={products}
            productsLoading={productsLoading}
            page={page}
            totalPages={totalPages}
            isAuthenticated={isAuthenticated}
            isWishlisted={isWishlisted}
            isWishlistBusy={isWishlistBusy}
            onWishlistToggle={(e, id) => { void toggleWishlist(e, id); }}
            onPageChange={setPage}
            onClearFilters={clearAllFilters}
          />
        </div>
      </main>

      <Footer />
    </div>
  );
}

export default function ProductsPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen bg-bg">
        <div className="mx-auto max-w-7xl px-4 py-8">
          <div className="grid gap-5" style={{ gridTemplateColumns: "260px 1fr" }}>
            <div className="skeleton h-[400px] rounded-lg" />
            <div className="grid grid-cols-3 gap-4">
              {[1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} className="overflow-hidden rounded-lg">
                  <div className="skeleton h-[200px] w-full rounded-none" />
                  <div className="flex flex-col gap-2 px-4 py-3.5">
                    <div className="skeleton h-[13px] w-4/5" />
                    <div className="skeleton h-[17px] w-1/2" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    }>
      <ProductsPageContent />
    </Suspense>
  );
}
