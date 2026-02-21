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
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

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

function ProductsPageContent() {
  const searchParams = useSearchParams();
  const { isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [mainCategory, setMainCategory] = useState("");
  const [subCategory, setSubCategory] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [productsLoading, setProductsLoading] = useState(false);
  const [status, setStatus] = useState("Loading products...");
  const [wishlistByProductId, setWishlistByProductId] = useState<Record<string, string>>({});
  const [wishlistPendingProductId, setWishlistPendingProductId] = useState<string | null>(null);

  useEffect(() => {
    const q = searchParams.get("q") || "";
    const main = searchParams.get("mainCategory") || "";
    const sub = searchParams.get("subCategory") || "";
    const cat = searchParams.get("category") || "";
    setQuery(q);
    setSearch(q);
    setMainCategory(main);
    setSubCategory(sub);
    setCategory(cat);
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
        setStatus("Loading products...");
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("size", "12");
        if (search.trim()) params.set("q", search.trim());
        if (category.trim()) params.set("category", category.trim());
        if (mainCategory.trim()) params.set("mainCategory", mainCategory.trim());
        if (subCategory.trim()) params.set("subCategory", subCategory.trim());

        const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch products");

        const data = (await res.json()) as ProductPageResponse;
        setProducts(data.content || []);
        setTotalPages(Math.max(data.totalPages || 1, 1));
        setStatus(`Showing ${data.content?.length || 0} products`);
      } catch {
        setStatus("Failed to load products.");
      } finally {
        setProductsLoading(false);
      }
    };

    void run();
  }, [search, category, mainCategory, subCategory, page]);

  const uniqueCategories = useMemo(
    () => Array.from(new Set(allCategories.map((c) => c.name))).sort((a, b) => a.localeCompare(b)),
    [allCategories]
  );

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    if (productsLoading) return;
    setPage(0);
    setSearch(query);
  };

  const activeFilter = mainCategory || subCategory || category;
  const activeSearch = search.trim();

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
              <span className="text-2xl">🛒</span>
              <p className="text-lg font-bold text-white">Rumal Store</p>
            </Link>
            <Link href="/" className="rounded-lg bg-[var(--brand)] px-5 py-2 text-sm font-semibold text-white no-underline transition hover:bg-[var(--brand-hover)]">
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">{activeFilter ? activeFilter : "All Products"}</span>
        </nav>

        <CategoryMenu />

        {/* Page Header + Search */}
        <section className="mb-5 animate-rise rounded-xl bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold text-[var(--ink)]">
                {activeFilter
                  ? `Results for "${activeFilter}"`
                  : activeSearch
                    ? `Results for "${activeSearch}"`
                    : "All Products"}
              </h1>
              <p className="mt-0.5 text-sm text-[var(--muted)]">{status}</p>
            </div>
            <Link
              href={isAuthenticated ? "/orders" : "/"}
              className="btn-outline no-underline text-sm"
            >
              {isAuthenticated ? "📦 My Orders" : "🔑 Sign In"}
            </Link>
          </div>

          <form onSubmit={onSearch} className="grid gap-3 md:grid-cols-[1fr,200px,auto]">
            <div className="relative flex items-center overflow-hidden rounded-lg border border-[var(--line)] bg-white">
              <span className="pl-3 text-[var(--muted)]">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
              </span>
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search by name, description, SKU..."
                className="flex-1 border-none px-3 py-2.5 text-sm outline-none"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => { setQuery(""); setSearch(""); setPage(0); }}
                  disabled={productsLoading}
                  className="mr-2 flex h-6 w-6 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-600 hover:bg-gray-300 disabled:cursor-not-allowed disabled:opacity-60"
                  title="Clear search"
                >
                  ×
                </button>
              )}
            </div>
            <select
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                if (!e.target.value) {
                  setMainCategory("");
                  setSubCategory("");
                }
                setPage(0);
              }}
              disabled={productsLoading}
              className="rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
            >
              <option value="">All Categories</option>
              {uniqueCategories.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
            <button
              type="submit"
              disabled={productsLoading}
              className="btn-primary text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {productsLoading ? "Searching..." : "Search"}
            </button>
          </form>
        </section>

        {/* Product Grid */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
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
                    <div className="grid h-full w-full place-items-center bg-gradient-to-br from-gray-100 to-gray-200 text-3xl">
                      📦
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
                  <div className="mt-1.5 flex items-center justify-between">
                    <span className="star-rating">★★★★☆ <span className="star-rating-count">4.5</span></span>
                    <span className="text-[10px] text-[var(--muted)]">100+ sold</span>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>

        {products.length === 0 && status !== "Loading products..." && (
          <div className="empty-state mt-6">
            <div className="empty-state-icon">🔍</div>
            <p className="empty-state-title">No products found</p>
            <p className="empty-state-desc">Try adjusting your search or browse all categories</p>
            <button
              disabled={productsLoading}
              onClick={() => { setQuery(""); setSearch(""); setCategory(""); setMainCategory(""); setSubCategory(""); setPage(0); }}
              className="btn-primary inline-block px-6 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              Clear All Filters
            </button>
          </div>
        )}

        {/* Pagination */}
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={setPage}
          disabled={productsLoading}
        />
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
