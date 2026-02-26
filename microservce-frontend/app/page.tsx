"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useAuthSession } from "../lib/authSession";
import CategoryMenu from "./components/CategoryMenu";
import CartNavWidget from "./components/CartNavWidget";
import WishlistNavWidget from "./components/WishlistNavWidget";
import Footer from "./components/Footer";
import PosterSlot from "./components/posters/PosterSlot";
import ProductSearchBar from "./components/search/ProductSearchBar";
import { API_BASE } from "../lib/constants";
import { money, calcDiscount } from "../lib/format";
import { resolveImageUrl } from "../lib/image";
import type { ProductSummary, PagedResponse } from "../lib/types";
import type { LoadingStatus } from "../lib/types/status";
import { fetchTrending, fetchRecommended, fetchRecentlyViewed, getOrCreateSessionId, type PersonalizationProduct } from "../lib/personalization";
import ProductCard from "./components/catalog/ProductCard";
import ProductCardSkeleton from "./components/catalog/ProductCardSkeleton";
import HeroSection from "./components/home/HeroSection";
import TrustBar from "./components/home/TrustBar";
import CTABanner from "./components/home/CTABanner";

export default function LandingPage() {
  const session = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState<LoadingStatus>("loading");
  const [authActionPending, setAuthActionPending] = useState<"login" | "signup" | "forgot" | null>(null);
  const [logoutPending, setLogoutPending] = useState(false);
  const [trending, setTrending] = useState<PersonalizationProduct[]>([]);
  const [recommended, setRecommended] = useState<PersonalizationProduct[]>([]);
  const [recentlyViewed, setRecentlyViewed] = useState<PersonalizationProduct[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/products?page=0&size=8`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) throw new Error("Failed to load products");
        if (controller.signal.aborted) return;
        const data = (await res.json()) as PagedResponse<ProductSummary>;
        setProducts(data.content || []);
        setStatus("ready");
      } catch {
        if (!controller.signal.aborted) setStatus("error");
      }
    };
    void run();
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (session.status !== "ready" && session.status !== "error") return;
    const token = session.isAuthenticated ? session.token : null;
    getOrCreateSessionId();
    fetchTrending(8).then(setTrending).catch((e) => console.error("Failed to load trending:", e));
    fetchRecommended(8, token).then(setRecommended).catch((e) => console.error("Failed to load recommended:", e));
    fetchRecentlyViewed(8, token).then(setRecentlyViewed).catch((e) => console.error("Failed to load recently viewed:", e));
  }, [session.status, session.isAuthenticated, session.token]);

  useEffect(() => {
    const resetAuthPending = () => setAuthActionPending(null);
    const onVisibilityChange = () => { if (document.visibilityState === "visible") resetAuthPending(); };
    window.addEventListener("pageshow", resetAuthPending);
    window.addEventListener("focus", resetAuthPending);
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      window.removeEventListener("pageshow", resetAuthPending);
      window.removeEventListener("focus", resetAuthPending);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);

  const dealProducts = useMemo(() => products.filter((p) => p.discountedPrice !== null).slice(0, 4), [products]);
  const trendingProducts = trending.length > 0 ? trending : products.slice(0, 8);
  const authBusy = authActionPending !== null || session.status === "loading" || session.status === "idle";

  const startLogin = async () => {
    if (authBusy) return;
    setAuthActionPending("login");
    try { await session.login("/products"); } finally { setAuthActionPending(null); }
  };
  const startSignup = async () => {
    if (authBusy) return;
    setAuthActionPending("signup");
    try { await session.signup("/products"); } finally { setAuthActionPending(null); }
  };
  const startForgotPassword = async () => {
    if (authBusy) return;
    setAuthActionPending("forgot");
    try { await session.forgotPassword("/"); } finally { setAuthActionPending(null); }
  };
  const startLogout = async () => {
    if (logoutPending) return;
    setLogoutPending(true);
    try { await session.logout(); } finally { setLogoutPending(false); }
  };

  return (
    <div className="min-h-screen bg-bg">
      {/* --- Header --- */}
      <header className="sticky top-0 z-50 border-b border-line-bright bg-header-bg backdrop-blur-[20px]">

        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
          {/* Logo */}
          <Link href="/" className="flex shrink-0 items-center gap-3 no-underline">
            <div className="flex h-9 w-9 items-center justify-center rounded-md bg-[var(--gradient-brand)] text-xs font-black text-white shadow-[0_0_16px_var(--brand-glow)]">RS</div>
            <div>
              <p className="m-0 font-[Syne,sans-serif] text-lg font-extrabold text-white">
                Rumal Store
              </p>
              <p className="m-0 text-[9px] tracking-[0.18em] text-brand opacity-55">
                ONLINE MARKETPLACE
              </p>
            </div>
          </Link>

          {/* Search */}
          <div className="mx-4 hidden flex-1 md:block">
            <ProductSearchBar maxWidth={520} />
          </div>

          {/* Auth Area */}
          {session.isAuthenticated ? (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <WishlistNavWidget apiClient={session.apiClient} />
              <CartNavWidget apiClient={session.apiClient} emailVerified={session.emailVerified} />
              <span
                className="hidden md:inline-block rounded-full border border-line-bright bg-brand-soft px-3 py-1.5 text-xs font-medium text-brand"
              >
                {(session.profile?.email as string) || "User"}
              </span>
              {session.canViewAdmin ? (
                <Link href="/admin/orders"
                  className="rounded-xl border border-accent-glow bg-accent-soft px-4 py-2 text-xs font-bold text-accent-light no-underline transition"
                >
                  Admin
                </Link>
              ) : (
                <Link href="/profile"
                  className="rounded-xl border border-line-bright bg-brand-soft px-4 py-2 text-xs font-bold text-brand no-underline transition"
                >
                  Profile
                </Link>
              )}
              <button
                onClick={() => { void startLogout(); }}
                disabled={logoutPending}
                className="cursor-pointer rounded-xl border-none bg-[var(--gradient-brand)] px-4 py-2 text-xs font-bold text-white shadow-[0_0_14px_var(--line-bright)] transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {logoutPending ? "Logging out..." : "Logout"}
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <button
                onClick={() => { void startLogin(); }}
                disabled={authBusy}
                className="cursor-pointer rounded-xl border-none bg-[var(--gradient-brand)] px-5 py-2 text-xs font-bold text-white shadow-[0_0_14px_var(--line-bright)] transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {authActionPending === "login" ? "Redirecting..." : "Login"}
              </button>
              <button
                onClick={() => { void startSignup(); }}
                disabled={authBusy}
                className="cursor-pointer rounded-xl border border-line-bright bg-brand-soft px-5 py-2 text-xs font-bold text-brand transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {authActionPending === "signup" ? "Redirecting..." : "Sign Up"}
              </button>
              <button
                onClick={() => { void startForgotPassword(); }}
                disabled={authBusy}
                className="cursor-pointer border-none bg-transparent px-2 py-1 text-xs text-muted underline decoration-[rgba(104,104,160,0.4)] transition disabled:cursor-not-allowed disabled:opacity-50"
              >
                {authActionPending === "forgot" ? "Redirecting..." : "Forgot Password?"}
              </button>
            </div>
          )}
        </div>
      </header>

      <main>
        {/* Category Menu */}
        <div className="mx-auto max-w-7xl px-4 pt-4">
          <CategoryMenu />
        </div>
        <div className="mx-auto max-w-7xl px-4 pt-4">
          <PosterSlot placement="HOME_TOP_STRIP" variant="strip" />
        </div>
        <div className="mx-auto max-w-7xl px-4 pt-4">
          <PosterSlot placement="HOME_HERO" variant="hero" />
        </div>

        <HeroSection
          isAuthenticated={session.isAuthenticated}
          canViewAdmin={session.canViewAdmin}
          authBusy={authBusy}
          authActionPending={authActionPending}
          onSignup={() => { void startSignup(); }}
        />

        <TrustBar />

        {session.status === "error" && (
          <div className="mx-auto max-w-7xl px-4">
            <p className="mb-4 rounded-md border border-danger-glow bg-danger-soft px-4 py-3 text-base text-danger">
              {session.error}
            </p>
          </div>
        )}

        {/* --- FLASH DEALS --- */}
        <section className="mx-auto max-w-7xl px-4 pb-8">
          <div className="grid gap-4 lg:grid-cols-2">
            <PosterSlot placement="HOME_MID_LEFT" variant="tile" />
            <PosterSlot placement="HOME_MID_RIGHT" variant="tile" />
          </div>
        </section>
        {dealProducts.length > 0 && (
          <section
            className="animate-rise mx-auto max-w-7xl px-4 pb-12"
            style={{ animationDelay: "200ms" }}
          >
            <div className="section-header">
              <div className="flex items-center gap-3">
                <h2>Flash Deals</h2>
                <span className="flash-label">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
                  </svg>
                  Limited Time
                </span>
              </div>
              <Link href="/products" className="flex items-center gap-1 text-sm font-bold text-brand no-underline">
                View All {"->"}
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {dealProducts.map((product, idx) => (
                <ProductCard key={product.id} product={product} index={idx} />
              ))}
            </div>
          </section>
        )}

        {/* --- TRENDING PRODUCTS --- */}
        <section
          className="animate-rise mx-auto max-w-7xl px-4 pb-12"
          style={{ animationDelay: "300ms" }}
        >
          <div className="section-header">
            <h2>Trending Products</h2>
            <Link href="/products" className="flex items-center gap-1 text-sm font-bold text-brand no-underline">
              View All {"->"}
            </Link>
          </div>

          {status === "loading" && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <ProductCardSkeleton count={4} />
            </div>
          )}

          {status === "error" && (
            <p className="rounded-[12px] border border-warning-border bg-warning-soft px-4 py-3.5 text-base text-warning-text">
              Warning: Product catalog is unavailable right now. Please try again later.
            </p>
          )}

          {status === "ready" && trendingProducts.length === 0 && (
            <p className="rounded-[12px] border border-dashed border-line-bright p-6 text-center text-base text-muted">
              No products available yet. Check back soon!
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {trendingProducts.map((product, idx) => (
              <ProductCard key={product.id} product={product} index={idx} />
            ))}
          </div>
        </section>

        {/* --- RECOMMENDED FOR YOU --- */}
        {recommended.length > 0 && (
          <section className="animate-rise mx-auto max-w-7xl px-4 pb-12" style={{ animationDelay: "350ms" }}>
            <div className="section-header">
              <h2>Recommended For You</h2>
              <Link href="/products" className="flex items-center gap-1 text-sm font-bold text-brand no-underline">
                View All {"->"}
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {recommended.map((product, idx) => (
                <ProductCard key={product.id} product={product} index={idx} />
              ))}
            </div>
          </section>
        )}

        {/* --- RECENTLY VIEWED --- */}
        {recentlyViewed.length > 0 && (
          <section className="animate-rise mx-auto max-w-7xl px-4 pb-12" style={{ animationDelay: "380ms" }}>
            <div className="section-header">
              <h2>Recently Viewed</h2>
            </div>
            <div className="flex gap-4 overflow-x-auto pb-2">
              {recentlyViewed.map((product, idx) => {
                const discount = calcDiscount(product.regularPrice, product.sellingPrice);
                const imgUrl = resolveImageUrl(product.mainImage);
                return (
                  <Link
                    href={`/products/${encodeURIComponent((product.slug || product.id).trim())}`}
                    key={product.id}
                    className="product-card animate-rise shrink-0 no-underline"
                    style={{ animationDelay: `${idx * 40}ms`, minWidth: "200px", maxWidth: "220px" }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
                    <div className="relative aspect-square overflow-hidden bg-surface-2">
                      {imgUrl ? (
                        <Image src={imgUrl} alt={product.name} width={300} height={300} className="product-card-img" unoptimized />
                      ) : (
                        <div className="grid h-full w-full place-items-center bg-[linear-gradient(135deg,var(--surface),#1c1c38)] text-xs font-semibold text-muted-2">No Image</div>
                      )}
                    </div>
                    <div className="product-card-body">
                      <p className="mb-1 line-clamp-1 text-sm font-semibold text-ink">{product.name}</p>
                      <span className="price-current text-sm">{money(product.sellingPrice)}</span>
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>
        )}

        <CTABanner
          isAuthenticated={session.isAuthenticated}
          canViewAdmin={session.canViewAdmin}
          authBusy={authBusy}
          authActionPending={authActionPending}
          onSignup={() => { void startSignup(); }}
        />
      </main>

      <Footer />
    </div>
  );
}

