"use client";

import StatusBadge, { ACTIVE_INACTIVE_COLORS } from "../../ui/StatusBadge";
import PosterFormField from "./PosterFormField";
import {
  type PosterVariant,
  type VariantFormState,
} from "./types";

type Props = {
  variants: PosterVariant[];
  variantsLoading: boolean;
  variantForm: VariantFormState;
  showVariantForm: boolean;
  editingVariantId: string | null;
  savingVariant: boolean;
  onVariantFieldChange: <K extends keyof VariantFormState>(key: K, value: VariantFormState[K]) => void;
  onShowVariantForm: () => void;
  onCancelVariantForm: () => void;
  onSaveVariant: () => void;
  onEditVariant: (v: PosterVariant) => void;
  onDeleteVariant: (v: PosterVariant) => void;
};

export default function PosterVariantsSection({
  variants,
  variantsLoading,
  variantForm,
  showVariantForm,
  editingVariantId,
  savingVariant,
  onVariantFieldChange,
  onShowVariantForm,
  onCancelVariantForm,
  onSaveVariant,
  onEditVariant,
  onDeleteVariant,
}: Props) {
  return (
    <div className="mt-6 border-t border-line pt-5">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="m-0 text-ink text-base">A/B Variants</h3>
        {!showVariantForm && (
          <button type="button" onClick={onShowVariantForm} className="px-3.5 py-[7px] rounded-lg border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold text-[0.8rem]">
            + Add Variant
          </button>
        )}
      </div>

      {/* Variant Form */}
      {showVariantForm && (
        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-[12px] p-3.5 mb-4">
          <h4 className="m-0 mb-3 text-ink-light text-[0.88rem]">
            {editingVariantId ? "Edit Variant" : "New Variant"}
          </h4>
          <div className="grid gap-3 md:grid-cols-2">
            <PosterFormField label="Variant Name">
              <input value={variantForm.variantName} onChange={(e) => onVariantFieldChange("variantName", e.target.value)} placeholder="e.g. Control, Variant A" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
            <PosterFormField label="Weight (1-100)" hint="Higher weight = more traffic share.">
              <input type="number" min={1} max={100} value={variantForm.weight} onChange={(e) => onVariantFieldChange("weight", e.target.value)} className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
          </div>
          <div className="grid gap-3 md:grid-cols-3 mt-3">
            <PosterFormField label="Desktop Image URL (Optional)">
              <input value={variantForm.desktopImage} onChange={(e) => onVariantFieldChange("desktopImage", e.target.value)} placeholder="posters/variant-desktop.jpg" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
            <PosterFormField label="Mobile Image URL (Optional)">
              <input value={variantForm.mobileImage} onChange={(e) => onVariantFieldChange("mobileImage", e.target.value)} placeholder="posters/variant-mobile.jpg" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
            <PosterFormField label="Tablet Image URL (Optional)">
              <input value={variantForm.tabletImage} onChange={(e) => onVariantFieldChange("tabletImage", e.target.value)} placeholder="posters/variant-tablet.jpg" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
          </div>
          <div className="grid gap-3 md:grid-cols-2 mt-3">
            <PosterFormField label="Link URL (Optional)">
              <input value={variantForm.linkUrl} onChange={(e) => onVariantFieldChange("linkUrl", e.target.value)} placeholder="https://example.com/sale" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
            </PosterFormField>
            <PosterFormField label="Status">
              <label className="flex items-center gap-2 rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5">
                <input type="checkbox" checked={variantForm.active} onChange={(e) => onVariantFieldChange("active", e.target.checked)} />
                Active variant
              </label>
            </PosterFormField>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <button type="button" disabled={savingVariant} onClick={() => { void onSaveVariant(); }} className="btn-primary px-3.5 py-2 rounded-lg font-bold text-[0.82rem]">
              {savingVariant ? "Saving..." : editingVariantId ? "Update Variant" : "Create Variant"}
            </button>
            <button type="button" onClick={onCancelVariantForm} className="px-3.5 py-2 rounded-lg border border-line bg-surface-2 text-ink-light font-bold text-[0.82rem]">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Variants Table */}
      {variantsLoading ? (
        <div className="skeleton h-[80px] rounded-[12px]" />
      ) : variants.length === 0 ? (
        <p className="text-muted text-[0.82rem]">No variants yet. Add one to start A/B testing.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b-2 border-line">
                {["Name", "Weight", "Active", "Impressions", "Clicks", "CTR", "Actions"].map((h) => (
                  <th key={h} className="px-2.5 py-2 text-left text-xs text-muted font-bold uppercase tracking-[0.04em]">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {variants.map((v) => (
                <tr key={v.id} className="border-b border-line">
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-ink-light">
                    <span className="font-semibold text-ink">{v.variantName}</span>
                  </td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-ink-light">{v.weight}</td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-ink-light">
                    <StatusBadge value={v.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                  </td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-[#60a5fa] font-semibold">{v.impressions.toLocaleString()}</td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-[#34d399] font-semibold">{v.clicks.toLocaleString()}</td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-[#c084fc] font-semibold">{v.clickThroughRate.toFixed(2)}%</td>
                  <td className="px-2.5 py-2 border-b border-line text-[0.8rem] text-ink-light">
                    <div className="flex gap-2">
                      <button type="button" onClick={() => onEditVariant(v)} className="px-2.5 py-1 rounded-md border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold text-xs">Edit</button>
                      <button type="button" onClick={() => onDeleteVariant(v)} className="px-2.5 py-1 rounded-md border border-[rgba(239,68,68,0.2)] bg-[rgba(239,68,68,0.08)] text-[#fca5a5] font-bold text-xs">Delete</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
