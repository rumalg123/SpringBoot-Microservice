"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useAuthSession } from "../lib/authSession";
import CategoryMenu from "./components/CategoryMenu";
import Footer from "./components/Footer";

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

export default function LandingPage() {
  const router = useRouter();
  const session = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState("loading");
  const [authActionPending, setAuthActionPending] = useState<"login" | "signup" | null>(null);

  useEffect(() => {
    if (session.status === "ready" && session.isAuthenticated) {
      router.replace(session.canViewAdmin ? "/admin/orders" : "/products");
    }
  }, [router, session.isAuthenticated, session.status, session.canViewAdmin]);

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products?page=0&size=8`, { cache: "no-store" });
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

  const dealProducts = useMemo(() => products.filter((p) => p.discountedPrice !== null).slice(0, 4), [products]);
  const trendingProducts = useMemo(() => products.slice(0, 8), [products]);
  const authBusy = authActionPending !== null || session.status === "loading" || session.status === "idle";

  const startLogin = async () => {
    if (authBusy) return;
    setAuthActionPending("login");
    try {
      await session.login("/products");
    } finally {
      setAuthActionPending(null);
    }
  };

  const startSignup = async () => {
    if (authBusy) return;
    setAuthActionPending("signup");
    try {
      await session.signup("/products");
    } finally {
      setAuthActionPending(null);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      {/* Top Header Bar (simplified for landing) */}
      <header className="bg-[var(--header-bg)] shadow-lg">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
          <Link href="/" className="flex items-center gap-2 text-white no-underline">
            <span className="text-2xl">🛒</span>
            <div>
              <p className="text-lg font-bold leading-tight text-white">Rumal Store</p>
              <p className="text-[10px] tracking-[0.15em] text-gray-400">ONLINE MARKETPLACE</p>
            </div>
          </Link>
          <div className="mx-6 hidden flex-1 md:block">
            <div className="flex max-w-xl items-center overflow-hidden rounded-lg bg-white">
              <input
                type="text"
                placeholder="Search products, brands and more..."
                className="flex-1 border-none px-4 py-2.5 text-sm text-[var(--ink)] outline-none"
                readOnly
                onFocus={(e) => { e.target.blur(); window.location.href = "/products"; }}
              />
              <button className="bg-[var(--brand)] px-5 py-2.5 text-white transition hover:bg-[var(--brand-hover)]">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
              </button>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => { void startLogin(); }}
              disabled={authBusy}
              className="rounded-lg bg-[var(--brand)] px-5 py-2 text-sm font-semibold text-white transition hover:bg-[var(--brand-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {authActionPending === "login" ? "Redirecting..." : "Login"}
            </button>
            <button
              onClick={() => { void startSignup(); }}
              disabled={authBusy}
              className="hidden rounded-lg border border-white/30 px-5 py-2 text-sm font-semibold text-white transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50 sm:inline-block"
            >
              {authActionPending === "signup" ? "Redirecting..." : "Sign Up"}
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Category Menu */}
        <CategoryMenu />

        {/* Hero Banner */}
        <section className="animate-rise mb-6 overflow-hidden rounded-2xl bg-gradient-to-r from-[#ff4747] via-[#ff6b35] to-[#ff8c00] p-8 text-white shadow-xl md:p-12">
          <div className="relative grid items-center gap-8 md:grid-cols-[1.4fr,0.6fr]">
            <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
            <div className="relative space-y-5">
              <span className="inline-block rounded-full bg-white/20 px-4 py-1.5 text-xs font-bold uppercase tracking-wider backdrop-blur">
                🔥 Mega Sale Live Now
              </span>
              <h1 className="text-3xl font-extrabold leading-tight md:text-5xl">
                Discover Amazing
                <br />
                Deals Every Day
              </h1>
              <p className="max-w-lg text-sm text-white/80 md:text-base">
                Shop thousands of products at unbeatable prices. Free shipping on your first order.
                Sign in to start shopping!
              </p>
              <div className="flex flex-wrap gap-3">
                <Link
                  href="/products"
                  className="inline-flex items-center gap-2 rounded-lg bg-white px-6 py-3 text-sm font-bold no-underline shadow-sm transition hover:shadow-lg"
                  style={{ color: 'var(--brand)' }}
                >
                  🛍️ Shop Now
                </Link>
                <button
                  onClick={() => { void startSignup(); }}
                  disabled={authBusy}
                  className="rounded-lg border-2 border-white bg-white/10 px-6 py-3 text-sm font-bold text-white transition hover:bg-white/25 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {authActionPending === "signup" ? "Redirecting..." : "Create Account"}
                </button>
              </div>
            </div>
            <div className="hidden text-center md:block">
              <div className="inline-block rounded-2xl bg-white/15 p-6 backdrop-blur">
                <p className="text-5xl font-extrabold">UP TO</p>
                <p className="text-7xl font-black text-yellow-300">70%</p>
                <p className="text-2xl font-extrabold">OFF</p>
              </div>
            </div>
          </div>
        </section>

        {session.status === "error" && (
          <p className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {session.error}
          </p>
        )}

        {/* Trust Bar */}
        <section className="mb-6 animate-rise" style={{ animationDelay: "100ms" }}>
          <div className="trust-bar">
            <div className="trust-item">
              <span className="trust-icon">🚚</span>
              <div className="trust-text">
                <h4>Free Shipping</h4>
                <p>On orders over $25</p>
              </div>
            </div>
            <div className="trust-item">
              <span className="trust-icon">🔒</span>
              <div className="trust-text">
                <h4>Secure Payment</h4>
                <p>100% secure checkout</p>
              </div>
            </div>
            <div className="trust-item">
              <span className="trust-icon">🔄</span>
              <div className="trust-text">
                <h4>Easy Returns</h4>
                <p>30-day return policy</p>
              </div>
            </div>
            <div className="trust-item">
              <span className="trust-icon">💬</span>
              <div className="trust-text">
                <h4>24/7 Support</h4>
                <p>Ready to help you</p>
              </div>
            </div>
          </div>
        </section>

        {/* Flash Deals */}
        {dealProducts.length > 0 && (
          <section className="mb-8 animate-rise" style={{ animationDelay: "200ms" }}>
            <div className="section-header">
              <div className="flex items-center gap-3">
                <h2>⚡ Flash Deals</h2>
                <span className="flash-label">🔥 Limited Time</span>
              </div>
              <Link href="/products" className="text-sm font-semibold text-[var(--brand)] no-underline hover:underline">
                View All →
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {dealProducts.map((product, idx) => {
                const discount = calcDiscount(product.regularPrice, product.sellingPrice);
                return (
                  <Link
                    href={`/products/${product.id}`}
                    key={product.id}
                    className="product-card animate-rise relative no-underline"
                    style={{ animationDelay: `${idx * 80}ms` }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
                    <div className="aspect-square overflow-hidden bg-[#f8f8f8]">
                      {resolveImageUrl(product.mainImage) ? (
                        <Image
                          src={resolveImageUrl(product.mainImage) || ""}
                          alt={product.name}
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
                      <p className="line-clamp-2 text-sm font-medium text-[var(--ink)]">{product.name}</p>
                      <div className="mt-2 flex items-center gap-1">
                        <span className="price-current">{money(product.sellingPrice)}</span>
                        {product.discountedPrice !== null && (
                          <span className="price-original">{money(product.regularPrice)}</span>
                        )}
                      </div>
                      <div className="mt-1.5">
                        <span className="star-rating">★★★★★</span>
                        <span className="star-rating-count">| 1k+ sold</span>
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>
        )}

        {/* Trending Products */}
        <section className="mb-8 animate-rise" style={{ animationDelay: "300ms" }}>
          <div className="section-header">
            <h2>🔥 Trending Products</h2>
            <Link href="/products" className="text-sm font-semibold text-[var(--brand)] no-underline hover:underline">
              View All →
            </Link>
          </div>

          {status === "loading" && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="overflow-hidden rounded-xl">
                  <div className="skeleton h-48 w-full" />
                  <div className="p-4 space-y-2">
                    <div className="skeleton h-4 w-3/4" />
                    <div className="skeleton h-5 w-1/2" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {status === "error" && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              ⚠️ Product catalog is unavailable right now. Please try again later.
            </p>
          )}

          {status === "ready" && trendingProducts.length === 0 && (
            <p className="rounded-xl border border-dashed border-[var(--line)] px-4 py-6 text-center text-sm text-[var(--muted)]">
              No products available yet. Check back soon!
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {trendingProducts.map((product, idx) => {
              const discount = calcDiscount(product.regularPrice, product.sellingPrice);
              return (
                <Link
                  href={`/products/${product.id}`}
                  key={product.id}
                  className="product-card animate-rise relative no-underline"
                  style={{ animationDelay: `${idx * 60}ms` }}
                >
                  {discount && <span className="badge-sale">-{discount}%</span>}
                  <div className="aspect-square overflow-hidden bg-[#f8f8f8]">
                    {resolveImageUrl(product.mainImage) ? (
                      <Image
                        src={resolveImageUrl(product.mainImage) || ""}
                        alt={product.name}
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
                    <p className="line-clamp-2 text-sm font-medium text-[var(--ink)]">{product.name}</p>
                    <p className="mt-1 line-clamp-1 text-xs text-[var(--muted)]">{product.shortDescription}</p>
                    <div className="mt-2 flex items-center gap-1">
                      <span className="price-current">{money(product.sellingPrice)}</span>
                      {product.discountedPrice !== null && (
                        <span className="price-original">{money(product.regularPrice)}</span>
                      )}
                    </div>
                    <div className="mt-1.5 flex items-center justify-between">
                      <div>
                        <span className="star-rating">★★★★☆</span>
                        <span className="star-rating-count">4.5</span>
                      </div>
                      <span className="text-[10px] text-[var(--muted)]">500+ sold</span>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>

        {/* CTA Banner */}
        <section className="mb-8 animate-rise overflow-hidden rounded-2xl bg-gradient-to-r from-[var(--header-bg)] to-[#2d2d5e] p-8 text-center text-white" style={{ animationDelay: "400ms" }}>
          <h2 className="text-2xl font-bold text-white md:text-3xl">Join Rumal Store Today!</h2>
          <p className="mx-auto mt-2 max-w-xl text-sm text-gray-300">
            Create your free account and start shopping. Get exclusive deals, track your orders, and enjoy a seamless shopping experience.
          </p>
          <div className="mt-5 flex justify-center gap-3">
            <button
              onClick={() => { void startSignup(); }}
              disabled={authBusy}
              className="rounded-lg bg-[var(--brand)] px-8 py-3 text-sm font-bold text-white transition hover:bg-[var(--brand-hover)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {authActionPending === "signup" ? "Redirecting..." : "Sign Up Free"}
            </button>
            <Link
              href="/products"
              className="rounded-lg border border-white/30 px-8 py-3 text-sm font-bold text-white no-underline transition hover:bg-white/10"
            >
              Browse Products
            </Link>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
