"use client";

import FormField from "../ui/FormField";
import VendorSearchField from "./VendorSearchField";
import type { VendorSummary } from "../admin/products/types";
import type { WarehouseFormData } from "./types";

type Props = {
  form: WarehouseFormData;
  onChange: (patch: Partial<WarehouseFormData>) => void;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
  showVendorId?: boolean;
  showTypeSelect?: boolean;
  vendors?: VendorSummary[];
  loadingVendors?: boolean;
};

export default function WarehouseForm({ form, onChange, saving, onSave, onCancel, showVendorId = false, showTypeSelect = true, vendors, loadingVendors = false }: Props) {
  const isEdit = Boolean(form.id);

  return (
    <div>
      <h3 className="text-lg font-bold text-ink mb-4">
        {isEdit ? "Edit Warehouse" : "New Warehouse"}
      </h3>

      <FormField label="Warehouse Name" htmlFor="wh-name" required>
        <input id="wh-name" value={form.name} onChange={(e) => onChange({ name: e.target.value })} className="form-input w-full" placeholder="e.g. Main Distribution Center" />
      </FormField>

      <FormField label="Description" htmlFor="wh-desc">
        <textarea id="wh-desc" value={form.description} onChange={(e) => onChange({ description: e.target.value })} rows={2} className="form-input w-full resize-y" placeholder="Optional description" />
      </FormField>

      {showTypeSelect && (
        <FormField label="Type" htmlFor="wh-type" required>
          <select id="wh-type" value={form.warehouseType} onChange={(e) => onChange({ warehouseType: e.target.value })} className="form-select w-full" disabled={isEdit}>
            <option value="VENDOR_OWNED">Vendor Owned</option>
            <option value="PLATFORM_MANAGED">Platform Managed</option>
          </select>
        </FormField>
      )}

      {showVendorId && vendors && (
        <VendorSearchField
          vendors={vendors}
          selectedVendorId={form.vendorId}
          onSelect={(vendorId) => onChange({ vendorId })}
          disabled={isEdit || saving}
          loading={loadingVendors}
        />
      )}

      {showVendorId && !vendors && (
        <FormField label="Vendor ID" htmlFor="wh-vendor" helpText="Leave empty for platform warehouse">
          <input id="wh-vendor" value={form.vendorId} onChange={(e) => onChange({ vendorId: e.target.value })} className="form-input w-full" placeholder="Vendor UUID" disabled={isEdit} />
        </FormField>
      )}

      <div className="text-[0.78rem] font-bold text-brand uppercase tracking-[0.06em] mb-3 mt-5">Address</div>
      <div className="grid grid-cols-2 gap-x-3.5">
        <FormField label="Address Line 1" htmlFor="wh-addr1">
          <input id="wh-addr1" value={form.addressLine1} onChange={(e) => onChange({ addressLine1: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="Address Line 2" htmlFor="wh-addr2">
          <input id="wh-addr2" value={form.addressLine2} onChange={(e) => onChange({ addressLine2: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="City" htmlFor="wh-city">
          <input id="wh-city" value={form.city} onChange={(e) => onChange({ city: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="State" htmlFor="wh-state">
          <input id="wh-state" value={form.state} onChange={(e) => onChange({ state: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="Postal Code" htmlFor="wh-zip">
          <input id="wh-zip" value={form.postalCode} onChange={(e) => onChange({ postalCode: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="Country Code" htmlFor="wh-country">
          <input id="wh-country" value={form.countryCode} onChange={(e) => onChange({ countryCode: e.target.value })} className="form-input w-full" maxLength={2} placeholder="US" />
        </FormField>
      </div>

      <div className="text-[0.78rem] font-bold text-brand uppercase tracking-[0.06em] mb-3 mt-5">Contact</div>
      <div className="grid grid-cols-2 gap-x-3.5">
        <FormField label="Contact Name" htmlFor="wh-cn">
          <input id="wh-cn" value={form.contactName} onChange={(e) => onChange({ contactName: e.target.value })} className="form-input w-full" />
        </FormField>
        <FormField label="Contact Phone" htmlFor="wh-cp">
          <input id="wh-cp" value={form.contactPhone} onChange={(e) => onChange({ contactPhone: e.target.value })} className="form-input w-full" />
        </FormField>
      </div>
      <FormField label="Contact Email" htmlFor="wh-ce">
        <input id="wh-ce" value={form.contactEmail} onChange={(e) => onChange({ contactEmail: e.target.value })} className="form-input w-full" type="email" />
      </FormField>

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
