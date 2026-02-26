"use client";

import { PayoutConfig } from "./types";

/* ------------------------------------------------------------------ */
/*  Props                                                              */
/* ------------------------------------------------------------------ */

export interface VendorPayoutTabProps {
  payoutConfig: PayoutConfig;
  loadingPayout: boolean;
  savingPayout: boolean;
  onFieldChange: (key: keyof PayoutConfig, value: string | number | "") => void;
  onSave: () => void;
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function VendorPayoutTab({
  payoutConfig,
  loadingPayout,
  savingPayout,
  onFieldChange,
  onSave,
}: VendorPayoutTabProps) {
  /* ---- local render helper ---- */

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

  /* ---- render ---- */

  if (loadingPayout) {
    return (
      <div className="text-center p-12 text-muted">Loading payout config...</div>
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
            Payout Settings
          </h3>
          <div className="grid grid-cols-3 gap-4">
            {renderInput("Payout Currency", payoutConfig.payoutCurrency, (v) => onFieldChange("payoutCurrency", v), {
              maxLength: 3,
              placeholder: "USD",
            })}
            <div className="mb-4">
              <label className="block text-[0.78rem] font-semibold text-muted mb-1">Payout Schedule</label>
              <select
                value={payoutConfig.payoutSchedule}
                onChange={(e) => onFieldChange("payoutSchedule", e.target.value)}
                className="form-input w-full"
              >
                <option value="WEEKLY">Weekly</option>
                <option value="BIWEEKLY">Biweekly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </div>
            {renderInput(
              "Payout Minimum",
              payoutConfig.payoutMinimum,
              (v) => onFieldChange("payoutMinimum", v === "" ? "" : Number(v)),
              { type: "number", placeholder: "0.00" }
            )}
          </div>
        </div>

        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h3 className="text-lg font-bold text-ink mb-5">
            Bank Details
          </h3>
          <div className="grid grid-cols-2 gap-4">
            {renderInput("Bank Account Holder", payoutConfig.bankAccountHolder, (v) =>
              onFieldChange("bankAccountHolder", v)
            )}
            {renderInput("Bank Name", payoutConfig.bankName, (v) => onFieldChange("bankName", v))}
          </div>
          <div className="grid grid-cols-2 gap-4">
            {renderInput("Bank Routing Code", payoutConfig.bankRoutingCode, (v) =>
              onFieldChange("bankRoutingCode", v)
            )}
            {renderInput("Bank Account Number", payoutConfig.bankAccountNumberMasked, (v) =>
              onFieldChange("bankAccountNumberMasked", v)
            )}
          </div>
          {renderInput("Tax ID", payoutConfig.taxId, (v) => onFieldChange("taxId", v))}
        </div>

        <div className="flex justify-end mt-2">
          <button
            type="submit"
            className="btn-brand px-6 py-2.5 rounded-md font-semibold"
            disabled={savingPayout}
          >
            {savingPayout ? "Saving..." : "Save Payout Config"}
          </button>
        </div>
      </form>
    </div>
  );
}
