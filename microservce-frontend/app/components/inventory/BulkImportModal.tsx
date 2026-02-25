"use client";

import { useState } from "react";
import type { Warehouse } from "./types";

export type BulkStockItem = {
  productId: string;
  vendorId: string;
  warehouseId: string;
  sku: string;
  quantityOnHand: number;
  lowStockThreshold: number;
  backorderable: boolean;
};

type BulkFormRow = {
  productId: string;
  vendorId: string;
  warehouseId: string;
  sku: string;
  quantityOnHand: number | "";
  lowStockThreshold: number | "";
  backorderable: boolean;
};

type Props = {
  open: boolean;
  warehouses: Warehouse[];
  loading: boolean;
  vendorId?: string;
  onConfirm: (items: BulkStockItem[]) => void;
  onCancel: () => void;
};

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "7px 10px",
  borderRadius: 8,
  border: "1px solid var(--line)",
  background: "var(--surface-2)",
  color: "var(--ink)",
  fontSize: "0.78rem",
  outline: "none",
};

const emptyRow = (vendorId: string): BulkFormRow => ({
  productId: "",
  vendorId,
  warehouseId: "",
  sku: "",
  quantityOnHand: 0,
  lowStockThreshold: 10,
  backorderable: false,
});

export default function BulkImportModal({ open, warehouses, loading, vendorId = "", onConfirm, onCancel }: Props) {
  const [rows, setRows] = useState<BulkFormRow[]>([emptyRow(vendorId)]);

  if (!open) return null;

  const updateRow = (index: number, patch: Partial<BulkFormRow>) => {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const addRow = () => setRows((prev) => [...prev, emptyRow(vendorId)]);

  const removeRow = (index: number) => {
    if (rows.length <= 1) return;
    setRows((prev) => prev.filter((_, i) => i !== index));
  };

  const handleConfirm = () => {
    const valid = rows.filter((r) => r.productId.trim() && r.warehouseId);
    if (valid.length === 0) return;
    onConfirm(valid.map((r) => ({ ...r, quantityOnHand: Number(r.quantityOnHand) || 0, lowStockThreshold: Number(r.lowStockThreshold) || 10 })));
  };

  const handleCancel = () => {
    setRows([emptyRow(vendorId)]);
    onCancel();
  };

  const activeWarehouses = warehouses.filter((w) => w.active);

  return (
    <div className="confirm-modal-overlay" onClick={handleCancel}>
      <div className="confirm-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" style={{ maxWidth: 780 }}>
        <h3 className="confirm-modal-title">Bulk Stock Import</h3>
        <p className="confirm-modal-message" style={{ marginBottom: 12 }}>
          Add or update stock items in bulk. Existing product+warehouse combinations will be updated.
        </p>

        <div style={{ overflowX: "auto", maxHeight: 340, marginBottom: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.78rem" }}>
            <thead>
              <tr>
                <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>Product ID</th>
                {!vendorId && <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>Vendor ID</th>}
                <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>Warehouse</th>
                <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>SKU</th>
                <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>Qty</th>
                <th style={{ padding: "6px 6px", textAlign: "left", color: "var(--muted)", fontWeight: 700, fontSize: "0.7rem", textTransform: "uppercase" }}>Threshold</th>
                <th style={{ padding: "6px 6px", width: 30 }}></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={i}>
                  <td style={{ padding: "4px 4px" }}>
                    <input value={row.productId} onChange={(e) => updateRow(i, { productId: e.target.value })} style={{ ...inputStyle, minWidth: 120 }} placeholder="UUID" />
                  </td>
                  {!vendorId && (
                    <td style={{ padding: "4px 4px" }}>
                      <input value={row.vendorId} onChange={(e) => updateRow(i, { vendorId: e.target.value })} style={{ ...inputStyle, minWidth: 120 }} placeholder="UUID" />
                    </td>
                  )}
                  <td style={{ padding: "4px 4px" }}>
                    <select value={row.warehouseId} onChange={(e) => updateRow(i, { warehouseId: e.target.value })} style={{ ...inputStyle, minWidth: 120, cursor: "pointer" }}>
                      <option value="">Select...</option>
                      {activeWarehouses.map((w) => (
                        <option key={w.id} value={w.id}>{w.name}</option>
                      ))}
                    </select>
                  </td>
                  <td style={{ padding: "4px 4px" }}>
                    <input value={row.sku} onChange={(e) => updateRow(i, { sku: e.target.value })} style={{ ...inputStyle, minWidth: 80 }} />
                  </td>
                  <td style={{ padding: "4px 4px" }}>
                    <input type="number" min={0} value={row.quantityOnHand} onChange={(e) => updateRow(i, { quantityOnHand: e.target.value === "" ? "" : Number(e.target.value) })} style={{ ...inputStyle, width: 70 }} />
                  </td>
                  <td style={{ padding: "4px 4px" }}>
                    <input type="number" min={0} value={row.lowStockThreshold} onChange={(e) => updateRow(i, { lowStockThreshold: e.target.value === "" ? "" : Number(e.target.value) })} style={{ ...inputStyle, width: 70 }} />
                  </td>
                  <td style={{ padding: "4px 4px" }}>
                    <button type="button" onClick={() => removeRow(i)} disabled={rows.length <= 1} style={{ background: "none", border: "none", color: "var(--danger)", cursor: rows.length <= 1 ? "not-allowed" : "pointer", opacity: rows.length <= 1 ? 0.3 : 1, fontSize: "0.9rem" }}>
                      x
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <button type="button" onClick={addRow} className="btn-ghost" style={{ fontSize: "0.78rem", padding: "6px 14px", marginBottom: 14 }}>
          + Add Row
        </button>

        <div className="confirm-modal-actions">
          <button type="button" onClick={handleCancel} disabled={loading} className="btn-outline confirm-modal-cancel">
            Cancel
          </button>
          <button type="button" onClick={handleConfirm} disabled={loading || rows.every((r) => !r.productId.trim())} className="btn-primary confirm-modal-confirm">
            {loading ? "Importing..." : `Import ${rows.filter((r) => r.productId.trim() && r.warehouseId).length} Items`}
          </button>
        </div>
      </div>
    </div>
  );
}
