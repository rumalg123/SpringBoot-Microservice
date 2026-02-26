"use client";

import { FormEvent } from "react";
import toast from "react-hot-toast";
import StatusBadge, { LIFECYCLE_COLORS, APPROVAL_COLORS, ACTIVE_INACTIVE_COLORS } from "../../ui/StatusBadge";
import type { Promotion, CouponCode, Analytics, CouponFormState } from "./types";
import { money } from "./types";

type WorkflowButton = {
  label: string;
  action: string;
  needsNote?: boolean;
  color: string;
  bg: string;
  border: string;
};

type Props = {
  promotion: Promotion;
  tab: "details" | "coupons" | "analytics";
  onTabChange: (tab: "details" | "coupons" | "analytics") => void;
  onBack: () => void;
  onEdit: (p: Promotion) => void;
  /* workflow */
  workflowBusy: boolean;
  approvalNote: string;
  onApprovalNoteChange: (note: string) => void;
  onWorkflow: (action: string, body?: object) => void;
  /* coupons */
  coupons: CouponCode[];
  couponLoading: boolean;
  couponForm: CouponFormState;
  onCouponFormChange: (form: CouponFormState) => void;
  couponSubmitting: boolean;
  onCreateCoupon: (e: FormEvent) => void;
  /* analytics */
  analytics: Analytics | null;
};

function getWorkflowButtons(p: Promotion): WorkflowButton[] {
  const btns: WorkflowButton[] = [];
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
}

export default function PromotionDetailPanel({
  promotion,
  tab,
  onTabChange,
  onBack,
  onEdit,
  workflowBusy,
  approvalNote,
  onApprovalNoteChange,
  onWorkflow,
  coupons,
  couponLoading,
  couponForm,
  onCouponFormChange,
  couponSubmitting,
  onCreateCoupon,
  analytics,
}: Props) {
  const buttons = getWorkflowButtons(promotion);

  return (
    <section className="mb-5 rounded-lg border border-line bg-[rgba(17,17,40,0.7)] p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <button type="button" onClick={onBack} className="rounded-lg border border-line-bright bg-brand-soft px-3 py-1.5 text-sm font-bold text-brand">
            &larr; Back
          </button>
          <h2 className="m-0 font-[Syne,sans-serif] text-ink">{promotion.name}</h2>
        </div>
        <div className="flex gap-2">
          <StatusBadge value={promotion.lifecycleStatus} colorMap={LIFECYCLE_COLORS} />
          <StatusBadge value={promotion.approvalStatus} colorMap={APPROVAL_COLORS} />
        </div>
      </div>

      {/* Tabs */}
      <div className="mb-4 flex gap-1 border-b border-line">
        {(["details", "coupons", "analytics"] as const).map((t) => (
          <button key={t} type="button" onClick={() => onTabChange(t)}
            className={`cursor-pointer rounded-t-lg border border-b-0 px-4 py-2 text-sm font-bold ${
              tab === t
                ? "border-line-bright bg-brand-soft text-brand"
                : "border-transparent bg-transparent text-muted"
            }`}
          >
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {/* Details tab */}
      {tab === "details" && (
        <div className="grid gap-4">
          <p className="text-[0.85rem] text-ink-light">{promotion.description}</p>

          <div className="grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-3">
            <div><span className="text-muted">Scope:</span> <span className="text-ink-light">{promotion.scopeType}</span></div>
            <div><span className="text-muted">Application:</span> <span className="text-ink-light">{promotion.applicationLevel.replace(/_/g, " ")}</span></div>
            <div><span className="text-muted">Benefit:</span> <span className="text-ink-light">{promotion.benefitType.replace(/_/g, " ")}</span></div>
            <div><span className="text-muted">Value:</span> <span className="text-ink-light">{promotion.benefitValue ?? "\u2014"}</span></div>
            <div><span className="text-muted">Funding:</span> <span className="text-ink-light">{promotion.fundingSource}</span></div>
            <div><span className="text-muted">Priority:</span> <span className="text-ink-light">{promotion.priority}</span></div>
            <div><span className="text-muted">Min Order:</span> <span className="text-ink-light">{money(promotion.minimumOrderAmount)}</span></div>
            <div><span className="text-muted">Max Discount:</span> <span className="text-ink-light">{money(promotion.maximumDiscountAmount)}</span></div>
            <div><span className="text-muted">Budget:</span> <span className="text-ink-light">{money(promotion.budgetAmount)}</span></div>
            <div><span className="text-muted">Burned:</span> <span className="text-ink-light">{money(promotion.burnedBudgetAmount)}</span></div>
            <div><span className="text-muted">Remaining:</span> <span className="text-ink-light">{money(promotion.remainingBudgetAmount)}</span></div>
            <div><span className="text-muted">Stackable:</span> <span className="text-ink-light">{promotion.stackable ? "Yes" : "No"}</span></div>
            <div><span className="text-muted">Exclusive:</span> <span className="text-ink-light">{promotion.exclusive ? "Yes" : "No"}</span></div>
            <div><span className="text-muted">Auto-apply:</span> <span className="text-ink-light">{promotion.autoApply ? "Yes" : "No"}</span></div>
            {promotion.vendorId && <div><span className="text-muted">Vendor:</span> <span className="text-xs text-ink-light">{promotion.vendorId}</span></div>}
            {promotion.startsAt && <div><span className="text-muted">Starts:</span> <span className="text-ink-light">{new Date(promotion.startsAt).toLocaleString()}</span></div>}
            {promotion.endsAt && <div><span className="text-muted">Ends:</span> <span className="text-ink-light">{new Date(promotion.endsAt).toLocaleString()}</span></div>}
          </div>

          {promotion.buyQuantity != null && promotion.getQuantity != null && (
            <div className="text-sm text-ink-light">Buy {promotion.buyQuantity} Get {promotion.getQuantity}</div>
          )}

          {promotion.spendTiers && promotion.spendTiers.length > 0 && (
            <div>
              <h4 className="m-0 mb-1.5 text-ink">Spend Tiers</h4>
              <div className="grid gap-1 text-sm">
                {promotion.spendTiers.map((t, i) => (
                  <div key={i} className="text-ink-light">Spend {money(t.thresholdAmount)} &rarr; Save {money(t.discountAmount)}</div>
                ))}
              </div>
            </div>
          )}

          {promotion.approvalNote && (
            <div className="alert alert-warning">{promotion.approvalNote}</div>
          )}

          {/* Workflow buttons */}
          <div className="flex flex-wrap gap-2">
            {buttons.map((btn) => (
              <button key={btn.action} type="button" disabled={workflowBusy}
                onClick={() => {
                  if (btn.needsNote) {
                    if (!approvalNote.trim()) { toast.error("Please enter a note for rejection"); return; }
                    void onWorkflow(btn.action, { note: approvalNote.trim() });
                  } else {
                    void onWorkflow(btn.action);
                  }
                }}
                style={{ border: `1px solid ${btn.border}`, background: btn.bg, color: btn.color }}
                className={`rounded-md px-3.5 py-2 text-sm font-bold ${workflowBusy ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
              >
                {btn.label}
              </button>
            ))}
            <button type="button" onClick={() => onEdit(promotion)} className="rounded-md border border-line-bright bg-brand-soft px-3.5 py-2 text-sm font-bold text-brand">
              Edit
            </button>
          </div>

          {/* Approval note input (for reject) */}
          {promotion.approvalStatus === "PENDING" && (
            <div>
              <label className="form-label">Rejection Note</label>
              <input value={approvalNote} onChange={(e) => onApprovalNoteChange(e.target.value)} placeholder="Reason for rejection..." className="form-input" />
            </div>
          )}
        </div>
      )}

      {/* Coupons tab */}
      {tab === "coupons" && (
        <div className="grid gap-4">
          <form onSubmit={(e) => { void onCreateCoupon(e); }} className="grid items-end gap-3 sm:grid-cols-3 lg:grid-cols-4">
            <div>
              <label className="form-label">Code</label>
              <input value={couponForm.code} onChange={(e) => onCouponFormChange({ ...couponForm, code: e.target.value })} placeholder="SUMMER20" className="form-input" />
            </div>
            <div>
              <label className="form-label">Max Uses</label>
              <input value={couponForm.maxUses} onChange={(e) => onCouponFormChange({ ...couponForm, maxUses: e.target.value })} placeholder="1000" className="form-input" />
            </div>
            <div>
              <label className="form-label">Per Customer</label>
              <input value={couponForm.maxUsesPerCustomer} onChange={(e) => onCouponFormChange({ ...couponForm, maxUsesPerCustomer: e.target.value })} placeholder="1" className="form-input" />
            </div>
            <div>
              <button type="submit" disabled={couponSubmitting} className="btn-primary w-full rounded-md px-3.5 py-2.5 font-extrabold">
                {couponSubmitting ? "Creating..." : "Create Coupon"}
              </button>
            </div>
          </form>

          {couponLoading && <div className="skeleton h-[60px] rounded-xl" />}
          {!couponLoading && coupons.length === 0 && <p className="text-[0.85rem] text-muted">No coupon codes yet.</p>}
          {coupons.length > 0 && (
            <div className="grid gap-2">
              {coupons.map((c) => (
                <div key={c.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-line bg-white/[0.02] px-3.5 py-2.5">
                  <div className="flex items-center gap-3">
                    <code className="text-[0.9rem] font-bold text-brand">{c.code}</code>
                    <StatusBadge value={c.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                  </div>
                  <div className="flex items-center gap-3 text-[0.78rem] text-muted">
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
          {!analytics && <div className="skeleton h-20 rounded-xl" />}
          {analytics && (
            <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-md border border-line bg-white/[0.02] p-3">
                <div className="mb-1 text-muted">Budget</div>
                <div className="text-lg font-bold text-ink">{money(analytics.budgetAmount)}</div>
                <div className="text-xs text-muted">Burned: {money(analytics.burnedBudgetAmount)} | Reserved: {money(analytics.activeReservedBudgetAmount)}</div>
              </div>
              <div className="rounded-md border border-line bg-white/[0.02] p-3">
                <div className="mb-1 text-muted">Coupons</div>
                <div className="text-lg font-bold text-ink">{analytics.activeCouponCodeCount} / {analytics.couponCodeCount}</div>
                <div className="text-xs text-muted">active / total</div>
              </div>
              <div className="rounded-md border border-line bg-white/[0.02] p-3">
                <div className="mb-1 text-muted">Reservations</div>
                <div className="text-lg font-bold text-ink">{analytics.reservationCount}</div>
                <div className="text-xs text-muted">Committed: {analytics.committedReservationCount} | Active: {analytics.activeReservedReservationCount}</div>
              </div>
              <div className="rounded-md border border-line bg-white/[0.02] p-3">
                <div className="mb-1 text-muted">Discounts Given</div>
                <div className="text-lg font-bold text-success">{money(analytics.committedDiscountAmount)}</div>
                <div className="text-xs text-muted">Released: {money(analytics.releasedDiscountAmount)}</div>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
