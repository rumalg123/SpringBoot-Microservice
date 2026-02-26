"use client";

import { useEffect, useRef, useState } from "react";
import StatusBadge, { VENDOR_STATUS_COLORS } from "../../ui/StatusBadge";
import ConfirmModal from "../../ConfirmModal";
import { VendorProfile } from "./types";

/* ------------------------------------------------------------------ */
/*  Props                                                              */
/* ------------------------------------------------------------------ */

export interface VendorActionsTabProps {
  vendor: Pick<VendorProfile, "verificationStatus" | "acceptingOrders">;
  verificationDocUrl: string;
  onVerificationDocUrlChange: (v: string) => void;
  verificationNotes: string;
  onVerificationNotesChange: (v: string) => void;
  requestingVerification: boolean;
  onRequestVerification: () => void;
  togglingOrders: boolean;
  onToggleOrders: (action: "stop" | "resume") => void;
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function VendorActionsTab({
  vendor,
  verificationDocUrl,
  onVerificationDocUrlChange,
  verificationNotes,
  onVerificationNotesChange,
  requestingVerification,
  onRequestVerification,
  togglingOrders,
  onToggleOrders,
}: VendorActionsTabProps) {
  /* confirm modal for order toggle */
  const [confirmModal, setConfirmModal] = useState<{
    open: boolean;
    action: "stop" | "resume";
  }>({ open: false, action: "stop" });

  /* close the modal when the parent finishes toggling (togglingOrders: true -> false) */
  const prevToggling = useRef(togglingOrders);
  useEffect(() => {
    if (prevToggling.current && !togglingOrders) {
      setConfirmModal({ open: false, action: "stop" });
    }
    prevToggling.current = togglingOrders;
  }, [togglingOrders]);

  const isVerificationDisabled =
    vendor.verificationStatus === "PENDING_VERIFICATION" || vendor.verificationStatus === "VERIFIED";

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

  return (
    <div>
      {/* Verification section */}
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
        <h3 className="text-lg font-bold text-ink mb-5">
          Verification
        </h3>

        <div className="flex items-center gap-3 mb-5">
          <span className="text-[0.82rem] text-muted font-semibold">Current Status:</span>
          {vendor.verificationStatus ? (
            <StatusBadge value={vendor.verificationStatus} colorMap={VENDOR_STATUS_COLORS} />
          ) : (
            <span className="text-[0.82rem] text-muted">Not set</span>
          )}
        </div>

        {!isVerificationDisabled && (
          <>
            {renderInput("Document URL (optional)", verificationDocUrl, onVerificationDocUrlChange, {
              placeholder: "https://link-to-verification-document...",
            })}
            {renderTextarea("Notes (optional)", verificationNotes, onVerificationNotesChange, {
              rows: 3,
              placeholder: "Any additional information for the review team...",
            })}
          </>
        )}

        <button
          type="button"
          className="btn-brand px-6 py-2.5 rounded-md font-semibold"
          disabled={isVerificationDisabled || requestingVerification}
          onClick={onRequestVerification}
          style={{
            opacity: isVerificationDisabled ? 0.5 : 1,
            cursor: isVerificationDisabled ? "not-allowed" : "pointer",
          }}
        >
          {requestingVerification ? "Submitting..." : "Request Verification"}
        </button>

        {isVerificationDisabled && (
          <p className="text-[0.78rem] text-muted mt-2">
            {vendor.verificationStatus === "VERIFIED"
              ? "Your vendor account is already verified."
              : "A verification request is already pending."}
          </p>
        )}
      </div>

      {/* Order receiving section */}
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
        <h3 className="text-lg font-bold text-ink mb-5">
          Order Receiving
        </h3>

        <div className="flex items-center gap-3 mb-5">
          <span
            className="inline-block w-2.5 h-2.5 rounded-full"
            style={{ background: vendor.acceptingOrders ? "var(--success)" : "var(--warning-text)" }}
          />
          <span className="text-[0.9rem] font-semibold text-ink">
            {vendor.acceptingOrders ? "Accepting Orders" : "Orders Paused"}
          </span>
        </div>

        <button
          type="button"
          className="btn-brand px-6 py-2.5 rounded-md font-semibold"
          disabled={togglingOrders}
          onClick={() =>
            setConfirmModal({
              open: true,
              action: vendor.acceptingOrders ? "stop" : "resume",
            })
          }
          style={{
            background: vendor.acceptingOrders ? "var(--danger-soft)" : undefined,
            color: vendor.acceptingOrders ? "#f87171" : undefined,
            border: vendor.acceptingOrders ? "1px solid rgba(239,68,68,0.25)" : undefined,
          }}
        >
          {vendor.acceptingOrders ? "Stop Orders" : "Resume Orders"}
        </button>
      </div>

      {/* Confirm modal */}
      <ConfirmModal
        open={confirmModal.open}
        title={confirmModal.action === "stop" ? "Stop Accepting Orders" : "Resume Accepting Orders"}
        message={
          confirmModal.action === "stop"
            ? "Are you sure you want to stop accepting new orders? Existing orders will not be affected."
            : "Are you sure you want to resume accepting new orders?"
        }
        confirmLabel={confirmModal.action === "stop" ? "Stop Orders" : "Resume Orders"}
        danger={confirmModal.action === "stop"}
        loading={togglingOrders}
        onConfirm={() => onToggleOrders(confirmModal.action)}
        onCancel={() => setConfirmModal({ open: false, action: "stop" })}
      />
    </div>
  );
}
