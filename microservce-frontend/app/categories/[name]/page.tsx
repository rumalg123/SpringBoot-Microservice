"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import CategoryMenu from "../../components/CategoryMenu";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
import { useAuthSession } from "../../../lib/authSession";

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

type Category = {
  id: string;
  name: string;
  slug?: string | null;
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

export default function CategoryProductsPage() {
  const params = useParams<{ name: string }>();
  const router = useRouter();
  const { isAuthenticated, profile, logout, canViewAdmin } = useAuthSession();

  const [resolvedCategory, setResolvedCategory] = useState<Category | null>(null);
  const [mainCategory, setMainCategory] = useState("");
  const [subCategory, setSubCategory] = useState("");
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [routeLoading, setRouteLoading] = useState(true);
  const [productsLoading, setProductsLoading] = useState(false);
  const [status, setStatus] = useState("Resolving category...");

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
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) {
          throw new Error("Failed to load categories");
        }

        const categories = ((await res.json()) as Category[]) || [];
        const match = categories.find((category) => toCategorySlug(category) === requestedSlug);
        if (!match) {
          router.replace("/products");
          return;
        }

        if (match.type === "PARENT") {
          setMainCategory(match.name);
          setSubCategory("");
        } else {
          const parent = categories.find((category) => category.id === match.parentCategoryId);
          setMainCategory(parent?.name || "");
          setSubCategory(match.name);
        }

        setResolvedCategory(match);
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

  useEffect(() => {
    if (routeLoading || !resolvedCategory) return;
    const run = async () => {
      setProductsLoading(true);
      setStatus("Loading products...");
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const searchParams = new URLSearchParams();
        searchParams.set("page", String(page));
        searchParams.set("size", "12");
        if (search.trim()) searchParams.set("q", search.trim());
        if (mainCategory.trim()) searchParams.set("mainCategory", mainCategory.trim());
        if (subCategory.trim()) searchParams.set("subCategory", subCategory.trim());

        const res = await fetch(`${apiBase}/products?${searchParams.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch products");

        const data = (await res.json()) as ProductPageResponse;
        setProducts(data.content || []);
        setTotalPages(Math.max(data.totalPages || 1, 1));
        setStatus(`Showing ${data.content?.length || 0} products`);
      } catch {
        setProducts([]);
        setTotalPages(1);
        setStatus("Failed to load products.");
      } finally {
        setProductsLoading(false);
      }
    };

    void run();
  }, [routeLoading, resolvedCategory, mainCategory, subCategory, search, page]);

  const activeFilter = useMemo(() => {
    if (subCategory && mainCategory) return `${mainCategory} > ${subCategory}`;
    if (subCategory) return subCategory;
    if (mainCategory) return mainCategory;
    return resolvedCategory?.name || "Category";
  }, [mainCategory, resolvedCategory, subCategory]);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    if (productsLoading) return;
    setPage(0);
    setSearch(query);
  };

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          canViewAdmin={canViewAdmin}
          onLogout={() => { void logout(); }}
        />
      )}

      {!isAuthenticated && (
        <header className="bg-[var(--header-bg)] shadow-lg">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2 text-white no-underline">
              <span className="text-2xl">ðŸ›’</span>
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
          <span className="breadcrumb-sep">â€º</span>
          <Link href="/categories">Categories</Link>
          <span className="breadcrumb-sep">â€º</span>
          <span className="breadcrumb-current">{activeFilter}</span>
        </nav>

        <CategoryMenu />

        <section className="mb-5 animate-rise rounded-xl bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold text-[var(--ink)]">{activeFilter} Products</h1>
              <p className="mt-0.5 text-sm text-[var(--muted)]">{status}</p>
            </div>
            <Link
              href={isAuthenticated ? "/orders" : "/"}
              className="btn-outline no-underline text-sm"
            >
              {isAuthenticated ? "ðŸ“¦ My Orders" : "ðŸ”‘ Sign In"}
            </Link>
          </div>

          <form onSubmit={onSearch} className="grid gap-3 md:grid-cols-[1fr,auto]">
            <div className="relative flex items-center overflow-hidden rounded-lg border border-[var(--line)] bg-white">
              <span className="pl-3 text-[var(--muted)]">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
              </span>
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={`Search in ${activeFilter}...`}
                className="flex-1 border-none px-3 py-2.5 text-sm outline-none"
                disabled={routeLoading}
              />
              {query && (
                <button
                  type="button"
                  onClick={() => { setQuery(""); setSearch(""); setPage(0); }}
                  disabled={productsLoading || routeLoading}
                  className="mr-2 flex h-6 w-6 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-600 hover:bg-gray-300 disabled:cursor-not-allowed disabled:opacity-60"
                  title="Clear search"
                >
                  Ã—
                </button>
              )}
            </div>
            <button
              type="submit"
              disabled={productsLoading || routeLoading}
              className="btn-primary text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {productsLoading ? "Searching..." : "Search"}
            </button>
          </form>
        </section>

        {routeLoading && (
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
        )}

        {!routeLoading && (
          <>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {products.map((p, idx) => {
                const discount = calcDiscount(p.regularPrice, p.sellingPrice);
                return (
                  <Link
                    href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`}
                    key={p.id}
                    className="product-card animate-rise relative no-underline"
                    style={{ animationDelay: `${idx * 50}ms` }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
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
                          ðŸ“¦
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
                        <span className="star-rating">â˜…â˜…â˜…â˜…â˜† <span className="star-rating-count">4.5</span></span>
                        <span className="text-[10px] text-[var(--muted)]">100+ sold</span>
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>

            {products.length === 0 && status !== "Loading products..." && (
              <div className="empty-state mt-6">
                <div className="empty-state-icon">ðŸ”</div>
                <p className="empty-state-title">No products found in {activeFilter}</p>
                <p className="empty-state-desc">Try a different keyword or browse all products</p>
                <div className="mt-2 flex flex-wrap justify-center gap-2">
                  <button
                    disabled={productsLoading}
                    onClick={() => { setQuery(""); setSearch(""); setPage(0); }}
                    className="btn-primary inline-block px-6 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Clear Search
                  </button>
                  <Link href="/products" className="btn-outline inline-block px-6 py-2.5 text-sm no-underline">
                    Browse All Products
                  </Link>
                </div>
              </div>
            )}

            <Pagination
              currentPage={page}
              totalPages={totalPages}
              onPageChange={setPage}
              disabled={productsLoading}
            />
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
