"use client";

import { FormEvent } from "react";
import type { AxiosInstance } from "axios";
import SearchableSelect from "../../ui/SearchableSelect";
import MultiSearchSelect from "../../ui/MultiSearchSelect";
import type { FormState, ScopeType, AppLevel, BenefitType, FundingSource, SpendTier } from "./types";

type Props = {
  form: FormState;
  setField: <K extends keyof FormState>(key: K, value: FormState[K]) => void;
  apiClient: AxiosInstance | null;
  onSubmit: (e: FormEvent) => void;
  onCancel: () => void;
  submitting: boolean;
  editingPromotion: boolean;
  spendTiers: SpendTier[];
  onAddTier: () => void;
  onRemoveTier: (i: number) => void;
  onUpdateTier: (i: number, key: keyof SpendTier, value: number) => void;
};

export default function PromotionForm({
  form,
  setField,
  apiClient,
  onSubmit,
  onCancel,
  submitting,
  editingPromotion,
  spendTiers,
  onAddTier,
  onRemoveTier,
  onUpdateTier,
}: Props) {
  return (
    <>
      <div className="mb-4">
        <h1 className="m-0 font-[Syne,sans-serif] font-extrabold text-ink">
          {editingPromotion ? "Edit Promotion" : "Admin Promotions"}
        </h1>
        <p className="mb-0 mt-1 text-[0.85rem] text-muted">
          Create, manage, and monitor promotions with coupon codes and analytics.
        </p>
      </div>

      <section className="mb-5 rounded-lg border border-line bg-[rgba(17,17,40,0.7)] p-4">
        <h3 className="m-0 mb-3 text-ink">{editingPromotion ? "Update Promotion" : "Create Promotion"}</h3>
        <form onSubmit={(e) => { void onSubmit(e); }} className="grid gap-3">
          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <label className="form-label">Name *</label>
              <input value={form.name} onChange={(e) => setField("name", e.target.value)} placeholder="Summer Sale 20% Off" className="form-input" />
            </div>
            <div>
              <label className="form-label">Vendor (optional)</label>
              <SearchableSelect
                apiClient={apiClient}
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
                <label className="form-label m-0">Spend Tiers</label>
                <button type="button" onClick={onAddTier} className="rounded-lg border border-line-bright bg-brand-soft px-2.5 py-1 text-xs font-bold text-brand">+ Add Tier</button>
              </div>
              {spendTiers.map((tier, i) => (
                <div key={i} className="mb-2 flex items-center gap-2">
                  <input type="number" value={tier.thresholdAmount} onChange={(e) => onUpdateTier(i, "thresholdAmount", Number(e.target.value))} placeholder="Spend $" className="form-input flex-1" />
                  <span className="text-muted">&rarr;</span>
                  <input type="number" value={tier.discountAmount} onChange={(e) => onUpdateTier(i, "discountAmount", Number(e.target.value))} placeholder="Save $" className="form-input flex-1" />
                  <button type="button" onClick={() => onRemoveTier(i)} className="rounded-lg border border-red-500/20 bg-red-500/[0.08] px-2.5 py-1.5 text-xs font-bold text-red-300">X</button>
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
                    apiClient={apiClient}
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
                    apiClient={apiClient}
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

          <div className="flex flex-wrap gap-4 text-[0.85rem]">
            <label className="flex items-center gap-2 text-ink-light">
              <input type="checkbox" checked={form.stackable} onChange={(e) => setField("stackable", e.target.checked)} /> Stackable
            </label>
            <label className="flex items-center gap-2 text-ink-light">
              <input type="checkbox" checked={form.exclusive} onChange={(e) => setField("exclusive", e.target.checked)} /> Exclusive
            </label>
            <label className="flex items-center gap-2 text-ink-light">
              <input type="checkbox" checked={form.autoApply} onChange={(e) => setField("autoApply", e.target.checked)} /> Auto-apply
            </label>
          </div>

          <div className="flex flex-wrap gap-2">
            <button type="submit" disabled={submitting} className="btn-primary rounded-md px-3.5 py-2.5 font-extrabold">
              {submitting ? "Saving..." : (editingPromotion ? "Update Promotion" : "Create Promotion")}
            </button>
            {editingPromotion && (
              <button type="button" onClick={onCancel} className="rounded-md border border-line bg-surface-2 px-3.5 py-2.5 font-bold text-ink-light">
                Cancel Edit
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
