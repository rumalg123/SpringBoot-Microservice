"use client";

import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import AppNav from "../components/AppNav";
import CategoryMenu from "../components/CategoryMenu";
import { useAuthSession } from "../../lib/authSession";

type ProductSummary = {
  id: string;
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

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function ProductsPageContent() {
  const searchParams = useSearchParams();
  const { isAuthenticated, profile, logout, canViewAdmin } = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [mainCategory, setMainCategory] = useState("");
  const [subCategory, setSubCategory] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [status, setStatus] = useState("Loading products...");

  useEffect(() => {
    const main = searchParams.get("mainCategory") || "";
    const sub = searchParams.get("subCategory") || "";
    const cat = searchParams.get("category") || "";
    setMainCategory(main);
    setSubCategory(sub);
    setCategory(cat);
    setPage(0);
  }, [searchParams]);

  useEffect(() => {
    const run = async () => {
      try {
        setStatus("Loading products...");
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("size", "12");
        params.set("type", "PARENT");
        if (search.trim()) params.set("q", search.trim());
        if (category.trim()) params.set("category", category.trim());
        if (mainCategory.trim()) params.set("mainCategory", mainCategory.trim());
        if (subCategory.trim()) params.set("subCategory", subCategory.trim());

        const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to fetch products");

        const data = (await res.json()) as ProductPageResponse;
        setProducts(data.content || []);
        setTotalPages(Math.max(data.totalPages || 1, 1));
        setStatus("Showing catalog results.");
      } catch {
        setStatus("Failed to load products.");
      }
    };

    void run();
  }, [search, category, mainCategory, subCategory, page]);

  const uniqueCategories = useMemo(() => {
    const all = new Set<string>();
    for (const p of products) {
      (p.categories || []).forEach((c) => all.add(c));
    }
    return Array.from(all).sort();
  }, [products]);

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(query);
  };

  return (
    <main className="mx-auto min-h-screen max-w-7xl px-6 py-8">
      <CategoryMenu />
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          canViewAdmin={canViewAdmin}
          onLogout={() => {
            void logout();
          }}
        />
      )}

      <section className="card-surface animate-rise rounded-3xl p-6 md:p-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs tracking-[0.22em] text-[var(--muted)]">PRODUCT CATALOG</p>
            <h1 className="text-4xl text-[var(--ink)]">Shop The Collection</h1>
          </div>
          <Link
            href={isAuthenticated ? "/orders" : "/"}
            className="rounded-full border border-[var(--line)] bg-white px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
          >
            {isAuthenticated ? "View My Purchases" : "Sign In"}
          </Link>
        </div>

        {(mainCategory || subCategory || category) && (
          <p className="mb-3 text-sm text-[var(--muted)]">
            Filtered by: {mainCategory || subCategory || category}
          </p>
        )}

        <form onSubmit={onSearch} className="mb-6 grid gap-3 md:grid-cols-[1fr,200px,auto]">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name, description, SKU"
            className="rounded-xl border border-[var(--line)] bg-white px-4 py-2 text-sm"
          />
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
            className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm capitalize"
          >
            <option value="">All categories</option>
            {uniqueCategories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <button type="submit" className="btn-brand rounded-xl px-4 py-2 text-sm font-semibold">
            Search
          </button>
        </form>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {products.map((p, idx) => (
            <Link
              href={`/products/${p.id}`}
              key={p.id}
              className="card-surface animate-rise rounded-2xl p-4 transition hover:-translate-y-1"
              style={{ animationDelay: `${idx * 50}ms` }}
            >
              <div className="mb-3 h-40 rounded-xl bg-[linear-gradient(150deg,#eee6dc,#f8f4ef)] p-3 text-xs text-[var(--muted)]">
                {p.mainImage || "image-placeholder.jpg"}
              </div>
              <p className="line-clamp-1 text-lg font-semibold text-[var(--ink)]">{p.name}</p>
              <p className="mt-1 line-clamp-2 text-sm text-[var(--muted)]">{p.shortDescription}</p>
              <p className="mt-2 text-xs text-[var(--muted)]">SKU: {p.sku}</p>
              <div className="mt-3 flex items-center gap-2">
                <span className="text-base font-semibold text-[var(--ink)]">{money(p.sellingPrice)}</span>
                {p.discountedPrice !== null && (
                  <span className="text-xs text-[var(--muted)] line-through">{money(p.regularPrice)}</span>
                )}
              </div>
            </Link>
          ))}
        </div>

        {products.length === 0 && (
          <p className="mt-8 rounded-xl border border-dashed border-[var(--line)] p-4 text-sm text-[var(--muted)]">
            No products found.
          </p>
        )}

        <div className="mt-6 flex items-center justify-between">
          <p className="text-sm text-[var(--muted)]">{status}</p>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((old) => Math.max(old - 1, 0))}
              disabled={page <= 0}
              className="rounded-lg border border-[var(--line)] bg-white px-3 py-1 text-sm disabled:opacity-50"
            >
              Prev
            </button>
            <span className="text-sm text-[var(--muted)]">
              Page {page + 1} / {Math.max(totalPages, 1)}
            </span>
            <button
              onClick={() => setPage((old) => (old + 1 < totalPages ? old + 1 : old))}
              disabled={page + 1 >= totalPages}
              className="rounded-lg border border-[var(--line)] bg-white px-3 py-1 text-sm disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}

export default function ProductsPage() {
  return (
    <Suspense fallback={<main className="mx-auto min-h-screen max-w-7xl px-6 py-8 text-[var(--muted)]">Loading products...</main>}>
      <ProductsPageContent />
    </Suspense>
  );
}
