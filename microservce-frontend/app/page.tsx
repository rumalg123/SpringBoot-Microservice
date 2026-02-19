"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthSession } from "../lib/authSession";
import CategoryMenu from "./components/CategoryMenu";

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
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function LandingPage() {
  const router = useRouter();
  const session = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState("loading");

  useEffect(() => {
    if (session.status === "ready" && session.isAuthenticated) {
      router.replace(session.canViewAdmin ? "/admin/orders" : "/products");
    }
  }, [router, session.isAuthenticated, session.status, session.canViewAdmin]);

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products?page=0&size=4`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to load products");
        const data = (await res.json()) as ProductPageResponse;
        setProducts(data.content || []);
        setStatus("ready");
      } catch {
        setStatus("error");
      }
    };
    void run();
  }, []);

  const heroProducts = useMemo(() => products.slice(0, 3), [products]);

  return (
    <main className="mx-auto min-h-screen max-w-7xl px-6 py-10">
      <CategoryMenu />
      <section className="animate-rise relative overflow-hidden rounded-3xl border border-[var(--line)] bg-[linear-gradient(130deg,#2e2018,#50362b)] p-8 text-white shadow-2xl md:p-12">
        <div className="absolute -right-14 -top-20 h-64 w-64 rounded-full bg-[#f18f63]/20 blur-3xl" />
        <div className="absolute -bottom-24 -left-14 h-64 w-64 rounded-full bg-[#4fa2ab]/20 blur-3xl" />
        <div className="relative grid gap-10 md:grid-cols-[1.2fr,0.8fr] md:items-center">
          <div className="space-y-6">
            <p className="inline-flex rounded-full border border-white/35 bg-white/8 px-3 py-1 text-xs tracking-[0.24em] text-zinc-100">
              CURATED CATALOG
            </p>
            <h1 className="text-4xl font-semibold leading-tight md:text-6xl">
              Discover your next
              <br />
              favorite product.
              <br />
              Shop with confidence.
            </h1>
            <p className="max-w-2xl text-sm text-zinc-200 md:text-base">
              Browse catalog items publicly, then sign in to track purchases and manage your account.
            </p>
            <div className="flex flex-wrap gap-3">
              <button
                onClick={() => void session.login("/products")}
                disabled={session.status === "loading" || session.status === "idle"}
                className="rounded-full bg-[#c8603a] px-5 py-2 text-sm font-semibold text-white transition hover:bg-[#d26f4c] disabled:opacity-50"
              >
                Login
              </button>
              <button
                onClick={() => void session.signup("/products")}
                disabled={session.status === "loading" || session.status === "idle"}
                className="rounded-full border border-white/45 bg-white/8 px-5 py-2 text-sm font-semibold text-white transition hover:bg-white/12 disabled:opacity-50"
              >
                Create Account
              </button>
              <Link
                href="/products"
                className="rounded-full border border-white/40 px-5 py-2 text-sm font-semibold text-zinc-100 transition hover:bg-white/10"
              >
                Browse Catalog
              </Link>
            </div>
            {session.status === "error" && (
              <p className="rounded-xl border border-red-300/40 bg-red-500/15 px-3 py-2 text-xs text-red-100">
                {session.error}
              </p>
            )}
          </div>
          <div className="grid gap-3 rounded-2xl border border-white/25 bg-white/8 p-5 text-sm">
            <p className="text-xs tracking-[0.2em] text-zinc-200">FEATURED</p>
            {heroProducts.length === 0 && <p className="text-zinc-200">Loading featured products...</p>}
            {heroProducts.map((p) => (
              <Link
                href={`/products/${p.id}`}
                key={p.id}
                className="rounded-xl border border-white/20 bg-black/20 px-3 py-3 transition hover:border-white/40"
              >
                <p className="text-sm font-semibold">{p.name}</p>
                <p className="mt-1 text-xs text-zinc-200">{money(p.sellingPrice)}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="animate-rise mt-10">
        <div className="mb-5 flex items-end justify-between">
          <div>
            <p className="text-xs tracking-[0.22em] text-[var(--muted)]">SHOP NOW</p>
            <h2 className="text-3xl text-[var(--ink)]">Trending Products</h2>
          </div>
          <Link href="/products" className="text-sm font-semibold text-[var(--brand)] hover:underline">
            View all products
          </Link>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {status === "loading" && <p className="text-sm text-[var(--muted)]">Loading catalog...</p>}
          {status === "error" && (
            <p className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900">
              Product catalog is unavailable right now.
            </p>
          )}
          {products.map((product, idx) => (
            <Link
              href={`/products/${product.id}`}
              key={product.id}
              className="card-surface rounded-2xl p-4 transition hover:-translate-y-0.5"
              style={{ animationDelay: `${idx * 60}ms` }}
            >
              <div className="mb-3 h-40 rounded-xl bg-[linear-gradient(145deg,#ece5da,#f8f4ee)] p-3 text-xs text-[var(--muted)]">
                {product.mainImage || "product-image.jpg"}
              </div>
              <p className="line-clamp-1 text-lg font-semibold text-[var(--ink)]">{product.name}</p>
              <p className="mt-1 line-clamp-2 text-sm text-[var(--muted)]">{product.shortDescription}</p>
              <div className="mt-3 flex items-center gap-2">
                <span className="text-base font-semibold text-[var(--ink)]">{money(product.sellingPrice)}</span>
                {product.discountedPrice !== null && (
                  <span className="text-xs text-[var(--muted)] line-through">
                    {money(product.regularPrice)}
                  </span>
                )}
              </div>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
