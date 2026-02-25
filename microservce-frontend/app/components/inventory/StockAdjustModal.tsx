"use client";

import { useState } from "react";

type Props = {
  open: boolean;
  itemLabel: string;
  loading: boolean;
  onConfirm: (quantityChange: number, reason: string) => void;
  onCancel: () => void;
};

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "9px 12px",
  borderRadius: 10,
  border: "1px solid var(--line)",
  background: "var(--surface-2)",
  color: "var(--ink)",
  fontSize: "0.82rem",
  outline: "none",
};

export default function StockAdjustModal({ open, itemLabel, loading, onConfirm, onCancel }: Props) {
  const [qty, setQty] = useState<number | "">(0);
  const [reason, setReason] = useState("");

  if (!open) return null;

  const handleConfirm = () => {
    if (qty === "" || qty === 0) return;
    onConfirm(Number(qty), reason);
    setQty(0);
    setReason("");
  };

  const handleCancel = () => {
    setQty(0);
    setReason("");
    onCancel();
  };

  return (
    <div className="confirm-modal-overlay" onClick={handleCancel}>
      <div className="confirm-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3 className="confirm-modal-title">Adjust Stock</h3>
        <p className="confirm-modal-message" style={{ marginBottom: 16 }}>
          Adjusting stock for <strong>{itemLabel}</strong>
        </p>

        <div style={{ marginBottom: 14 }}>
          <label style={{ display: "block", marginBottom: 5, fontSize: "0.78rem", fontWeight: 600, color: "var(--ink-light)" }}>
            Quantity Change <span style={{ color: "var(--danger)" }}>*</span>
          </label>
          <input
            type="number"
            value={qty}
            onChange={(e) => setQty(e.target.value === "" ? "" : Number(e.target.value))}
            style={inputStyle}
            placeholder="Positive to add, negative to subtract"
            autoFocus
          />
          <p style={{ marginTop: 4, fontSize: "0.72rem", color: "var(--muted)" }}>
            Use positive values to add stock, negative to remove
          </p>
        </div>

        <div style={{ marginBottom: 14 }}>
          <label style={{ display: "block", marginBottom: 5, fontSize: "0.78rem", fontWeight: 600, color: "var(--ink-light)" }}>
            Reason <span style={{ color: "var(--danger)" }}>*</span>
          </label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            style={{ ...inputStyle, resize: "vertical" }}
            placeholder="Reason for this adjustment (required for audit trail)"
          />
        </div>

        <div className="confirm-modal-actions">
          <button type="button" onClick={handleCancel} disabled={loading} className="btn-outline confirm-modal-cancel">
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading || qty === "" || qty === 0 || !reason.trim()}
            className="btn-primary confirm-modal-confirm"
          >
            {loading ? "Adjusting..." : "Adjust Stock"}
          </button>
        </div>
      </div>
    </div>
  );
}
