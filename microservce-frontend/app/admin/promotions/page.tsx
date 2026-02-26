"use client";

import { FormEvent, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import PromotionForm from "../../components/admin/promotions/PromotionForm";
import PromotionDetailPanel from "../../components/admin/promotions/PromotionDetailPanel";
import PromotionsList from "../../components/admin/promotions/PromotionsList";
import { useAuthSession } from "../../../lib/authSession";
import type {
  Promotion,
  CouponCode,
  Analytics,
  PromotionAnalytics,
  PageResponse,
  FormState,
  SpendTier,
  CouponFormState,
} from "../../components/admin/promotions/types";
import { emptyForm } from "../../components/admin/promotions/types";

/* ───── helpers ───── */

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

/* ───── component ───── */

export default function AdminPromotionsPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();

  /* list state */
  const [page, setPage] = useState(0);

  /* filters */
  const [filterLifecycle, setFilterLifecycle] = useState("");
  const [filterApproval, setFilterApproval] = useState("");
  const [filterScope, setFilterScope] = useState("");
  const [filterBenefit, setFilterBenefit] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [committedSearch, setCommittedSearch] = useState("");

  /* form */
  const [form, setForm] = useState<FormState>(emptyForm);
  const [submitting, setSubmitting] = useState(false);

  /* detail & workflow */
  const [selected, setSelected] = useState<Promotion | null>(null);
  const [workflowBusy, setWorkflowBusy] = useState(false);
  const [approvalNote, setApprovalNote] = useState("");

  /* coupons */
  const [couponForm, setCouponForm] = useState<CouponFormState>({ code: "", maxUses: "", maxUsesPerCustomer: "", reservationTtlSeconds: "300", active: true, startsAt: "", endsAt: "" });
  const [couponSubmitting, setCouponSubmitting] = useState(false);

  /* tabs for selected promo */
  const [tab, setTab] = useState<"details" | "coupons" | "analytics">("details");

  /* page-level analytics view */
  const [analyticsView, setAnalyticsView] = useState(false);
  const [analyticsPage, setAnalyticsPage] = useState(0);
  const [analyticsSearch, setAnalyticsSearch] = useState("");
  const [committedAnalyticsSearch, setCommittedAnalyticsSearch] = useState("");
  const [analyticsFilterLifecycle, setAnalyticsFilterLifecycle] = useState("");
  const [analyticsFilterScope, setAnalyticsFilterScope] = useState("");
  const [analyticsFilterBenefit, setAnalyticsFilterBenefit] = useState("");
  const [analyticsFilterVendor, setAnalyticsFilterVendor] = useState("");

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((s) => ({ ...s, [key]: value }));

  /* ───── promotions list query ───── */
  const { data: listData, isLoading: loading } = useQuery({
    queryKey: ["admin-promotions", page, committedSearch, filterLifecycle, filterApproval, filterScope, filterBenefit],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      if (committedSearch.trim()) params.set("q", committedSearch.trim());
      if (filterLifecycle) params.set("lifecycleStatus", filterLifecycle);
      if (filterApproval) params.set("approvalStatus", filterApproval);
      if (filterScope) params.set("scopeType", filterScope);
      if (filterBenefit) params.set("benefitType", filterBenefit);
      const res = await session.apiClient!.get(`/admin/promotions?${params.toString()}`);
      return res.data as PageResponse<Promotion>;
    },
    enabled: session.status === "ready" && session.isAuthenticated && !!session.apiClient,
  });

  const items = listData?.content || [];
  const totalPages = listData?.totalPages ?? listData?.page?.totalPages ?? 0;
  const currentPage = listData?.number ?? listData?.page?.number ?? 0;
  const statusMsg = loading
    ? "Loading promotions..."
    : `${listData?.totalElements ?? listData?.page?.totalElements ?? 0} promotion(s) found.`;

  /* ───── analytics list query ───── */
  const { data: analyticsListData, isLoading: analyticsLoading } = useQuery({
    queryKey: ["admin-promotions-analytics", analyticsPage, committedAnalyticsSearch, analyticsFilterLifecycle, analyticsFilterScope, analyticsFilterBenefit, analyticsFilterVendor],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(analyticsPage), size: "20" });
      if (committedAnalyticsSearch.trim()) params.set("q", committedAnalyticsSearch.trim());
      if (analyticsFilterLifecycle) params.set("lifecycleStatus", analyticsFilterLifecycle);
      if (analyticsFilterScope) params.set("scopeType", analyticsFilterScope);
      if (analyticsFilterBenefit) params.set("benefitType", analyticsFilterBenefit);
      if (analyticsFilterVendor.trim()) params.set("vendorId", analyticsFilterVendor.trim());
      const res = await session.apiClient!.get(`/admin/promotions/analytics?${params.toString()}`);
      return res.data as PageResponse<PromotionAnalytics>;
    },
    enabled: analyticsView && session.status === "ready" && session.isAuthenticated && !!session.apiClient,
  });

  const analyticsData = analyticsListData?.content || [];
  const analyticsTotalPages = analyticsListData?.totalPages ?? analyticsListData?.page?.totalPages ?? 0;

  /* ───── coupons query ───── */
  const { data: coupons = [], isLoading: couponLoading } = useQuery({
    queryKey: ["admin-promotions", selected?.id, "coupons"],
    queryFn: async () => {
      const res = await session.apiClient!.get(`/admin/promotions/${selected!.id}/coupons`);
      const raw = res.data as { content?: CouponCode[] };
      return raw.content || [];
    },
    enabled: !!session.apiClient && !!selected && tab === "coupons",
  });

  /* ───── single promo analytics query ───── */
  const { data: analytics = null } = useQuery<Analytics | null>({
    queryKey: ["admin-promotions", selected?.id, "analytics"],
    queryFn: async () => {
      const res = await session.apiClient!.get(`/admin/promotions/${selected!.id}/analytics`);
      return res.data as Analytics;
    },
    enabled: !!session.apiClient && !!selected && tab === "analytics",
  });

  /* ───── workflow mutation ───── */
  const workflowMutation = useMutation({
    mutationFn: async ({ action, body }: { action: string; body?: object }) => {
      const res = await session.apiClient!.post(`/admin/promotions/${selected!.id}/${action}`, body || {});
      return res.data as Promotion;
    },
    onSuccess: (updated, { action }) => {
      setSelected(updated);
      toast.success(`Promotion ${action}ed`);
      setApprovalNote("");
      void queryClient.invalidateQueries({ queryKey: ["admin-promotions"] });
    },
    onError: (e, { action }) => {
      toast.error(getApiErrorMessage(e, `Failed to ${action}.`));
    },
    onSettled: () => {
      setWorkflowBusy(false);
    },
  });

  const doWorkflow = async (action: string, body?: object) => {
    if (!session.apiClient || !selected || workflowBusy) return;
    setWorkflowBusy(true);
    workflowMutation.mutate({ action, body });
  };

  /* ───── create/edit mutation ───── */
  const submitMutation = useMutation({
    mutationFn: async (payload: object) => {
      if (form.id) {
        await session.apiClient!.put(`/admin/promotions/${form.id}`, payload);
      } else {
        await session.apiClient!.post("/admin/promotions", payload);
      }
    },
    onSuccess: () => {
      toast.success(form.id ? "Promotion updated" : "Promotion created");
      setForm(emptyForm);
      void queryClient.invalidateQueries({ queryKey: ["admin-promotions"] });
    },
    onError: (err) => {
      toast.error(getApiErrorMessage(err, "Save failed"));
    },
    onSettled: () => {
      setSubmitting(false);
    },
  });

  /* ───── coupon create mutation ───── */
  const createCouponMutation = useMutation({
    mutationFn: async (payload: object) => {
      await session.apiClient!.post(`/admin/promotions/${selected!.id}/coupons`, payload);
    },
    onSuccess: () => {
      toast.success("Coupon created");
      setCouponForm({ code: "", maxUses: "", maxUsesPerCustomer: "", reservationTtlSeconds: "300", active: true, startsAt: "", endsAt: "" });
      void queryClient.invalidateQueries({ queryKey: ["admin-promotions", selected?.id, "coupons"] });
    },
    onError: (err) => {
      toast.error(getApiErrorMessage(err, "Failed to create coupon"));
    },
    onSettled: () => {
      setCouponSubmitting(false);
    },
  });

  /* ───── select / detail ───── */
  const selectPromo = async (p: Promotion) => {
    setSelected(p);
    setTab("details");
    window.scrollTo({ top: 0, behavior: "smooth" });
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

      submitMutation.mutate(payload);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Save failed"));
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
      createCouponMutation.mutate(payload);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to create coupon"));
      setCouponSubmitting(false);
    }
  };

  /* ───── spend tier management ───── */
  const addTier = () => setForm((s) => ({ ...s, spendTiers: [...s.spendTiers, { thresholdAmount: 0, discountAmount: 0 }] }));
  const removeTier = (i: number) => setForm((s) => ({ ...s, spendTiers: s.spendTiers.filter((_, idx) => idx !== i) }));
  const updateTier = (i: number, key: keyof SpendTier, value: number) =>
    setForm((s) => ({ ...s, spendTiers: s.spendTiers.map((t, idx) => (idx === i ? { ...t, [key]: value } : t)) }));

  /* ───── page change handlers (reset page to 0 on filter changes) ───── */
  const handleFilterLifecycleChange = (v: string) => { setFilterLifecycle(v); setPage(0); };
  const handleFilterApprovalChange = (v: string) => { setFilterApproval(v); setPage(0); };
  const handleFilterScopeChange = (v: string) => { setFilterScope(v); setPage(0); };
  const handleFilterBenefitChange = (v: string) => { setFilterBenefit(v); setPage(0); };

  const handleAnalyticsFilterLifecycleChange = (v: string) => { setAnalyticsFilterLifecycle(v); setAnalyticsPage(0); };
  const handleAnalyticsFilterScopeChange = (v: string) => { setAnalyticsFilterScope(v); setAnalyticsPage(0); };
  const handleAnalyticsFilterBenefitChange = (v: string) => { setAnalyticsFilterBenefit(v); setAnalyticsPage(0); };
  const handleAnalyticsFilterVendorChange = (v: string) => { setAnalyticsFilterVendor(v); setAnalyticsPage(0); };

  /* ───── render gates ───── */
  if (session.status === "loading" || session.status === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><div className="spinner-lg" /></div>;
  }
  if (!session.isAuthenticated) return null;

  return (
    <AdminPageShell
      title="Promotions"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Promotions" }]}
    >
      {/* ───── Selected Promotion Detail ───── */}
      {selected && (
        <PromotionDetailPanel
          promotion={selected}
          tab={tab}
          onTabChange={setTab}
          onBack={() => setSelected(null)}
          onEdit={editPromo}
          workflowBusy={workflowBusy}
          approvalNote={approvalNote}
          onApprovalNoteChange={setApprovalNote}
          onWorkflow={doWorkflow}
          coupons={coupons}
          couponLoading={couponLoading}
          couponForm={couponForm}
          onCouponFormChange={setCouponForm}
          couponSubmitting={couponSubmitting}
          onCreateCoupon={createCoupon}
          analytics={analytics}
        />
      )}

      {/* ───── Create / Edit Form + List ───── */}
      {!selected && !analyticsView && (
        <PromotionForm
          form={form}
          setField={setField}
          apiClient={session.apiClient}
          onSubmit={submit}
          onCancel={() => setForm(emptyForm)}
          submitting={submitting}
          editingPromotion={!!form.id}
          spendTiers={form.spendTiers}
          onAddTier={addTier}
          onRemoveTier={removeTier}
          onUpdateTier={updateTier}
        />
      )}

      {/* ───── Promotions List / Analytics View ───── */}
      {!selected && (
        <PromotionsList
          items={items}
          loading={loading}
          statusMsg={statusMsg}
          page={currentPage}
          totalPages={totalPages}
          onPageChange={setPage}
          onSelectPromo={selectPromo}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onSearchSubmit={() => { setCommittedSearch(searchQuery); setPage(0); }}
          filterLifecycle={filterLifecycle}
          onFilterLifecycleChange={handleFilterLifecycleChange}
          filterApproval={filterApproval}
          onFilterApprovalChange={handleFilterApprovalChange}
          filterScope={filterScope}
          onFilterScopeChange={handleFilterScopeChange}
          filterBenefit={filterBenefit}
          onFilterBenefitChange={handleFilterBenefitChange}
          analyticsView={analyticsView}
          onToggleView={(v) => { setAnalyticsView(v === "Analytics"); if (v === "Promotions") setSelected(null); }}
          analyticsData={analyticsData}
          analyticsLoading={analyticsLoading}
          analyticsPage={analyticsPage}
          analyticsTotalPages={analyticsTotalPages}
          onAnalyticsPageChange={setAnalyticsPage}
          analyticsSearch={analyticsSearch}
          onAnalyticsSearchChange={setAnalyticsSearch}
          onAnalyticsSearchSubmit={() => { setCommittedAnalyticsSearch(analyticsSearch); setAnalyticsPage(0); }}
          analyticsFilterLifecycle={analyticsFilterLifecycle}
          onAnalyticsFilterLifecycleChange={handleAnalyticsFilterLifecycleChange}
          analyticsFilterScope={analyticsFilterScope}
          onAnalyticsFilterScopeChange={handleAnalyticsFilterScopeChange}
          analyticsFilterBenefit={analyticsFilterBenefit}
          onAnalyticsFilterBenefitChange={handleAnalyticsFilterBenefitChange}
          analyticsFilterVendor={analyticsFilterVendor}
          onAnalyticsFilterVendorChange={handleAnalyticsFilterVendorChange}
          apiClient={session.apiClient}
        />
      )}
    </AdminPageShell>
  );
}
