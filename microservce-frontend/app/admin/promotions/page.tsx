"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import ConfirmModal from "../../components/ConfirmModal";
import SearchableSelect from "../../components/ui/SearchableSelect";
import MultiSearchSelect from "../../components/ui/MultiSearchSelect";
import StatusBadge, { LIFECYCLE_COLORS, APPROVAL_COLORS, ACTIVE_INACTIVE_COLORS } from "../../components/ui/StatusBadge";
import { useAuthSession } from "../../../lib/authSession";

/* ───── enums & types ───── */

type ScopeType = "ORDER" | "VENDOR" | "PRODUCT" | "CATEGORY";
type AppLevel = "LINE_ITEM" | "CART" | "SHIPPING";
type BenefitType = "PERCENTAGE_OFF" | "FIXED_AMOUNT_OFF" | "FREE_SHIPPING" | "BUY_X_GET_Y" | "TIERED_SPEND" | "BUNDLE_DISCOUNT";
type FundingSource = "PLATFORM" | "VENDOR" | "SHARED";
type LifecycleStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "ARCHIVED";
type ApprovalStatus = "NOT_REQUIRED" | "PENDING" | "APPROVED" | "REJECTED";

type SpendTier = { thresholdAmount: number; discountAmount: number };

type Promotion = {
  id: string;
  name: string;
  description: string;
  vendorId: string | null;
  scopeType: ScopeType;
  targetProductIds: string[];
  targetCategoryIds: string[];
  applicationLevel: AppLevel;
  benefitType: BenefitType;
  benefitValue: number | null;
  buyQuantity: number | null;
  getQuantity: number | null;
  spendTiers: SpendTier[];
  minimumOrderAmount: number | null;
  maximumDiscountAmount: number | null;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  fundingSource: FundingSource;
  stackable: boolean;
  exclusive: boolean;
  autoApply: boolean;
  priority: number;
  lifecycleStatus: LifecycleStatus;
  approvalStatus: ApprovalStatus;
  approvalNote: string | null;
  startsAt: string | null;
  endsAt: string | null;
  createdByUserSub: string | null;
  submittedAt: string | null;
  approvedAt: string | null;
  rejectedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

type CouponCode = {
  id: string;
  promotionId: string;
  code: string;
  active: boolean;
  maxUses: number | null;
  maxUsesPerCustomer: number | null;
  reservationTtlSeconds: number | null;
  startsAt: string | null;
  endsAt: string | null;
  createdAt: string;
};

type Analytics = {
  promotionId: string;
  name: string;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  activeReservedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  couponCodeCount: number;
  activeCouponCodeCount: number;
  reservationCount: number;
  activeReservedReservationCount: number;
  committedReservationCount: number;
  releasedReservationCount: number;
  expiredReservationCount: number;
  committedDiscountAmount: number | null;
  releasedDiscountAmount: number | null;
};

type PromotionAnalytics = {
  promotionId: string;
  name: string;
  vendorId: string | null;
  scopeType: string;
  applicationLevel: string;
  benefitType: string;
  lifecycleStatus: string;
  approvalStatus: string;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  activeReservedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  couponCodeCount: number;
  activeCouponCodeCount: number;
  reservationCount: number;
  activeReservedReservationCount: number;
  committedReservationCount: number;
  releasedReservationCount: number;
  expiredReservationCount: number;
  committedDiscountAmount: number | null;
  releasedDiscountAmount: number | null;
  startsAt: string | null;
  endsAt: string | null;
  createdAt: string;
  updatedAt: string;
};

type PageResponse<T> = { content: T[]; totalElements: number; totalPages: number; number: number; size: number };

type FormState = {
  id?: string;
  name: string;
  description: string;
  vendorId: string;
  scopeType: ScopeType;
  targetProductIds: string;
  targetCategoryIds: string;
  applicationLevel: AppLevel;
  benefitType: BenefitType;
  benefitValue: string;
  buyQuantity: string;
  getQuantity: string;
  spendTiers: SpendTier[];
  minimumOrderAmount: string;
  maximumDiscountAmount: string;
  budgetAmount: string;
  fundingSource: FundingSource;
  stackable: boolean;
  exclusive: boolean;
  autoApply: boolean;
  priority: string;
  startsAt: string;
  endsAt: string;
};

const emptyForm: FormState = {
  name: "", description: "", vendorId: "", scopeType: "ORDER", targetProductIds: "", targetCategoryIds: "",
  applicationLevel: "CART", benefitType: "PERCENTAGE_OFF", benefitValue: "", buyQuantity: "", getQuantity: "",
  spendTiers: [], minimumOrderAmount: "", maximumDiscountAmount: "", budgetAmount: "", fundingSource: "PLATFORM",
  stackable: false, exclusive: false, autoApply: true, priority: "100", startsAt: "", endsAt: "",
};

/* ───── helpers ───── */

const money = (v: number | null | undefined) => v != null ? `$${Number(v).toFixed(2)}` : "—";

function toLocalDateTime(v: string | null) {
  if (!v) return "";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function toIsoOrNull(v: string) {
  const t = v.trim();
  if (!t) return null;
  const d = new Date(t);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

function getApiErrorMessage(err: unknown, fallback: string) {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { message?: string; response?: { data?: { message?: string; error?: string } | string } };
    const rd = maybe.response?.data;
    if (typeof rd === "string" && rd.trim()) return rd.trim();
    if (rd && typeof rd === "object") {
      if (typeof rd.message === "string" && rd.message.trim()) return rd.message.trim();
      if (typeof rd.error === "string" && rd.error.trim()) return rd.error.trim();
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) return maybe.message.trim();
  }
  return fallback;
}

/* LIFECYCLE_COLORS, APPROVAL_COLORS, and StatusBadge imported from components/ui/StatusBadge */

const panelStyle: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)", border: "1px solid var(--line)", borderRadius: 16, padding: 16,
};

const fieldBase: React.CSSProperties = {
  background: "var(--surface-2)", borderColor: "var(--line)", color: "var(--ink)",
};

/* ───── component ───── */

export default function AdminPromotionsPage() {
  const router = useRouter();
  const session = useAuthSession();

  /* list state */
  const [items, setItems] = useState<Promotion[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [statusMsg, setStatusMsg] = useState("Loading promotions...");

  /* filters */
  const [filterLifecycle, setFilterLifecycle] = useState("");
  const [filterApproval, setFilterApproval] = useState("");
  const [filterScope, setFilterScope] = useState("");
  const [filterBenefit, setFilterBenefit] = useState("");
  const [searchQuery, setSearchQuery] = useState("");

  /* form */
  const [form, setForm] = useState<FormState>(emptyForm);
  const [submitting, setSubmitting] = useState(false);

  /* detail & workflow */
  const [selected, setSelected] = useState<Promotion | null>(null);
  const [workflowBusy, setWorkflowBusy] = useState(false);
  const [approvalNote, setApprovalNote] = useState("");

  /* coupons */
  const [coupons, setCoupons] = useState<CouponCode[]>([]);
  const [couponLoading, setCouponLoading] = useState(false);
  const [couponForm, setCouponForm] = useState({ code: "", maxUses: "", maxUsesPerCustomer: "", reservationTtlSeconds: "300", active: true, startsAt: "", endsAt: "" });
  const [couponSubmitting, setCouponSubmitting] = useState(false);

  /* analytics */
  const [analytics, setAnalytics] = useState<Analytics | null>(null);

  /* tabs for selected promo */
  const [tab, setTab] = useState<"details" | "coupons" | "analytics">("details");

  /* page-level analytics view */
  const [analyticsView, setAnalyticsView] = useState(false);
  const [analyticsData, setAnalyticsData] = useState<PromotionAnalytics[]>([]);
  const [analyticsPage, setAnalyticsPage] = useState(0);
  const [analyticsTotalPages, setAnalyticsTotalPages] = useState(0);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [analyticsSearch, setAnalyticsSearch] = useState("");
  const [analyticsFilterLifecycle, setAnalyticsFilterLifecycle] = useState("");
  const [analyticsFilterScope, setAnalyticsFilterScope] = useState("");
  const [analyticsFilterBenefit, setAnalyticsFilterBenefit] = useState("");
  const [analyticsFilterVendor, setAnalyticsFilterVendor] = useState("");

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((s) => ({ ...s, [key]: value }));

  /* ───── load list ───── */
  const load = async (p = 0) => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(p), size: "20" });
      if (searchQuery.trim()) params.set("q", searchQuery.trim());
      if (filterLifecycle) params.set("lifecycleStatus", filterLifecycle);
      if (filterApproval) params.set("approvalStatus", filterApproval);
      if (filterScope) params.set("scopeType", filterScope);
      if (filterBenefit) params.set("benefitType", filterBenefit);
      const res = await session.apiClient.get(`/admin/promotions?${params.toString()}`);
      const data = res.data as PageResponse<Promotion>;
      setItems(data.content || []);
      setTotalPages(data.totalPages || 0);
      setPage(data.number || 0);
      setStatusMsg(`${data.totalElements} promotion(s) found.`);
    } catch (e) {
      setStatusMsg(getApiErrorMessage(e, "Failed to load promotions."));
    } finally {
      setLoading(false);
    }
  };

  /* ───── load analytics list ───── */
  const loadAnalyticsList = async (p = 0) => {
    if (!session.apiClient) return;
    setAnalyticsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(p), size: "20" });
      if (analyticsSearch.trim()) params.set("q", analyticsSearch.trim());
      if (analyticsFilterLifecycle) params.set("lifecycleStatus", analyticsFilterLifecycle);
      if (analyticsFilterScope) params.set("scopeType", analyticsFilterScope);
      if (analyticsFilterBenefit) params.set("benefitType", analyticsFilterBenefit);
      if (analyticsFilterVendor.trim()) params.set("vendorId", analyticsFilterVendor.trim());
      const res = await session.apiClient.get(`/admin/promotions/analytics?${params.toString()}`);
      const data = res.data as PageResponse<PromotionAnalytics>;
      setAnalyticsData(data.content || []);
      setAnalyticsTotalPages(data.totalPages || 0);
      setAnalyticsPage(data.number || 0);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to load promotion analytics."));
    } finally {
      setAnalyticsLoading(false);
    }
  };

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) { router.replace("/"); return; }
    if (!session.canViewAdmin) { router.replace("/products"); return; }
    void load();
  }, [session.status, session.isAuthenticated, session.canViewAdmin, router]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { void load(0); }, [filterLifecycle, filterApproval, filterScope, filterBenefit]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (analyticsView && session.status === "ready" && session.isAuthenticated) void loadAnalyticsList(0);
  }, [analyticsView, analyticsFilterLifecycle, analyticsFilterScope, analyticsFilterBenefit, analyticsFilterVendor]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ───── select / detail ───── */
  const selectPromo = async (p: Promotion) => {
    setSelected(p);
    setTab("details");
    setAnalytics(null);
    setCoupons([]);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const loadCoupons = async (promoId: string) => {
    if (!session.apiClient) return;
    setCouponLoading(true);
    try {
      const res = await session.apiClient.get(`/admin/promotions/${promoId}/coupons`);
      setCoupons((res.data as CouponCode[]) || []);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to load coupons."));
    } finally {
      setCouponLoading(false);
    }
  };

  const loadAnalytics = async (promoId: string) => {
    if (!session.apiClient) return;
    try {
      const res = await session.apiClient.get(`/admin/promotions/${promoId}/analytics`);
      setAnalytics(res.data as Analytics);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to load analytics."));
    }
  };

  useEffect(() => {
    if (!selected) return;
    if (tab === "coupons") void loadCoupons(selected.id);
    if (tab === "analytics") void loadAnalytics(selected.id);
  }, [tab, selected?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ───── workflow actions ───── */
  const doWorkflow = async (action: string, body?: object) => {
    if (!session.apiClient || !selected || workflowBusy) return;
    setWorkflowBusy(true);
    try {
      const res = await session.apiClient.post(`/admin/promotions/${selected.id}/${action}`, body || {});
      const updated = res.data as Promotion;
      setSelected(updated);
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
      toast.success(`Promotion ${action}ed`);
      setApprovalNote("");
    } catch (e) {
      toast.error(getApiErrorMessage(e, `Failed to ${action}.`));
    } finally {
      setWorkflowBusy(false);
    }
  };

  /* ───── create / edit ───── */
  const editPromo = (p: Promotion) => {
    setForm({
      id: p.id,
      name: p.name,
      description: p.description,
      vendorId: p.vendorId || "",
      scopeType: p.scopeType,
      targetProductIds: (p.targetProductIds || []).join(", "),
      targetCategoryIds: (p.targetCategoryIds || []).join(", "),
      applicationLevel: p.applicationLevel,
      benefitType: p.benefitType,
      benefitValue: p.benefitValue != null ? String(p.benefitValue) : "",
      buyQuantity: p.buyQuantity != null ? String(p.buyQuantity) : "",
      getQuantity: p.getQuantity != null ? String(p.getQuantity) : "",
      spendTiers: p.spendTiers || [],
      minimumOrderAmount: p.minimumOrderAmount != null ? String(p.minimumOrderAmount) : "",
      maximumDiscountAmount: p.maximumDiscountAmount != null ? String(p.maximumDiscountAmount) : "",
      budgetAmount: p.budgetAmount != null ? String(p.budgetAmount) : "",
      fundingSource: p.fundingSource,
      stackable: p.stackable,
      exclusive: p.exclusive,
      autoApply: p.autoApply,
      priority: String(p.priority),
      startsAt: toLocalDateTime(p.startsAt),
      endsAt: toLocalDateTime(p.endsAt),
    });
    setSelected(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient || submitting) return;
    setSubmitting(true);
    try {
      if (!form.name.trim()) throw new Error("Name is required");
      if (!form.description.trim()) throw new Error("Description is required");

      const parseIds = (s: string) => {
        const parts = s.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean);
        return parts.length ? parts : undefined;
      };
      const toNum = (s: string) => { const n = Number(s); return s.trim() && Number.isFinite(n) ? n : undefined; };

      const payload = {
        name: form.name.trim(),
        description: form.description.trim(),
        vendorId: form.vendorId.trim() || null,
        scopeType: form.scopeType,
        targetProductIds: parseIds(form.targetProductIds),
        targetCategoryIds: parseIds(form.targetCategoryIds),
        applicationLevel: form.applicationLevel,
        benefitType: form.benefitType,
        benefitValue: toNum(form.benefitValue),
        buyQuantity: toNum(form.buyQuantity),
        getQuantity: toNum(form.getQuantity),
        spendTiers: form.spendTiers.length ? form.spendTiers : undefined,
        minimumOrderAmount: toNum(form.minimumOrderAmount),
        maximumDiscountAmount: toNum(form.maximumDiscountAmount),
        budgetAmount: toNum(form.budgetAmount),
        fundingSource: form.fundingSource,
        stackable: form.stackable,
        exclusive: form.exclusive,
        autoApply: form.autoApply,
        priority: Number(form.priority) || 100,
        startsAt: toIsoOrNull(form.startsAt),
        endsAt: toIsoOrNull(form.endsAt),
      };

      if (form.id) {
        await session.apiClient.put(`/admin/promotions/${form.id}`, payload);
        toast.success("Promotion updated");
      } else {
        await session.apiClient.post("/admin/promotions", payload);
        toast.success("Promotion created");
      }
      setForm(emptyForm);
      await load(page);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Save failed"));
    } finally {
      setSubmitting(false);
    }
  };

  /* ───── coupon create ───── */
  const createCoupon = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient || !selected || couponSubmitting) return;
    setCouponSubmitting(true);
    try {
      if (!couponForm.code.trim()) throw new Error("Coupon code is required");
      const toInt = (s: string) => { const n = parseInt(s, 10); return s.trim() && Number.isFinite(n) ? n : undefined; };
      const payload = {
        code: couponForm.code.trim().toUpperCase(),
        maxUses: toInt(couponForm.maxUses),
        maxUsesPerCustomer: toInt(couponForm.maxUsesPerCustomer),
        reservationTtlSeconds: toInt(couponForm.reservationTtlSeconds),
        active: couponForm.active,
        startsAt: toIsoOrNull(couponForm.startsAt),
        endsAt: toIsoOrNull(couponForm.endsAt),
      };
      await session.apiClient.post(`/admin/promotions/${selected.id}/coupons`, payload);
      toast.success("Coupon created");
      setCouponForm({ code: "", maxUses: "", maxUsesPerCustomer: "", reservationTtlSeconds: "300", active: true, startsAt: "", endsAt: "" });
      await loadCoupons(selected.id);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to create coupon"));
    } finally {
      setCouponSubmitting(false);
    }
  };

  /* ───── spend tier management ───── */
  const addTier = () => setForm((s) => ({ ...s, spendTiers: [...s.spendTiers, { thresholdAmount: 0, discountAmount: 0 }] }));
  const removeTier = (i: number) => setForm((s) => ({ ...s, spendTiers: s.spendTiers.filter((_, idx) => idx !== i) }));
  const updateTier = (i: number, key: keyof SpendTier, value: number) =>
    setForm((s) => ({ ...s, spendTiers: s.spendTiers.map((t, idx) => (idx === i ? { ...t, [key]: value } : t)) }));

  /* ───── render gates ───── */
  if (session.status === "loading" || session.status === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><div className="spinner-lg" /></div>;
  }
  if (!session.isAuthenticated) return null;

  /* ───── workflow buttons for selected promo ───── */
  const workflowButtons = (p: Promotion) => {
    const btns: { label: string; action: string; needsNote?: boolean; color: string; bg: string; border: string }[] = [];
    const { lifecycleStatus: ls, approvalStatus: as_ } = p;

    if (ls === "DRAFT" && (as_ === "NOT_REQUIRED" || as_ === "REJECTED")) {
      btns.push({ label: "Submit for Approval", action: "submit", color: "var(--brand)", bg: "var(--brand-soft)", border: "var(--line-bright)" });
    }
    if (as_ === "PENDING") {
      btns.push({ label: "Approve", action: "approve", color: "var(--success)", bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)" });
      btns.push({ label: "Reject", action: "reject", needsNote: true, color: "#f87171", bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)" });
    }
    if (ls === "DRAFT" && (as_ === "APPROVED" || as_ === "NOT_REQUIRED")) {
      btns.push({ label: "Activate", action: "activate", color: "var(--success)", bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)" });
    }
    if (ls === "ACTIVE") {
      btns.push({ label: "Pause", action: "pause", color: "var(--warning-text)", bg: "var(--warning-soft)", border: "var(--warning-border)" });
    }
    if (ls === "PAUSED") {
      btns.push({ label: "Activate", action: "activate", color: "var(--success)", bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)" });
      btns.push({ label: "Archive", action: "archive", color: "#f87171", bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)" });
    }
    return btns;
  };

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />
      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link><span className="breadcrumb-sep">&gt;</span>
          <Link href="/admin/products">Admin</Link><span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">Promotions</span>
        </nav>

        {/* ───── Page-level View Toggle ───── */}
        {!selected && (
          <div className="mb-4 flex gap-1" style={{ borderBottom: "1px solid var(--line)" }}>
            {(["Promotions", "Analytics"] as const).map((v) => {
              const isActive = v === "Analytics" ? analyticsView : !analyticsView;
              return (
                <button key={v} type="button" onClick={() => { setAnalyticsView(v === "Analytics"); if (v === "Promotions") setSelected(null); }}
                  style={{
                    padding: "10px 20px", fontWeight: 700, fontSize: "0.85rem", borderRadius: "8px 8px 0 0",
                    border: "1px solid transparent", borderBottom: "none", cursor: "pointer",
                    background: isActive ? "var(--brand-soft)" : "transparent",
                    color: isActive ? "var(--brand)" : "var(--muted)",
                    borderColor: isActive ? "var(--line-bright)" : "transparent",
                  }}
                >
                  {v}
                </button>
              );
            })}
          </div>
        )}

        {/* ───── Selected Promotion Detail ───── */}
        {selected && (
          <section className="mb-5" style={panelStyle}>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-3">
                <button type="button" onClick={() => setSelected(null)} style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.8rem" }}>
                  &larr; Back
                </button>
                <h2 style={{ margin: 0, color: "var(--ink)", fontFamily: "'Syne', sans-serif" }}>{selected.name}</h2>
              </div>
              <div className="flex gap-2">
                <StatusBadge value={selected.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
                <StatusBadge value={selected.approvalStatus} colorMap={APPROVAL_COLORS} />
              </div>
            </div>

            {/* Tabs */}
            <div className="mb-4 flex gap-1" style={{ borderBottom: "1px solid var(--line)" }}>
              {(["details", "coupons", "analytics"] as const).map((t) => (
                <button key={t} type="button" onClick={() => setTab(t)}
                  style={{
                    padding: "8px 16px", fontWeight: 700, fontSize: "0.82rem", borderRadius: "8px 8px 0 0",
                    border: "1px solid transparent", borderBottom: "none", cursor: "pointer",
                    background: tab === t ? "var(--brand-soft)" : "transparent",
                    color: tab === t ? "var(--brand)" : "var(--muted)",
                    borderColor: tab === t ? "var(--line-bright)" : "transparent",
                  }}
                >
                  {t.charAt(0).toUpperCase() + t.slice(1)}
                </button>
              ))}
            </div>

            {/* Details tab */}
            {tab === "details" && (
              <div className="grid gap-4">
                <p style={{ color: "var(--ink-light)", fontSize: "0.85rem" }}>{selected.description}</p>

                <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3" style={{ fontSize: "0.82rem" }}>
                  <div><span style={{ color: "var(--muted)" }}>Scope:</span> <span style={{ color: "var(--ink-light)" }}>{selected.scopeType}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Application:</span> <span style={{ color: "var(--ink-light)" }}>{selected.applicationLevel.replace(/_/g, " ")}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Benefit:</span> <span style={{ color: "var(--ink-light)" }}>{selected.benefitType.replace(/_/g, " ")}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Value:</span> <span style={{ color: "var(--ink-light)" }}>{selected.benefitValue ?? "—"}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Funding:</span> <span style={{ color: "var(--ink-light)" }}>{selected.fundingSource}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Priority:</span> <span style={{ color: "var(--ink-light)" }}>{selected.priority}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Min Order:</span> <span style={{ color: "var(--ink-light)" }}>{money(selected.minimumOrderAmount)}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Max Discount:</span> <span style={{ color: "var(--ink-light)" }}>{money(selected.maximumDiscountAmount)}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Budget:</span> <span style={{ color: "var(--ink-light)" }}>{money(selected.budgetAmount)}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Burned:</span> <span style={{ color: "var(--ink-light)" }}>{money(selected.burnedBudgetAmount)}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Remaining:</span> <span style={{ color: "var(--ink-light)" }}>{money(selected.remainingBudgetAmount)}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Stackable:</span> <span style={{ color: "var(--ink-light)" }}>{selected.stackable ? "Yes" : "No"}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Exclusive:</span> <span style={{ color: "var(--ink-light)" }}>{selected.exclusive ? "Yes" : "No"}</span></div>
                  <div><span style={{ color: "var(--muted)" }}>Auto-apply:</span> <span style={{ color: "var(--ink-light)" }}>{selected.autoApply ? "Yes" : "No"}</span></div>
                  {selected.vendorId && <div><span style={{ color: "var(--muted)" }}>Vendor:</span> <span style={{ color: "var(--ink-light)", fontSize: "0.75rem" }}>{selected.vendorId}</span></div>}
                  {selected.startsAt && <div><span style={{ color: "var(--muted)" }}>Starts:</span> <span style={{ color: "var(--ink-light)" }}>{new Date(selected.startsAt).toLocaleString()}</span></div>}
                  {selected.endsAt && <div><span style={{ color: "var(--muted)" }}>Ends:</span> <span style={{ color: "var(--ink-light)" }}>{new Date(selected.endsAt).toLocaleString()}</span></div>}
                </div>

                {selected.buyQuantity != null && selected.getQuantity != null && (
                  <div style={{ fontSize: "0.82rem", color: "var(--ink-light)" }}>Buy {selected.buyQuantity} Get {selected.getQuantity}</div>
                )}

                {selected.spendTiers && selected.spendTiers.length > 0 && (
                  <div>
                    <h4 style={{ color: "var(--ink)", margin: "0 0 6px" }}>Spend Tiers</h4>
                    <div className="grid gap-1" style={{ fontSize: "0.8rem" }}>
                      {selected.spendTiers.map((t, i) => (
                        <div key={i} style={{ color: "var(--ink-light)" }}>Spend {money(t.thresholdAmount)} &rarr; Save {money(t.discountAmount)}</div>
                      ))}
                    </div>
                  </div>
                )}

                {selected.approvalNote && (
                  <div className="alert alert-warning">{selected.approvalNote}</div>
                )}

                {/* Workflow buttons */}
                <div className="flex flex-wrap gap-2">
                  {workflowButtons(selected).map((btn) => (
                    <button key={btn.action} type="button" disabled={workflowBusy}
                      onClick={() => {
                        if (btn.needsNote) {
                          if (!approvalNote.trim()) { toast.error("Please enter a note for rejection"); return; }
                          void doWorkflow(btn.action, { note: approvalNote.trim() });
                        } else {
                          void doWorkflow(btn.action);
                        }
                      }}
                      style={{ padding: "8px 14px", borderRadius: 10, fontWeight: 700, fontSize: "0.82rem", border: `1px solid ${btn.border}`, background: btn.bg, color: btn.color, cursor: workflowBusy ? "not-allowed" : "pointer", opacity: workflowBusy ? 0.6 : 1 }}
                    >
                      {btn.label}
                    </button>
                  ))}
                  <button type="button" onClick={() => editPromo(selected)} style={{ padding: "8px 14px", borderRadius: 10, fontWeight: 700, fontSize: "0.82rem", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)" }}>
                    Edit
                  </button>
                </div>

                {/* Approval note input (for reject) */}
                {selected.approvalStatus === "PENDING" && (
                  <div>
                    <label className="form-label">Rejection Note</label>
                    <input value={approvalNote} onChange={(e) => setApprovalNote(e.target.value)} placeholder="Reason for rejection..." className="form-input" />
                  </div>
                )}
              </div>
            )}

            {/* Coupons tab */}
            {tab === "coupons" && (
              <div className="grid gap-4">
                <form onSubmit={(e) => { void createCoupon(e); }} className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4 items-end">
                  <div>
                    <label className="form-label">Code</label>
                    <input value={couponForm.code} onChange={(e) => setCouponForm((s) => ({ ...s, code: e.target.value }))} placeholder="SUMMER20" className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Max Uses</label>
                    <input value={couponForm.maxUses} onChange={(e) => setCouponForm((s) => ({ ...s, maxUses: e.target.value }))} placeholder="1000" className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Per Customer</label>
                    <input value={couponForm.maxUsesPerCustomer} onChange={(e) => setCouponForm((s) => ({ ...s, maxUsesPerCustomer: e.target.value }))} placeholder="1" className="form-input" />
                  </div>
                  <div>
                    <button type="submit" disabled={couponSubmitting} className="btn-primary" style={{ padding: "10px 14px", borderRadius: 10, fontWeight: 800, width: "100%" }}>
                      {couponSubmitting ? "Creating..." : "Create Coupon"}
                    </button>
                  </div>
                </form>

                {couponLoading && <div className="skeleton" style={{ height: 60, borderRadius: 12 }} />}
                {!couponLoading && coupons.length === 0 && <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No coupon codes yet.</p>}
                {coupons.length > 0 && (
                  <div className="grid gap-2">
                    {coupons.map((c) => (
                      <div key={c.id} className="flex flex-wrap items-center justify-between gap-2" style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                        <div className="flex items-center gap-3">
                          <code style={{ color: "var(--brand)", fontWeight: 700, fontSize: "0.9rem" }}>{c.code}</code>
                          <StatusBadge value={c.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                        </div>
                        <div className="flex items-center gap-3" style={{ fontSize: "0.78rem", color: "var(--muted)" }}>
                          {c.maxUses != null && <span>Max: {c.maxUses}</span>}
                          {c.maxUsesPerCustomer != null && <span>Per user: {c.maxUsesPerCustomer}</span>}
                          <span>{new Date(c.createdAt).toLocaleDateString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Analytics tab */}
            {tab === "analytics" && (
              <div>
                {!analytics && <div className="skeleton" style={{ height: 80, borderRadius: 12 }} />}
                {analytics && (
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" style={{ fontSize: "0.82rem" }}>
                    <div style={{ padding: 12, borderRadius: 10, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ color: "var(--muted)", marginBottom: 4 }}>Budget</div>
                      <div style={{ color: "var(--ink)", fontWeight: 700, fontSize: "1rem" }}>{money(analytics.budgetAmount)}</div>
                      <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>Burned: {money(analytics.burnedBudgetAmount)} | Reserved: {money(analytics.activeReservedBudgetAmount)}</div>
                    </div>
                    <div style={{ padding: 12, borderRadius: 10, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ color: "var(--muted)", marginBottom: 4 }}>Coupons</div>
                      <div style={{ color: "var(--ink)", fontWeight: 700, fontSize: "1rem" }}>{analytics.activeCouponCodeCount} / {analytics.couponCodeCount}</div>
                      <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>active / total</div>
                    </div>
                    <div style={{ padding: 12, borderRadius: 10, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ color: "var(--muted)", marginBottom: 4 }}>Reservations</div>
                      <div style={{ color: "var(--ink)", fontWeight: 700, fontSize: "1rem" }}>{analytics.reservationCount}</div>
                      <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>Committed: {analytics.committedReservationCount} | Active: {analytics.activeReservedReservationCount}</div>
                    </div>
                    <div style={{ padding: 12, borderRadius: 10, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ color: "var(--muted)", marginBottom: 4 }}>Discounts Given</div>
                      <div style={{ color: "var(--success)", fontWeight: 700, fontSize: "1rem" }}>{money(analytics.committedDiscountAmount)}</div>
                      <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>Released: {money(analytics.releasedDiscountAmount)}</div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </section>
        )}

        {/* ───── Create / Edit Form ───── */}
        {!selected && !analyticsView && (
          <>
            <div className="mb-4">
              <h1 style={{ margin: 0, color: "var(--ink)", fontFamily: "'Syne', sans-serif", fontWeight: 800 }}>
                {form.id ? "Edit Promotion" : "Admin Promotions"}
              </h1>
              <p style={{ margin: "4px 0 0", color: "var(--muted)", fontSize: "0.85rem" }}>
                Create, manage, and monitor promotions with coupon codes and analytics.
              </p>
            </div>

            <section className="mb-5" style={panelStyle}>
              <h3 style={{ margin: "0 0 12px", color: "var(--ink)" }}>{form.id ? "Update Promotion" : "Create Promotion"}</h3>
              <form onSubmit={(e) => { void submit(e); }} className="grid gap-3">
                <div className="grid gap-3 md:grid-cols-2">
                  <div>
                    <label className="form-label">Name *</label>
                    <input value={form.name} onChange={(e) => setField("name", e.target.value)} placeholder="Summer Sale 20% Off" className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Vendor (optional)</label>
                    <SearchableSelect
                      apiClient={session.apiClient}
                      endpoint="/admin/vendors"
                      labelField="name"
                      valueField="id"
                      placeholder="Search vendor by name..."
                      value={form.vendorId}
                      onChange={(v) => setField("vendorId", v)}
                      disabled={submitting}
                    />
                  </div>
                </div>

                <div>
                  <label className="form-label">Description *</label>
                  <textarea value={form.description} onChange={(e) => setField("description", e.target.value)} rows={2} placeholder="Promotion description..." className="form-input" />
                </div>

                <div className="grid gap-3 md:grid-cols-4">
                  <div>
                    <label className="form-label">Scope Type</label>
                    <select value={form.scopeType} onChange={(e) => setField("scopeType", e.target.value as ScopeType)} className="form-select">
                      {(["ORDER", "VENDOR", "PRODUCT", "CATEGORY"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="form-label">Application Level</label>
                    <select value={form.applicationLevel} onChange={(e) => setField("applicationLevel", e.target.value as AppLevel)} className="form-select">
                      {(["LINE_ITEM", "CART", "SHIPPING"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="form-label">Benefit Type</label>
                    <select value={form.benefitType} onChange={(e) => setField("benefitType", e.target.value as BenefitType)} className="form-select">
                      {(["PERCENTAGE_OFF", "FIXED_AMOUNT_OFF", "FREE_SHIPPING", "BUY_X_GET_Y", "TIERED_SPEND", "BUNDLE_DISCOUNT"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="form-label">Funding Source</label>
                    <select value={form.fundingSource} onChange={(e) => setField("fundingSource", e.target.value as FundingSource)} className="form-select">
                      {(["PLATFORM", "VENDOR", "SHARED"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
                    </select>
                  </div>
                </div>

                {/* Conditional fields based on benefit type */}
                {(form.benefitType === "PERCENTAGE_OFF" || form.benefitType === "FIXED_AMOUNT_OFF" || form.benefitType === "BUNDLE_DISCOUNT") && (
                  <div className="grid gap-3 md:grid-cols-3">
                    <div>
                      <label className="form-label">Benefit Value {form.benefitType === "PERCENTAGE_OFF" ? "(%)" : "($)"}</label>
                      <input value={form.benefitValue} onChange={(e) => setField("benefitValue", e.target.value)} placeholder={form.benefitType === "PERCENTAGE_OFF" ? "20" : "5.00"} className="form-input" />
                    </div>
                    <div>
                      <label className="form-label">Min Order Amount</label>
                      <input value={form.minimumOrderAmount} onChange={(e) => setField("minimumOrderAmount", e.target.value)} placeholder="0.00" className="form-input" />
                    </div>
                    <div>
                      <label className="form-label">Max Discount</label>
                      <input value={form.maximumDiscountAmount} onChange={(e) => setField("maximumDiscountAmount", e.target.value)} placeholder="50.00" className="form-input" />
                    </div>
                  </div>
                )}

                {form.benefitType === "BUY_X_GET_Y" && (
                  <div className="grid gap-3 md:grid-cols-2">
                    <div>
                      <label className="form-label">Buy Quantity</label>
                      <input value={form.buyQuantity} onChange={(e) => setField("buyQuantity", e.target.value)} placeholder="2" className="form-input" />
                    </div>
                    <div>
                      <label className="form-label">Get Quantity</label>
                      <input value={form.getQuantity} onChange={(e) => setField("getQuantity", e.target.value)} placeholder="1" className="form-input" />
                    </div>
                  </div>
                )}

                {form.benefitType === "TIERED_SPEND" && (
                  <div>
                    <div className="mb-2 flex items-center justify-between">
                      <label className="form-label" style={{ margin: 0 }}>Spend Tiers</label>
                      <button type="button" onClick={addTier} style={{ padding: "4px 10px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.75rem" }}>+ Add Tier</button>
                    </div>
                    {form.spendTiers.map((tier, i) => (
                      <div key={i} className="mb-2 flex items-center gap-2">
                        <input type="number" value={tier.thresholdAmount} onChange={(e) => updateTier(i, "thresholdAmount", Number(e.target.value))} placeholder="Spend $" className="form-input" style={{ flex: 1 }} />
                        <span style={{ color: "var(--muted)" }}>&rarr;</span>
                        <input type="number" value={tier.discountAmount} onChange={(e) => updateTier(i, "discountAmount", Number(e.target.value))} placeholder="Save $" className="form-input" style={{ flex: 1 }} />
                        <button type="button" onClick={() => removeTier(i)} style={{ padding: "6px 10px", borderRadius: 8, border: "1px solid rgba(239,68,68,0.2)", background: "rgba(239,68,68,0.08)", color: "#fca5a5", fontWeight: 700, fontSize: "0.75rem" }}>X</button>
                      </div>
                    ))}
                  </div>
                )}

                {(form.scopeType === "PRODUCT" || form.scopeType === "CATEGORY") && (
                  <div className="grid gap-3 md:grid-cols-2">
                    {form.scopeType === "PRODUCT" && (
                      <div>
                        <label className="form-label">Target Products</label>
                        <MultiSearchSelect
                          apiClient={session.apiClient}
                          endpoint="/admin/products?page=0&size=10"
                          searchParam="q"
                          labelField="name"
                          valueField="id"
                          placeholder="Search products by name..."
                          values={form.targetProductIds ? form.targetProductIds.split(",").map(s => s.trim()).filter(Boolean) : []}
                          onChange={(vals) => setField("targetProductIds", vals.join(","))}
                          disabled={submitting}
                        />
                      </div>
                    )}
                    {form.scopeType === "CATEGORY" && (
                      <div>
                        <label className="form-label">Target Categories</label>
                        <MultiSearchSelect
                          apiClient={session.apiClient}
                          endpoint="/admin/categories"
                          searchParam="q"
                          labelField="name"
                          valueField="id"
                          placeholder="Search categories by name..."
                          values={form.targetCategoryIds ? form.targetCategoryIds.split(",").map(s => s.trim()).filter(Boolean) : []}
                          onChange={(vals) => setField("targetCategoryIds", vals.join(","))}
                          disabled={submitting}
                        />
                      </div>
                    )}
                  </div>
                )}

                <div className="grid gap-3 md:grid-cols-4">
                  <div>
                    <label className="form-label">Budget Amount</label>
                    <input value={form.budgetAmount} onChange={(e) => setField("budgetAmount", e.target.value)} placeholder="10000.00" className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Priority (0-10000)</label>
                    <input value={form.priority} onChange={(e) => setField("priority", e.target.value)} placeholder="100" className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Starts At</label>
                    <input type="datetime-local" value={form.startsAt} onChange={(e) => setField("startsAt", e.target.value)} className="form-input" />
                  </div>
                  <div>
                    <label className="form-label">Ends At</label>
                    <input type="datetime-local" value={form.endsAt} onChange={(e) => setField("endsAt", e.target.value)} className="form-input" />
                  </div>
                </div>

                <div className="flex flex-wrap gap-4" style={{ fontSize: "0.85rem" }}>
                  <label className="flex items-center gap-2" style={{ color: "var(--ink-light)" }}>
                    <input type="checkbox" checked={form.stackable} onChange={(e) => setField("stackable", e.target.checked)} /> Stackable
                  </label>
                  <label className="flex items-center gap-2" style={{ color: "var(--ink-light)" }}>
                    <input type="checkbox" checked={form.exclusive} onChange={(e) => setField("exclusive", e.target.checked)} /> Exclusive
                  </label>
                  <label className="flex items-center gap-2" style={{ color: "var(--ink-light)" }}>
                    <input type="checkbox" checked={form.autoApply} onChange={(e) => setField("autoApply", e.target.checked)} /> Auto-apply
                  </label>
                </div>

                <div className="flex flex-wrap gap-2">
                  <button type="submit" disabled={submitting} className="btn-primary" style={{ padding: "10px 14px", borderRadius: 10, fontWeight: 800 }}>
                    {submitting ? "Saving..." : (form.id ? "Update Promotion" : "Create Promotion")}
                  </button>
                  {form.id && (
                    <button type="button" onClick={() => setForm(emptyForm)} style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid var(--line)", background: "var(--surface-2)", color: "var(--ink-light)", fontWeight: 700 }}>
                      Cancel Edit
                    </button>
                  )}
                </div>
              </form>
            </section>

            {/* ───── Filters & List ───── */}
            <section style={panelStyle}>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <h2 style={{ margin: 0, color: "var(--ink)" }}>Promotions</h2>
                <span style={{ color: "var(--muted)", fontSize: "0.8rem" }}>{statusMsg}</span>
              </div>

              {/* Filters */}
              <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
                <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") void load(0); }} placeholder="Search name..." className="form-input" />
                <select value={filterLifecycle} onChange={(e) => setFilterLifecycle(e.target.value)} className="form-select">
                  <option value="">All Lifecycle</option>
                  {(["DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
                </select>
                <select value={filterApproval} onChange={(e) => setFilterApproval(e.target.value)} className="form-select">
                  <option value="">All Approval</option>
                  {(["NOT_REQUIRED", "PENDING", "APPROVED", "REJECTED"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
                </select>
                <select value={filterScope} onChange={(e) => setFilterScope(e.target.value)} className="form-select">
                  <option value="">All Scope</option>
                  {(["ORDER", "VENDOR", "PRODUCT", "CATEGORY"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
                </select>
                <select value={filterBenefit} onChange={(e) => setFilterBenefit(e.target.value)} className="form-select">
                  <option value="">All Benefits</option>
                  {(["PERCENTAGE_OFF", "FIXED_AMOUNT_OFF", "FREE_SHIPPING", "BUY_X_GET_Y", "TIERED_SPEND", "BUNDLE_DISCOUNT"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
                </select>
              </div>

              {loading && <div className="skeleton" style={{ height: 120, borderRadius: 12 }} />}
              {!loading && items.length === 0 && <p style={{ color: "var(--muted)" }}>No promotions found.</p>}

              {/* Promo list */}
              <div className="grid gap-3">
                {items.map((p) => (
                  <div key={p.id} onClick={() => selectPromo(p)} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === "Enter") selectPromo(p); }}
                    className="transition"
                    style={{ padding: "14px 16px", borderRadius: 12, border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)", cursor: "pointer" }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--line-bright)"; }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-1">
                      <div className="flex items-center gap-2">
                        <strong style={{ color: "var(--ink)" }}>{p.name}</strong>
                        <StatusBadge value={p.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
                        <StatusBadge value={p.approvalStatus} colorMap={APPROVAL_COLORS} />
                      </div>
                      <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>{new Date(p.createdAt).toLocaleDateString()}</span>
                    </div>
                    <div className="flex flex-wrap gap-3" style={{ fontSize: "0.78rem", color: "var(--muted)" }}>
                      <span>{p.scopeType}</span>
                      <span>{p.applicationLevel.replace(/_/g, " ")}</span>
                      <span>{p.benefitType.replace(/_/g, " ")}{p.benefitValue != null ? `: ${p.benefitValue}` : ""}</span>
                      <span>Priority: {p.priority}</span>
                      {p.budgetAmount != null && <span>Budget: {money(p.budgetAmount)}</span>}
                      {p.autoApply && <span style={{ color: "var(--brand)" }}>Auto</span>}
                    </div>
                  </div>
                ))}
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="mt-4 flex items-center justify-center gap-2">
                  <button type="button" disabled={page === 0} onClick={() => void load(page - 1)}
                    style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.8rem", opacity: page === 0 ? 0.4 : 1, cursor: page === 0 ? "not-allowed" : "pointer" }}>
                    Prev
                  </button>
                  <span style={{ fontSize: "0.82rem", color: "var(--muted)" }}>Page {page + 1} of {totalPages}</span>
                  <button type="button" disabled={page >= totalPages - 1} onClick={() => void load(page + 1)}
                    style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.8rem", opacity: page >= totalPages - 1 ? 0.4 : 1, cursor: page >= totalPages - 1 ? "not-allowed" : "pointer" }}>
                    Next
                  </button>
                </div>
              )}
            </section>
          </>
        )}

        {/* ───── Promotion Analytics View ───── */}
        {!selected && analyticsView && (
          <section style={panelStyle}>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 style={{ margin: 0, color: "var(--ink)", fontFamily: "'Syne', sans-serif" }}>Promotion Analytics</h2>
            </div>

            {/* Analytics Filters */}
            <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
              <input value={analyticsSearch} onChange={(e) => setAnalyticsSearch(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") void loadAnalyticsList(0); }} placeholder="Search name..." className="form-input" />
              <select value={analyticsFilterLifecycle} onChange={(e) => setAnalyticsFilterLifecycle(e.target.value)} className="form-select">
                <option value="">All Lifecycle</option>
                {(["DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
              </select>
              <select value={analyticsFilterScope} onChange={(e) => setAnalyticsFilterScope(e.target.value)} className="form-select">
                <option value="">All Scope</option>
                {(["ORDER", "VENDOR", "PRODUCT", "CATEGORY"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
              </select>
              <select value={analyticsFilterBenefit} onChange={(e) => setAnalyticsFilterBenefit(e.target.value)} className="form-select">
                <option value="">All Benefits</option>
                {(["PERCENTAGE_OFF", "FIXED_AMOUNT_OFF", "FREE_SHIPPING", "BUY_X_GET_Y", "TIERED_SPEND", "BUNDLE_DISCOUNT"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
              </select>
              <SearchableSelect
                apiClient={session.apiClient}
                endpoint="/admin/vendors"
                labelField="name"
                valueField="id"
                placeholder="Filter by vendor..."
                value={analyticsFilterVendor}
                onChange={(v) => { setAnalyticsFilterVendor(v); }}
              />
            </div>

            {analyticsLoading && <div className="skeleton" style={{ height: 200, borderRadius: 12 }} />}
            {!analyticsLoading && analyticsData.length === 0 && <p style={{ color: "var(--muted)" }}>No analytics data found.</p>}

            {!analyticsLoading && analyticsData.length > 0 && (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.8rem" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid var(--line)" }}>
                      {["Name", "Status", "Budget", "Coupons", "Reservations", "Committed Discount", "Dates"].map((h) => (
                        <th key={h} style={{ padding: "10px 8px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {analyticsData.map((a) => {
                      const budgetPct = a.budgetAmount && a.budgetAmount > 0 ? Math.min(100, ((a.burnedBudgetAmount || 0) / a.budgetAmount) * 100) : 0;
                      return (
                        <tr key={a.promotionId} style={{ borderBottom: "1px solid var(--line)" }}
                          onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.03)"; }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = "transparent"; }}
                        >
                          {/* Name */}
                          <td style={{ padding: "10px 8px", color: "var(--ink)", fontWeight: 600, maxWidth: 200 }}>
                            <div style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{a.name}</div>
                            <div style={{ fontSize: "0.7rem", color: "var(--muted)", marginTop: 2 }}>
                              {a.scopeType} / {a.benefitType.replace(/_/g, " ")}
                            </div>
                          </td>

                          {/* Status */}
                          <td style={{ padding: "10px 8px", whiteSpace: "nowrap" }}>
                            <div className="flex flex-col gap-1">
                              <StatusBadge value={a.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
                              <StatusBadge value={a.approvalStatus} colorMap={APPROVAL_COLORS} />
                            </div>
                          </td>

                          {/* Budget with progress bar */}
                          <td style={{ padding: "10px 8px", minWidth: 160 }}>
                            {a.budgetAmount != null ? (
                              <div>
                                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4, fontSize: "0.75rem" }}>
                                  <span style={{ color: "var(--ink-light)" }}>{money(a.burnedBudgetAmount)} / {money(a.budgetAmount)}</span>
                                </div>
                                <div style={{ width: "100%", height: 6, borderRadius: 3, background: "rgba(255,255,255,0.08)", overflow: "hidden" }}>
                                  <div style={{
                                    width: `${budgetPct}%`, height: "100%", borderRadius: 3,
                                    background: budgetPct > 90 ? "#f87171" : budgetPct > 70 ? "var(--warning-text)" : "var(--success)",
                                    transition: "width 0.3s ease",
                                  }} />
                                </div>
                                <div style={{ fontSize: "0.68rem", color: "var(--muted)", marginTop: 2 }}>
                                  Remaining: {money(a.remainingBudgetAmount)}
                                </div>
                              </div>
                            ) : (
                              <span style={{ color: "var(--muted)" }}>--</span>
                            )}
                          </td>

                          {/* Coupons */}
                          <td style={{ padding: "10px 8px", whiteSpace: "nowrap" }}>
                            <div style={{ color: "var(--ink)", fontWeight: 600 }}>{a.activeCouponCodeCount} / {a.couponCodeCount}</div>
                            <div style={{ fontSize: "0.68rem", color: "var(--muted)" }}>active / total</div>
                          </td>

                          {/* Reservations */}
                          <td style={{ padding: "10px 8px", minWidth: 140 }}>
                            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                              <span style={{ color: "var(--success)", fontWeight: 600 }} title="Committed">{a.committedReservationCount}</span>
                              <span style={{ color: "var(--muted)" }}>/</span>
                              <span style={{ color: "var(--warning-text)", fontWeight: 600 }} title="Released">{a.releasedReservationCount}</span>
                              <span style={{ color: "var(--muted)" }}>/</span>
                              <span style={{ color: "#f87171", fontWeight: 600 }} title="Expired">{a.expiredReservationCount}</span>
                            </div>
                            <div style={{ fontSize: "0.68rem", color: "var(--muted)" }}>committed / released / expired</div>
                            <div style={{ fontSize: "0.68rem", color: "var(--muted)", marginTop: 1 }}>Total: {a.reservationCount} | Active: {a.activeReservedReservationCount}</div>
                          </td>

                          {/* Committed Discount */}
                          <td style={{ padding: "10px 8px", whiteSpace: "nowrap" }}>
                            <div style={{ color: "var(--success)", fontWeight: 700 }}>{money(a.committedDiscountAmount)}</div>
                            <div style={{ fontSize: "0.68rem", color: "var(--muted)" }}>Released: {money(a.releasedDiscountAmount)}</div>
                          </td>

                          {/* Dates */}
                          <td style={{ padding: "10px 8px", fontSize: "0.72rem", whiteSpace: "nowrap" }}>
                            {a.startsAt && <div style={{ color: "var(--ink-light)" }}>From: {new Date(a.startsAt).toLocaleDateString()}</div>}
                            {a.endsAt && <div style={{ color: "var(--ink-light)" }}>To: {new Date(a.endsAt).toLocaleDateString()}</div>}
                            {!a.startsAt && !a.endsAt && <span style={{ color: "var(--muted)" }}>No dates</span>}
                            <div style={{ color: "var(--muted)", marginTop: 2 }}>Created: {new Date(a.createdAt).toLocaleDateString()}</div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Analytics Pagination */}
            {analyticsTotalPages > 1 && (
              <div className="mt-4 flex items-center justify-center gap-2">
                <button type="button" disabled={analyticsPage === 0} onClick={() => void loadAnalyticsList(analyticsPage - 1)}
                  style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.8rem", opacity: analyticsPage === 0 ? 0.4 : 1, cursor: analyticsPage === 0 ? "not-allowed" : "pointer" }}>
                  Prev
                </button>
                <span style={{ fontSize: "0.82rem", color: "var(--muted)" }}>Page {analyticsPage + 1} of {analyticsTotalPages}</span>
                <button type="button" disabled={analyticsPage >= analyticsTotalPages - 1} onClick={() => void loadAnalyticsList(analyticsPage + 1)}
                  style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontWeight: 700, fontSize: "0.8rem", opacity: analyticsPage >= analyticsTotalPages - 1 ? 0.4 : 1, cursor: analyticsPage >= analyticsTotalPages - 1 ? "not-allowed" : "pointer" }}>
                  Next
                </button>
              </div>
            )}
          </section>
        )}
      </main>
      <Footer />
    </div>
  );
}
