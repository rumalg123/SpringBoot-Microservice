"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import VendorPageShell from "../../components/ui/VendorPageShell";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import { useRespondToVendorRefund, useVendorRefunds } from "../../../lib/hooks/queries/useRefunds";

const FILTER_OPTIONS = [
  "all",
  "REQUESTED",
  "VENDOR_APPROVED",
  "VENDOR_REJECTED",
  "ESCALATED_TO_ADMIN",
  "REFUND_PROCESSING",
  "REFUND_COMPLETED",
  "REFUND_FAILED",
  "ADMIN_REJECTED",
] as const;

function statusBadge(status: string) {
  const normalized = status.toUpperCase();
  const success = ["REFUND_COMPLETED", "VENDOR_APPROVED"].includes(normalized);
  const danger = ["VENDOR_REJECTED", "REFUND_FAILED", "ADMIN_REJECTED"].includes(normalized);
  const warning = ["REQUESTED", "ESCALATED_TO_ADMIN", "REFUND_PROCESSING"].includes(normalized);

  let className = "text-brand bg-brand-soft";
  if (success) className = "text-success bg-success-soft";
  if (danger) className = "text-danger bg-danger-soft";
  if (warning) className = "text-warning-text bg-warning-soft";

  return (
    <span className={`text-[0.68rem] font-bold px-2 py-0.5 rounded-sm ${className}`}>
      {normalized.replace(/_/g, " ")}
    </span>
  );
}

export default function VendorRefundsPage() {
  const session = useAuthSession();
  const canReadRefunds = session.isVendorAdmin || session.canViewVendorFinance;
  const canManageRefunds = session.isVendorAdmin || session.canManageVendorFinance;

  const [statusFilter, setStatusFilter] = useState<(typeof FILTER_OPTIONS)[number]>("all");
  const [expandedRefundId, setExpandedRefundId] = useState<string | null>(null);
  const [decisionNote, setDecisionNote] = useState("");
  const [decisionApproved, setDecisionApproved] = useState(true);

  const { data: refunds = [], isLoading } = useVendorRefunds(session.apiClient, {
    status: statusFilter === "all" ? null : statusFilter,
  });
  const respondMutation = useRespondToVendorRefund(session.apiClient);

  const handleSubmitDecision = (refundId: string) => {
    respondMutation.mutate(
      {
        refundId,
        approved: decisionApproved,
        note: decisionNote,
      },
      {
        onSuccess: () => {
          toast.success(decisionApproved ? "Refund request approved" : "Refund request rejected");
          setExpandedRefundId(null);
          setDecisionNote("");
          setDecisionApproved(true);
        },
        onError: (error) => {
          toast.error(error instanceof Error ? error.message : "Failed to update refund request");
        },
      }
    );
  };

  if (session.status === "loading" || session.status === "idle") {
    return (
      <VendorPageShell title="Refund Requests" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Refunds" }]}>
        <p className="text-muted text-center py-10">Loading...</p>
      </VendorPageShell>
    );
  }

  if (!canReadRefunds) {
    return (
      <VendorPageShell title="Refund Requests" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Refunds" }]}>
        <p className="text-muted text-center py-10">Unauthorized.</p>
      </VendorPageShell>
    );
  }

  return (
    <VendorPageShell
      title="Refund Requests"
      breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Refunds" }]}
      actions={
        <>
          <span className="text-sm text-muted flex items-center">{refunds.length} request{refunds.length === 1 ? "" : "s"}</span>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value as (typeof FILTER_OPTIONS)[number])}
            className="filter-select"
          >
            {FILTER_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option === "all" ? "All Statuses" : option.replace(/_/g, " ")}
              </option>
            ))}
          </select>
        </>
      }
    >
      {isLoading ? (
        <p className="text-muted text-center py-10">Loading refund requests...</p>
      ) : refunds.length === 0 ? (
        <div className="text-center px-5 py-[60px] rounded-[14px] bg-[var(--card)] border border-line-bright">
          <p className="text-muted text-[0.9rem]">No refund requests found.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {refunds.map((refund) => {
            const actionable = canManageRefunds && refund.status === "REQUESTED";
            const expanded = expandedRefundId === refund.id;
            return (
              <article key={refund.id} className="rounded-[14px] border border-line-bright bg-[var(--card)] px-5 py-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2 mb-2">
                      {statusBadge(refund.status)}
                      <span className="text-[0.78rem] font-semibold text-white">{money(refund.refundAmount)}</span>
                    </div>
                    <p className="m-0 text-[0.82rem] text-ink-light">Vendor order {refund.vendorOrderId}</p>
                    <p className="mt-1 mb-0 text-[0.74rem] text-muted">Customer reason: {refund.customerReason || "No reason provided"}</p>
                    {refund.vendorResponseNote && (
                      <p className="mt-2 mb-0 text-[0.74rem] text-warning-text">Response note: {refund.vendorResponseNote}</p>
                    )}
                    {refund.adminNote && (
                      <p className="mt-2 mb-0 text-[0.74rem] text-brand">Platform note: {refund.adminNote}</p>
                    )}
                    <p className="mt-2 mb-0 text-[0.72rem] text-muted">
                      Requested {new Date(refund.createdAt).toLocaleString()}
                      {refund.vendorResponseDeadline ? ` • respond by ${new Date(refund.vendorResponseDeadline).toLocaleString()}` : ""}
                    </p>
                  </div>

                  {actionable && (
                    <button
                      type="button"
                      onClick={() => {
                        setExpandedRefundId(expanded ? null : refund.id);
                        setDecisionNote("");
                        setDecisionApproved(true);
                      }}
                      className="px-[14px] py-[7px] rounded-[9px] border border-line-bright bg-brand-soft text-brand text-[0.75rem] font-bold cursor-pointer"
                    >
                      {expanded ? "Hide Review" : "Review Request"}
                    </button>
                  )}
                </div>

                {expanded && actionable && (
                  <div className="mt-4 rounded-[10px] border border-line-bright bg-[rgba(255,255,255,0.03)] px-4 py-4">
                    <div className="flex gap-4 flex-wrap">
                      <label className="flex items-center gap-2 text-sm cursor-pointer">
                        <input type="radio" checked={decisionApproved} onChange={() => setDecisionApproved(true)} />
                        <span className="text-success">Approve refund</span>
                      </label>
                      <label className="flex items-center gap-2 text-sm cursor-pointer">
                        <input type="radio" checked={!decisionApproved} onChange={() => setDecisionApproved(false)} />
                        <span className="text-danger">Reject request</span>
                      </label>
                    </div>
                    <textarea
                      rows={3}
                      maxLength={1000}
                      value={decisionNote}
                      onChange={(event) => setDecisionNote(event.target.value)}
                      placeholder="Add a response note for the customer and platform review team"
                      className="form-input resize-y mt-3"
                    />
                    <div className="mt-3 flex justify-end">
                      <button
                        type="button"
                        disabled={respondMutation.isPending || (!decisionApproved && !decisionNote.trim())}
                        onClick={() => handleSubmitDecision(refund.id)}
                        className="rounded-md bg-[var(--gradient-brand)] px-[18px] py-[9px] text-sm font-bold text-white shadow-[0_0_14px_var(--line-bright)] disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {respondMutation.isPending ? "Saving..." : "Submit Decision"}
                      </button>
                    </div>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </VendorPageShell>
  );
}
