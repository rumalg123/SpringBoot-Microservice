"use client";

import { useState } from "react";

type Props = {
  open: boolean;
  itemLabel: string;
  loading: boolean;
  onConfirm: (quantityChange: number, reason: string) => void;
  onCancel: () => void;
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
        <p className="confirm-modal-message mb-4">
          Adjusting stock for <strong>{itemLabel}</strong>
        </p>

        <div className="mb-3.5">
          <label className="block mb-[5px] text-[0.78rem] font-semibold text-ink-light">
            Quantity Change <span className="text-danger">*</span>
          </label>
          <input
            type="number"
            value={qty}
            onChange={(e) => setQty(e.target.value === "" ? "" : Number(e.target.value))}
            className="form-input w-full"
            placeholder="Positive to add, negative to subtract"
            autoFocus
          />
          <p className="mt-1 text-xs text-muted">
            Use positive values to add stock, negative to remove
          </p>
        </div>

        <div className="mb-3.5">
          <label className="block mb-[5px] text-[0.78rem] font-semibold text-ink-light">
            Reason <span className="text-danger">*</span>
          </label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            className="form-input w-full resize-y"
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
