"use client";

import { FormEvent, MouseEvent, Suspense, useEffect, useMemo, useState } from "react";
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
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (base) {
    return `${base.replace(/\/+$/, "")}/${imageName.replace(/^\/+/, "")}`;
  }
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = imageName
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  return `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
}

function calcDiscount(regular: number, selling: number): number | null {
  if (regular > selling && regular > 0) {
    return Math.round(((regular - selling) / regular) * 100);
  }
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
      } catch {
        setAllCategories([]);
      }
    };
    void run();
  }, []);

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
    if (!isAuthenticated || !apiClient) {
      setWishlistByProductId({});
      return;
    }
    let cancelled = false;
    const run = async () => {
      try {
        const res = await apiClient.get("/wishlist/me");
        const data = (res.data as WishlistResponse) || { items: [], itemCount: 0 };
        if (cancelled) return;
        const map: Record<string, string> = {};
        for (const item of data.items || []) {
          if (item.productId && item.id) {
            map[item.productId] = item.id;
          }
        }
        setWishlistByProductId(map);
      } catch {
        if (!cancelled) {
          setWishlistByProductId({});
        }
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, apiClient]);

  useEffect(() => {
    const run = async () => {
      setProductsLoading(true);
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const sort = sortParams(sortBy);
        const canUseDirectCategoryQuery =
          selectedParentNames.length <= 1 && selectedSubNames.length <= 1;

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

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    if (productsLoading) return;
    setPage(0);
    setSearch(query);
  };

  const applyPriceFilter = (e: FormEvent) => {
    e.preventDefault();
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

  const clearAllFilters = () => {
    setQuery("");
    setSearch("");
    setSelectedParentNames([]);
    setSelectedSubNames([]);
    setMinPriceInput("");
    setMaxPriceInput("");
    setAppliedMinPrice(null);
    setAppliedMaxPrice(null);
    setSortBy("newest");
    setPage(0);
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

  const applyWishlistResponse = (payload: WishlistResponse) => {
    const map: Record<string, string> = {};
    for (const item of payload.items || []) {
      if (item.productId && item.id) {
        map[item.productId] = item.id;
      }
    }
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
        setWishlistByProductId((old) => {
          const next = { ...old };
          delete next[productId];
          return next;
        });
        toast.success("Removed from wishlist");
      } else {
        const res = await apiClient.post("/wishlist/me/items", { productId });
        applyWishlistResponse((res.data as WishlistResponse) || { items: [], itemCount: 0 });
        toast.success("Added to wishlist");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Wishlist update failed");
    } finally {
      setWishlistPendingProductId(null);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--bg)]">
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
        <header className="bg-[var(--header-bg)] shadow-lg">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2 text-white no-underline">
              <span className="inline-grid h-8 w-8 place-items-center rounded-md bg-white/10 text-xs font-bold text-white">RS</span>
              <p className="text-lg font-bold text-white">Rumal Store</p>
            </Link>
            <Link href="/" className="rounded-lg bg-[var(--brand)] px-5 py-2 text-sm font-semibold text-white no-underline transition hover:bg-[var(--brand-hover)]">
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">Products</span>
        </nav>

        <CategoryMenu />

        <CatalogToolbar
          title="Product Catalog"
          status={status}
          filterSummary={activeFilterLabel}
          sortBy={sortBy}
          loading={productsLoading}
          query={query}
          onSortChange={(value) => {
            setSortBy(value);
            setPage(0);
          }}
          onResetFilters={clearAllFilters}
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
            loading={productsLoading}
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
              {products.map((p, idx) => {
                const discount = calcDiscount(p.regularPrice, p.sellingPrice);
                const isWished = Boolean(wishlistByProductId[p.id]);
                const wishlistBusy = wishlistPendingProductId === p.id;
                return (
                  <Link
                    href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`}
                    key={p.id}
                    className="product-card animate-rise relative no-underline"
                    style={{ animationDelay: `${idx * 50}ms` }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
                    {isAuthenticated && (
                      <button
                        type="button"
                        onClick={(event) => { void toggleWishlist(event, p.id); }}
                        disabled={wishlistBusy}
                        className={`absolute right-2 top-2 z-20 inline-flex h-8 w-8 items-center justify-center rounded-full border text-xs transition disabled:cursor-not-allowed disabled:opacity-60 ${
                          isWished
                            ? "border-red-500 bg-red-50 text-red-600"
                            : "border-[var(--line)] bg-white text-[var(--muted)] hover:border-red-300 hover:text-red-500"
                        }`}
                        title={isWished ? "Remove from wishlist" : "Add to wishlist"}
                        aria-label={isWished ? "Remove from wishlist" : "Add to wishlist"}
                      >
                        {wishlistBusy ? (
                          "..."
                        ) : (
                          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill={isWished ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
                          </svg>
                        )}
                      </button>
                    )}
                    <div className="aspect-square overflow-hidden bg-[#f8f8f8]">
                      {resolveImageUrl(p.mainImage) ? (
                        <Image
                          src={resolveImageUrl(p.mainImage) || ""}
                          alt={p.name}
                          width={400}
                          height={400}
                          className="product-card-img"
                          unoptimized
                        />
                      ) : (
                        <div className="grid h-full w-full place-items-center bg-gradient-to-br from-gray-100 to-gray-200 text-sm font-semibold text-gray-500">
                          Image
                        </div>
                      )}
                    </div>
                    <div className="product-card-body">
                      <p className="line-clamp-2 text-sm font-medium text-[var(--ink)]">{p.name}</p>
                      <p className="mt-1 line-clamp-1 text-xs text-[var(--muted)]">{p.shortDescription}</p>
                      <p className="mt-1.5 text-[10px] text-[var(--muted)]">SKU: {p.sku}</p>
                      <div className="mt-2 flex items-center gap-1">
                        <span className="price-current">{money(p.sellingPrice)}</span>
                        {p.discountedPrice !== null && (
                          <>
                            <span className="price-original">{money(p.regularPrice)}</span>
                            {discount && <span className="price-discount-badge">-{discount}%</span>}
                          </>
                        )}
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>

            {products.length === 0 && !productsLoading && (
              <div className="empty-state mt-6">
                <div className="empty-state-icon">Search</div>
                <p className="empty-state-title">No products found</p>
                <p className="empty-state-desc">Try adjusting your search and filters</p>
                <button
                  disabled={productsLoading}
                  onClick={clearAllFilters}
                  className="btn-primary inline-block px-6 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Clear All Filters
                </button>
              </div>
            )}

            <Pagination
              currentPage={page}
              totalPages={totalPages}
              onPageChange={setPage}
              disabled={productsLoading}
            />
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
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-8">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="overflow-hidden rounded-xl">
                <div className="skeleton h-48 w-full" />
                <div className="space-y-2 p-4">
                  <div className="skeleton h-4 w-3/4" />
                  <div className="skeleton h-5 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    }>
      <ProductsPageContent />
    </Suspense>
  );
}
