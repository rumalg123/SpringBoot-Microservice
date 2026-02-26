"use client";

import Pagination from "../../Pagination";
import ProductFilterForm from "./ProductFilterForm";
import ProductTableRow from "./ProductTableRow";

type ProductType = "SINGLE" | "PARENT" | "VARIATION";
type ApprovalStatus = "NOT_REQUIRED" | "DRAFT" | "PENDING" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

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
  selectedProductIds?: string[];
  onToggleProductSelection?: (id: string) => void;
  onToggleSelectAllCurrentPage?: () => void;
  onShowActive: () => void;
  onShowDeleted: () => void;
  onQChange: (value: string) => void;
  onSkuChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onVendorIdChange: (value: string) => void;
  onVendorSearchChange: (value: string) => void;
  onTypeChange: (value: ProductType | "") => void;
  approvalStatus?: ApprovalStatus | "";
  onApprovalStatusChange?: (value: ApprovalStatus | "") => void;
  activeFilter?: "" | "true" | "false";
  onActiveFilterChange?: (value: "" | "true" | "false") => void;
  onApplyFilters: (e: React.FormEvent) => void | Promise<void>;
  onEditProduct: (id: string) => void | Promise<void>;
  onDeleteProductRequest: (product: ProductSummary) => void;
  onRestoreProduct: (id: string) => void | Promise<void>;
  onPageChange: (page: number) => void | Promise<void>;
  canApproveReject?: boolean;
  approvingProductId?: string | null;
  rejectingProductId?: string | null;
  submitForReviewProductId?: string | null;
  onSubmitForReview?: (id: string) => void | Promise<void>;
  onApproveProduct?: (id: string) => void | Promise<void>;
  onRejectProductRequest?: (product: ProductSummary) => void;
};

export default function ProductCatalogPanel({
  title, showDeleted, q, sku, category, vendorId, vendorSearch, type,
  parentCategories, subCategories, vendors, loadingVendors, showVendorFilter = true,
  rows, pageMeta, currentPage, filtersSubmitting, listLoading,
  productRowActionBusy, loadingProductId, restoringProductId,
  selectedProductIds, onToggleProductSelection, onToggleSelectAllCurrentPage,
  onShowActive, onShowDeleted,
  onQChange, onSkuChange, onCategoryChange, onVendorIdChange, onVendorSearchChange, onTypeChange,
  approvalStatus = "", onApprovalStatusChange, activeFilter = "", onActiveFilterChange,
  onApplyFilters, onEditProduct, onDeleteProductRequest, onRestoreProduct, onPageChange,
  canApproveReject = false, approvingProductId, rejectingProductId, submitForReviewProductId,
  onSubmitForReview, onApproveProduct, onRejectProductRequest,
}: Props) {
  const selectionEnabled = !showDeleted && Boolean(onToggleProductSelection) && Boolean(onToggleSelectAllCurrentPage);
  const selectedSet = new Set(selectedProductIds || []);
  const allCurrentSelected = selectionEnabled && rows.length > 0 && rows.every((p) => selectedSet.has(p.id));
  const someCurrentSelected = selectionEnabled && rows.some((p) => selectedSet.has(p.id));

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

      <ProductFilterForm
        q={q} sku={sku} category={category} type={type}
        parentCategories={parentCategories} subCategories={subCategories}
        vendorId={vendorId} vendorSearch={vendorSearch}
        vendors={vendors} loadingVendors={loadingVendors} showVendorFilter={showVendorFilter}
        approvalStatus={approvalStatus} activeFilter={activeFilter}
        filtersSubmitting={filtersSubmitting} listLoading={listLoading}
        onQChange={onQChange} onSkuChange={onSkuChange} onCategoryChange={onCategoryChange}
        onTypeChange={onTypeChange} onApprovalStatusChange={onApprovalStatusChange}
        onActiveFilterChange={onActiveFilterChange}
        onVendorIdChange={onVendorIdChange} onVendorSearchChange={onVendorSearchChange}
        onApplyFilters={onApplyFilters}
      />

      <h2 className="mb-3 text-2xl text-[var(--ink)]">{title}</h2>
      <div className="overflow-hidden rounded-2xl border border-[var(--line)] bg-surface">
        <table className="w-full text-left text-sm">
          <thead className="bg-surface-2 text-ink">
            <tr>
              {selectionEnabled && (
                <th className="w-10 px-3 py-2">
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
              <ProductTableRow
                key={p.id}
                product={p}
                selectionEnabled={selectionEnabled}
                isSelected={selectedSet.has(p.id)}
                showDeleted={showDeleted}
                checkboxStyle={checkboxStyle}
                canApproveReject={canApproveReject}
                productRowActionBusy={productRowActionBusy}
                loadingProductId={loadingProductId}
                restoringProductId={restoringProductId}
                approvingProductId={approvingProductId}
                rejectingProductId={rejectingProductId}
                submitForReviewProductId={submitForReviewProductId}
                onToggleProductSelection={onToggleProductSelection}
                onEditProduct={onEditProduct}
                onDeleteProductRequest={onDeleteProductRequest}
                onRestoreProduct={onRestoreProduct}
                onSubmitForReview={onSubmitForReview}
                onApproveProduct={onApproveProduct}
                onRejectProductRequest={onRejectProductRequest}
              />
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
