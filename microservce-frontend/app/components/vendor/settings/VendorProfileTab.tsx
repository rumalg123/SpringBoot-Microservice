"use client";

import { VendorProfile } from "./types";

/* ------------------------------------------------------------------ */
/*  Props                                                              */
/* ------------------------------------------------------------------ */

export interface VendorProfileTabProps {
  vendor: VendorProfile;
  loadingProfile: boolean;
  savingProfile: boolean;
  onFieldChange: (key: keyof VendorProfile, value: string | number | boolean | "") => void;
  onSave: () => void;
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function VendorProfileTab({
  vendor,
  loadingProfile,
  savingProfile,
  onFieldChange,
  onSave,
}: VendorProfileTabProps) {
  /* ---- local render helpers ---- */

  const renderInput = (
    label: string,
    value: string | number | "",
    onChange: (v: string) => void,
    opts?: { type?: string; required?: boolean; placeholder?: string; maxLength?: number; disabled?: boolean }
  ) => (
    <div className="mb-4">
      <label className="block text-[0.78rem] font-semibold text-muted mb-1">
        {label}
        {opts?.required && <span className="text-brand ml-0.5">*</span>}
      </label>
      <input
        type={opts?.type || "text"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={opts?.placeholder}
        maxLength={opts?.maxLength}
        disabled={opts?.disabled}
        required={opts?.required}
        className="form-input w-full"
      />
    </div>
  );

  const renderTextarea = (
    label: string,
    value: string,
    onChange: (v: string) => void,
    opts?: { rows?: number; placeholder?: string }
  ) => (
    <div className="mb-4">
      <label className="block text-[0.78rem] font-semibold text-muted mb-1">{label}</label>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={opts?.placeholder}
        rows={opts?.rows || 4}
        className="form-input w-full min-h-[100px] resize-y"
      />
    </div>
  );

  /* ---- render ---- */

  if (loadingProfile) {
    return (
      <div className="text-center p-12 text-muted">Loading profile...</div>
    );
  }

  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSave();
        }}
      >
        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Basic Information
          </h3>
          {renderInput("Name", vendor.name, (v) => onFieldChange("name", v), { required: true })}
          <div className="grid grid-cols-2 gap-4">
            {renderInput("Contact Email", vendor.contactEmail, (v) => onFieldChange("contactEmail", v), {
              type: "email",
              required: true,
            })}
            {renderInput("Support Email", vendor.supportEmail, (v) => onFieldChange("supportEmail", v), {
              type: "email",
            })}
          </div>
          <div className="grid grid-cols-2 gap-4">
            {renderInput("Contact Phone", vendor.contactPhone, (v) => onFieldChange("contactPhone", v))}
            {renderInput("Contact Person Name", vendor.contactPersonName, (v) =>
              onFieldChange("contactPersonName", v)
            )}
          </div>
        </div>

        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Branding
          </h3>
          {renderInput("Logo Image URL", vendor.logoImage, (v) => onFieldChange("logoImage", v), {
            placeholder: "https://...",
          })}
          {renderInput("Banner Image URL", vendor.bannerImage, (v) => onFieldChange("bannerImage", v), {
            placeholder: "https://...",
          })}
          {renderInput("Website URL", vendor.websiteUrl, (v) => onFieldChange("websiteUrl", v), {
            placeholder: "https://...",
          })}
        </div>

        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Details
          </h3>
          {renderTextarea("Description", vendor.description, (v) => onFieldChange("description", v), {
            rows: 5,
            placeholder: "Tell customers about your store...",
          })}
          {renderTextarea("Return Policy", vendor.returnPolicy, (v) => onFieldChange("returnPolicy", v), {
            rows: 3,
          })}
          {renderTextarea("Shipping Policy", vendor.shippingPolicy, (v) => onFieldChange("shippingPolicy", v), {
            rows: 3,
          })}
        </div>

        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Fulfillment
          </h3>
          <div className="grid grid-cols-2 gap-4">
            {renderInput(
              "Processing Time (days)",
              vendor.processingTimeDays,
              (v) => onFieldChange("processingTimeDays", v === "" ? "" : Number(v)),
              { type: "number" }
            )}
            {renderInput(
              "Free Shipping Threshold",
              vendor.freeShippingThreshold,
              (v) => onFieldChange("freeShippingThreshold", v === "" ? "" : Number(v)),
              { type: "number", placeholder: "0.00" }
            )}
          </div>

          <div className="mb-4 flex items-center gap-2.5">
            <label
              className="block text-[0.78rem] font-semibold text-muted mb-0 cursor-pointer flex items-center gap-2"
            >
              <input
                type="checkbox"
                checked={vendor.acceptsReturns}
                onChange={(e) => onFieldChange("acceptsReturns", e.target.checked)}
                className="w-[18px] h-[18px] accent-brand"
              />
              Accepts Returns
            </label>
          </div>

          {vendor.acceptsReturns &&
            renderInput(
              "Return Window (days)",
              vendor.returnWindowDays,
              (v) => onFieldChange("returnWindowDays", v === "" ? "" : Number(v)),
              { type: "number" }
            )}
        </div>

        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Categories
          </h3>
          {renderInput("Primary Category", vendor.primaryCategory, (v) => onFieldChange("primaryCategory", v))}
          {renderInput("Specializations", vendor.specializations, (v) => onFieldChange("specializations", v), {
            placeholder: "Comma-separated values",
          })}
        </div>

        <div className="flex justify-end mt-2">
          <button
            type="submit"
            className="btn-brand px-6 py-2.5 rounded-md font-semibold"
            disabled={savingProfile}
          >
            {savingProfile ? "Saving..." : "Save Profile"}
          </button>
        </div>
      </form>
    </div>
  );
}
