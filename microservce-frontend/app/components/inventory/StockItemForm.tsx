"use client";

import FormField from "../ui/FormField";
import type { StockItemFormData } from "./types";
import type { Warehouse } from "./types";

type Props = {
  form: StockItemFormData;
  onChange: (patch: Partial<StockItemFormData>) => void;
  warehouses: Warehouse[];
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
  showVendorId?: boolean;
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

const selectStyle: React.CSSProperties = {
  ...inputStyle,
  cursor: "pointer",
  appearance: "none" as const,
  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`,
  backgroundRepeat: "no-repeat",
  backgroundPosition: "right 10px center",
  paddingRight: 30,
};

export default function StockItemForm({ form, onChange, warehouses, saving, onSave, onCancel, showVendorId = false }: Props) {
  const isEdit = Boolean(form.id);

  return (
    <div>
      <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 16 }}>
        {isEdit ? "Edit Stock Item" : "New Stock Item"}
      </h3>

      <FormField label="Product ID" htmlFor="si-product" required>
        <input id="si-product" value={form.productId} onChange={(e) => onChange({ productId: e.target.value })} style={inputStyle} placeholder="Product UUID" disabled={isEdit} />
      </FormField>

      {showVendorId && (
        <FormField label="Vendor ID" htmlFor="si-vendor" required>
          <input id="si-vendor" value={form.vendorId} onChange={(e) => onChange({ vendorId: e.target.value })} style={inputStyle} placeholder="Vendor UUID" disabled={isEdit} />
        </FormField>
      )}

      <FormField label="Warehouse" htmlFor="si-warehouse" required>
        <select id="si-warehouse" value={form.warehouseId} onChange={(e) => onChange({ warehouseId: e.target.value })} style={selectStyle} disabled={isEdit}>
          <option value="">Select warehouse...</option>
          {warehouses.filter((w) => w.active).map((w) => (
            <option key={w.id} value={w.id}>{w.name}</option>
          ))}
        </select>
      </FormField>

      <FormField label="SKU" htmlFor="si-sku" helpText="Stock keeping unit identifier">
        <input id="si-sku" value={form.sku} onChange={(e) => onChange({ sku: e.target.value })} style={inputStyle} placeholder="e.g. ELEC-EBUDS-001" />
      </FormField>

      {!isEdit && (
        <FormField label="Quantity on Hand" htmlFor="si-qty" required>
          <input id="si-qty" type="number" min={0} value={form.quantityOnHand} onChange={(e) => onChange({ quantityOnHand: e.target.value === "" ? "" : Number(e.target.value) })} style={inputStyle} />
        </FormField>
      )}

      <FormField label="Low Stock Threshold" htmlFor="si-threshold" helpText="Alert when available stock falls to or below this value">
        <input id="si-threshold" type="number" min={0} value={form.lowStockThreshold} onChange={(e) => onChange({ lowStockThreshold: e.target.value === "" ? "" : Number(e.target.value) })} style={inputStyle} />
      </FormField>

      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14 }}>
        <input id="si-backorder" type="checkbox" checked={form.backorderable} onChange={(e) => onChange({ backorderable: e.target.checked })} style={{ accentColor: "var(--brand)", cursor: "pointer" }} />
        <label htmlFor="si-backorder" style={{ fontSize: "0.82rem", color: "var(--ink-light)", cursor: "pointer" }}>Allow backorders</label>
      </div>

      <div style={{ display: "flex", gap: 10, marginTop: 20, justifyContent: "flex-end" }}>
        <button type="button" onClick={onCancel} className="btn-ghost" style={{ padding: "8px 18px", fontSize: "0.82rem" }} disabled={saving}>
          Cancel
        </button>
        <button type="button" onClick={onSave} className="btn-primary" style={{ padding: "8px 22px", fontSize: "0.82rem" }} disabled={saving}>
          {saving ? "Saving..." : isEdit ? "Update" : "Create"}
        </button>
      </div>
    </div>
  );
}
