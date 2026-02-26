"use client";

import { FormEvent, MouseEvent, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import CategoryMenu from "../../components/CategoryMenu";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
import CatalogToolbar from "../../components/catalog/CatalogToolbar";
import { API_BASE } from "../../../lib/constants";
import CatalogFiltersSidebar from "../../components/catalog/CatalogFiltersSidebar";
import PosterSlot from "../../components/posters/PosterSlot";
import { useAuthSession } from "../../../lib/authSession";
import { emitWishlistUpdate } from "../../../lib/navEvents";
import { money, calcDiscount } from "../../../lib/format";
import { resolveImageUrl } from "../../../lib/image";
import { useWishlistToggle } from "../../../lib/hooks/useWishlistToggle";
import ProductCard from "../../components/catalog/ProductCard";
import ProductCardSkeleton from "../../components/catalog/ProductCardSkeleton";
import type { ProductSummary, PagedResponse } from "../../../lib/types";
import type { WishlistItem, WishlistResponse } from "../../../lib/types/wishlist";
import type { Category } from "../../../lib/types/category";
import type { SortKey } from "../../../lib/types/product";
import { PAGE_SIZE_SMALL as PAGE_SIZE, AGGREGATE_PAGE_SIZE, AGGREGATE_MAX_PAGES } from "../../../lib/constants";


function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

function toCategorySlug(category: Category): string {
  const fromSlug = (category.slug || "").trim();
  if (fromSlug) return slugify(fromSlug);
  return slugify(category.name || "");
}

function parsePrice(value: string): number | null {
  const normalized = value.trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return Number(parsed.toFixed(2));
}

function sortParams(sortBy: SortKey): { field: string; direction: "ASC" | "DESC" } {
  switch (sortBy) {
    case "priceAsc":
      return { field: "regularPrice", direction: "ASC" };
    case "priceDesc":
      return { field: "regularPrice", direction: "DESC" };
    case "nameAsc":
      return { field: "name", direction: "ASC" };
    case "newest":
    default:
      return { field: "createdAt", direction: "DESC" };
  }
}

function normalizeLower(values: string[]): string[] {
  return values.map((value) => value.trim().toLowerCase()).filter(Boolean);
}

function matchesCategorySelection(
  product: ProductSummary,
  selectedParentNames: string[],
  selectedSubNames: string[]
): boolean {
  const categories = new Set(normalizeLower(product.categories || []));
  const selectedParents = normalizeLower(selectedParentNames);
  const selectedSubs = normalizeLower(selectedSubNames);

  const parentMatch =
    selectedParents.length === 0 || selectedParents.some((name) => categories.has(name));
  const subMatch =
    selectedSubs.length === 0 || selectedSubs.some((name) => categories.has(name));

  return parentMatch && subMatch;
}

function sortProductsClient(products: ProductSummary[], sortBy: SortKey): ProductSummary[] {
  const copy = [...products];
  if (sortBy === "priceAsc") {
    copy.sort((a, b) => a.sellingPrice - b.sellingPrice);
  } else if (sortBy === "priceDesc") {
    copy.sort((a, b) => b.sellingPrice - a.sellingPrice);
  } else if (sortBy === "nameAsc") {
    copy.sort((a, b) => a.name.localeCompare(b.name));
  }
  return copy;
}

export default function CategoryProductsPage() {
  const params = useParams<{ name: string }>();
  const router = useRouter();
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

  const [resolvedCategory, setResolvedCategory] = useState<Category | null>(null);
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
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [routeLoading, setRouteLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(false);
  const [status, setStatus] = useState("Resolving category...");
  const { isWishlisted, isBusy: isWishlistBusy, toggle: toggleWishlist } = useWishlistToggle(apiClient, isAuthenticated);

  useEffect(() => {
    const run = async () => {
      const routeName = decodeURIComponent(params.name || "").trim();
      const requestedSlug = slugify(routeName);
      if (!requestedSlug || requestedSlug === "all") {
        router.replace("/products");
        return;
      }

      setRouteLoading(true);
      setStatus("Resolving category...");
      try {
        const apiBase = API_BASE;
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) {
          throw new Error("Failed to load categories");
        }

        const categories = ((await res.json()) as Category[]) || [];
        setAllCategories(categories);

        const match = categories.find((category) => toCategorySlug(category) === requestedSlug);
        if (!match) {
          router.replace("/products");
          return;
        }

        setResolvedCategory(match);
        if (match.type === "PARENT") {
          setSelectedParentNames([match.name]);
          setSelectedSubNames([]);
        } else {
          const parent = categories.find((category) => category.id === match.parentCategoryId);
          setSelectedParentNames(parent ? [parent.name] : []);
          setSelectedSubNames([match.name]);
        }
        setPage(0);
      } catch {
        setResolvedCategory(null);
        setStatus("Could not resolve category.");
      } finally {
        setRouteLoading(false);
      }
    };

    void run();
  }, [params.name, router]);

  const parents = useMemo(
    () => allCategories.filter((category) => category.type === "PARENT").sort((a, b) => a.name.localeCompare(b.name)),
    [allCategories]
  );

  const subsByParent = useMemo(() => {
    const map = new Map<string, Category[]>();
    for (const sub of allCategories.filter((category) => category.type === "SUB" && category.parentCategoryId)) {
      const key = sub.parentCategoryId as string;
      const existing = map.get(key) || [];
      existing.push(sub);
      map.set(key, existing);
    }
    for (const [, list] of map) {
      list.sort((a, b) => a.name.localeCompare(b.name));
    }
    return map;
  }, [allCategories]);

  const parentNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const parent of parents) {
      map.set(parent.id, parent.name);
    }
    return map;
  }, [parents]);

  const parentBySubName = useMemo(() => {
    const map = new Map<string, string>();
    for (const sub of allCategories.filter((category) => category.type === "SUB" && category.parentCategoryId)) {
      const parentName = parentNameById.get(sub.parentCategoryId || "");
      if (parentName) {
        map.set(sub.name.toLowerCase(), parentName);
      }
    }
    return map;
  }, [allCategories, parentNameById]);

  useEffect(() => {
    if (parents.length === 0) return;
    setExpandedParentIds((previous) => {
      const next = { ...previous };
      for (const parent of parents) {
        if (selectedParentNames.includes(parent.name)) {
          next[parent.id] = true;
        }
      }
      return next;
    });
  }, [parents, selectedParentNames]);

  useEffect(() => {
    if (routeLoading || !resolvedCategory) return;
    const controller = new AbortController();
    const { signal } = controller;
    const run = async () => {
      setProductsLoading(true);
      try {
        const apiBase = API_BASE;
        const sort = sortParams(sortBy);
        const canUseDirectCategoryQuery =
          selectedParentNames.length <= 1 && selectedSubNames.length <= 1;

        if (canUseDirectCategoryQuery) {
          const searchParams = new URLSearchParams();
          searchParams.set("page", String(page));
          searchParams.set("size", String(PAGE_SIZE));
          searchParams.set("sort", `${sort.field},${sort.direction}`);
          if (search.trim()) searchParams.set("q", search.trim());
          if (appliedMinPrice !== null) searchParams.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) searchParams.set("maxSellingPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) searchParams.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) searchParams.set("subCategory", selectedSubNames[0]);

          const res = await fetch(`${apiBase}/products?${searchParams.toString()}`, { cache: "no-store", signal });
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
          const searchParams = new URLSearchParams();
          searchParams.set("page", String(currentPage));
          searchParams.set("size", String(AGGREGATE_PAGE_SIZE));
          searchParams.set("sort", `${sort.field},${sort.direction}`);
          if (search.trim()) searchParams.set("q", search.trim());
          if (appliedMinPrice !== null) searchParams.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) searchParams.set("maxSellingPrice", appliedMaxPrice.toString());

          const res = await fetch(`${apiBase}/products?${searchParams.toString()}`, { cache: "no-store", signal });
          if (!res.ok) throw new Error("Failed to fetch products");

          const data = (await res.json()) as PagedResponse<ProductSummary>;
          if (signal.aborted) return;
          aggregated.push(...(data.content || []));
          totalServerPages = Math.max(data.totalPages ?? data.page?.totalPages ?? 1, 1);
          currentPage += 1;
        }

        const dedupedMap = new Map<string, ProductSummary>();
        for (const product of aggregated) {
          dedupedMap.set(product.id, product);
        }
        const deduped = Array.from(dedupedMap.values());
        const filtered = sortProductsClient(
          deduped.filter((product) => matchesCategorySelection(product, selectedParentNames, selectedSubNames)),
          sortBy
        );

        const computedTotalPages = Math.max(Math.ceil(filtered.length / PAGE_SIZE), 1);
        const boundedPage = Math.min(page, computedTotalPages - 1);
        const start = boundedPage * PAGE_SIZE;
        const slice = filtered.slice(start, start + PAGE_SIZE);
        if (boundedPage !== page) {
          setPage(boundedPage);
        }
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
  }, [
    routeLoading,
    resolvedCategory,
    search,
    selectedParentNames,
    selectedSubNames,
    appliedMinPrice,
    appliedMaxPrice,
    sortBy,
    page
  ]);

  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    if (productsLoading || routeLoading) return;
    setPage(0);
    setSearch(query);
  };

  const applyPriceFilter = (event: FormEvent) => {
    event.preventDefault();
    const min = parsePrice(minPriceInput);
    const max = parsePrice(maxPriceInput);
    if (min !== null && max !== null && min > max) {
      toast.error("Min price must be lower than max price");
      return;
    }
    setAppliedMinPrice(min);
    setAppliedMaxPrice(max);
    setPage(0);
  };

  const resetToRouteDefaults = () => {
    setQuery("");
    setSearch("");
    setMinPriceInput("");
    setMaxPriceInput("");
    setAppliedMinPrice(null);
    setAppliedMaxPrice(null);
    setSortBy("newest");
    setPage(0);

    if (!resolvedCategory) {
      setSelectedParentNames([]);
      setSelectedSubNames([]);
      return;
    }

    if (resolvedCategory.type === "PARENT") {
      setSelectedParentNames([resolvedCategory.name]);
      setSelectedSubNames([]);
      return;
    }

    const parentName = parentNameById.get(resolvedCategory.parentCategoryId || "");
    setSelectedParentNames(parentName ? [parentName] : []);
    setSelectedSubNames([resolvedCategory.name]);
  };

  const toggleParentSelection = (parent: Category) => {
    setSelectedParentNames((previous) => {
      const exists = previous.includes(parent.name);
      if (exists) {
        const subs = (subsByParent.get(parent.id) || []).map((sub) => sub.name);
        setSelectedSubNames((oldSubs) => oldSubs.filter((sub) => !subs.includes(sub)));
        return previous.filter((name) => name !== parent.name);
      }
      return [...previous, parent.name];
    });
    setPage(0);
  };

  const toggleSubSelection = (sub: Category) => {
    setSelectedSubNames((previous) => {
      const exists = previous.includes(sub.name);
      if (exists) return previous.filter((name) => name !== sub.name);
      return [...previous, sub.name];
    });
    const parentName = parentBySubName.get(sub.name.toLowerCase());
    if (parentName) {
      setSelectedParentNames((previous) => (
        previous.includes(parentName) ? previous : [...previous, parentName]
      ));
    }
    setPage(0);
  };

  const toggleParentExpanded = (parentId: string) => {
    setExpandedParentIds((previous) => ({
      ...previous,
      [parentId]: !previous[parentId]
    }));
  };

  const activeFilter = useMemo(() => {
    if (selectedSubNames.length > 0 && selectedParentNames.length > 0) {
      return `${selectedParentNames[0]} > ${selectedSubNames[0]}`;
    }
    if (selectedSubNames.length > 0) return selectedSubNames[0];
    if (selectedParentNames.length > 0) return selectedParentNames[0];
    return resolvedCategory?.name || "Category";
  }, [resolvedCategory, selectedParentNames, selectedSubNames]);

  const activeFilterLabel = useMemo(() => {
    const parts: string[] = [];
    if (selectedParentNames.length > 0) {
      parts.push(`Categories: ${selectedParentNames.join(", ")}`);
    }
    if (selectedSubNames.length > 0) {
      parts.push(`Subcategories: ${selectedSubNames.join(", ")}`);
    }
    if (search.trim()) {
      parts.push(`Search: "${search.trim()}"`);
    }
    if (appliedMinPrice !== null || appliedMaxPrice !== null) {
      parts.push(`Price: ${appliedMinPrice ?? 0} - ${appliedMaxPrice ?? "Any"}`);
    }
    if (parts.length === 0) {
      return "All Products";
    }
    return parts.join(" | ");
  }, [selectedParentNames, selectedSubNames, search, appliedMinPrice, appliedMaxPrice]);

  const busy = routeLoading || productsLoading;

  return (
    <div className="min-h-screen bg-[var(--bg)]">
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
        <header style={{ background: "var(--header-bg)", backdropFilter: "blur(12px)", borderBottom: "1px solid rgba(0,212,255,0.1)", position: "sticky", top: 0, zIndex: 50 }}>
          <div style={{ maxWidth: "1280px", margin: "0 auto", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px" }}>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "10px", textDecoration: "none" }}>
              <span style={{ width: "32px", height: "32px", borderRadius: "8px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", display: "grid", placeItems: "center" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" /><line x1="3" y1="6" x2="21" y2="6" /><path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
              </span>
              <span style={{ fontSize: "1.1rem", fontWeight: 800, color: "#fff", fontFamily: "'Syne', sans-serif" }}>Rumal Store</span>
            </Link>
            <Link href="/" style={{ padding: "8px 20px", borderRadius: "8px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", fontWeight: 700, fontSize: "0.82rem", textDecoration: "none" }}>
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <Link href="/categories">Categories</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">{activeFilter}</span>
        </nav>

         <CategoryMenu />

         <div className="mt-4">
           <PosterSlot placement="CATEGORY_TOP" variant="strip" />
         </div>

         {routeLoading && (
          <ProductCardSkeleton count={6} />
        )}

        {!routeLoading && (
          <>
            <CatalogToolbar
              title={`${activeFilter} Products`}
              status={status}
              filterSummary={activeFilterLabel}
              sortBy={sortBy}
              loading={busy}
              query={query}
              searchPlaceholder={`Search in ${activeFilter}...`}
              onSortChange={(value) => {
                setSortBy(value);
                setPage(0);
              }}
              onResetFilters={resetToRouteDefaults}
              onQueryChange={setQuery}
              onSearchSubmit={onSearch}
              onClearSearch={() => {
                setQuery("");
                setSearch("");
                setPage(0);
              }}
            />

            <div className="grid gap-5 lg:grid-cols-[300px,1fr]">
              <CatalogFiltersSidebar
                parents={parents}
                subsByParent={subsByParent}
                selectedParentNames={selectedParentNames}
                selectedSubNames={selectedSubNames}
                expandedParentIds={expandedParentIds}
                minPriceInput={minPriceInput}
                maxPriceInput={maxPriceInput}
                loading={busy}
                onMinPriceChange={setMinPriceInput}
                onMaxPriceChange={setMaxPriceInput}
                onApplyPriceFilter={applyPriceFilter}
                onClearPriceFilter={() => {
                  setMinPriceInput("");
                  setMaxPriceInput("");
                  setAppliedMinPrice(null);
                  setAppliedMaxPrice(null);
                  setPage(0);
                }}
                onToggleParent={toggleParentSelection}
                onToggleSub={toggleSubSelection}
                onToggleParentExpanded={toggleParentExpanded}
              />

              <section>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3">
                  {products.map((p, idx) => (
                    <ProductCard
                      key={p.id}
                      product={p}
                      index={idx}
                      showWishlist={isAuthenticated}
                      isWishlisted={isWishlisted(p.id)}
                      wishlistBusy={isWishlistBusy(p.id)}
                      onWishlistToggle={(e) => { void toggleWishlist(e, p.id); }}
                    />
                  ))}
                </div>

                {products.length === 0 && !productsLoading && (
                  <div className="empty-state mt-6">
                    <div className="empty-state-icon">Search</div>
                    <p className="empty-state-title">No products found</p>
                    <p className="empty-state-desc">Try adjusting your search and filters</p>
                    <button
                      disabled={busy}
                      onClick={resetToRouteDefaults}
                      className="btn-primary inline-block px-6 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      Reset Filters
                    </button>
                  </div>
                )}

                <Pagination
                  currentPage={page}
                  totalPages={totalPages}
                  onPageChange={setPage}
                  disabled={busy}
                />
              </section>
            </div>
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
