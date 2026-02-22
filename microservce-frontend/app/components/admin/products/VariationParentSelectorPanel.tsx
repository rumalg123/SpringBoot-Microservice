"use client";

import VariationRowsEditor from "./VariationRowsEditor";
import type { VariationParentSelectorPanelProps } from "./types";

export default function VariationParentSelectorPanel({ state, actions, helpers }: VariationParentSelectorPanelProps) {
  const {
    form,
    isEditingVariation,
    parentSearch,
    loadingParentProducts,
    productMutationBusy,
    parentProducts,
    filteredParentProducts,
    variationParentId,
    selectingVariationParentId,
    selectedVariationParent,
    parentVariationAttributes,
    variationAttributeValues,
    canQueueVariation,
    canAddVariationDraft,
    canCreateQueuedVariations,
    variationDrafts,
    creatingQueuedVariationBatch,
    variationDraftBlockedReason,
    savingProduct,
  } = state;

  const {
    setParentSearch,
    refreshVariationParents,
    onSelectVariationParent,
    setVariationAttributeValues,
    setVariationDrafts,
    addVariationDraft,
    createQueuedVariations,
    loadVariationDraftToForm,
    removeVariationDraft,
    updateVariationDraftPayload,
    updateVariationDraftAttributeValue,
  } = actions;

  const {
    slugify,
    preventNumberInputScroll,
    preventNumberInputArrows,
  } = helpers;

  return (
    <div className="rounded-lg border border-[var(--line)] p-3">
      <p className="text-xs font-semibold text-[var(--ink)]">Child Variation Setup</p>
      <p className="mt-1 text-[11px] text-[var(--muted)]">
        {isEditingVariation
          ? "Editing mode: parent selection is locked. Categories remain inherited from the existing parent."
          : "Select a parent product first. Categories are inherited automatically."}
      </p>
      <div className="mt-2 flex gap-2">
        <input
          value={parentSearch}
          onChange={(e) => setParentSearch(e.target.value)}
          placeholder="Search parent by name, SKU, or slug"
          className="w-full rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
          disabled={isEditingVariation || loadingParentProducts || productMutationBusy}
        />
        <button
          type="button"
          onClick={() => {
            void refreshVariationParents();
          }}
          disabled={isEditingVariation || loadingParentProducts || productMutationBusy || Boolean(selectingVariationParentId)}
          className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          {loadingParentProducts ? "Refreshing..." : "Refresh"}
        </button>
      </div>
      {!loadingParentProducts && parentProducts.length > 0 && (
        <p className="mt-1 text-[11px] text-[var(--muted)]">
          Showing {Math.min(filteredParentProducts.length, 20)} of {parentProducts.length} parent products
        </p>
      )}
      <div className="mt-2 max-h-40 overflow-auto rounded-lg border border-[var(--line)]" style={{ background: "var(--surface)" }}>
        {loadingParentProducts && (
          <p className="px-3 py-2 text-xs text-[var(--muted)]">Loading parent products...</p>
        )}
        {!loadingParentProducts && parentProducts.length === 0 && (
          <p className="px-3 py-2 text-xs text-[var(--muted)]">No active parent products found.</p>
        )}
        {!loadingParentProducts && parentProducts.length > 0 && filteredParentProducts.length === 0 && (
          <p className="px-3 py-2 text-xs text-[var(--muted)]">No matching parent products.</p>
        )}
        {filteredParentProducts.slice(0, 20).map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => {
              void onSelectVariationParent(p.id);
            }}
            disabled={isEditingVariation || Boolean(selectingVariationParentId) || productMutationBusy}
            className={`flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-xs transition hover:bg-[var(--brand-soft)] ${variationParentId === p.id ? "bg-[var(--brand-soft)]" : ""
              } disabled:cursor-not-allowed disabled:opacity-60`}
          >
            <span className="min-w-0">
              <span className="block truncate text-[var(--ink)]">{p.name}</span>
              <span className="block truncate text-[10px] text-[var(--muted)]">/{p.slug || "no-slug"}</span>
            </span>
            <span className="shrink-0 text-[var(--muted)]">{p.sku}</span>
          </button>
        ))}
      </div>
      {selectedVariationParent && (
        <div className="mt-2 rounded-md border border-[var(--line)] bg-[var(--bg)] px-2 py-1.5 text-xs text-[var(--muted)]">
          <p>
            Selected parent: <span className="font-semibold text-[var(--ink)]">{selectedVariationParent.name}</span> ({selectedVariationParent.sku})
          </p>
          {(form.mainCategoryName || form.subCategoryNames.length > 0) && (
            <div className="mt-1 flex flex-wrap gap-1">
              {form.mainCategoryName && (
                <span className="rounded-full bg-[var(--brand)] px-2 py-0.5 text-[10px] font-semibold text-white">
                  {form.mainCategoryName}
                </span>
              )}
              {form.subCategoryNames.map((name) => (
                <span
                  key={`variation-sub-${name}`}
                  className="rounded-full border border-[var(--line)] px-2 py-0.5 text-[10px] text-[var(--ink)]"
                  style={{ background: "var(--surface-2)" }}
                >
                  {name}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
      {selectingVariationParentId && (
        <p className="mt-2 text-xs text-[var(--muted)]">Loading parent attributes...</p>
      )}
      {parentVariationAttributes.length > 0 && (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {parentVariationAttributes.map((attributeName) => (
            <input
              key={attributeName}
              value={variationAttributeValues[attributeName] || ""}
              onChange={(e) =>
                setVariationAttributeValues((old) => ({
                  ...old,
                  [attributeName]: e.target.value,
                }))
              }
              placeholder={`${attributeName} (required)`}
              className="rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
              disabled={productMutationBusy}
              required
            />
          ))}
        </div>
      )}
      {parentVariationAttributes.length === 0 && (
        <p className="mt-2 text-xs text-[var(--muted)]">
          Select a parent product to load variation attributes.
        </p>
      )}
      <VariationRowsEditor
        canQueueVariation={canQueueVariation}
        canAddVariationDraft={canAddVariationDraft}
        canCreateQueuedVariations={canCreateQueuedVariations}
        variationDrafts={variationDrafts}
        creatingQueuedVariationBatch={creatingQueuedVariationBatch}
        variationDraftBlockedReason={variationDraftBlockedReason}
        savingProduct={savingProduct}
        setVariationDrafts={setVariationDrafts}
        addVariationDraft={addVariationDraft}
        createQueuedVariations={createQueuedVariations}
        loadVariationDraftToForm={loadVariationDraftToForm}
        removeVariationDraft={removeVariationDraft}
        updateVariationDraftPayload={updateVariationDraftPayload}
        updateVariationDraftAttributeValue={updateVariationDraftAttributeValue}
        slugify={slugify}
        preventNumberInputScroll={preventNumberInputScroll}
        preventNumberInputArrows={preventNumberInputArrows}
      />
    </div>
  );
}
