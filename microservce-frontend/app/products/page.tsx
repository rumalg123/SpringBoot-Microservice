"use client";

import { FormEvent, MouseEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useSearchParams } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import CategoryMenu from "../components/CategoryMenu";
import Footer from "../components/Footer";
import Pagination from "../components/Pagination";
import CatalogToolbar from "../components/catalog/CatalogToolbar";
import CatalogFiltersSidebar from "../components/catalog/CatalogFiltersSidebar";
import { useAuthSession } from "../../lib/authSession";
import { emitWishlistUpdate } from "../../lib/navEvents";

type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  sku: string;
  categories: string[];
};

type ProductPageResponse = {
  content: ProductSummary[];
  number: number;
  totalPages: number;
};

type WishlistItem = {
  id: string;
  productId: string;
};

type WishlistResponse = {
  items: WishlistItem[];
  itemCount: number;
};

type Category = {
  id: string;
  name: string;
  slug?: string | null;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

type SortKey = "newest" | "priceAsc" | "priceDesc" | "nameAsc";

const PAGE_SIZE = 12;
const AGGREGATE_PAGE_SIZE = 100;
const AGGREGATE_MAX_PAGES = 10;

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function resolveImageUrl(imageName: string | null): string | null {
  if (!imageName) return null;
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map((segment) => encodeURIComponent(segment)).join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (!base) return apiUrl;
  if (normalized.startsWith("products/")) {
    return `${base.replace(/\/+$/, "")}/${normalized}`;
  }
  return apiUrl;
}

function calcDiscount(regular: number, selling: number): number | null {
  if (regular > selling && regular > 0) return Math.round(((regular - selling) / regular) * 100);
  return null;
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
  const { isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = useAuthSession();

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
  const [wishlistByProductId, setWishlistByProductId] = useState<Record<string, string>>({});
  const [wishlistPendingProductId, setWishlistPendingProductId] = useState<string | null>(null);

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
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
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
    if (!isAuthenticated || !apiClient) { setWishlistByProductId({}); return; }
    let cancelled = false;
    const run = async () => {
      try {
        const res = await apiClient.get("/wishlist/me");
        const data = (res.data as WishlistResponse) || { items: [], itemCount: 0 };
        if (cancelled) return;
        const map: Record<string, string> = {};
        for (const item of data.items || []) { if (item.productId && item.id) map[item.productId] = item.id; }
        setWishlistByProductId(map);
      } catch { if (!cancelled) setWishlistByProductId({}); }
    };
    void run();
    return () => { cancelled = true; };
  }, [isAuthenticated, apiClient]);

  useEffect(() => {
    const run = async () => {
      setProductsLoading(true);
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const sort = sortParams(sortBy);
        const canUseDirectCategoryQuery = selectedParentNames.length <= 1 && selectedSubNames.length <= 1;

        if (canUseDirectCategoryQuery) {
          const params = new URLSearchParams();
          params.set("page", String(page));
          params.set("size", String(PAGE_SIZE));
          params.set("sort", `${sort.field},${sort.direction}`);
          if (search.trim()) params.set("q", search.trim());
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) params.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) params.set("subCategory", selectedSubNames[0]);
          const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store" });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as ProductPageResponse;
          setProducts(data.content || []);
          setTotalPages(Math.max(data.totalPages || 1, 1));
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
          if (search.trim()) params.set("q", search.trim());
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());
          const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store" });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as ProductPageResponse;
          aggregated.push(...(data.content || []));
          totalServerPages = Math.max(data.totalPages || 1, 1);
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
      } catch {
        setProducts([]);
        setTotalPages(1);
        setStatus("Failed to load products.");
      } finally {
        setProductsLoading(false);
      }
    };
    void run();
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

  const applyWishlistResponse = (payload: WishlistResponse) => {
    const map: Record<string, string> = {};
    for (const item of payload.items || []) { if (item.productId && item.id) map[item.productId] = item.id; }
    setWishlistByProductId(map);
  };

  const toggleWishlist = async (event: MouseEvent<HTMLButtonElement>, productId: string) => {
    event.preventDefault();
    event.stopPropagation();
    if (!apiClient || !isAuthenticated) return;
    if (wishlistPendingProductId === productId) return;
    setWishlistPendingProductId(productId);
    try {
      const existingItemId = wishlistByProductId[productId];
      if (existingItemId) {
        await apiClient.delete(`/wishlist/me/items/${existingItemId}`);
        setWishlistByProductId((old) => { const next = { ...old }; delete next[productId]; return next; });
        toast.success("Removed from wishlist");
        emitWishlistUpdate();
      } else {
        const res = await apiClient.post("/wishlist/me/items", { productId });
        applyWishlistResponse((res.data as WishlistResponse) || { items: [], itemCount: 0 });
        toast.success("Added to wishlist");
        emitWishlistUpdate();
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Wishlist update failed");
    } finally {
      setWishlistPendingProductId(null);
    }
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          canViewAdmin={canViewAdmin}
          apiClient={apiClient}
          emailVerified={emailVerified}
          onLogout={() => { void logout(); }}
        />
      )}

      {!isAuthenticated && (
        <header
          style={{
            background: "rgba(8,8,18,0.88)",
            backdropFilter: "blur(20px)",
            borderBottom: "1px solid rgba(0,212,255,0.1)",
            position: "sticky",
            top: 0,
            zIndex: 50,
          }}
        >
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-3 no-underline">
              <div style={{ width: "34px", height: "34px", borderRadius: "10px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 900, fontSize: "0.7rem", color: "#fff", boxShadow: "0 0 14px rgba(0,212,255,0.3)" }}>
                RS
              </div>
              <p style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, color: "#fff", fontSize: "1rem", margin: 0 }}>Rumal Store</p>
            </Link>
            <Link
              href="/"
              className="no-underline rounded-xl px-5 py-2 text-sm font-bold transition"
              style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", boxShadow: "0 0 14px rgba(0,212,255,0.2)" }}
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
        <div style={{ display: "grid", gridTemplateColumns: "260px 1fr", gap: "20px", alignItems: "start" }}>
          {/* Left Filter Sidebar */}
          <CatalogFiltersSidebar
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
          <section>
            {/* Loading skeletons */}
            {productsLoading && (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: "16px" }}>
                {[1, 2, 3, 4, 5, 6].map((i) => (
                  <div key={i} style={{ borderRadius: "16px", overflow: "hidden", background: "var(--surface)", border: "1px solid var(--line)" }}>
                    <div className="skeleton" style={{ height: "220px", width: "100%", borderRadius: 0 }} />
                    <div style={{ padding: "14px 16px", display: "flex", flexDirection: "column", gap: "8px" }}>
                      <div className="skeleton" style={{ height: "13px", width: "80%" }} />
                      <div className="skeleton" style={{ height: "13px", width: "60%" }} />
                      <div className="skeleton" style={{ height: "18px", width: "45%" }} />
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!productsLoading && (
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: "16px" }}>
                {products.map((p, idx) => {
                  const discount = calcDiscount(p.regularPrice, p.sellingPrice);
                  const isWished = Boolean(wishlistByProductId[p.id]);
                  const wishlistBusy = wishlistPendingProductId === p.id;
                  const imgUrl = resolveImageUrl(p.mainImage);

                  return (
                    <Link
                      href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`}
                      key={p.id}
                      className="product-card animate-rise no-underline"
                      style={{ animationDelay: `${idx * 45}ms` }}
                    >
                      {discount && <span className="badge-sale">-{discount}%</span>}

                      {/* Wishlist Button */}
                      {isAuthenticated && (
                        <button
                          type="button"
                          onClick={(event) => { void toggleWishlist(event, p.id); }}
                          disabled={wishlistBusy}
                          style={{
                            position: "absolute",
                            top: "10px",
                            right: "10px",
                            zIndex: 20,
                            width: "32px",
                            height: "32px",
                            borderRadius: "50%",
                            border: isWished ? "1.5px solid rgba(239,68,68,0.6)" : "1.5px solid rgba(0,212,255,0.2)",
                            background: isWished ? "rgba(239,68,68,0.12)" : "rgba(8,8,18,0.8)",
                            color: isWished ? "#ef4444" : "#6868a0",
                            cursor: wishlistBusy ? "not-allowed" : "pointer",
                            opacity: wishlistBusy ? 0.5 : 1,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            transition: "all 0.2s",
                            backdropFilter: "blur(8px)",
                            boxShadow: isWished ? "0 0 10px rgba(239,68,68,0.2)" : "none",
                          }}
                          title={isWished ? "Remove from wishlist" : "Add to wishlist"}
                          aria-label={isWished ? "Remove from wishlist" : "Add to wishlist"}
                        >
                          {wishlistBusy ? (
                            <span className="spinner-sm" style={{ width: "10px", height: "10px" }} />
                          ) : (
                            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24"
                              fill={isWished ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2"
                              strokeLinecap="round" strokeLinejoin="round">
                              <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
                            </svg>
                          )}
                        </button>
                      )}

                      {/* Image */}
                      <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)" }}>
                        {imgUrl ? (
                          <Image src={imgUrl} alt={p.name} width={400} height={400} className="product-card-img" unoptimized />
                        ) : (
                          <div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, #111128, #1c1c38)", color: "#4a4a70", fontSize: "0.75rem", fontWeight: 600 }}>
                            No Image
                          </div>
                        )}
                        <div className="product-card-overlay" style={{ borderRadius: "15px 15px 0 0" }}>
                          <span style={{ background: "linear-gradient(135deg,#00d4ff,#7c3aed)", color: "#fff", padding: "7px 16px", borderRadius: "20px", fontSize: "0.72rem", fontWeight: 800, letterSpacing: "0.04em" }}>
                            View Product →
                          </span>
                        </div>
                      </div>

                      {/* Body */}
                      <div className="product-card-body">
                        <p style={{ margin: "0 0 4px", fontSize: "0.875rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
                          {p.name}
                        </p>
                        <p style={{ margin: "0 0 8px", fontSize: "0.7rem", color: "var(--muted)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {p.shortDescription}
                        </p>
                        <div style={{ display: "flex", alignItems: "center", gap: "4px", flexWrap: "wrap" }}>
                          <span className="price-current">{money(p.sellingPrice)}</span>
                          {p.discountedPrice !== null && (
                            <>
                              <span className="price-original">{money(p.regularPrice)}</span>
                              {discount && <span className="price-discount-badge">-{discount}%</span>}
                            </>
                          )}
                        </div>
                        <p style={{ margin: "6px 0 0", fontSize: "0.65rem", color: "var(--muted-2)" }}>SKU: {p.sku}</p>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )}

            {/* Empty state */}
            {products.length === 0 && !productsLoading && (
              <div className="empty-state mt-6">
                <div className="empty-state-icon">
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                  </svg>
                </div>
                <p className="empty-state-title">No products found</p>
                <p className="empty-state-desc">Try adjusting your search or filter criteria</p>
                <button
                  disabled={productsLoading}
                  onClick={clearAllFilters}
                  className="btn-primary"
                  style={{ marginTop: "8px" }}
                >
                  Clear All Filters
                </button>
              </div>
            )}

            <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} disabled={productsLoading} />
          </section>
        </div>
      </main>

      <Footer />
    </div>
  );
}

export default function ProductsPage() {
  return (
    <Suspense fallback={
      <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
        <div className="mx-auto max-w-7xl px-4 py-8">
          <div style={{ display: "grid", gridTemplateColumns: "260px 1fr", gap: "20px" }}>
            <div className="skeleton" style={{ height: "400px", borderRadius: "16px" }} />
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "16px" }}>
              {[1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} style={{ borderRadius: "16px", overflow: "hidden" }}>
                  <div className="skeleton" style={{ height: "200px", width: "100%", borderRadius: 0 }} />
                  <div style={{ padding: "14px 16px", display: "flex", flexDirection: "column", gap: "8px" }}>
                    <div className="skeleton" style={{ height: "13px", width: "80%" }} />
                    <div className="skeleton" style={{ height: "17px", width: "50%" }} />
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
