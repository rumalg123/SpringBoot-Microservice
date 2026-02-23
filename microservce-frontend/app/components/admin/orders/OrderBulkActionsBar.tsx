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
      <div
        style={{
          marginBottom: "12px",
          padding: "10px 12px",
          borderRadius: "12px",
          border: "1px solid rgba(251,146,60,0.12)",
          background: "rgba(251,146,60,0.05)",
          color: "#fdba74",
          fontSize: "0.75rem",
        }}
      >
        Vendor-scoped users manage status through vendor orders. Use the <strong>Vendor Orders</strong> action in each row.
      </div>
    );
  }

  return (
    <div
      style={{
        marginBottom: "12px",
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: "10px",
        padding: "10px 12px",
        borderRadius: "12px",
        border: "1px solid rgba(0,212,255,0.08)",
        background: "rgba(0,212,255,0.03)",
      }}
    >
      <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>
        Selected: <strong style={{ color: "#c8c8e8" }}>{selectedCount}</strong>
      </span>
      <select
        value={bulkStatus}
        onChange={(e) => onBulkStatusChange(e.target.value)}
        disabled={bulkSaving || statusSavingId !== null || selectedCount === 0}
        style={{
          minWidth: "180px",
          borderRadius: "8px",
          border: "1px solid rgba(0,212,255,0.15)",
          background: "rgba(0,212,255,0.04)",
          color: "#c8c8e8",
          padding: "8px 10px",
          fontSize: "0.75rem",
        }}
      >
        {ORDER_STATUSES.map((s) => (
          <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
        ))}
      </select>
      <button
        type="button"
        onClick={() => { void onApplyBulkStatus(); }}
        disabled={bulkSaving || statusSavingId !== null || selectedCount === 0}
        style={{
          padding: "8px 12px",
          borderRadius: "8px",
          border: "1px solid rgba(0,212,255,0.18)",
          background: bulkSaving ? "rgba(0,212,255,0.12)" : "rgba(0,212,255,0.08)",
          color: "#67e8f9",
          fontSize: "0.72rem",
          fontWeight: 700,
          cursor: bulkSaving ? "not-allowed" : "pointer",
          opacity: selectedCount === 0 ? 0.55 : 1,
        }}
      >
        {bulkSaving ? "Applying..." : "Apply to Selected"}
      </button>
      {selectedCount > 0 && bulkInvalidSelectionCount > 0 && (
        <span style={{ color: "#fdba74", fontSize: "0.72rem" }}>
          {bulkInvalidSelectionCount} selected order{bulkInvalidSelectionCount === 1 ? "" : "s"} cannot transition to {bulkStatus.replaceAll("_", " ")}
        </span>
      )}
      {selectedCount > 0 && (
        <button
          type="button"
          onClick={onClearSelection}
          disabled={bulkSaving || statusSavingId !== null}
          style={{
            padding: "8px 10px",
            borderRadius: "8px",
            border: "1px solid rgba(255,255,255,0.08)",
            background: "rgba(255,255,255,0.02)",
            color: "var(--muted)",
            fontSize: "0.72rem",
          }}
        >
          Clear Selection
        </button>
      )}
    </div>
  );
}

