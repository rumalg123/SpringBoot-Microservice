"use client";

import { ORDER_STATUSES } from "./orderStatus";

type Props = {
  isVendorScopedActor: boolean;
  selectedCount: number;
  bulkStatus: string;
  bulkSaving: boolean;
  statusSavingId: string | null;
  bulkInvalidSelectionCount: number;
  onBulkStatusChange: (value: string) => void;
  onApplyBulkStatus: () => void | Promise<void>;
  onClearSelection: () => void;
};

export default function OrderBulkActionsBar({
  isVendorScopedActor,
  selectedCount,
  bulkStatus,
  bulkSaving,
  statusSavingId,
  bulkInvalidSelectionCount,
  onBulkStatusChange,
  onApplyBulkStatus,
  onClearSelection,
}: Props) {
  if (isVendorScopedActor) {
    return (
      <div className="mb-3 rounded-xl border border-[rgba(251,146,60,0.12)] bg-[rgba(251,146,60,0.05)] px-3 py-2.5 text-xs text-[#fdba74]">
        Vendor-scoped users manage status through vendor orders. Use the <strong>Vendor Orders</strong> action in each row.
      </div>
    );
  }

  return (
    <div className="mb-3 flex flex-wrap items-center gap-2.5 rounded-xl border border-[rgba(0,212,255,0.08)] bg-[rgba(0,212,255,0.03)] px-3 py-2.5">
      <span className="text-xs text-muted">
        Selected: <strong className="text-[#c8c8e8]">{selectedCount}</strong>
      </span>
      <select
        value={bulkStatus}
        onChange={(e) => onBulkStatusChange(e.target.value)}
        disabled={bulkSaving || statusSavingId !== null || selectedCount === 0}
        className="min-w-[180px] rounded-md border border-[rgba(0,212,255,0.15)] bg-[rgba(0,212,255,0.04)] px-2.5 py-2 text-xs text-[#c8c8e8]"
      >
        {ORDER_STATUSES.map((s) => (
          <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
        ))}
      </select>
      <button
        type="button"
        onClick={() => { void onApplyBulkStatus(); }}
        disabled={bulkSaving || statusSavingId !== null || selectedCount === 0}
        className={`rounded-md border border-[rgba(0,212,255,0.18)] px-3 py-2 text-xs font-bold text-[#67e8f9] ${
          bulkSaving ? "cursor-not-allowed bg-[rgba(0,212,255,0.12)]" : "cursor-pointer bg-[rgba(0,212,255,0.08)]"
        } ${selectedCount === 0 ? "opacity-55" : "opacity-100"}`}
      >
        {bulkSaving ? "Applying..." : "Apply to Selected"}
      </button>
      {selectedCount > 0 && bulkInvalidSelectionCount > 0 && (
        <span className="text-xs text-[#fdba74]">
          {bulkInvalidSelectionCount} selected order{bulkInvalidSelectionCount === 1 ? "" : "s"} cannot transition to {bulkStatus.replaceAll("_", " ")}
        </span>
      )}
      {selectedCount > 0 && (
        <button
          type="button"
          onClick={onClearSelection}
          disabled={bulkSaving || statusSavingId !== null}
          className="rounded-md border border-white/[0.08] bg-white/[0.02] px-2.5 py-2 text-xs text-muted"
        >
          Clear Selection
        </button>
      )}
    </div>
  );
}
