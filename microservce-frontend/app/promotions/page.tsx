"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import Pagination from "../components/Pagination";
import { useAuthSession } from "../../lib/authSession";

/* ───── types ───── */

type SpendTier = { thresholdAmount: number; discountAmount: number };

type PublicPromotion = {
  id: string;
  name: string;
  description: string | null;
  scopeType: string;
  applicationLevel: string;
  benefitType: string;
  benefitValue: number | null;
  buyQuantity: number | null;
  getQuantity: number | null;
  spendTiers: SpendTier[];
  minimumOrderAmount: number | null;
  maximumDiscountAmount: number | null;
  stackable: boolean;
  autoApply: boolean;
  targetProductIds: string[];
  targetCategoryIds: string[];
  flashSale: boolean;
  flashSaleStartAt: string | null;
  flashSaleEndAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
};

type PageResponse = {
  content: PublicPromotion[];
  number: number;
  totalPages: number;
  totalElements: number;
};

/* ───── helpers ───── */

const API_BASE = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").replace(/\/+$/, "");
const PAGE_SIZE = 20;

function formatBenefit(p: PublicPromotion): string {
  switch (p.benefitType) {
    case "PERCENTAGE_OFF":
      return `${p.benefitValue ?? 0}% OFF`;
    case "FIXED_AMOUNT_OFF":
      return `$${p.benefitValue ?? 0} OFF`;
    case "FREE_SHIPPING":
      return "FREE SHIPPING";
    case "BUY_X_GET_Y":
      return `Buy ${p.buyQuantity ?? 0} Get ${p.getQuantity ?? 0}`;
    case "TIERED_SPEND":
      return "Spend & Save";
    default:
      return p.benefitType.replace(/_/g, " ");
  }
}

function formatDate(iso: string | null): string {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  } catch {
    return iso;
  }
}

function formatScopeLabel(scope: string): string {
  switch (scope) {
    case "PLATFORM": return "Platform";
    case "VENDOR": return "Vendor";
    case "CATEGORY": return "Category";
    default: return scope;
  }
}

function benefitColor(benefitType: string): { bg: string; border: string; text: string } {
  switch (benefitType) {
    case "PERCENTAGE_OFF":
      return { bg: "rgba(0,212,255,0.1)", border: "rgba(0,212,255,0.3)", text: "var(--brand)" };
    case "FIXED_AMOUNT_OFF":
      return { bg: "rgba(52,211,153,0.1)", border: "rgba(52,211,153,0.3)", text: "#34d399" };
    case "FREE_SHIPPING":
      return { bg: "rgba(167,139,250,0.1)", border: "rgba(167,139,250,0.3)", text: "#a78bfa" };
    case "BUY_X_GET_Y":
      return { bg: "rgba(251,191,36,0.1)", border: "rgba(251,191,36,0.3)", text: "#fbbf24" };
    case "TIERED_SPEND":
      return { bg: "rgba(244,114,182,0.1)", border: "rgba(244,114,182,0.3)", text: "#f472b6" };
    default:
      return { bg: "rgba(255,255,255,0.05)", border: "var(--line-bright)", text: "var(--muted)" };
  }
}

/* ───── countdown hook ───── */

function useCountdown(endIso: string | null): string {
  const [label, setLabel] = useState("");

  useEffect(() => {
    if (!endIso) { setLabel(""); return; }

    const tick = () => {
      const diff = new Date(endIso).getTime() - Date.now();
      if (diff <= 0) { setLabel("Ended"); return; }
      const d = Math.floor(diff / 86_400_000);
      const h = Math.floor((diff % 86_400_000) / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1_000);
      if (d > 0) setLabel(`${d}d ${h}h ${m}m ${s}s`);
      else if (h > 0) setLabel(`${h}h ${m}m ${s}s`);
      else setLabel(`${m}m ${s}s`);
    };

    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endIso]);

  return label;
}

/* ───── flash sale card ───── */

function FlashSaleCard({ promo }: { promo: PublicPromotion }) {
  const countdown = useCountdown(promo.flashSaleEndAt);
  const bc = benefitColor(promo.benefitType);

  return (
    <article
      style={{
        minWidth: "280px",
        maxWidth: "340px",
        flex: "0 0 auto",
        background: "rgba(255,255,255,0.03)",
        border: "1px solid transparent",
        borderRadius: 16,
        padding: "24px 20px",
        position: "relative",
        overflow: "hidden",
        boxShadow: "0 0 20px rgba(0,212,255,0.08), inset 0 0 20px rgba(0,212,255,0.02)",
      }}
    >
      {/* gradient border overlay */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: 16,
          padding: "1px",
          background: "linear-gradient(135deg, var(--brand), var(--accent))",
          WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
          WebkitMaskComposite: "xor",
          maskComposite: "exclude",
          pointerEvents: "none",
        }}
      />
      {/* flash badge */}
      <div
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "6px",
          marginBottom: "12px",
          padding: "4px 12px",
          borderRadius: "20px",
          background: "linear-gradient(135deg, rgba(251,191,36,0.15), rgba(244,114,182,0.15))",
          border: "1px solid rgba(251,191,36,0.3)",
          fontSize: "0.65rem",
          fontWeight: 800,
          letterSpacing: "0.1em",
          textTransform: "uppercase",
          color: "#fbbf24",
        }}
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
        FLASH SALE
      </div>

      <h3 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.05rem", fontWeight: 800, color: "#fff", margin: "0 0 8px 0", lineHeight: 1.3 }}>
        {promo.name}
      </h3>

      {/* benefit */}
      <span
        style={{
          display: "inline-block",
          padding: "4px 14px",
          borderRadius: "20px",
          background: bc.bg,
          border: `1px solid ${bc.border}`,
          color: bc.text,
          fontSize: "0.78rem",
          fontWeight: 800,
          marginBottom: "14px",
        }}
      >
        {formatBenefit(promo)}
      </span>

      {/* countdown */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          marginTop: "4px",
        }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
        </svg>
        <span
          style={{
            fontSize: "0.85rem",
            fontWeight: 700,
            color: countdown === "Ended" ? "var(--danger)" : "var(--brand)",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {countdown || "..."}
        </span>
      </div>
    </article>
  );
}

/* ───── main page ───── */

export default function PromotionsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = session;

  /* flash sales */
  const [flashSales, setFlashSales] = useState<PublicPromotion[]>([]);
  const [flashLoading, setFlashLoading] = useState(true);

  /* all promotions */
  const [promotions, setPromotions] = useState<PublicPromotion[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  /* filters */
  const [search, setSearch] = useState("");
  const [scopeFilter, setScopeFilter] = useState("");
  const [benefitFilter, setBenefitFilter] = useState("");

  /* debounced search */
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 400);
    return () => { if (searchTimerRef.current) clearTimeout(searchTimerRef.current); };
  }, [search]);

  /* reset page on filter change */
  useEffect(() => { setPage(0); }, [scopeFilter, benefitFilter]);

  /* fetch flash sales */
  useEffect(() => {
    const run = async () => {
      setFlashLoading(true);
      try {
        const res = await fetch(`${API_BASE}/promotions/flash-sales?page=0&size=10`, { cache: "no-store" });
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        const data = (await res.json()) as PageResponse;
        setFlashSales(data.content || []);
      } catch (err) {
        console.error("Failed to load flash sales", err);
        setFlashSales([]);
      } finally {
        setFlashLoading(false);
      }
    };
    void run();
  }, []);

  /* fetch all promotions */
  useEffect(() => {
    const run = async () => {
      setLoading(true);
      try {
        const params = new URLSearchParams();
        if (debouncedSearch.trim()) params.set("q", debouncedSearch.trim());
        if (scopeFilter) params.set("scopeType", scopeFilter);
        if (benefitFilter) params.set("benefitType", benefitFilter);
        params.set("page", String(page));
        params.set("size", String(PAGE_SIZE));

        const res = await fetch(`${API_BASE}/promotions?${params.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        const data = (await res.json()) as PageResponse;
        setPromotions(data.content || []);
        setTotalPages(data.totalPages ?? 0);
        setTotalElements(data.totalElements ?? 0);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : "Failed to load promotions");
        setPromotions([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    };
    void run();
  }, [debouncedSearch, scopeFilter, benefitFilter, page]);

  /* flash sales scroll container */
  const flashScrollRef = useRef<HTMLDivElement>(null);

  const scrollFlash = (dir: "left" | "right") => {
    if (!flashScrollRef.current) return;
    const amount = 310;
    flashScrollRef.current.scrollBy({ left: dir === "left" ? -amount : amount, behavior: "smooth" });
  };

  /* loading state */
  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }

  /* ───── styles ───── */

  const selectStyle: React.CSSProperties = {
    padding: "10px 14px",
    borderRadius: "10px",
    border: "1px solid var(--line-bright)",
    background: "rgba(255,255,255,0.03)",
    color: "#fff",
    fontSize: "0.82rem",
    fontWeight: 600,
    outline: "none",
    cursor: "pointer",
    minWidth: "160px",
  };

  const inputStyle: React.CSSProperties = {
    flex: 1,
    minWidth: "200px",
    padding: "10px 14px 10px 40px",
    borderRadius: "10px",
    border: "1px solid var(--line-bright)",
    background: "rgba(255,255,255,0.03)",
    color: "#fff",
    fontSize: "0.85rem",
    fontWeight: 500,
    outline: "none",
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumb */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&rsaquo;</span>
          <span className="breadcrumb-current">Promotions</span>
        </nav>

        {/* Page Header */}
        <div style={{ marginBottom: "32px" }}>
          <h1
            style={{
              fontFamily: "'Syne', sans-serif",
              fontSize: "1.75rem",
              fontWeight: 800,
              color: "#fff",
              margin: 0,
            }}
          >
            Promotions & Deals
          </h1>
          <p style={{ marginTop: "6px", fontSize: "0.85rem", color: "var(--muted)" }}>
            Discover current offers, flash sales, and savings across the platform.
          </p>
        </div>

        {/* ── Flash Sales Section ── */}
        {!flashLoading && flashSales.length > 0 && (
          <section style={{ marginBottom: "40px" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="#fbbf24"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
                <h2
                  style={{
                    fontFamily: "'Syne', sans-serif",
                    fontSize: "1.2rem",
                    fontWeight: 800,
                    color: "#fff",
                    margin: 0,
                  }}
                >
                  Flash Sales
                </h2>
                <span
                  style={{
                    fontSize: "0.65rem",
                    fontWeight: 700,
                    letterSpacing: "0.08em",
                    padding: "3px 10px",
                    borderRadius: "20px",
                    background: "rgba(251,191,36,0.12)",
                    border: "1px solid rgba(251,191,36,0.25)",
                    color: "#fbbf24",
                  }}
                >
                  LIMITED TIME
                </span>
              </div>
              {/* scroll arrows */}
              <div style={{ display: "flex", gap: "6px" }}>
                <button
                  type="button"
                  onClick={() => scrollFlash("left")}
                  style={{
                    width: "32px",
                    height: "32px",
                    borderRadius: "8px",
                    border: "1px solid var(--line-bright)",
                    background: "rgba(255,255,255,0.03)",
                    color: "var(--muted)",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                  aria-label="Scroll left"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                </button>
                <button
                  type="button"
                  onClick={() => scrollFlash("right")}
                  style={{
                    width: "32px",
                    height: "32px",
                    borderRadius: "8px",
                    border: "1px solid var(--line-bright)",
                    background: "rgba(255,255,255,0.03)",
                    color: "var(--muted)",
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                  aria-label="Scroll right"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
                </button>
              </div>
            </div>

            {/* Horizontal scroll container */}
            <div
              ref={flashScrollRef}
              style={{
                display: "flex",
                gap: "16px",
                overflowX: "auto",
                paddingBottom: "8px",
                scrollbarWidth: "thin",
                scrollbarColor: "var(--line-bright) transparent",
              }}
            >
              {flashSales.map((fs) => (
                <FlashSaleCard key={fs.id} promo={fs} />
              ))}
            </div>
          </section>
        )}

        {/* ── All Promotions Section ── */}
        <section>
          <h2
            style={{
              fontFamily: "'Syne', sans-serif",
              fontSize: "1.2rem",
              fontWeight: 800,
              color: "#fff",
              margin: "0 0 16px 0",
            }}
          >
            All Promotions
          </h2>

          {/* Search & Filters */}
          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: "12px",
              marginBottom: "24px",
              alignItems: "center",
            }}
          >
            {/* Search input with icon */}
            <div style={{ position: "relative", flex: 1, minWidth: "200px" }}>
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="var(--muted)"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                style={{ position: "absolute", left: "14px", top: "50%", transform: "translateY(-50%)", pointerEvents: "none" }}
              >
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                type="text"
                placeholder="Search promotions..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                style={inputStyle}
                onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line-bright)"; }}
              />
            </div>

            {/* Scope filter */}
            <select
              value={scopeFilter}
              onChange={(e) => setScopeFilter(e.target.value)}
              style={selectStyle}
            >
              <option value="">All Scopes</option>
              <option value="PLATFORM">Platform</option>
              <option value="VENDOR">Vendor</option>
              <option value="CATEGORY">Category</option>
            </select>

            {/* Benefit filter */}
            <select
              value={benefitFilter}
              onChange={(e) => setBenefitFilter(e.target.value)}
              style={selectStyle}
            >
              <option value="">All Benefits</option>
              <option value="PERCENTAGE_OFF">Percentage Off</option>
              <option value="FIXED_AMOUNT_OFF">Fixed Amount Off</option>
              <option value="FREE_SHIPPING">Free Shipping</option>
              <option value="BUY_X_GET_Y">Buy X Get Y</option>
              <option value="TIERED_SPEND">Tiered Spend</option>
            </select>
          </div>

          {/* Loading */}
          {loading && (
            <div style={{ textAlign: "center", padding: "60px 0" }}>
              <div className="spinner-lg" />
              <p style={{ marginTop: "12px", color: "var(--muted)", fontSize: "0.82rem" }}>Loading promotions...</p>
            </div>
          )}

          {/* Empty state */}
          {!loading && promotions.length === 0 && (
            <div className="empty-state">
              <div className="empty-state-icon">
                <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
                  <line x1="7" y1="7" x2="7.01" y2="7" />
                </svg>
              </div>
              <p className="empty-state-title">No promotions found</p>
              <p className="empty-state-desc">
                {debouncedSearch || scopeFilter || benefitFilter
                  ? "Try adjusting your search or filters."
                  : "Check back later for new deals and offers."}
              </p>
            </div>
          )}

          {/* Promotion Cards Grid */}
          {!loading && promotions.length > 0 && (
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))",
                gap: "18px",
              }}
            >
              {promotions.map((promo) => {
                const bc = benefitColor(promo.benefitType);
                const hasDateRange = promo.startsAt || promo.endsAt;

                return (
                  <article
                    key={promo.id}
                    className="animate-rise"
                    style={{
                      background: "rgba(255,255,255,0.03)",
                      border: "1px solid var(--line)",
                      borderRadius: 16,
                      padding: "20px",
                      display: "flex",
                      flexDirection: "column",
                      gap: "12px",
                      transition: "border-color 0.2s, box-shadow 0.2s",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = "var(--line-bright)";
                      e.currentTarget.style.boxShadow = "0 4px 24px rgba(0,0,0,0.3)";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = "var(--line)";
                      e.currentTarget.style.boxShadow = "none";
                    }}
                  >
                    {/* Top row: name + flash badge if applicable */}
                    <div>
                      <h3
                        style={{
                          fontFamily: "'Syne', sans-serif",
                          fontSize: "1rem",
                          fontWeight: 800,
                          color: "#fff",
                          margin: 0,
                          lineHeight: 1.35,
                        }}
                      >
                        {promo.name}
                      </h3>
                      {promo.description && (
                        <p
                          style={{
                            marginTop: "6px",
                            fontSize: "0.8rem",
                            lineHeight: 1.5,
                            color: "var(--muted)",
                            display: "-webkit-box",
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: "vertical",
                            overflow: "hidden",
                          }}
                        >
                          {promo.description}
                        </p>
                      )}
                    </div>

                    {/* Badges row */}
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", alignItems: "center" }}>
                      {/* Benefit badge */}
                      <span
                        style={{
                          display: "inline-block",
                          padding: "4px 12px",
                          borderRadius: "20px",
                          background: bc.bg,
                          border: `1px solid ${bc.border}`,
                          color: bc.text,
                          fontSize: "0.72rem",
                          fontWeight: 800,
                          letterSpacing: "0.02em",
                        }}
                      >
                        {formatBenefit(promo)}
                      </span>

                      {/* Scope pill */}
                      <span
                        style={{
                          display: "inline-block",
                          padding: "3px 10px",
                          borderRadius: "20px",
                          background: "var(--brand-soft)",
                          border: "1px solid var(--line-bright)",
                          color: "var(--brand)",
                          fontSize: "0.65rem",
                          fontWeight: 700,
                          letterSpacing: "0.06em",
                          textTransform: "uppercase",
                        }}
                      >
                        {formatScopeLabel(promo.scopeType)}
                      </span>

                      {/* Flash sale indicator */}
                      {promo.flashSale && (
                        <span
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "4px",
                            padding: "3px 10px",
                            borderRadius: "20px",
                            background: "rgba(251,191,36,0.12)",
                            border: "1px solid rgba(251,191,36,0.25)",
                            color: "#fbbf24",
                            fontSize: "0.65rem",
                            fontWeight: 700,
                            letterSpacing: "0.06em",
                          }}
                        >
                          <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
                          FLASH
                        </span>
                      )}

                      {/* Auto-apply badge */}
                      {promo.autoApply && (
                        <span
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "4px",
                            padding: "3px 10px",
                            borderRadius: "20px",
                            background: "rgba(52,211,153,0.1)",
                            border: "1px solid rgba(52,211,153,0.25)",
                            color: "#34d399",
                            fontSize: "0.65rem",
                            fontWeight: 700,
                          }}
                        >
                          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                          Auto-applies
                        </span>
                      )}

                      {/* Stackable badge */}
                      {promo.stackable && (
                        <span
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "4px",
                            padding: "3px 10px",
                            borderRadius: "20px",
                            background: "rgba(167,139,250,0.1)",
                            border: "1px solid rgba(167,139,250,0.25)",
                            color: "#a78bfa",
                            fontSize: "0.65rem",
                            fontWeight: 700,
                          }}
                        >
                          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2" /><path d="M16 2v4" /><path d="M8 2v4" /></svg>
                          Stackable
                        </span>
                      )}
                    </div>

                    {/* Details row */}
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "12px", fontSize: "0.75rem", color: "var(--muted)" }}>
                      {promo.minimumOrderAmount != null && promo.minimumOrderAmount > 0 && (
                        <span>Min. order: <strong style={{ color: "var(--ink-light)" }}>${promo.minimumOrderAmount}</strong></span>
                      )}
                      {promo.maximumDiscountAmount != null && promo.maximumDiscountAmount > 0 && (
                        <span>Max discount: <strong style={{ color: "var(--ink-light)" }}>${promo.maximumDiscountAmount}</strong></span>
                      )}
                      {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                        <span>
                          {promo.spendTiers.length} tier{promo.spendTiers.length !== 1 ? "s" : ""}
                        </span>
                      )}
                    </div>

                    {/* Spend tiers detail */}
                    {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                      <div
                        style={{
                          background: "rgba(244,114,182,0.05)",
                          border: "1px solid rgba(244,114,182,0.15)",
                          borderRadius: "10px",
                          padding: "10px 14px",
                        }}
                      >
                        <p style={{ fontSize: "0.7rem", fontWeight: 700, color: "#f472b6", margin: "0 0 6px 0", letterSpacing: "0.06em", textTransform: "uppercase" }}>
                          Spend Tiers
                        </p>
                        {promo.spendTiers
                          .slice()
                          .sort((a, b) => a.thresholdAmount - b.thresholdAmount)
                          .map((tier, idx) => (
                            <div key={idx} style={{ fontSize: "0.75rem", color: "var(--muted)", padding: "2px 0" }}>
                              Spend <strong style={{ color: "var(--ink-light)" }}>${tier.thresholdAmount}</strong> &rarr; Save <strong style={{ color: "#f472b6" }}>${tier.discountAmount}</strong>
                            </div>
                          ))}
                      </div>
                    )}

                    {/* Dates */}
                    {hasDateRange && (
                      <div style={{ fontSize: "0.72rem", color: "var(--muted)", display: "flex", alignItems: "center", gap: "6px" }}>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
                        </svg>
                        <span>
                          {promo.startsAt ? formatDate(promo.startsAt) : "Open"}{" "}&ndash;{" "}
                          {promo.endsAt ? formatDate(promo.endsAt) : "Ongoing"}
                        </span>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}

          {/* Pagination */}
          {!loading && totalPages > 0 && (
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={setPage}
              disabled={loading}
            />
          )}
        </section>
      </main>

      <Footer />
    </div>
  );
}
