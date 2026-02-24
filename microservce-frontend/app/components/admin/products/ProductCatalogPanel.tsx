"use client";

import Pagination from "../../Pagination";
import StatusBadge, { APPROVAL_COLORS } from "../../ui/StatusBadge";

type ProductType = "SINGLE" | "PARENT" | "VARIATION";
type ApprovalStatus = "NOT_REQUIRED" | "PENDING" | "APPROVED" | "REJECTED";

type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  sellingPrice: number;
  sku: string;
  productType: ProductType;
  vendorId: string;
  categories: string[];
  active: boolean;
  approvalStatus?: ApprovalStatus;
  variations?: Array<{ name: string; value: string }>;
};

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type VendorSummary = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  active: boolean;
  deleted: boolean;
  status?: string;
};

type PageMeta = {
  totalElements: number;
  totalPages: number;
};

type Props = {
  title: string;
  showDeleted: boolean;
  q: string;
  sku: string;
  category: string;
  vendorId: string;
  vendorSearch: string;
  type: ProductType | "";
  parentCategories: Category[];
  subCategories: Category[];
  vendors: VendorSummary[];
  loadingVendors: boolean;
  showVendorFilter?: boolean;
  rows: ProductSummary[];
  pageMeta: PageMeta;
  currentPage: number;
  filtersSubmitting: boolean;
  listLoading: boolean;
  productRowActionBusy: boolean;
  loadingProductId: string | null;
  restoringProductId: string | null;
  /* Selection props (optional) */
  selectedProductIds?: string[];
  onToggleProductSelection?: (id: string) => void;
  onToggleSelectAllCurrentPage?: () => void;
  /* Callbacks */
  onShowActive: () => void;
  onShowDeleted: () => void;
  onQChange: (value: string) => void;
  onSkuChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onVendorIdChange: (value: string) => void;
  onVendorSearchChange: (value: string) => void;
  onTypeChange: (value: ProductType | "") => void;
  onApplyFilters: (e: React.FormEvent) => void | Promise<void>;
  onEditProduct: (id: string) => void | Promise<void>;
  onDeleteProductRequest: (product: ProductSummary) => void;
  onRestoreProduct: (id: string) => void | Promise<void>;
  onPageChange: (page: number) => void | Promise<void>;
  /* Approval workflow (optional) */
  canApproveReject?: boolean;
  approvingProductId?: string | null;
  rejectingProductId?: string | null;
  submitForReviewProductId?: string | null;
  onSubmitForReview?: (id: string) => void | Promise<void>;
  onApproveProduct?: (id: string) => void | Promise<void>;
  onRejectProductRequest?: (product: ProductSummary) => void;
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function ProductCatalogPanel({
  title,
  showDeleted,
  q,
  sku,
  category,
  vendorId,
  vendorSearch,
  type,
  parentCategories,
  subCategories,
  vendors,
  loadingVendors,
  showVendorFilter = true,
  rows,
  pageMeta,
  currentPage,
  filtersSubmitting,
  listLoading,
  productRowActionBusy,
  loadingProductId,
  restoringProductId,
  selectedProductIds,
  onToggleProductSelection,
  onToggleSelectAllCurrentPage,
  onShowActive,
  onShowDeleted,
  onQChange,
  onSkuChange,
  onCategoryChange,
  onVendorIdChange,
  onVendorSearchChange,
  onTypeChange,
  onApplyFilters,
  onEditProduct,
  onDeleteProductRequest,
  onRestoreProduct,
  onPageChange,
  canApproveReject = false,
  approvingProductId,
  rejectingProductId,
  submitForReviewProductId,
  onSubmitForReview,
  onApproveProduct,
  onRejectProductRequest,
}: Props) {
  const selectionEnabled = !showDeleted && Boolean(onToggleProductSelection) && Boolean(onToggleSelectAllCurrentPage);
  const selectedSet = new Set(selectedProductIds || []);
  const allCurrentSelected = selectionEnabled && rows.length > 0 && rows.every((p) => selectedSet.has(p.id));
  const someCurrentSelected = selectionEnabled && rows.some((p) => selectedSet.has(p.id));

  const filteredVendors = vendors
    .filter((vendor) => !vendor.deleted)
    .filter((vendor) => {
      const needle = vendorSearch.trim().toLowerCase();
      if (!needle) return true;
      return vendor.name.toLowerCase().includes(needle)
        || vendor.slug.toLowerCase().includes(needle)
        || (vendor.contactEmail || "").toLowerCase().includes(needle)
        || vendor.id.toLowerCase().includes(needle);
    })
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(0, 100);

  const checkboxStyle: React.CSSProperties = {
    width: 16,
    height: 16,
    accentColor: "var(--brand, #00d4ff)",
    cursor: "pointer",
  };

  return (
    <div className="order-2 lg:order-1">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN CATALOG</p>
          <h1 className="text-2xl font-bold text-[var(--ink)]">Product Operations</h1>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onShowActive}
            disabled={filtersSubmitting || listLoading}
            className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${!showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
          >
            Active
          </button>
          <button
            type="button"
            onClick={onShowDeleted}
            disabled={filtersSubmitting || listLoading}
            className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
          >
            Deleted
          </button>
        </div>
      </div>

      <form onSubmit={onApplyFilters} className={`mb-5 grid gap-3 md:grid-cols-2 ${showVendorFilter ? "xl:grid-cols-7" : "xl:grid-cols-5"}`}>
        <input
          value={q}
          onChange={(e) => onQChange(e.target.value)}
          placeholder="Search text"
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        />
        <input
          value={sku}
          onChange={(e) => onSkuChange(e.target.value)}
          placeholder="SKU"
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        />
        <select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        >
          <option value="">All Categories</option>
          {parentCategories.length > 0 && (
            <optgroup label="Main Categories">
              {parentCategories
                .slice()
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((item) => (
                  <option key={item.id} value={item.name}>
                    {item.name}
                  </option>
                ))}
            </optgroup>
          )}
          {subCategories.length > 0 && (
            <optgroup label="Sub Categories">
              {subCategories
                .slice()
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((item) => (
                  <option key={item.id} value={item.name}>
                    {item.name}
                  </option>
                ))}
            </optgroup>
          )}
        </select>
        <select
          value={type}
          onChange={(e) => onTypeChange(e.target.value as ProductType | "")}
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        >
          <option value="">All Types</option>
          <option value="SINGLE">SINGLE</option>
          <option value="PARENT">PARENT</option>
          <option value="VARIATION">VARIATION</option>
        </select>
        {showVendorFilter && (
          <>
            <input
              value={vendorSearch}
              onChange={(e) => onVendorSearchChange(e.target.value)}
              placeholder="Vendor search"
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
              style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            />
            <select
              value={vendorId}
              onChange={(e) => onVendorIdChange(e.target.value)}
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
              style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            >
              <option value="">{loadingVendors ? "Loading vendors..." : "All Vendors"}</option>
              {filteredVendors.map((vendor) => (
                <option key={vendor.id} value={vendor.id}>
                  {vendor.name} ({vendor.slug})
                </option>
              ))}
            </select>
          </>
        )}
        <button
          type="submit"
          disabled={filtersSubmitting || listLoading}
          className="btn-brand rounded-xl px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60"
        >
          {(filtersSubmitting || listLoading) ? "Applying..." : "Apply Filters"}
        </button>
      </form>

      <h2 className="mb-3 text-2xl text-[var(--ink)]">{title}</h2>
      <div className="overflow-hidden rounded-2xl border border-[var(--line)]" style={{ background: "var(--surface)" }}>
        <table className="w-full text-left text-sm">
          <thead style={{ background: "var(--surface-2)", color: "var(--ink)" }}>
            <tr>
              {selectionEnabled && (
                <th className="px-3 py-2" style={{ width: 40 }}>
                  <input
                    type="checkbox"
                    checked={allCurrentSelected}
                    ref={(el) => { if (el) el.indeterminate = someCurrentSelected && !allCurrentSelected; }}
                    onChange={() => onToggleSelectAllCurrentPage?.()}
                    style={checkboxStyle}
                    title="Select all on this page"
                  />
                </th>
              )}
              <th className="px-3 py-2">Name</th>
              <th className="px-3 py-2">SKU</th>
              <th className="px-3 py-2">Type</th>
              <th className="px-3 py-2">Price</th>
              <th className="px-3 py-2">Approval</th>
              <th className="px-3 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={selectionEnabled ? 7 : 6}>
                  <div className="empty-state">
                    <div className="empty-state-icon">Items</div>
                    <p className="empty-state-title">No products</p>
                    <p className="empty-state-desc">{showDeleted ? "No deleted products" : "Create your first product to get started"}</p>
                  </div>
                </td>
              </tr>
            )}
            {rows.map((p) => (
              <tr
                key={p.id}
                className="border-t border-[var(--line)]"
                style={selectionEnabled && selectedSet.has(p.id) ? { background: "rgba(0,212,255,0.04)" } : undefined}
              >
                {selectionEnabled && (
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      checked={selectedSet.has(p.id)}
                      onChange={() => onToggleProductSelection?.(p.id)}
                      style={checkboxStyle}
                    />
                  </td>
                )}
                <td className="px-3 py-2">
                  <p className="font-semibold text-[var(--ink)]">{p.name}</p>
                  <p className="line-clamp-1 text-xs text-[var(--muted)]">{p.shortDescription}</p>
                </td>
                <td className="px-3 py-2 font-mono text-xs text-[var(--muted)]">{p.sku}</td>
                <td className="text-xs text-[var(--muted)]">
                  <span className={`type-badge type-badge--${p.productType.toLowerCase()}`}>{p.productType}</span>
                </td>
                <td className="px-3 py-2 text-[var(--ink)]">{money(p.sellingPrice)}</td>
                <td className="px-3 py-2">
                  {p.approvalStatus ? (
                    <StatusBadge value={p.approvalStatus} colorMap={APPROVAL_COLORS} />
                  ) : (
                    <span className="text-xs text-[var(--muted)]">--</span>
                  )}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-2">
                    {!showDeleted && (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            void onEditProduct(p.id);
                          }}
                          disabled={productRowActionBusy}
                          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                        >
                          {loadingProductId === p.id ? "Loading..." : "Edit"}
                        </button>
                        <button
                          type="button"
                          onClick={() => onDeleteProductRequest(p)}
                          disabled={productRowActionBusy}
                          className="rounded-md border border-red-900/30 px-2 py-1 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                          style={{ background: "rgba(239,68,68,0.06)" }}
                        >
                          Delete
                        </button>
                        {/* Submit for Review - shown when NOT_REQUIRED or REJECTED */}
                        {onSubmitForReview && (p.approvalStatus === "NOT_REQUIRED" || p.approvalStatus === "REJECTED") && (
                          <button
                            type="button"
                            onClick={() => { void onSubmitForReview(p.id); }}
                            disabled={productRowActionBusy || submitForReviewProductId === p.id}
                            className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                            style={{
                              borderColor: "rgba(0,212,255,0.25)",
                              background: "rgba(0,212,255,0.06)",
                              color: "var(--brand, #00d4ff)",
                            }}
                          >
                            {submitForReviewProductId === p.id ? "Submitting..." : "Submit for Review"}
                          </button>
                        )}
                        {/* Approve - shown when PENDING, for platform staff/super admin */}
                        {canApproveReject && onApproveProduct && p.approvalStatus === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => { void onApproveProduct(p.id); }}
                            disabled={productRowActionBusy || approvingProductId === p.id}
                            className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                            style={{
                              borderColor: "rgba(34,197,94,0.3)",
                              background: "rgba(34,197,94,0.08)",
                              color: "var(--success, #22c55e)",
                            }}
                          >
                            {approvingProductId === p.id ? "Approving..." : "Approve"}
                          </button>
                        )}
                        {/* Reject - shown when PENDING, for platform staff/super admin */}
                        {canApproveReject && onRejectProductRequest && p.approvalStatus === "PENDING" && (
                          <button
                            type="button"
                            onClick={() => onRejectProductRequest(p)}
                            disabled={productRowActionBusy || rejectingProductId === p.id}
                            className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                            style={{
                              borderColor: "rgba(239,68,68,0.25)",
                              background: "rgba(239,68,68,0.06)",
                              color: "#f87171",
                            }}
                          >
                            {rejectingProductId === p.id ? "Rejecting..." : "Reject"}
                          </button>
                        )}
                      </>
                    )}
                    {showDeleted && (
                      <button
                        type="button"
                        onClick={() => {
                          void onRestoreProduct(p.id);
                        }}
                        disabled={productRowActionBusy}
                        className="rounded-md border border-emerald-900/30 px-2 py-1 text-xs text-emerald-400 disabled:cursor-not-allowed disabled:opacity-60"
                        style={{ background: "rgba(16,185,129,0.06)" }}
                      >
                        {restoringProductId === p.id ? "Restoring..." : "Restore"}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination
        currentPage={currentPage}
        totalPages={pageMeta.totalPages}
        totalElements={pageMeta.totalElements}
        onPageChange={(p) => {
          void onPageChange(p);
        }}
        disabled={listLoading}
      />
    </div>
  );
}
