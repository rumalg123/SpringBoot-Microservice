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

export default function StockItemForm({ form, onChange, warehouses, saving, onSave, onCancel, showVendorId = false }: Props) {
  const isEdit = Boolean(form.id);

  return (
    <div>
      <h3 className="text-lg font-bold text-ink mb-4">
        {isEdit ? "Edit Stock Item" : "New Stock Item"}
      </h3>

      <FormField label="Product ID" htmlFor="si-product" required>
        <input id="si-product" value={form.productId} onChange={(e) => onChange({ productId: e.target.value })} className="form-input w-full" placeholder="Product UUID" disabled={isEdit} />
      </FormField>

      {showVendorId && (
        <FormField label="Vendor ID" htmlFor="si-vendor" required>
          <input id="si-vendor" value={form.vendorId} onChange={(e) => onChange({ vendorId: e.target.value })} className="form-input w-full" placeholder="Vendor UUID" disabled={isEdit} />
        </FormField>
      )}

      <FormField label="Warehouse" htmlFor="si-warehouse" required>
        <select id="si-warehouse" value={form.warehouseId} onChange={(e) => onChange({ warehouseId: e.target.value })} className="form-select w-full" disabled={isEdit}>
          <option value="">Select warehouse...</option>
          {warehouses.filter((w) => w.active).map((w) => (
            <option key={w.id} value={w.id}>{w.name}</option>
          ))}
        </select>
      </FormField>

      <FormField label="SKU" htmlFor="si-sku" helpText="Stock keeping unit identifier">
        <input id="si-sku" value={form.sku} onChange={(e) => onChange({ sku: e.target.value })} className="form-input w-full" placeholder="e.g. ELEC-EBUDS-001" />
      </FormField>

      {!isEdit && (
        <FormField label="Quantity on Hand" htmlFor="si-qty" required>
          <input id="si-qty" type="number" min={0} value={form.quantityOnHand} onChange={(e) => onChange({ quantityOnHand: e.target.value === "" ? "" : Number(e.target.value) })} className="form-input w-full" />
        </FormField>
      )}

      <FormField label="Low Stock Threshold" htmlFor="si-threshold" helpText="Alert when available stock falls to or below this value">
        <input id="si-threshold" type="number" min={0} value={form.lowStockThreshold} onChange={(e) => onChange({ lowStockThreshold: e.target.value === "" ? "" : Number(e.target.value) })} className="form-input w-full" />
      </FormField>

      <div className="flex items-center gap-2 mb-3.5">
        <input id="si-backorder" type="checkbox" checked={form.backorderable} onChange={(e) => onChange({ backorderable: e.target.checked })} className="accent-brand cursor-pointer" />
        <label htmlFor="si-backorder" className="text-[0.82rem] text-ink-light cursor-pointer">Allow backorders</label>
      </div>

      <div className="flex gap-2.5 mt-5 justify-end">
        <button type="button" onClick={onCancel} className="btn-ghost px-4.5 py-2 text-[0.82rem]" disabled={saving}>
          Cancel
        </button>
        <button type="button" onClick={onSave} className="btn-primary px-5.5 py-2 text-[0.82rem]" disabled={saving}>
          {saving ? "Saving..." : isEdit ? "Update" : "Create"}
        </button>
      </div>
    </div>
  );
}
