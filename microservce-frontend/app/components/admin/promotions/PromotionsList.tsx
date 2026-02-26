"use client";

import type { AxiosInstance } from "axios";
import StatusBadge, { LIFECYCLE_COLORS, APPROVAL_COLORS } from "../../ui/StatusBadge";
import SearchableSelect from "../../ui/SearchableSelect";
import type { Promotion, PromotionAnalytics } from "./types";
import { money } from "./types";

type Props = {
  /* promotions list */
  items: Promotion[];
  loading: boolean;
  statusMsg: string;
  page: number;
  totalPages: number;
  onPageChange: (p: number) => void;
  onSelectPromo: (p: Promotion) => void;
  /* filters */
  searchQuery: string;
  onSearchChange: (q: string) => void;
  onSearchSubmit: () => void;
  filterLifecycle: string;
  onFilterLifecycleChange: (v: string) => void;
  filterApproval: string;
  onFilterApprovalChange: (v: string) => void;
  filterScope: string;
  onFilterScopeChange: (v: string) => void;
  filterBenefit: string;
  onFilterBenefitChange: (v: string) => void;
  /* analytics view */
  analyticsView: boolean;
  onToggleView: (view: "Promotions" | "Analytics") => void;
  analyticsData: PromotionAnalytics[];
  analyticsLoading: boolean;
  analyticsPage: number;
  analyticsTotalPages: number;
  onAnalyticsPageChange: (p: number) => void;
  analyticsSearch: string;
  onAnalyticsSearchChange: (q: string) => void;
  onAnalyticsSearchSubmit: () => void;
  analyticsFilterLifecycle: string;
  onAnalyticsFilterLifecycleChange: (v: string) => void;
  analyticsFilterScope: string;
  onAnalyticsFilterScopeChange: (v: string) => void;
  analyticsFilterBenefit: string;
  onAnalyticsFilterBenefitChange: (v: string) => void;
  analyticsFilterVendor: string;
  onAnalyticsFilterVendorChange: (v: string) => void;
  apiClient: AxiosInstance | null;
};

export default function PromotionsList({
  items,
  loading,
  statusMsg,
  page,
  totalPages,
  onPageChange,
  onSelectPromo,
  searchQuery,
  onSearchChange,
  onSearchSubmit,
  filterLifecycle,
  onFilterLifecycleChange,
  filterApproval,
  onFilterApprovalChange,
  filterScope,
  onFilterScopeChange,
  filterBenefit,
  onFilterBenefitChange,
  analyticsView,
  onToggleView,
  analyticsData,
  analyticsLoading,
  analyticsPage,
  analyticsTotalPages,
  onAnalyticsPageChange,
  analyticsSearch,
  onAnalyticsSearchChange,
  onAnalyticsSearchSubmit,
  analyticsFilterLifecycle,
  onAnalyticsFilterLifecycleChange,
  analyticsFilterScope,
  onAnalyticsFilterScopeChange,
  analyticsFilterBenefit,
  onAnalyticsFilterBenefitChange,
  analyticsFilterVendor,
  onAnalyticsFilterVendorChange,
  apiClient,
}: Props) {
  return (
    <>
      {/* ───── Page-level View Toggle ───── */}
      <div className="mb-4 flex gap-1 border-b border-line">
        {(["Promotions", "Analytics"] as const).map((v) => {
          const isActive = v === "Analytics" ? analyticsView : !analyticsView;
          return (
            <button key={v} type="button" onClick={() => onToggleView(v)}
              className={`cursor-pointer rounded-t-lg border border-b-0 px-5 py-2.5 text-[0.85rem] font-bold ${
                isActive
                  ? "border-line-bright bg-brand-soft text-brand"
                  : "border-transparent bg-transparent text-muted"
              }`}
            >
              {v}
            </button>
          );
        })}
      </div>

      {/* ───── Promotions List View ───── */}
      {!analyticsView && (
        <section className="rounded-lg border border-line bg-[rgba(17,17,40,0.7)] p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="m-0 text-ink">Promotions</h2>
            <span className="text-sm text-muted">{statusMsg}</span>
          </div>

          {/* Filters */}
          <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
            <input value={searchQuery} onChange={(e) => onSearchChange(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") onSearchSubmit(); }} placeholder="Search name..." className="form-input" />
            <select value={filterLifecycle} onChange={(e) => onFilterLifecycleChange(e.target.value)} className="form-select">
              <option value="">All Lifecycle</option>
              {(["DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
            </select>
            <select value={filterApproval} onChange={(e) => onFilterApprovalChange(e.target.value)} className="form-select">
              <option value="">All Approval</option>
              {(["NOT_REQUIRED", "PENDING", "APPROVED", "REJECTED"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
            </select>
            <select value={filterScope} onChange={(e) => onFilterScopeChange(e.target.value)} className="form-select">
              <option value="">All Scope</option>
              {(["ORDER", "VENDOR", "PRODUCT", "CATEGORY"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
            </select>
            <select value={filterBenefit} onChange={(e) => onFilterBenefitChange(e.target.value)} className="form-select">
              <option value="">All Benefits</option>
              {(["PERCENTAGE_OFF", "FIXED_AMOUNT_OFF", "FREE_SHIPPING", "BUY_X_GET_Y", "TIERED_SPEND", "BUNDLE_DISCOUNT"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
            </select>
          </div>

          {loading && <div className="skeleton h-[120px] rounded-xl" />}
          {!loading && items.length === 0 && <p className="text-muted">No promotions found.</p>}

          {/* Promo list */}
          <div className="grid gap-3">
            {items.map((p) => (
              <div key={p.id} onClick={() => onSelectPromo(p)} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === "Enter") onSelectPromo(p); }}
                className="cursor-pointer rounded-xl border border-line bg-white/[0.02] px-4 py-3.5 transition hover:border-line-bright"
              >
                <div className="mb-1 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <strong className="text-ink">{p.name}</strong>
                    <StatusBadge value={p.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
                    <StatusBadge value={p.approvalStatus} colorMap={APPROVAL_COLORS} />
                  </div>
                  <span className="text-xs text-muted">{new Date(p.createdAt).toLocaleDateString()}</span>
                </div>
                <div className="flex flex-wrap gap-3 text-[0.78rem] text-muted">
                  <span>{p.scopeType}</span>
                  <span>{p.applicationLevel.replace(/_/g, " ")}</span>
                  <span>{p.benefitType.replace(/_/g, " ")}{p.benefitValue != null ? `: ${p.benefitValue}` : ""}</span>
                  <span>Priority: {p.priority}</span>
                  {p.budgetAmount != null && <span>Budget: {money(p.budgetAmount)}</span>}
                  {p.autoApply && <span className="text-brand">Auto</span>}
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-2">
              <button type="button" disabled={page === 0} onClick={() => onPageChange(page - 1)}
                className={`rounded-lg border border-line-bright bg-brand-soft px-3 py-1.5 text-sm font-bold text-brand ${page === 0 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}>
                Prev
              </button>
              <span className="text-sm text-muted">Page {page + 1} of {totalPages}</span>
              <button type="button" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}
                className={`rounded-lg border border-line-bright bg-brand-soft px-3 py-1.5 text-sm font-bold text-brand ${page >= totalPages - 1 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}>
                Next
              </button>
            </div>
          )}
        </section>
      )}

      {/* ───── Promotion Analytics View ───── */}
      {analyticsView && (
        <section className="rounded-lg border border-line bg-[rgba(17,17,40,0.7)] p-4">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="m-0 font-[Syne,sans-serif] text-ink">Promotion Analytics</h2>
          </div>

          {/* Analytics Filters */}
          <div className="mb-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
            <input value={analyticsSearch} onChange={(e) => onAnalyticsSearchChange(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") onAnalyticsSearchSubmit(); }} placeholder="Search name..." className="form-input" />
            <select value={analyticsFilterLifecycle} onChange={(e) => onAnalyticsFilterLifecycleChange(e.target.value)} className="form-select">
              <option value="">All Lifecycle</option>
              {(["DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
            </select>
            <select value={analyticsFilterScope} onChange={(e) => onAnalyticsFilterScopeChange(e.target.value)} className="form-select">
              <option value="">All Scope</option>
              {(["ORDER", "VENDOR", "PRODUCT", "CATEGORY"] as const).map((v) => <option key={v} value={v}>{v}</option>)}
            </select>
            <select value={analyticsFilterBenefit} onChange={(e) => onAnalyticsFilterBenefitChange(e.target.value)} className="form-select">
              <option value="">All Benefits</option>
              {(["PERCENTAGE_OFF", "FIXED_AMOUNT_OFF", "FREE_SHIPPING", "BUY_X_GET_Y", "TIERED_SPEND", "BUNDLE_DISCOUNT"] as const).map((v) => <option key={v} value={v}>{v.replace(/_/g, " ")}</option>)}
            </select>
            <SearchableSelect
              apiClient={apiClient}
              endpoint="/admin/vendors"
              labelField="name"
              valueField="id"
              placeholder="Filter by vendor..."
              value={analyticsFilterVendor}
              onChange={(v) => { onAnalyticsFilterVendorChange(v); }}
            />
          </div>

          {analyticsLoading && <div className="skeleton h-[200px] rounded-xl" />}
          {!analyticsLoading && analyticsData.length === 0 && <p className="text-muted">No analytics data found.</p>}

          {!analyticsLoading && analyticsData.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-line">
                    {["Name", "Status", "Budget", "Coupons", "Reservations", "Committed Discount", "Dates"].map((h) => (
                      <th key={h} className="whitespace-nowrap px-2 py-2.5 text-left text-[0.72rem] font-bold uppercase tracking-[0.05em] text-muted">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {analyticsData.map((a) => {
                    const budgetPct = a.budgetAmount && a.budgetAmount > 0 ? Math.min(100, ((a.burnedBudgetAmount || 0) / a.budgetAmount) * 100) : 0;
                    return (
                      <tr key={a.promotionId} className="border-b border-line transition hover:bg-white/[0.03]">
                        {/* Name */}
                        <td className="max-w-[200px] px-2 py-2.5 font-semibold text-ink">
                          <div className="truncate">{a.name}</div>
                          <div className="mt-0.5 text-[0.7rem] text-muted">
                            {a.scopeType} / {a.benefitType.replace(/_/g, " ")}
                          </div>
                        </td>

                        {/* Status */}
                        <td className="whitespace-nowrap px-2 py-2.5">
                          <div className="flex flex-col gap-1">
                            <StatusBadge value={a.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
                            <StatusBadge value={a.approvalStatus} colorMap={APPROVAL_COLORS} />
                          </div>
                        </td>

                        {/* Budget with progress bar */}
                        <td className="min-w-[160px] px-2 py-2.5">
                          {a.budgetAmount != null ? (
                            <div>
                              <div className="mb-1 flex justify-between text-xs">
                                <span className="text-ink-light">{money(a.burnedBudgetAmount)} / {money(a.budgetAmount)}</span>
                              </div>
                              <div className="h-1.5 w-full overflow-hidden rounded-sm bg-white/[0.08]">
                                <div
                                  className={`h-full rounded-sm transition-all duration-300 ${budgetPct > 90 ? "bg-red-400" : budgetPct > 70 ? "bg-warning-text" : "bg-success"}`}
                                  style={{ width: `${budgetPct}%` }}
                                />
                              </div>
                              <div className="mt-0.5 text-[0.68rem] text-muted">
                                Remaining: {money(a.remainingBudgetAmount)}
                              </div>
                            </div>
                          ) : (
                            <span className="text-muted">--</span>
                          )}
                        </td>

                        {/* Coupons */}
                        <td className="whitespace-nowrap px-2 py-2.5">
                          <div className="font-semibold text-ink">{a.activeCouponCodeCount} / {a.couponCodeCount}</div>
                          <div className="text-[0.68rem] text-muted">active / total</div>
                        </td>

                        {/* Reservations */}
                        <td className="min-w-[140px] px-2 py-2.5">
                          <div className="flex flex-wrap gap-2">
                            <span className="font-semibold text-success" title="Committed">{a.committedReservationCount}</span>
                            <span className="text-muted">/</span>
                            <span className="font-semibold text-warning-text" title="Released">{a.releasedReservationCount}</span>
                            <span className="text-muted">/</span>
                            <span className="font-semibold text-red-400" title="Expired">{a.expiredReservationCount}</span>
                          </div>
                          <div className="text-[0.68rem] text-muted">committed / released / expired</div>
                          <div className="mt-px text-[0.68rem] text-muted">Total: {a.reservationCount} | Active: {a.activeReservedReservationCount}</div>
                        </td>

                        {/* Committed Discount */}
                        <td className="whitespace-nowrap px-2 py-2.5">
                          <div className="font-bold text-success">{money(a.committedDiscountAmount)}</div>
                          <div className="text-[0.68rem] text-muted">Released: {money(a.releasedDiscountAmount)}</div>
                        </td>

                        {/* Dates */}
                        <td className="whitespace-nowrap px-2 py-2.5 text-[0.72rem]">
                          {a.startsAt && <div className="text-ink-light">From: {new Date(a.startsAt).toLocaleDateString()}</div>}
                          {a.endsAt && <div className="text-ink-light">To: {new Date(a.endsAt).toLocaleDateString()}</div>}
                          {!a.startsAt && !a.endsAt && <span className="text-muted">No dates</span>}
                          <div className="mt-0.5 text-muted">Created: {new Date(a.createdAt).toLocaleDateString()}</div>
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
              <button type="button" disabled={analyticsPage === 0} onClick={() => onAnalyticsPageChange(analyticsPage - 1)}
                className={`rounded-lg border border-line-bright bg-brand-soft px-3 py-1.5 text-sm font-bold text-brand ${analyticsPage === 0 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}>
                Prev
              </button>
              <span className="text-sm text-muted">Page {analyticsPage + 1} of {analyticsTotalPages}</span>
              <button type="button" disabled={analyticsPage >= analyticsTotalPages - 1} onClick={() => onAnalyticsPageChange(analyticsPage + 1)}
                className={`rounded-lg border border-line-bright bg-brand-soft px-3 py-1.5 text-sm font-bold text-brand ${analyticsPage >= analyticsTotalPages - 1 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}>
                Next
              </button>
            </div>
          )}
        </section>
      )}
    </>
  );
}
