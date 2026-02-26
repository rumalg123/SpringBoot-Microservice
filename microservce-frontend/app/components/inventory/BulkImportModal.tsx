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
      <div className="confirm-modal max-w-[780px]" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h3 className="confirm-modal-title">Bulk Stock Import</h3>
        <p className="confirm-modal-message mb-3">
          Add or update stock items in bulk. Existing product+warehouse combinations will be updated.
        </p>

        <div className="overflow-x-auto max-h-[340px] mb-3">
          <table className="w-full border-collapse text-[0.78rem]">
            <thead>
              <tr>
                <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">Product ID</th>
                {!vendorId && <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">Vendor ID</th>}
                <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">Warehouse</th>
                <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">SKU</th>
                <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">Qty</th>
                <th className="px-1.5 py-1.5 text-left text-muted font-bold text-[0.7rem] uppercase">Threshold</th>
                <th className="px-1.5 py-1.5 w-[30px]"></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={i}>
                  <td className="p-1">
                    <input value={row.productId} onChange={(e) => updateRow(i, { productId: e.target.value })} className="form-input w-full min-w-[120px] text-[0.78rem] py-[7px] px-2.5" placeholder="UUID" />
                  </td>
                  {!vendorId && (
                    <td className="p-1">
                      <input value={row.vendorId} onChange={(e) => updateRow(i, { vendorId: e.target.value })} className="form-input w-full min-w-[120px] text-[0.78rem] py-[7px] px-2.5" placeholder="UUID" />
                    </td>
                  )}
                  <td className="p-1">
                    <select value={row.warehouseId} onChange={(e) => updateRow(i, { warehouseId: e.target.value })} className="form-select w-full min-w-[120px] text-[0.78rem] py-[7px] px-2.5 cursor-pointer">
                      <option value="">Select...</option>
                      {activeWarehouses.map((w) => (
                        <option key={w.id} value={w.id}>{w.name}</option>
                      ))}
                    </select>
                  </td>
                  <td className="p-1">
                    <input value={row.sku} onChange={(e) => updateRow(i, { sku: e.target.value })} className="form-input w-full min-w-[80px] text-[0.78rem] py-[7px] px-2.5" />
                  </td>
                  <td className="p-1">
                    <input type="number" min={0} value={row.quantityOnHand} onChange={(e) => updateRow(i, { quantityOnHand: e.target.value === "" ? "" : Number(e.target.value) })} className="form-input w-[70px] text-[0.78rem] py-[7px] px-2.5" />
                  </td>
                  <td className="p-1">
                    <input type="number" min={0} value={row.lowStockThreshold} onChange={(e) => updateRow(i, { lowStockThreshold: e.target.value === "" ? "" : Number(e.target.value) })} className="form-input w-[70px] text-[0.78rem] py-[7px] px-2.5" />
                  </td>
                  <td className="p-1">
                    <button
                      type="button"
                      onClick={() => removeRow(i)}
                      disabled={rows.length <= 1}
                      className="bg-transparent border-none text-danger text-[0.9rem]"
                      style={{ cursor: rows.length <= 1 ? "not-allowed" : "pointer", opacity: rows.length <= 1 ? 0.3 : 1 }}
                    >
                      x
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <button type="button" onClick={addRow} className="btn-ghost text-[0.78rem] px-3.5 py-1.5 mb-3.5">
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
