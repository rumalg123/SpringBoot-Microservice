"use client";

import ProductImagesEditor from "./ProductImagesEditor";
import VariationAttributesEditor from "./VariationAttributesEditor";
import VariationParentSelectorPanel from "./VariationParentSelectorPanel";
import type { ProductEditorPanelProps, ProductType } from "./types";

export default function ProductEditorPanel({ state, actions, helpers }: ProductEditorPanelProps) {
  const {
    form,
    emptyForm,
    productMutationBusy,
    isEditingProduct,
    isEditingVariation,
    productSlugStatus,
    parentAttributeNames,
    newParentAttributeName,
    uploadingImages,
    parentCategories,
    subCategoryOptions,
    priceValidationMessage,
    parentSearch,
    loadingParentProducts,
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
    productSlugBlocked,
    savingProduct,
  } = state;

  const {
    submitProduct,
    setForm,
    setProductSlugTouched,
    setProductSlugStatus,
    setParentAttributeNames,
    setNewParentAttributeName,
    setVariationParentId,
    setSelectedVariationParent,
    setParentSearch,
    setParentVariationAttributes,
    setVariationAttributeValues,
    setVariationDrafts,
    addParentAttribute,
    removeParentAttribute,
    refreshVariationParents,
    onSelectVariationParent,
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

  return (<section className="card-surface rounded-2xl p-5">
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-2xl text-[var(--ink)]">
                    {form.id
                      ? form.productType === "VARIATION"
                        ? "Update Variation"
                        : "Update Product"
                      : form.productType === "VARIATION"
                        ? "Create Child Variations"
                        : "Create Product"}
                  </h2>
                  {form.id && (
                    <button
                      type="button"
                      onClick={() => {
                        setForm(emptyForm);
                        setProductSlugTouched(false);
                        setProductSlugStatus("idle");
                        setParentAttributeNames([]);
                        setNewParentAttributeName("");
                        setVariationParentId("");
                        setSelectedVariationParent(null);
                        setParentSearch("");
                        setParentVariationAttributes([]);
                        setVariationAttributeValues({});
                        setVariationDrafts([]);
                      }}
                      disabled={productMutationBusy}
                      className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                    >
                      Reset
                    </button>
                  )}
                </div>
                <form onSubmit={submitProduct} className="grid gap-3 text-sm">
                  <div className="form-group">
                    <label className="form-label">Product Name</label>
                    <input
                      value={form.name}
                      onChange={(e) => {
                        const value = e.target.value;
                        setForm((o) => ({ ...o, name: value }));
                      }}
                      placeholder="e.g. Nike Air Max 90"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Slug</label>
                    <input
                      value={form.slug}
                      onChange={(e) => {
                        setProductSlugTouched(true);
                        setForm((o) => ({ ...o, slug: slugify(e.target.value).slice(0, 180) }));
                      }}
                      placeholder="product-url-slug"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy}
                      required
                    />
                    <p
                      className={`mt-1 text-[11px] ${productSlugStatus === "taken" || productSlugStatus === "invalid"
                        ? "text-red-600"
                        : productSlugStatus === "available"
                          ? "text-emerald-600"
                          : "text-[var(--muted)]"
                        }`}
                    >
                      {productSlugStatus === "checking" && "Checking slug..."}
                      {productSlugStatus === "available" && "Slug is available"}
                      {productSlugStatus === "taken" && "Slug is already taken"}
                      {productSlugStatus === "invalid" && "Enter a valid slug"}
                      {productSlugStatus === "idle" && "Slug is used in product URL"}
                    </p>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Short Description</label>
                    <input value={form.shortDescription} onChange={(e) => setForm((o) => ({ ...o, shortDescription: e.target.value }))} placeholder="Brief product summary" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Description</label>
                    <textarea value={form.description} onChange={(e) => setForm((o) => ({ ...o, description: e.target.value }))} placeholder="Full product description" className="rounded-lg border border-[var(--line)] px-3 py-2" rows={3} disabled={productMutationBusy} required />
                  </div>
                  <ProductImagesEditor
                    form={form}
                    maxImageCount={helpers.MAX_IMAGE_COUNT}
                    resolveImageUrl={helpers.resolveImageUrl}
                    uploadingImages={uploadingImages}
                    productMutationBusy={productMutationBusy}
                    uploadImages={actions.uploadImages}
                    setDragImageIndex={actions.setDragImageIndex}
                    onImageDrop={actions.onImageDrop}
                    removeImage={actions.removeImage}
                  />
                  <div className="form-group">
                    <label className="form-label">Product Type</label>
                    <select
                      value={form.productType}
                      onChange={(e) => {
                        const nextType = e.target.value as ProductType;
                        setForm((o) => ({
                          ...o,
                          productType: nextType,
                          mainCategoryName: nextType === "VARIATION" ? "" : o.mainCategoryName,
                          subCategoryNames: nextType === "VARIATION" ? [] : o.subCategoryNames,
                        }));
                        if (nextType !== "PARENT") {
                          setParentAttributeNames([]);
                          setNewParentAttributeName("");
                        }
                        if (nextType !== "VARIATION") {
                          setVariationParentId("");
                          setSelectedVariationParent(null);
                          setParentSearch("");
                          setParentVariationAttributes([]);
                          setVariationAttributeValues({});
                          setVariationDrafts([]);
                        }
                      }}
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy || isEditingProduct}
                    >
                      <option value="SINGLE">SINGLE</option>
                      <option value="PARENT">PARENT</option>
                      <option value="VARIATION">VARIATION</option>
                    </select>
                    <p className="mt-1 text-[11px] text-[var(--muted)]">
                      {isEditingProduct
                        ? "Product type is locked while editing."
                        : "Select product type first. Variation products inherit categories from their parent."}
                    </p>
                  </div>
                  {form.productType !== "VARIATION" && (
                    <>
                      <select
                        value={form.mainCategoryName}
                        onChange={(e) =>
                          setForm((o) => ({
                            ...o,
                            mainCategoryName: e.target.value,
                            subCategoryNames: [],
                          }))
                        }
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                        required
                      >
                        <option value="">Select Main Category</option>
                        {parentCategories.map((c) => (
                          <option key={c.id} value={c.name}>
                            {c.name}
                          </option>
                        ))}
                      </select>
                      <div className="rounded-lg border border-[var(--line)] p-2">
                        <p className="mb-1 text-xs text-[var(--muted)]">Sub Categories (multiple)</p>
                        <div className="grid gap-1">
                          {subCategoryOptions.length === 0 && (
                            <p className="text-xs text-[var(--muted)]">No sub categories for selected main category.</p>
                          )}
                          {subCategoryOptions.map((c) => (
                            <label key={c.id} className="flex items-center gap-2 text-xs text-[var(--ink)]">
                              <input
                                type="checkbox"
                                checked={form.subCategoryNames.includes(c.name)}
                                onChange={(e) =>
                                  setForm((o) => ({
                                    ...o,
                                    subCategoryNames: e.target.checked
                                      ? [...o.subCategoryNames, c.name]
                                      : o.subCategoryNames.filter((n) => n !== c.name),
                                  }))
                                }
                                disabled={productMutationBusy}
                              />
                              {c.name}
                            </label>
                          ))}
                        </div>
                      </div>
                    </>
                  )}
                  <div className="form-group">
                    <label className="form-label">SKU</label>
                    <input value={form.sku} onChange={(e) => setForm((o) => ({ ...o, sku: e.target.value }))} placeholder="e.g. NK-AM90-BLK-42" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} required />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="form-group">
                      <label className="form-label">Regular Price</label>
                      <input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        min="0.01"
                        value={form.regularPrice}
                        onChange={(e) => setForm((o) => ({ ...o, regularPrice: e.target.value }))}
                        onWheel={preventNumberInputScroll}
                        onKeyDown={preventNumberInputArrows}
                        placeholder="0.00"
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Discounted Price</label>
                      <input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        min="0"
                        value={form.discountedPrice}
                        onChange={(e) => setForm((o) => ({ ...o, discountedPrice: e.target.value }))}
                        onWheel={preventNumberInputScroll}
                        onKeyDown={preventNumberInputArrows}
                        placeholder="Optional"
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                      />
                    </div>
                  </div>
                  {priceValidationMessage && (
                    <p className="text-xs font-semibold text-red-600">{priceValidationMessage}</p>
                  )}
                  <input value={form.vendorId} onChange={(e) => setForm((o) => ({ ...o, vendorId: e.target.value }))} placeholder="Vendor UUID (optional)" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} />
                  {form.productType === "PARENT" && (
                    <VariationAttributesEditor
                      parentAttributeNames={parentAttributeNames}
                      newParentAttributeName={newParentAttributeName}
                      setNewParentAttributeName={setNewParentAttributeName}
                      addParentAttribute={addParentAttribute}
                      removeParentAttribute={removeParentAttribute}
                      productMutationBusy={productMutationBusy}
                    />
                  )}
                  {form.productType === "VARIATION" && (
                    <VariationParentSelectorPanel state={state} actions={actions} helpers={helpers} />
                  )}
                  <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
                    <input type="checkbox" checked={form.active} onChange={(e) => setForm((o) => ({ ...o, active: e.target.checked }))} disabled={productMutationBusy} />
                    Active
                  </label>
                  <button
                    type="submit"
                    disabled={Boolean(priceValidationMessage) || savingProduct || creatingQueuedVariationBatch || uploadingImages || Boolean(selectingVariationParentId) || productSlugBlocked}
                    className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {savingProduct
                      ? "Saving..."
                      : form.productType === "VARIATION" && !form.id
                        ? "Add Child Variation"
                        : form.productType === "VARIATION" && form.id
                          ? "Update Variation"
                          : form.id
                            ? "Update Product"
                            : "Create Product"}
                  </button>
                </form>
              </section>  );
}


