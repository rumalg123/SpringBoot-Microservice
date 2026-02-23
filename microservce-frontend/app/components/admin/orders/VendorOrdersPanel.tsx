"use client";

import { getAllowedNextStatuses, canTransitionOrderStatus, getStatusChip, ORDER_STATUSES } from "./orderStatus";
import { VendorOrder } from "./types";

type Props = {
  orderId: string | null;
  loading: boolean;
  rows: VendorOrder[];
  statusDrafts: Record<string, string>;
  statusSavingId: string | null;
  activeHistoryVendorOrderId: string | null;
  historyLoading: boolean;
  onDraftChange: (vendorOrderId: string, status: string) => void;
  onToggleHistory: (vendorOrderId: string) => void | Promise<void>;
  onSaveStatus: (vendorOrderId: string) => void | Promise<void>;
  onRefresh: () => void | Promise<void>;
  onClose: () => void;
};

export default function VendorOrdersPanel({
  orderId,
  loading,
  rows,
  statusDrafts,
  statusSavingId,
  activeHistoryVendorOrderId,
  historyLoading,
  onDraftChange,
  onToggleHistory,
  onSaveStatus,
  onRefresh,
  onClose,
}: Props) {
  if (!orderId) return null;

  return (
    <div
      style={{
        marginBottom: "12px",
        borderRadius: "12px",
        border: "1px solid rgba(124,58,237,0.15)",
        background: "rgba(124,58,237,0.04)",
        padding: "12px",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px" }}>
        <div>
          <p style={{ margin: 0, color: "#ddd6fe", fontWeight: 700, fontSize: "0.85rem" }}>Vendor Orders</p>
          <p style={{ margin: "4px 0 0", color: "var(--muted)", fontSize: "0.7rem", fontFamily: "monospace" }}>{orderId}</p>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          <button
            type="button"
            onClick={() => { void onRefresh(); }}
            disabled={loading}
            style={{
              padding: "6px 10px",
              borderRadius: "8px",
              border: "1px solid rgba(124,58,237,0.18)",
              background: "rgba(124,58,237,0.08)",
              color: "#c4b5fd",
              fontSize: "0.72rem",
            }}
          >
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: "6px 10px",
              borderRadius: "8px",
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.02)",
              color: "var(--muted)",
              fontSize: "0.72rem",
            }}
          >
            Close
          </button>
        </div>
      </div>

      <div style={{ marginTop: "10px", display: "grid", gap: "8px" }}>
        {loading && <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>Loading vendor orders...</div>}
        {!loading && rows.length === 0 && (
          <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>No vendor orders found for this order.</div>
        )}
        {!loading && rows.map((row) => {
          const chip = getStatusChip(row.status || "PENDING");
          const draftStatus = statusDrafts[row.id] || row.status || "PENDING";
          const canSaveVendorOrder =
            draftStatus !== (row.status || "PENDING") && canTransitionOrderStatus(row.status, draftStatus);
          return (
            <div
              key={row.id}
              style={{
                border: "1px solid rgba(255,255,255,0.06)",
                background: "rgba(255,255,255,0.015)",
                borderRadius: "10px",
                padding: "10px",
              }}
            >
              <div style={{ display: "flex", flexWrap: "wrap", gap: "8px", alignItems: "center", justifyContent: "space-between" }}>
                <div style={{ display: "grid", gap: "3px" }}>
                  <span style={{ color: "#c8c8e8", fontSize: "0.72rem", fontFamily: "monospace" }}>{row.id}</span>
                  <span style={{ color: "var(--muted)", fontSize: "0.68rem", fontFamily: "monospace" }}>vendor: {row.vendorId}</span>
                </div>
                <span
                  style={{
                    borderRadius: "20px",
                    background: chip.bg,
                    border: `1px solid ${chip.border}`,
                    color: chip.color,
                    padding: "2px 10px",
                    fontSize: "0.7rem",
                    fontWeight: 800,
                  }}
                >
                  {(row.status || "PENDING").replaceAll("_", " ")}
                </span>
              </div>
              <div style={{ marginTop: "6px", display: "flex", flexWrap: "wrap", gap: "10px", color: "var(--muted)", fontSize: "0.7rem" }}>
                <span>Qty: {row.quantity}</span>
                <span>Items: {row.itemCount}</span>
                <span>Total: ${Number(row.orderTotal ?? 0).toFixed(2)}</span>
                <span>{new Date(row.createdAt).toLocaleString()}</span>
              </div>
              <div style={{ marginTop: "8px", display: "flex", flexWrap: "wrap", gap: "8px", alignItems: "center" }}>
                <select
                  value={draftStatus}
                  onChange={(e) => onDraftChange(row.id, e.target.value)}
                  disabled={loading || statusSavingId === row.id}
                  style={{
                    minWidth: "170px",
                    borderRadius: "8px",
                    border: "1px solid rgba(124,58,237,0.18)",
                    background: "rgba(124,58,237,0.06)",
                    color: "#e9d5ff",
                    padding: "8px 10px",
                    fontSize: "0.74rem",
                  }}
                >
                  {ORDER_STATUSES.map((s) => (
                    <option key={s} value={s} disabled={!getAllowedNextStatuses(row.status).includes(s)}>
                      {s.replaceAll("_", " ")}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => { void onToggleHistory(row.id); }}
                  disabled={loading || statusSavingId !== null}
                  style={{
                    padding: "8px 10px",
                    borderRadius: "8px",
                    border: "1px solid rgba(255,255,255,0.08)",
                    background: activeHistoryVendorOrderId === row.id ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.02)",
                    color: activeHistoryVendorOrderId === row.id ? "#c8c8e8" : "var(--muted)",
                    fontSize: "0.72rem",
                    fontWeight: 700,
                  }}
                >
                  {historyLoading && activeHistoryVendorOrderId === row.id ? "Loading..." : activeHistoryVendorOrderId === row.id ? "Hide History" : "History"}
                </button>
                <button
                  type="button"
                  onClick={() => { void onSaveStatus(row.id); }}
                  disabled={loading || statusSavingId !== null || !canSaveVendorOrder}
                  title={
                    canTransitionOrderStatus(row.status, draftStatus)
                      ? "Save vendor-order status"
                      : `Invalid transition: ${row.status} -> ${draftStatus}`
                  }
                  style={{
                    padding: "8px 10px",
                    borderRadius: "8px",
                    border: "1px solid rgba(124,58,237,0.18)",
                    background: statusSavingId === row.id ? "rgba(124,58,237,0.12)" : "rgba(124,58,237,0.08)",
                    color: "#ddd6fe",
                    fontSize: "0.72rem",
                    fontWeight: 700,
                    opacity: canSaveVendorOrder ? 1 : 0.55,
                  }}
                >
                  {statusSavingId === row.id ? "Saving..." : "Save"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

