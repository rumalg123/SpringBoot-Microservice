"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import Pagination from "../components/Pagination";
import { useAuthSession } from "../../lib/authSession";
import type { PublicPromotion, SpendTier } from "../../lib/types/promotion";
import type { PagedResponse } from "../../lib/types/pagination";
import { API_BASE, PAGE_SIZE_DEFAULT as PAGE_SIZE } from "../../lib/constants";

/* ───── helpers ───── */

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
      className="relative shrink-0 overflow-hidden rounded-lg border border-transparent bg-[rgba(255,255,255,0.03)] px-5 py-6"
      style={{ minWidth: "280px", maxWidth: "340px", boxShadow: "0 0 20px rgba(0,212,255,0.08), inset 0 0 20px rgba(0,212,255,0.02)" }}
    >
      {/* gradient border overlay */}
      <div
        className="pointer-events-none absolute inset-0 rounded-lg p-px"
        style={{
          background: "linear-gradient(135deg, var(--brand), var(--accent))",
          WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
          WebkitMaskComposite: "xor",
          maskComposite: "exclude",
        }}
      />
      {/* flash badge */}
      <div className="mb-3 inline-flex items-center gap-1.5 rounded-xl border border-[rgba(251,191,36,0.3)] bg-[linear-gradient(135deg,rgba(251,191,36,0.15),rgba(244,114,182,0.15))] px-3 py-1 text-[0.65rem] font-extrabold uppercase tracking-[0.1em] text-[#fbbf24]">

        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
        FLASH SALE
      </div>

      <h3 className="mb-2 mt-0 font-[Syne,sans-serif] text-[1.05rem] font-extrabold leading-[1.3] text-white">
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
      <div className="mt-1 flex items-center gap-2">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
        </svg>
        <span
          className="text-sm font-bold tabular-nums"
          style={{ color: countdown === "Ended" ? "var(--danger)" : "var(--brand)" }}
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
        const data = (await res.json()) as PagedResponse<PublicPromotion>;
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
        const data = (await res.json()) as PagedResponse<PublicPromotion>;
        setPromotions(data.content || []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
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
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-base text-muted">Loading...</p>
        </div>
      </div>
    );
  }

  /* ───── styles ───── */


  const inputCls = "flex-1 min-w-[200px] rounded-md border border-line-bright bg-[rgba(255,255,255,0.03)] py-2.5 pl-10 pr-3.5 text-sm font-medium text-white outline-none focus:border-brand";

  return (
    <div className="min-h-screen bg-bg">
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
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
        <div className="mb-8">
          <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
            Promotions & Deals
          </h1>
          <p className="mt-1.5 text-sm text-muted">
            Discover current offers, flash sales, and savings across the platform.
          </p>
        </div>

        {/* ── Flash Sales Section ── */}
        {!flashLoading && flashSales.length > 0 && (
          <section className="mb-10">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="#fbbf24"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
                <h2 className="m-0 font-[Syne,sans-serif] text-[1.2rem] font-extrabold text-white">
                  Flash Sales
                </h2>
                <span className="rounded-xl border border-[rgba(251,191,36,0.25)] bg-[rgba(251,191,36,0.12)] px-2.5 py-[3px] text-[0.65rem] font-bold tracking-[0.08em] text-[#fbbf24]">

                  LIMITED TIME
                </span>
              </div>
              {/* scroll arrows */}
              <div className="flex gap-1.5">
                <button
                  type="button"
                  onClick={() => scrollFlash("left")}
                  className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.03)] text-muted"
                  aria-label="Scroll left"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                </button>
                <button
                  type="button"
                  onClick={() => scrollFlash("right")}
                  className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.03)] text-muted"
                  aria-label="Scroll right"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
                </button>
              </div>
            </div>

            {/* Horizontal scroll container */}
            <div
              ref={flashScrollRef}
              className="flex gap-4 overflow-x-auto pb-2"
              style={{ scrollbarWidth: "thin", scrollbarColor: "var(--line-bright) transparent" }}
            >
              {flashSales.map((fs) => (
                <FlashSaleCard key={fs.id} promo={fs} />
              ))}
            </div>
          </section>
        )}

        {/* ── All Promotions Section ── */}
        <section>
          <h2 className="mb-4 mt-0 font-[Syne,sans-serif] text-[1.2rem] font-extrabold text-white">
            All Promotions
          </h2>

          {/* Search & Filters */}
          <div className="mb-6 flex flex-wrap items-center gap-3">
            {/* Search input with icon */}
            <div className="relative min-w-[200px] flex-1">
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="var(--muted)"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2"
              >
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
              <input
                type="text"
                placeholder="Search promotions..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className={inputCls}
              />
            </div>

            {/* Scope filter */}
            <select
              value={scopeFilter}
              onChange={(e) => setScopeFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">All Scopes</option>
              <option value="ORDER">Order</option>
              <option value="VENDOR">Vendor</option>
              <option value="PRODUCT">Product</option>
              <option value="CATEGORY">Category</option>
            </select>

            {/* Benefit filter */}
            <select
              value={benefitFilter}
              onChange={(e) => setBenefitFilter(e.target.value)}
              className="filter-select"
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
            <div className="py-[60px] text-center">
              <div className="spinner-lg" />
              <p className="mt-3 text-sm text-muted">Loading promotions...</p>
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
            <div className="grid gap-[18px]" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))" }}>
              {promotions.map((promo) => {
                const bc = benefitColor(promo.benefitType);
                const hasDateRange = promo.startsAt || promo.endsAt;

                return (
                  <article
                    key={promo.id}
                    className="animate-rise flex flex-col gap-3 rounded-lg border border-line bg-[rgba(255,255,255,0.03)] p-5 transition-all duration-200 hover:border-line-bright hover:shadow-[0_4px_24px_rgba(0,0,0,0.3)]"
                  >
                    {/* Top row: name + flash badge if applicable */}
                    <div>
                      <h3 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold leading-[1.35] text-white">
                        {promo.name}
                      </h3>
                      {promo.description && (
                        <p className="mt-1.5 line-clamp-2 text-sm leading-[1.5] text-muted">
                          {promo.description}
                        </p>
                      )}
                    </div>

                    {/* Badges row */}
                    <div className="flex flex-wrap items-center gap-1.5">
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
                    <div className="flex flex-wrap gap-3 text-xs text-muted">
                      {promo.minimumOrderAmount != null && promo.minimumOrderAmount > 0 && (
                        <span>Min. order: <strong className="text-ink-light">${promo.minimumOrderAmount}</strong></span>
                      )}
                      {promo.maximumDiscountAmount != null && promo.maximumDiscountAmount > 0 && (
                        <span>Max discount: <strong className="text-ink-light">${promo.maximumDiscountAmount}</strong></span>
                      )}
                      {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                        <span>
                          {promo.spendTiers.length} tier{promo.spendTiers.length !== 1 ? "s" : ""}
                        </span>
                      )}
                    </div>

                    {/* Spend tiers detail */}
                    {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                      <div className="rounded-md border border-[rgba(244,114,182,0.15)] bg-[rgba(244,114,182,0.05)] px-3.5 py-2.5">
                        <p className="mb-1.5 mt-0 text-xs font-bold uppercase tracking-[0.06em] text-[#f472b6]">
                          Spend Tiers
                        </p>
                        {promo.spendTiers
                          .slice()
                          .sort((a, b) => a.thresholdAmount - b.thresholdAmount)
                          .map((tier, idx) => (
                            <div key={idx} className="py-0.5 text-xs text-muted">
                              Spend <strong className="text-ink-light">${tier.thresholdAmount}</strong> &rarr; Save <strong className="text-[#f472b6]">${tier.discountAmount}</strong>
                            </div>
                          ))}
                      </div>
                    )}

                    {/* Dates */}
                    {hasDateRange && (
                      <div className="flex items-center gap-1.5 text-xs text-muted">
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
