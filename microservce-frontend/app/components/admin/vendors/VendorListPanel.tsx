"use client";

import type { Vendor, VendorDeletionEligibility } from "./types";
import StatusBadge, { VERIFICATION_COLORS } from "../../ui/StatusBadge";

type VendorListPanelProps = {
  vendors: Vendor[];
  deletedVendors: Vendor[];
  showDeleted: boolean;
  loading: boolean;
  loadingDeleted: boolean;
  deletedLoaded: boolean;
  selectedVendorId: string;
  vendorDeletionEligibilityById: Record<string, VendorDeletionEligibility>;
  eligibilityLoadingVendorId?: string | null;
  orderToggleVendorId?: string | null;
  onRefresh: () => void;
  onToggleShowDeleted: (next: boolean) => void;
  onEditVendor: (vendor: Vendor) => void;
  onSelectVendor: (vendor: Vendor) => void;
  onOpenLogs: (vendor: Vendor) => void;
  onDeleteVendor: (vendor: Vendor) => void | Promise<void>;
  onConfirmDeleteVendor: (vendor: Vendor) => void | Promise<void>;
  onRestoreVendor: (vendor: Vendor) => void;
  onStopOrders: (vendor: Vendor) => void;
  onResumeOrders: (vendor: Vendor) => void;
  verifyingVendorId?: string | null;
  rejectingVerificationId?: string | null;
  onVerifyVendor: (vendor: Vendor) => void;
  onRejectVerification: (vendor: Vendor) => void;
};

function VendorRow({
  vendor,
  selectedVendorId,
  vendorDeletionEligibilityById,
  eligibilityLoadingVendorId = null,
  orderToggleVendorId = null,
  verifyingVendorId = null,
  rejectingVerificationId = null,
  showDeleted,
  onEditVendor,
  onSelectVendor,
  onOpenLogs,
  onDeleteVendor,
  onConfirmDeleteVendor,
  onRestoreVendor,
  onStopOrders,
  onResumeOrders,
  onVerifyVendor,
  onRejectVerification,
}: {
  vendor: Vendor;
  selectedVendorId: string;
  vendorDeletionEligibilityById: Record<string, VendorDeletionEligibility>;
  eligibilityLoadingVendorId?: string | null;
  orderToggleVendorId?: string | null;
  verifyingVendorId?: string | null;
  rejectingVerificationId?: string | null;
  showDeleted: boolean;
  onEditVendor: (vendor: Vendor) => void;
  onSelectVendor: (vendor: Vendor) => void;
  onOpenLogs: (vendor: Vendor) => void;
  onDeleteVendor: (vendor: Vendor) => void | Promise<void>;
  onConfirmDeleteVendor: (vendor: Vendor) => void | Promise<void>;
  onRestoreVendor: (vendor: Vendor) => void;
  onStopOrders: (vendor: Vendor) => void;
  onResumeOrders: (vendor: Vendor) => void;
  onVerifyVendor: (vendor: Vendor) => void;
  onRejectVerification: (vendor: Vendor) => void;
}) {
  const eligibility = vendorDeletionEligibilityById[vendor.id];
  const eligibilityLoading = eligibilityLoadingVendorId === vendor.id;
  const orderToggleLoading = orderToggleVendorId === vendor.id;
  const deleteBlocked = !showDeleted && !!eligibility && !eligibility.eligible;
  const deleteRequested = Boolean(vendor.deletionRequestedAt);
  const storefrontVisible = !vendor.deleted && vendor.active && vendor.status === "ACTIVE" && vendor.acceptingOrders;
  const deleteBlockText = deleteBlocked
    ? (eligibility.blockingReasons || []).join(", ") || "Delete blocked"
    : "";

  return (
    <tr className="border-t border-[var(--line)]">
      <td className="px-3 py-2">
        <p className="font-semibold text-[var(--ink)]">{vendor.name}</p>
        <p className="text-xs text-[var(--muted)]">{vendor.slug}</p>
      </td>
      <td className="px-3 py-2 text-xs text-[var(--muted)]">
        <div>{vendor.contactEmail}</div>
        {vendor.contactPersonName && <div>{vendor.contactPersonName}</div>}
      </td>
      <td className="px-3 py-2 text-xs text-[var(--muted)]">
        <div>{vendor.status} | {vendor.active ? "active" : "inactive"}</div>
        <div>{vendor.acceptingOrders ? "accepting orders" : "orders stopped"} | {storefrontVisible ? "storefront visible" : "storefront hidden"}</div>
        {vendor.verificationStatus && (
          <div className="mt-1">
            <StatusBadge value={vendor.verificationStatus} colorMap={VERIFICATION_COLORS} />
          </div>
        )}
        {!showDeleted && deleteRequested && (
          <div className="mt-1 text-[11px]" style={{ color: "#fca5a5" }}>
            Delete requested{vendor.deletionRequestedAt ? ` (${new Date(vendor.deletionRequestedAt).toLocaleString()})` : ""}
          </div>
        )}
        {!showDeleted && eligibility && !eligibility.eligible && (
          <div className="mt-1 text-[11px]" style={{ color: "#fca5a5" }}>
            Delete blocked: {deleteBlockText}
            {eligibility.refundHoldUntil ? ` (until ${new Date(eligibility.refundHoldUntil).toLocaleString()})` : ""}
          </div>
        )}
        {!showDeleted && eligibilityLoading && !eligibility && (
          <div className="mt-1 text-[11px]">Checking deletion eligibility...</div>
        )}
      </td>
      <td className="px-3 py-2">
        <div className="flex flex-wrap gap-2">
          {showDeleted ? (
            <button
              type="button"
              onClick={() => onRestoreVendor(vendor)}
              className="rounded-md border border-emerald-500/40 px-2 py-1 text-xs"
              style={{ background: "rgba(16,185,129,0.10)", color: "#34d399" }}
            >
              Restore
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => onEditVendor(vendor)}
                className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
                style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
              >
                Edit
              </button>
              <button
                type="button"
                onClick={() => onSelectVendor(vendor)}
                title="Open vendor users, deletion eligibility and lifecycle logs"
                className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
                style={{
                  background: selectedVendorId === vendor.id ? "var(--brand-soft)" : "var(--surface-2)",
                  color: selectedVendorId === vendor.id ? "var(--brand)" : "var(--ink-light)",
                }}
              >
                {selectedVendorId === vendor.id ? "Managing" : "Manage"}
              </button>
              <button
                type="button"
                onClick={() => onOpenLogs(vendor)}
                className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
                style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                title="Open lifecycle logs for this vendor"
              >
                Logs
              </button>
              <button
                type="button"
                onClick={() => (vendor.acceptingOrders ? onStopOrders(vendor) : onResumeOrders(vendor))}
                className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                style={{
                  borderColor: vendor.acceptingOrders ? "rgba(251,191,36,0.35)" : "rgba(16,185,129,0.35)",
                  background: vendor.acceptingOrders ? "rgba(251,191,36,0.08)" : "rgba(16,185,129,0.08)",
                  color: vendor.acceptingOrders ? "#fde68a" : "#86efac",
                }}
                disabled={orderToggleLoading}
                title={vendor.acceptingOrders ? "Stop new orders and hide products publicly" : "Resume orders (requires active + ACTIVE status)"}
              >
                {orderToggleLoading ? "Saving..." : vendor.acceptingOrders ? "Stop Orders" : "Resume Orders"}
              </button>
              {vendor.verificationStatus === "PENDING_VERIFICATION" && (
                <>
                  <button
                    type="button"
                    onClick={() => onVerifyVendor(vendor)}
                    className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                    style={{
                      borderColor: "rgba(34,197,94,0.4)",
                      background: "rgba(34,197,94,0.10)",
                      color: "#86efac",
                    }}
                    disabled={verifyingVendorId === vendor.id}
                    title="Verify this vendor"
                  >
                    {verifyingVendorId === vendor.id ? "Verifying..." : "Verify"}
                  </button>
                  <button
                    type="button"
                    onClick={() => onRejectVerification(vendor)}
                    className="rounded-md border px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                    style={{
                      borderColor: "rgba(239,68,68,0.4)",
                      background: "rgba(239,68,68,0.08)",
                      color: "#fca5a5",
                    }}
                    disabled={rejectingVerificationId === vendor.id}
                    title="Reject verification for this vendor"
                  >
                    {rejectingVerificationId === vendor.id ? "Rejecting..." : "Reject Verification"}
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={() => void (deleteRequested ? onConfirmDeleteVendor(vendor) : onDeleteVendor(vendor))}
                className="rounded-md border border-red-500/40 px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                style={{ background: deleteRequested ? "rgba(220,38,38,0.14)" : "rgba(239,68,68,0.08)", color: "#fca5a5" }}
                disabled={deleteBlocked || eligibilityLoading}
                title={
                  deleteBlocked
                    ? deleteBlockText
                    : eligibilityLoading
                      ? "Checking deletion eligibility..."
                      : deleteRequested
                        ? "Confirm delete vendor (second step)"
                        : "Create delete request (first step)"
                }
              >
                {eligibilityLoading ? "Checking..." : deleteRequested ? "Confirm Delete" : "Request Delete"}
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  );
}

export default function VendorListPanel({
  vendors,
  deletedVendors,
  showDeleted,
  loading,
  loadingDeleted,
  deletedLoaded,
  selectedVendorId,
  vendorDeletionEligibilityById,
  eligibilityLoadingVendorId = null,
  orderToggleVendorId = null,
  onRefresh,
  onToggleShowDeleted,
  onEditVendor,
  onSelectVendor,
  onOpenLogs,
  onDeleteVendor,
  onConfirmDeleteVendor,
  onRestoreVendor,
  onStopOrders,
  onResumeOrders,
  verifyingVendorId = null,
  rejectingVerificationId = null,
  onVerifyVendor,
  onRejectVerification,
}: VendorListPanelProps) {
  const rows = showDeleted ? deletedVendors : vendors;
  const isLoadingCurrent = showDeleted ? loadingDeleted && !deletedLoaded : loading;
  const emptyMessage = showDeleted ? "No deleted vendors." : "No vendors found.";

  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl text-[var(--ink)]">Vendors</h2>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onToggleShowDeleted(!showDeleted)}
            disabled={loading || loadingDeleted}
            className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
            style={{
              background: showDeleted ? "var(--brand-soft)" : "var(--surface-2)",
              color: showDeleted ? "var(--brand)" : "var(--ink-light)",
            }}
          >
            {showDeleted ? "Show Active" : "Show Deleted"}
          </button>
          <button
            type="button"
            onClick={onRefresh}
            disabled={loading || (showDeleted && loadingDeleted)}
            className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
            style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
          >
            {loading || (showDeleted && loadingDeleted) ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      <div className="overflow-hidden rounded-2xl border border-[var(--line)]" style={{ background: "var(--surface)" }}>
        <table className="w-full text-left text-sm">
          <thead style={{ background: "var(--surface-2)", color: "var(--ink)" }}>
            <tr>
              <th className="px-3 py-2">Vendor</th>
              <th className="px-3 py-2">Contact</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoadingCurrent && (
              <tr>
                <td colSpan={4} className="px-3 py-5 text-center text-sm text-[var(--muted)]">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoadingCurrent && rows.length === 0 && (
              <tr>
                <td colSpan={4} className="px-3 py-5 text-center text-sm text-[var(--muted)]">
                  {emptyMessage}
                </td>
              </tr>
            )}
            {!isLoadingCurrent &&
              rows.map((vendor) => (
                <VendorRow
                  key={vendor.id}
                  vendor={vendor}
                  selectedVendorId={selectedVendorId}
                  vendorDeletionEligibilityById={vendorDeletionEligibilityById}
                  eligibilityLoadingVendorId={eligibilityLoadingVendorId}
                  orderToggleVendorId={orderToggleVendorId}
                  showDeleted={showDeleted}
                  onEditVendor={onEditVendor}
                  onSelectVendor={onSelectVendor}
                  onOpenLogs={onOpenLogs}
                  onDeleteVendor={onDeleteVendor}
                  onConfirmDeleteVendor={onConfirmDeleteVendor}
                  onRestoreVendor={onRestoreVendor}
                  onStopOrders={onStopOrders}
                  onResumeOrders={onResumeOrders}
                  verifyingVendorId={verifyingVendorId}
                  rejectingVerificationId={rejectingVerificationId}
                  onVerifyVendor={onVerifyVendor}
                  onRejectVerification={onRejectVerification}
                />
              ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
