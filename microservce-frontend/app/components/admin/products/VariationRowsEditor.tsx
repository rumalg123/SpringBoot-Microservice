"use client";

import type { VariationRowsEditorProps } from "./types";

export default function VariationRowsEditor({
  canQueueVariation,
  canAddVariationDraft,
  canCreateQueuedVariations,
  variationDrafts,
  creatingQueuedVariationBatch,
  variationDraftBlockedReason,
  savingProduct,
  setVariationDrafts,
  addVariationDraft,
  createQueuedVariations,
  loadVariationDraftToForm,
  removeVariationDraft,
  updateVariationDraftPayload,
  updateVariationDraftAttributeValue,
  slugify,
  preventNumberInputScroll,
  preventNumberInputArrows,
}: VariationRowsEditorProps) {
  return (
    <>
      <div className="mt-3 flex flex-wrap gap-2">
        {canQueueVariation && (
          <>
            <button
              type="button"
              onClick={addVariationDraft}
              disabled={!canAddVariationDraft}
              className="btn-brand rounded-lg px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
            >
              Add Another Variation
            </button>
            <button
              type="button"
              onClick={() => {
                void createQueuedVariations();
              }}
              disabled={!canCreateQueuedVariations}
              className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
              style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
            >
              {creatingQueuedVariationBatch
                ? `Creating... (${variationDrafts.length})`
                : `Create Queued Variations (${variationDrafts.length})`}
            </button>
            {variationDrafts.length > 0 && (
              <button
                type="button"
                onClick={() => setVariationDrafts([])}
                disabled={creatingQueuedVariationBatch}
                className="rounded-lg border border-red-900/30 px-3 py-2 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                style={{ background: "rgba(239,68,68,0.06)" }}
              >
                Clear Queue
              </button>
            )}
          </>
        )}
      </div>
      {canQueueVariation && variationDraftBlockedReason && (
        <p className="mt-2 text-xs font-medium text-amber-700">{variationDraftBlockedReason}</p>
      )}
      {canQueueVariation && variationDrafts.length > 0 && (
        <div className="mt-3 rounded-lg border border-[var(--line)] bg-[var(--bg)] p-2">
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">
            Queued Variations
          </p>
          <div className="grid gap-2">
            {variationDrafts.map((draft) => (
              <div key={draft.id} className="rounded-md border border-[var(--line)] p-2 text-xs" style={{ background: "var(--surface-2)" }}>
                <div className="flex items-center justify-between gap-2">
                  <p className="truncate text-[var(--ink)]">
                    Parent: <span className="font-semibold">{draft.parentLabel}</span>
                  </p>
                  <div className="flex gap-1">
                    <button
                      type="button"
                      onClick={() => loadVariationDraftToForm(draft.id)}
                      disabled={creatingQueuedVariationBatch || savingProduct}
                      className="rounded border border-[var(--line)] px-2 py-0.5 text-[10px] disabled:cursor-not-allowed disabled:opacity-60"
                      style={{ background: "var(--surface-3)", color: "var(--ink-light)" }}
                    >
                      Load To Form
                    </button>
                    <button
                      type="button"
                      onClick={() => removeVariationDraft(draft.id)}
                      disabled={creatingQueuedVariationBatch || savingProduct}
                      className="rounded border border-red-900/30 px-2 py-0.5 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                      style={{ background: "rgba(239,68,68,0.06)" }}
                    >
                      Remove
                    </button>
                  </div>
                </div>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  <input
                    value={draft.payload.name}
                    onChange={(e) => updateVariationDraftPayload(draft.id, { name: e.target.value })}
                    placeholder="Variation name"
                    className="rounded border border-[var(--line)] px-2 py-1.5"
                  />
                  <input
                    value={draft.payload.slug}
                    onChange={(e) =>
                      updateVariationDraftPayload(draft.id, { slug: slugify(e.target.value).slice(0, 180) })
                    }
                    placeholder="Variation slug"
                    className="rounded border border-[var(--line)] px-2 py-1.5"
                  />
                  <input
                    value={draft.payload.sku}
                    onChange={(e) => updateVariationDraftPayload(draft.id, { sku: e.target.value })}
                    placeholder="Variation SKU"
                    className="rounded border border-[var(--line)] px-2 py-1.5"
                  />
                  <input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0.01"
                    value={draft.payload.regularPrice}
                    onChange={(e) => {
                      const next = Number(e.target.value);
                      updateVariationDraftPayload(draft.id, { regularPrice: Number.isFinite(next) ? next : 0 });
                    }}
                    onWheel={preventNumberInputScroll}
                    onKeyDown={preventNumberInputArrows}
                    placeholder="Regular price"
                    className="rounded border border-[var(--line)] px-2 py-1.5"
                  />
                  <input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    min="0"
                    value={draft.payload.discountedPrice === null ? "" : draft.payload.discountedPrice}
                    onChange={(e) => {
                      const raw = e.target.value.trim();
                      if (!raw) {
                        updateVariationDraftPayload(draft.id, { discountedPrice: null });
                        return;
                      }
                      const next = Number(raw);
                      updateVariationDraftPayload(draft.id, { discountedPrice: Number.isFinite(next) ? next : null });
                    }}
                    onWheel={preventNumberInputScroll}
                    onKeyDown={preventNumberInputArrows}
                    placeholder="Discounted price"
                    className="rounded border border-[var(--line)] px-2 py-1.5"
                  />
                </div>
                <label className="mt-2 inline-flex items-center gap-2 text-[11px] text-[var(--muted)]">
                  <input
                    type="checkbox"
                    checked={draft.payload.active}
                    onChange={(e) => updateVariationDraftPayload(draft.id, { active: e.target.checked })}
                  />
                  Active
                </label>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  {draft.payload.variations.map((pair) => (
                    <input
                      key={`${draft.id}-${pair.name}`}
                      value={pair.value}
                      onChange={(e) => updateVariationDraftAttributeValue(draft.id, pair.name, e.target.value)}
                      placeholder={`${pair.name} (required)`}
                      className="rounded border border-[var(--line)] px-2 py-1.5"
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  );
}
