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
import { fetchTrending, fetchRecommended, fetchRecentlyViewed, getOrCreateSessionId, type PersonalizationProduct } from "../lib/personalization";
import ProductCard from "./components/catalog/ProductCard";
import ProductCardSkeleton from "./components/catalog/ProductCardSkeleton";
import HeroSection from "./components/home/HeroSection";
import TrustBar from "./components/home/TrustBar";
import CTABanner from "./components/home/CTABanner";

export default function LandingPage() {
  const session = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState("loading");
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
    fetchTrending(8).then(setTrending).catch(() => {});
    fetchRecommended(8, token).then(setRecommended).catch(() => {});
    fetchRecentlyViewed(8, token).then(setRecentlyViewed).catch(() => {});
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
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      {/* --- Header --- */}
      <header
        style={{
          background: "var(--header-bg)",
          backdropFilter: "blur(20px)",
          WebkitBackdropFilter: "blur(20px)",
          borderBottom: "1px solid var(--line-bright)",
          position: "sticky",
          top: 0,
          zIndex: 50,
        }}
      >
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-3 no-underline" style={{ flexShrink: 0 }}>
            <div
              style={{
                width: "36px", height: "36px", borderRadius: "10px",
                background: "var(--gradient-brand)",
                display: "flex", alignItems: "center", justifyContent: "center",
                fontWeight: 900, fontSize: "0.7rem", color: "#fff",
                boxShadow: "0 0 16px var(--brand-glow)",
              }}
            >RS</div>
            <div>
              <p style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, color: "#fff", fontSize: "1rem", margin: 0 }}>
                Rumal Store
              </p>
              <p style={{ fontSize: "9px", color: "var(--brand)", letterSpacing: "0.18em", margin: 0, opacity: 0.55 }}>
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
                className="hidden md:inline-block rounded-full px-3 py-1.5 text-xs font-medium"
                style={{ background: "var(--brand-soft)", border: "1px solid var(--line-bright)", color: "var(--brand)" }}
              >
                {(session.profile?.email as string) || "User"}
              </span>
              {session.canViewAdmin ? (
                <Link href="/admin/orders"
                  className="rounded-xl px-4 py-2 text-xs font-bold no-underline transition"
                  style={{ border: "1px solid var(--accent-glow)", color: "#a78bfa", background: "var(--accent-soft)" }}
                >
                  Admin
                </Link>
              ) : (
                <Link href="/profile"
                  className="rounded-xl px-4 py-2 text-xs font-bold no-underline transition"
                  style={{ border: "1px solid var(--line-bright)", color: "var(--brand)", background: "var(--brand-soft)" }}
                >
                  Profile
                </Link>
              )}
              <button
                onClick={() => { void startLogout(); }}
                disabled={logoutPending}
                className="rounded-xl px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer", boxShadow: "0 0 14px var(--line-bright)" }}
              >
                {logoutPending ? "Logging out..." : "Logout"}
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <button
                onClick={() => { void startLogin(); }}
                disabled={authBusy}
                className="rounded-xl px-5 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer", boxShadow: "0 0 14px var(--line-bright)" }}
              >
                {authActionPending === "login" ? "Redirecting..." : "Login"}
              </button>
              <button
                onClick={() => { void startSignup(); }}
                disabled={authBusy}
                className="rounded-xl px-5 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ border: "1px solid var(--line-bright)", color: "var(--brand)", background: "var(--brand-soft)", cursor: "pointer" }}
              >
                {authActionPending === "signup" ? "Redirecting..." : "Sign Up"}
              </button>
              <button
                onClick={() => { void startForgotPassword(); }}
                disabled={authBusy}
                className="px-2 py-1 text-xs transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ color: "var(--muted)", background: "none", border: "none", textDecoration: "underline", textDecorationColor: "rgba(104,104,160,0.4)", cursor: "pointer" }}
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
            <p style={{ borderRadius: "10px", border: "1px solid var(--danger-glow)", background: "var(--danger-soft)", padding: "12px 16px", fontSize: "0.875rem", color: "var(--danger)", marginBottom: "16px" }}>
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
              <Link href="/products" className="no-underline" style={{ color: "var(--brand)", fontWeight: 700, fontSize: "0.85rem", display: "flex", alignItems: "center", gap: "4px" }}>
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
            <Link href="/products" className="no-underline" style={{ color: "var(--brand)", fontWeight: 700, fontSize: "0.85rem", display: "flex", alignItems: "center", gap: "4px" }}>
              View All {"->"}
            </Link>
          </div>

          {status === "loading" && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <ProductCardSkeleton count={4} />
            </div>
          )}

          {status === "error" && (
            <p style={{ borderRadius: "12px", border: "1px solid var(--warning-border)", background: "var(--warning-soft)", padding: "14px 16px", fontSize: "0.875rem", color: "var(--warning-text)" }}>
              Warning: Product catalog is unavailable right now. Please try again later.
            </p>
          )}

          {status === "ready" && trendingProducts.length === 0 && (
            <p style={{ borderRadius: "12px", border: "1px dashed var(--line-bright)", padding: "24px", textAlign: "center", fontSize: "0.875rem", color: "var(--muted)" }}>
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
              <Link href="/products" className="no-underline" style={{ color: "var(--brand)", fontWeight: 700, fontSize: "0.85rem", display: "flex", alignItems: "center", gap: "4px" }}>
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
            <div style={{ display: "flex", gap: "16px", overflowX: "auto", paddingBottom: "8px" }}>
              {recentlyViewed.map((product, idx) => {
                const discount = calcDiscount(product.regularPrice, product.sellingPrice);
                const imgUrl = resolveImageUrl(product.mainImage);
                return (
                  <Link
                    href={`/products/${encodeURIComponent((product.slug || product.id).trim())}`}
                    key={product.id}
                    className="product-card animate-rise no-underline"
                    style={{ animationDelay: `${idx * 40}ms`, minWidth: "200px", maxWidth: "220px", flexShrink: 0 }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
                    <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)" }}>
                      {imgUrl ? (
                        <Image src={imgUrl} alt={product.name} width={300} height={300} className="product-card-img" unoptimized />
                      ) : (
                        <div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, var(--surface), #1c1c38)", color: "var(--muted-2)", fontSize: "0.75rem", fontWeight: 600 }}>No Image</div>
                      )}
                    </div>
                    <div className="product-card-body">
                      <p style={{ margin: "0 0 4px", fontSize: "0.8rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 1, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{product.name}</p>
                      <span className="price-current" style={{ fontSize: "0.85rem" }}>{money(product.sellingPrice)}</span>
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

