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
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function resolveImageUrl(imageName: string | null): string | null {
  if (!imageName) return null;
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map((s) => encodeURIComponent(s)).join("/");
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

/* â”€â”€ Futuristic Trust Icon SVGs â”€â”€ */
const TrustIcons = {
  ship: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v3" />
      <polyline points="9 11 9 6 14 6 14 9" />
      <rect x="9" y="11" width="14" height="10" rx="2" />
      <circle cx="12" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
    </svg>
  ),
  lock: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  refresh: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 4 23 10 17 10" /><polyline points="1 20 1 14 7 14" />
      <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
    </svg>
  ),
  support: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  ),
};

export default function LandingPage() {
  const session = useAuthSession();
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState("loading");
  const [authActionPending, setAuthActionPending] = useState<"login" | "signup" | "forgot" | null>(null);
  const [logoutPending, setLogoutPending] = useState(false);

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
  const trendingProducts = useMemo(() => products.slice(0, 8), [products]);
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
      {/* â”€â”€ Header â”€â”€ */}
      <header
        style={{
          background: "rgba(8,8,18,0.88)",
          backdropFilter: "blur(20px)",
          WebkitBackdropFilter: "blur(20px)",
          borderBottom: "1px solid rgba(0,212,255,0.1)",
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
                background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                display: "flex", alignItems: "center", justifyContent: "center",
                fontWeight: 900, fontSize: "0.7rem", color: "#fff",
                boxShadow: "0 0 16px rgba(0,212,255,0.35)",
              }}
            >RS</div>
            <div>
              <p style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, color: "#fff", fontSize: "1rem", margin: 0 }}>
                Rumal Store
              </p>
              <p style={{ fontSize: "9px", color: "rgba(0,212,255,0.55)", letterSpacing: "0.18em", margin: 0 }}>
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
                style={{ background: "rgba(0,212,255,0.07)", border: "1px solid rgba(0,212,255,0.15)", color: "rgba(0,212,255,0.8)" }}
              >
                {(session.profile?.email as string) || "User"}
              </span>
              {session.canViewAdmin ? (
                <Link href="/admin/orders"
                  className="rounded-xl px-4 py-2 text-xs font-bold no-underline transition"
                  style={{ border: "1px solid rgba(124,58,237,0.35)", color: "#a78bfa", background: "rgba(124,58,237,0.08)" }}
                >
                  Admin
                </Link>
              ) : (
                <Link href="/profile"
                  className="rounded-xl px-4 py-2 text-xs font-bold no-underline transition"
                  style={{ border: "1px solid rgba(0,212,255,0.25)", color: "#00d4ff", background: "rgba(0,212,255,0.06)" }}
                >
                  Profile
                </Link>
              )}
              <button
                onClick={() => { void startLogout(); }}
                disabled={logoutPending}
                className="rounded-xl px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", border: "none", cursor: "pointer", boxShadow: "0 0 14px rgba(0,212,255,0.2)" }}
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
                style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", border: "none", cursor: "pointer", boxShadow: "0 0 14px rgba(0,212,255,0.2)" }}
              >
                {authActionPending === "login" ? "Redirecting..." : "Login"}
              </button>
              <button
                onClick={() => { void startSignup(); }}
                disabled={authBusy}
                className="rounded-xl px-5 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ border: "1px solid rgba(0,212,255,0.25)", color: "#00d4ff", background: "rgba(0,212,255,0.06)", cursor: "pointer" }}
              >
                {authActionPending === "signup" ? "Redirecting..." : "Sign Up"}
              </button>
              <button
                onClick={() => { void startForgotPassword(); }}
                disabled={authBusy}
                className="px-2 py-1 text-xs transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ color: "#6868a0", background: "none", border: "none", textDecoration: "underline", textDecorationColor: "rgba(104,104,160,0.4)", cursor: "pointer" }}
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

        {/* â”€â”€ HERO SECTION â”€â”€ */}
        <section
          className="animate-rise"
          style={{
            position: "relative",
            overflow: "hidden",
            background: "linear-gradient(135deg, #060618 0%, #0e0820 40%, #071428 100%)",
            padding: "0",
            marginBottom: "0",
          }}
        >
          {/* Decorative orbs */}
          <div style={{ position: "absolute", top: "-80px", left: "-80px", width: "450px", height: "450px", borderRadius: "50%", background: "radial-gradient(circle, rgba(0,212,255,0.08) 0%, transparent 70%)", pointerEvents: "none" }} />
          <div style={{ position: "absolute", bottom: "-100px", right: "-60px", width: "500px", height: "500px", borderRadius: "50%", background: "radial-gradient(circle, rgba(124,58,237,0.09) 0%, transparent 70%)", pointerEvents: "none" }} />
          <div style={{ position: "absolute", top: "30%", left: "40%", width: "200px", height: "200px", borderRadius: "50%", background: "radial-gradient(circle, rgba(0,212,255,0.05) 0%, transparent 70%)", pointerEvents: "none" }} />

          {/* Subtle grid */}
          <div
            style={{
              position: "absolute", inset: 0, pointerEvents: "none",
              backgroundImage: "linear-gradient(rgba(0,212,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(0,212,255,0.04) 1px, transparent 1px)",
              backgroundSize: "48px 48px",
            }}
          />

          <div className="mx-auto max-w-7xl px-4 py-16 md:py-24" style={{ position: "relative", zIndex: 1 }}>
            <div className="grid items-center gap-12 md:grid-cols-[1fr,auto]">
              <div style={{ maxWidth: "680px" }}>
                {/* Tag pill */}
                <div
                  className="mb-6 inline-flex items-center gap-2 rounded-full px-4 py-1.5"
                  style={{
                    background: "rgba(0,212,255,0.06)",
                    border: "1px solid rgba(0,212,255,0.2)",
                    fontSize: "0.72rem",
                    fontWeight: 800,
                    color: "#00d4ff",
                    letterSpacing: "0.12em",
                    textTransform: "uppercase",
                  }}
                >
                  <span
                    style={{
                      width: "6px", height: "6px", borderRadius: "50%",
                      background: "#00d4ff",
                      boxShadow: "0 0 8px #00d4ff",
                      display: "inline-block",
                      animation: "glowPulse 2s ease-in-out infinite",
                    }}
                  />
                  Mega Sale â€” Live Now
                </div>

                {/* Headline */}
                <h1
                  style={{
                    fontFamily: "'Syne', sans-serif",
                    fontSize: "clamp(2.4rem, 6vw, 4.5rem)",
                    fontWeight: 800,
                    lineHeight: 1.08,
                    letterSpacing: "-0.03em",
                    margin: "0 0 24px",
                    color: "#fff",
                  }}
                >
                  Your Next-Gen
                  <br />
                  <span
                    style={{
                      background: "linear-gradient(135deg, #00d4ff 0%, #7c3aed 100%)",
                      WebkitBackgroundClip: "text",
                      WebkitTextFillColor: "transparent",
                      backgroundClip: "text",
                    }}
                  >
                    Shopping Hub
                  </span>
                </h1>

                <p style={{ fontSize: "1rem", color: "#8888bb", lineHeight: 1.7, margin: "0 0 36px", maxWidth: "520px" }}>
                  Discover thousands of premium products at unbeatable prices. Secure payments, lightning-fast delivery, and a shopping experience built for the future.
                  {session.isAuthenticated ? " Welcome back â€” your deals are waiting." : " Sign in to unlock your personal store."}
                </p>

                {/* CTA Buttons */}
                <div className="flex flex-wrap gap-3">
                  <Link
                    href="/products"
                    className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition"
                    style={{
                      background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                      color: "#fff",
                      fontSize: "0.9rem",
                      boxShadow: "0 0 28px rgba(0,212,255,0.3)",
                    }}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                      <path d="M16 10a4 4 0 0 1-8 0" />
                    </svg>
                    Shop Now
                  </Link>
                  {session.isAuthenticated ? (
                    <Link
                      href={session.canViewAdmin ? "/admin/orders" : "/profile"}
                      className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition"
                      style={{ border: "1.5px solid rgba(0,212,255,0.3)", color: "#00d4ff", background: "rgba(0,212,255,0.06)", fontSize: "0.9rem" }}
                    >
                      {session.canViewAdmin ? "Go to Admin" : "My Profile"}
                    </Link>
                  ) : (
                    <button
                      onClick={() => { void startSignup(); }}
                      disabled={authBusy}
                      className="inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                      style={{ border: "1.5px solid rgba(0,212,255,0.3)", color: "#00d4ff", background: "rgba(0,212,255,0.06)", fontSize: "0.9rem", cursor: "pointer" }}
                    >
                      {authActionPending === "signup" ? "Redirecting..." : "Create Account â†’"}
                    </button>
                  )}
                </div>

                {/* Stats row */}
                <div className="mt-10 flex flex-wrap gap-6">
                  {[
                    { value: "50K+", label: "Products" },
                    { value: "99%", label: "Satisfaction" },
                    { value: "24/7", label: "Support" },
                  ].map(({ value, label }) => (
                    <div key={label}>
                      <p style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 800, color: "#00d4ff", margin: "0 0 2px", textShadow: "0 0 16px rgba(0,212,255,0.4)" }}>
                        {value}
                      </p>
                      <p style={{ fontSize: "0.72rem", color: "#6868a0", margin: 0, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.1em" }}>{label}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Side discount badge */}
              <div className="hidden md:flex flex-col items-center gap-4">
                <div
                  style={{
                    padding: "32px 28px",
                    borderRadius: "24px",
                    border: "1px solid rgba(0,212,255,0.2)",
                    background: "rgba(0,212,255,0.04)",
                    backdropFilter: "blur(12px)",
                    textAlign: "center",
                    boxShadow: "0 0 40px rgba(0,212,255,0.08)",
                  }}
                >
                  <p style={{ fontSize: "0.7rem", fontWeight: 800, color: "#00d4ff", letterSpacing: "0.16em", textTransform: "uppercase", margin: "0 0 4px" }}>UP TO</p>
                  <p style={{ fontFamily: "'Syne', sans-serif", fontSize: "5rem", fontWeight: 900, color: "#fff", lineHeight: 1, margin: "0 0 4px", textShadow: "0 0 32px rgba(0,212,255,0.3)" }}>
                    70<span style={{ color: "#00d4ff" }}>%</span>
                  </p>
                  <p style={{ fontSize: "1rem", fontWeight: 800, color: "rgba(255,255,255,0.6)", letterSpacing: "0.08em", margin: 0 }}>OFF TODAY</p>
                </div>
                <div
                  style={{
                    padding: "14px 20px",
                    borderRadius: "14px",
                    border: "1px solid rgba(124,58,237,0.25)",
                    background: "rgba(124,58,237,0.06)",
                    textAlign: "center",
                    fontSize: "0.78rem",
                    color: "#a78bfa",
                    fontWeight: 600,
                  }}
                >
                  âœ¦ Free shipping on orders $25+
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* â”€â”€ TRUST BAR â”€â”€ */}
        <section className="animate-rise mx-auto max-w-7xl px-4 py-8" style={{ animationDelay: "100ms" }}>
          <div className="trust-bar">
            {[
              { icon: TrustIcons.ship, title: "Free Shipping", desc: "On orders over $25" },
              { icon: TrustIcons.lock, title: "Secure Payment", desc: "100% encrypted checkout" },
              { icon: TrustIcons.refresh, title: "Easy Returns", desc: "30-day return policy" },
              { icon: TrustIcons.support, title: "24/7 Support", desc: "Always here to help you" },
            ].map(({ icon, title, desc }) => (
              <div key={title} className="trust-item">
                <span className="trust-icon">{icon}</span>
                <div className="trust-text">
                  <h4>{title}</h4>
                  <p>{desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {session.status === "error" && (
          <div className="mx-auto max-w-7xl px-4">
            <p style={{ borderRadius: "10px", border: "1px solid rgba(239,68,68,0.3)", background: "rgba(239,68,68,0.08)", padding: "12px 16px", fontSize: "0.875rem", color: "#f87171", marginBottom: "16px" }}>
              {session.error}
            </p>
          </div>
        )}

        {/* â”€â”€ FLASH DEALS â”€â”€ */}
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
                View All â†’
              </Link>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {dealProducts.map((product, idx) => {
                const discount = calcDiscount(product.regularPrice, product.sellingPrice);
                const imgUrl = resolveImageUrl(product.mainImage);
                return (
                  <Link
                    href={`/products/${encodeURIComponent((product.slug || product.id).trim())}`}
                    key={product.id}
                    className="product-card animate-rise no-underline"
                    style={{ animationDelay: `${idx * 80}ms` }}
                  >
                    {discount && <span className="badge-sale">-{discount}%</span>}
                    <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)" }}>
                      {imgUrl ? (
                        <Image src={imgUrl} alt={product.name} width={400} height={400} className="product-card-img" unoptimized />
                      ) : (
                        <div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, #111128, #1c1c38)", color: "#4a4a70", fontSize: "0.8rem", fontWeight: 600 }}>
                          No Image
                        </div>
                      )}
                      <div className="product-card-overlay">
                        <span style={{ background: "linear-gradient(135deg,#00d4ff,#7c3aed)", color: "#fff", padding: "8px 18px", borderRadius: "20px", fontSize: "0.78rem", fontWeight: 800, letterSpacing: "0.04em" }}>
                          View Deal â†’
                        </span>
                      </div>
                    </div>
                    <div className="product-card-body">
                      <p style={{ margin: "0 0 6px", fontSize: "0.875rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{product.name}</p>
                      <div style={{ display: "flex", alignItems: "center", gap: "4px", marginBottom: "6px" }}>
                        <span className="price-current">{money(product.sellingPrice)}</span>
                        {product.discountedPrice !== null && <span className="price-original">{money(product.regularPrice)}</span>}
                      </div>
                      <div className="star-rating">â˜…â˜…â˜…â˜…â˜… <span className="star-rating-count">| 1k+ sold</span></div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>
        )}

        {/* â”€â”€ TRENDING PRODUCTS â”€â”€ */}
        <section
          className="animate-rise mx-auto max-w-7xl px-4 pb-12"
          style={{ animationDelay: "300ms" }}
        >
          <div className="section-header">
            <h2>Trending Products</h2>
            <Link href="/products" className="no-underline" style={{ color: "var(--brand)", fontWeight: 700, fontSize: "0.85rem", display: "flex", alignItems: "center", gap: "4px" }}>
              View All â†’
            </Link>
          </div>

          {status === "loading" && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} style={{ borderRadius: "16px", overflow: "hidden", background: "var(--surface)", border: "1px solid var(--line)" }}>
                  <div className="skeleton" style={{ height: "220px", width: "100%", borderRadius: 0 }} />
                  <div style={{ padding: "14px 16px", display: "flex", flexDirection: "column", gap: "8px" }}>
                    <div className="skeleton" style={{ height: "14px", width: "75%" }} />
                    <div className="skeleton" style={{ height: "18px", width: "50%" }} />
                  </div>
                </div>
              ))}
            </div>
          )}

          {status === "error" && (
            <p style={{ borderRadius: "12px", border: "1px solid rgba(245,158,11,0.25)", background: "rgba(245,158,11,0.08)", padding: "14px 16px", fontSize: "0.875rem", color: "#fbbf24" }}>
              âš  Product catalog is unavailable right now. Please try again later.
            </p>
          )}

          {status === "ready" && trendingProducts.length === 0 && (
            <p style={{ borderRadius: "12px", border: "1px dashed var(--line-bright)", padding: "24px", textAlign: "center", fontSize: "0.875rem", color: "var(--muted)" }}>
              No products available yet. Check back soon!
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {trendingProducts.map((product, idx) => {
              const discount = calcDiscount(product.regularPrice, product.sellingPrice);
              const imgUrl = resolveImageUrl(product.mainImage);
              return (
                <Link
                  href={`/products/${encodeURIComponent((product.slug || product.id).trim())}`}
                  key={product.id}
                  className="product-card animate-rise no-underline"
                  style={{ animationDelay: `${idx * 60}ms` }}
                >
                  {discount && <span className="badge-sale">-{discount}%</span>}
                  <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)" }}>
                    {imgUrl ? (
                      <Image src={imgUrl} alt={product.name} width={400} height={400} className="product-card-img" unoptimized />
                    ) : (
                      <div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, #111128, #1c1c38)", color: "#4a4a70", fontSize: "0.8rem", fontWeight: 600 }}>
                        No Image
                      </div>
                    )}
                    <div className="product-card-overlay">
                      <span style={{ background: "linear-gradient(135deg,#00d4ff,#7c3aed)", color: "#fff", padding: "8px 18px", borderRadius: "20px", fontSize: "0.78rem", fontWeight: 800 }}>
                        View Product â†’
                      </span>
                    </div>
                  </div>
                  <div className="product-card-body">
                    <p style={{ margin: "0 0 4px", fontSize: "0.875rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{product.name}</p>
                    <p style={{ margin: "0 0 8px", fontSize: "0.72rem", color: "var(--muted)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{product.shortDescription}</p>
                    <div style={{ display: "flex", alignItems: "center", gap: "4px", marginBottom: "6px" }}>
                      <span className="price-current">{money(product.sellingPrice)}</span>
                      {product.discountedPrice !== null && <span className="price-original">{money(product.regularPrice)}</span>}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                      <span className="star-rating">â˜…â˜…â˜…â˜…â˜† <span className="star-rating-count">4.5</span></span>
                      <span style={{ fontSize: "0.65rem", color: "var(--muted)" }}>500+ sold</span>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>

        {/* â”€â”€ CTA BANNER â”€â”€ */}
        <section
          className="animate-rise mx-auto max-w-7xl px-4 pb-16"
          style={{ animationDelay: "400ms" }}
        >
          <div
            style={{
              borderRadius: "24px",
              padding: "56px 40px",
              textAlign: "center",
              background: "linear-gradient(135deg, #0a0a22 0%, #12082e 50%, #080e28 100%)",
              border: "1px solid rgba(0,212,255,0.12)",
              position: "relative",
              overflow: "hidden",
            }}
          >
            {/* Decorative orbs */}
            <div style={{ position: "absolute", top: "-60px", left: "20%", width: "300px", height: "300px", borderRadius: "50%", background: "radial-gradient(circle, rgba(0,212,255,0.07) 0%, transparent 70%)", pointerEvents: "none" }} />
            <div style={{ position: "absolute", bottom: "-60px", right: "15%", width: "250px", height: "250px", borderRadius: "50%", background: "radial-gradient(circle, rgba(124,58,237,0.08) 0%, transparent 70%)", pointerEvents: "none" }} />

            <div style={{ position: "relative", zIndex: 1 }}>
              <span style={{ display: "inline-block", fontFamily: "'Syne', sans-serif", fontSize: "2.4rem", fontWeight: 900, lineHeight: 1.15, color: "#fff", marginBottom: "16px" }}>
                {session.isAuthenticated ? "Welcome Back! ðŸ‘‹" : (
                  <>
                    Join{" "}
                    <span style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text" }}>
                      Rumal Store
                    </span>{" "}
                    Today
                  </>
                )}
              </span>
              <p style={{ fontSize: "0.95rem", color: "#8888bb", margin: "0 auto 36px", maxWidth: "500px", lineHeight: 1.7 }}>
                {session.isAuthenticated
                  ? "Continue shopping, check your orders, and manage your account all in one place."
                  : "Create your free account and unlock exclusive deals, fast checkout, order tracking, and a premium shopping experience."}
              </p>
              <div style={{ display: "flex", justifyContent: "center", gap: "14px", flexWrap: "wrap" }}>
                {session.isAuthenticated ? (
                  <Link
                    href={session.canViewAdmin ? "/admin/orders" : "/profile"}
                    className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition"
                    style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", fontSize: "0.9rem", boxShadow: "0 0 24px rgba(0,212,255,0.25)" }}
                  >
                    {session.canViewAdmin ? "Open Admin â†’" : "Open Profile â†’"}
                  </Link>
                ) : (
                  <button
                    onClick={() => { void startSignup(); }}
                    disabled={authBusy}
                    className="inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                    style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", fontSize: "0.9rem", cursor: "pointer", border: "none", boxShadow: "0 0 24px rgba(0,212,255,0.25)" }}
                  >
                    {authActionPending === "signup" ? "Redirecting..." : "Sign Up Free â†’"}
                  </button>
                )}
                <Link
                  href="/products"
                  className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition"
                  style={{ border: "1.5px solid rgba(0,212,255,0.3)", color: "#00d4ff", background: "rgba(0,212,255,0.06)", fontSize: "0.9rem" }}
                >
                  Browse Products
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}

