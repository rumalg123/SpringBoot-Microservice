"use client";

type BulkActionsToolbarProps = {
  selectedCount: number;
  bulkOperationBusy: boolean;
  bulkDeleting: boolean;
  bulkPriceUpdating: boolean;
  bulkReassigning: boolean;
  onBulkDelete: () => void;
  onBulkPriceUpdate: () => void;
  onBulkCategoryReassign: () => void;
  onClearSelection: () => void;
};

export default function BulkActionsToolbar({
  selectedCount,
  bulkOperationBusy,
  bulkDeleting,
  bulkPriceUpdating,
  bulkReassigning,
  onBulkDelete,
  onBulkPriceUpdate,
  onBulkCategoryReassign,
  onClearSelection,
}: BulkActionsToolbarProps) {
  return (
    <div className="flex flex-wrap items-center gap-2.5 px-3.5 py-2.5 rounded-lg bg-brand-soft border border-brand-soft">
      <span className="text-sm font-semibold text-brand">
        {selectedCount} selected
      </span>

      <button
        type="button"
        onClick={onBulkDelete}
        disabled={bulkOperationBusy}
        className={`text-sm px-3 py-1.5 rounded-sm border border-danger-soft bg-danger-soft text-danger ${bulkOperationBusy ? "cursor-not-allowed opacity-50" : "cursor-pointer"}`}
      >
        {bulkDeleting ? "Deleting..." : "Bulk Delete"}
      </button>

      <button
        type="button"
        onClick={onBulkPriceUpdate}
        disabled={bulkOperationBusy}
        className={`text-sm px-3 py-1.5 rounded-sm border border-brand-soft bg-brand-soft text-ink-light ${bulkOperationBusy ? "cursor-not-allowed opacity-50" : "cursor-pointer"}`}
      >
        {bulkPriceUpdating ? "Updating..." : "Bulk Price Update"}
      </button>

      <button
        type="button"
        onClick={onBulkCategoryReassign}
        disabled={bulkOperationBusy}
        className={`text-sm px-3 py-1.5 rounded-sm border border-accent-soft bg-accent-soft text-accent ${bulkOperationBusy ? "cursor-not-allowed opacity-50" : "cursor-pointer"}`}
      >
        {bulkReassigning ? "Reassigning..." : "Bulk Reassign Category"}
      </button>

      <button
        type="button"
        onClick={onClearSelection}
        disabled={bulkOperationBusy}
        className="text-xs px-2.5 py-1 rounded-sm border border-line bg-transparent text-muted cursor-pointer ml-auto"
      >
        Clear selection
      </button>
    </div>
  );
}
