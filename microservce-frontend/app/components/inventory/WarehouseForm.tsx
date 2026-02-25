"use client";

import FormField from "../ui/FormField";
import type { WarehouseFormData } from "./types";

type Props = {
  form: WarehouseFormData;
  onChange: (patch: Partial<WarehouseFormData>) => void;
  saving: boolean;
  onSave: () => void;
  onCancel: () => void;
  showVendorId?: boolean;
  showTypeSelect?: boolean;
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

const sectionTitle: React.CSSProperties = {
  fontSize: "0.78rem",
  fontWeight: 700,
  color: "var(--brand)",
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  marginBottom: 12,
  marginTop: 20,
};

export default function WarehouseForm({ form, onChange, saving, onSave, onCancel, showVendorId = false, showTypeSelect = true }: Props) {
  const isEdit = Boolean(form.id);

  return (
    <div>
      <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 16 }}>
        {isEdit ? "Edit Warehouse" : "New Warehouse"}
      </h3>

      <FormField label="Warehouse Name" htmlFor="wh-name" required>
        <input id="wh-name" value={form.name} onChange={(e) => onChange({ name: e.target.value })} style={inputStyle} placeholder="e.g. Main Distribution Center" />
      </FormField>

      <FormField label="Description" htmlFor="wh-desc">
        <textarea id="wh-desc" value={form.description} onChange={(e) => onChange({ description: e.target.value })} rows={2} style={{ ...inputStyle, resize: "vertical" }} placeholder="Optional description" />
      </FormField>

      {showTypeSelect && (
        <FormField label="Type" htmlFor="wh-type" required>
          <select id="wh-type" value={form.warehouseType} onChange={(e) => onChange({ warehouseType: e.target.value })} style={selectStyle} disabled={isEdit}>
            <option value="VENDOR_OWNED">Vendor Owned</option>
            <option value="PLATFORM_MANAGED">Platform Managed</option>
          </select>
        </FormField>
      )}

      {showVendorId && (
        <FormField label="Vendor ID" htmlFor="wh-vendor" helpText="Leave empty for platform warehouse">
          <input id="wh-vendor" value={form.vendorId} onChange={(e) => onChange({ vendorId: e.target.value })} style={inputStyle} placeholder="Vendor UUID" disabled={isEdit} />
        </FormField>
      )}

      <div style={sectionTitle}>Address</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" }}>
        <FormField label="Address Line 1" htmlFor="wh-addr1">
          <input id="wh-addr1" value={form.addressLine1} onChange={(e) => onChange({ addressLine1: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="Address Line 2" htmlFor="wh-addr2">
          <input id="wh-addr2" value={form.addressLine2} onChange={(e) => onChange({ addressLine2: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="City" htmlFor="wh-city">
          <input id="wh-city" value={form.city} onChange={(e) => onChange({ city: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="State" htmlFor="wh-state">
          <input id="wh-state" value={form.state} onChange={(e) => onChange({ state: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="Postal Code" htmlFor="wh-zip">
          <input id="wh-zip" value={form.postalCode} onChange={(e) => onChange({ postalCode: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="Country Code" htmlFor="wh-country">
          <input id="wh-country" value={form.countryCode} onChange={(e) => onChange({ countryCode: e.target.value })} style={inputStyle} maxLength={2} placeholder="US" />
        </FormField>
      </div>

      <div style={sectionTitle}>Contact</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" }}>
        <FormField label="Contact Name" htmlFor="wh-cn">
          <input id="wh-cn" value={form.contactName} onChange={(e) => onChange({ contactName: e.target.value })} style={inputStyle} />
        </FormField>
        <FormField label="Contact Phone" htmlFor="wh-cp">
          <input id="wh-cp" value={form.contactPhone} onChange={(e) => onChange({ contactPhone: e.target.value })} style={inputStyle} />
        </FormField>
      </div>
      <FormField label="Contact Email" htmlFor="wh-ce">
        <input id="wh-ce" value={form.contactEmail} onChange={(e) => onChange({ contactEmail: e.target.value })} style={inputStyle} type="email" />
      </FormField>

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
