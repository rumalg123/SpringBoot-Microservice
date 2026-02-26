"use client";

import StatusBadge, { APPROVAL_COLORS } from "../../ui/StatusBadge";

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

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

type Props = {
  product: ProductSummary;
  selectionEnabled: boolean;
  isSelected: boolean;
  showDeleted: boolean;
  checkboxStyle: React.CSSProperties;
  canApproveReject: boolean;
  productRowActionBusy: boolean;
  loadingProductId: string | null;
  restoringProductId: string | null;
  approvingProductId?: string | null;
  rejectingProductId?: string | null;
  submitForReviewProductId?: string | null;
  onToggleProductSelection?: (id: string) => void;
  onEditProduct: (id: string) => void | Promise<void>;
  onDeleteProductRequest: (product: ProductSummary) => void;
  onRestoreProduct: (id: string) => void | Promise<void>;
  onSubmitForReview?: (id: string) => void | Promise<void>;
  onApproveProduct?: (id: string) => void | Promise<void>;
  onRejectProductRequest?: (product: ProductSummary) => void;
};

export default function ProductTableRow({
  product: p, selectionEnabled, isSelected, showDeleted, checkboxStyle,
  canApproveReject, productRowActionBusy, loadingProductId, restoringProductId,
  approvingProductId, rejectingProductId, submitForReviewProductId,
  onToggleProductSelection, onEditProduct, onDeleteProductRequest, onRestoreProduct,
  onSubmitForReview, onApproveProduct, onRejectProductRequest,
}: Props) {
  return (
    <tr className={`border-t border-[var(--line)] ${selectionEnabled && isSelected ? "bg-[rgba(0,212,255,0.04)]" : ""}`}>
      {selectionEnabled && (
        <td className="px-3 py-2">
          <input
            type="checkbox"
            checked={isSelected}
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
                onClick={() => { void onEditProduct(p.id); }}
                disabled={productRowActionBusy}
                className="rounded-md border border-[var(--line)] bg-surface-2 px-2 py-1 text-xs text-ink-light disabled:cursor-not-allowed disabled:opacity-60"
              >
                {loadingProductId === p.id ? "Loading..." : "Edit"}
              </button>
              <button
                type="button"
                onClick={() => onDeleteProductRequest(p)}
                disabled={productRowActionBusy}
                className="rounded-md border border-red-900/30 bg-red-500/[0.06] px-2 py-1 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
              >
                Delete
              </button>
              {onSubmitForReview && (p.approvalStatus === "NOT_REQUIRED" || p.approvalStatus === "DRAFT" || p.approvalStatus === "REJECTED") && (
                <button
                  type="button"
                  onClick={() => { void onSubmitForReview(p.id); }}
                  disabled={productRowActionBusy || submitForReviewProductId === p.id}
                  className="rounded-md border border-[rgba(0,212,255,0.25)] bg-[rgba(0,212,255,0.06)] px-2 py-1 text-xs text-brand disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {submitForReviewProductId === p.id ? "Submitting..." : "Submit for Review"}
                </button>
              )}
              {canApproveReject && onApproveProduct && (p.approvalStatus === "PENDING" || p.approvalStatus === "PENDING_REVIEW") && (
                <button
                  type="button"
                  onClick={() => { void onApproveProduct(p.id); }}
                  disabled={productRowActionBusy || approvingProductId === p.id}
                  className="rounded-md border border-green-500/30 bg-green-500/[0.08] px-2 py-1 text-xs text-success disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {approvingProductId === p.id ? "Approving..." : "Approve"}
                </button>
              )}
              {canApproveReject && onRejectProductRequest && (p.approvalStatus === "PENDING" || p.approvalStatus === "PENDING_REVIEW") && (
                <button
                  type="button"
                  onClick={() => onRejectProductRequest(p)}
                  disabled={productRowActionBusy || rejectingProductId === p.id}
                  className="rounded-md border border-red-500/25 bg-red-500/[0.06] px-2 py-1 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {rejectingProductId === p.id ? "Rejecting..." : "Reject"}
                </button>
              )}
            </>
          )}
          {showDeleted && (
            <button
              type="button"
              onClick={() => { void onRestoreProduct(p.id); }}
              disabled={productRowActionBusy}
              className="rounded-md border border-emerald-900/30 bg-emerald-500/[0.06] px-2 py-1 text-xs text-emerald-400 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {restoringProductId === p.id ? "Restoring..." : "Restore"}
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}
