"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import AdminPageShell from "../../components/ui/AdminPageShell";
import TableSkeleton from "../../components/ui/TableSkeleton";

type Payment = {
  id: string;
  orderId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: string;
  paymentMethod: string;
  cardNoMasked: string | null;
  payherePaymentId: string | null;
  paidAt: string | null;
  createdAt: string;
  customerName?: string;
  customerEmail?: string;
};

type RefundRequest = {
  id: string;
  paymentId: string;
  orderId: string;
  vendorOrderId: string | null;
  vendorId: string | null;
  customerId: string;
  refundAmount: number;
  currency: string;
  customerReason: string | null;
  vendorResponseNote: string | null;
  adminNote: string | null;
  status: string;
  vendorResponseDeadline: string | null;
  createdAt: string;
  customerName?: string;
  customerEmail?: string;
};

type Payout = {
  id: string;
  vendorId: string;
  payoutAmount: number;
  platformFee: number;
  currency: string;
  status: string;
  referenceNumber: string | null;
  adminNote: string | null;
  approvedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  vendorName?: string;
};

type Paged<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
};

type Tab = "payments" | "refunds" | "payouts";

const PAYMENT_STATUS_OPTIONS = ["all", "PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED"] as const;
const REFUND_STATUS_OPTIONS = [
  "all",
  "REQUESTED",
  "VENDOR_APPROVED",
  "VENDOR_REJECTED",
  "ESCALATED_TO_ADMIN",
  "ADMIN_REJECTED",
  "REFUND_PROCESSING",
  "REFUND_COMPLETED",
  "REFUND_FAILED",
] as const;
const PAYOUT_STATUS_OPTIONS = ["all", "PENDING", "APPROVED", "COMPLETED", "CANCELLED"] as const;

function PaginationBar({
  page,
  totalPages,
  setPage,
}: {
  page: number;
  totalPages: number;
  setPage: (fn: (page: number) => number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex justify-center gap-2 mt-5">
      <button
        type="button"
        onClick={() => setPage((current) => Math.max(0, current - 1))}
        disabled={page === 0}
        className={`py-2 px-4 rounded-md border border-line-bright text-sm font-semibold ${page === 0 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
      >
        {"< Prev"}
      </button>
      <span className="flex items-center text-sm text-muted">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
        disabled={page >= totalPages - 1}
        className={`py-2 px-4 rounded-md border border-line-bright text-sm font-semibold ${page >= totalPages - 1 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
      >
        {"Next >"}
      </button>
    </div>
  );
}

function statusBadge(status: string) {
  const normalized = status.toUpperCase();
  const successStatuses = ["COMPLETED", "PAID", "REFUND_COMPLETED", "VENDOR_APPROVED"];
  const dangerStatuses = ["FAILED", "CANCELLED", "ADMIN_REJECTED", "VENDOR_REJECTED", "REFUND_FAILED"];
  const success = successStatuses.includes(normalized);
  const danger = dangerStatuses.includes(normalized);
  const className = success
    ? "text-success bg-success-soft"
    : danger
      ? "text-danger bg-danger-soft"
      : "text-warning-text bg-warning-soft";
  return (
    <span className={`text-[0.68rem] font-bold py-0.5 px-2 rounded-sm ${className}`}>
      {normalized.replace(/_/g, " ")}
    </span>
  );
}

export default function AdminPaymentsPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();
  const canManagePayments = session.isSuperAdmin || session.canManageAdminPayments;
  const ready = session.status === "ready" && !!session.apiClient && canManagePayments;

  const [tab, setTab] = useState<Tab>("payments");
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentStatusFilter, setPaymentStatusFilter] = useState<(typeof PAYMENT_STATUS_OPTIONS)[number]>("all");
  const [refundPage, setRefundPage] = useState(0);
  const [refundStatusFilter, setRefundStatusFilter] = useState<(typeof REFUND_STATUS_OPTIONS)[number]>("all");
  const [payoutPage, setPayoutPage] = useState(0);
  const [payoutStatusFilter, setPayoutStatusFilter] = useState<(typeof PAYOUT_STATUS_OPTIONS)[number]>("all");
  const [finalizingRefundId, setFinalizingRefundId] = useState<string | null>(null);
  const [finalizeNote, setFinalizeNote] = useState("");
  const [finalizeApproved, setFinalizeApproved] = useState(true);

  const { data: paymentsData, isLoading: paymentsLoading } = useQuery({
    queryKey: ["admin-payments", paymentPage, paymentStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(paymentPage), size: "20" });
      if (paymentStatusFilter !== "all") params.set("status", paymentStatusFilter);
      const res = await session.apiClient!.get<Paged<Payment>>(`/admin/payments?${params.toString()}`);
      return res.data;
    },
    enabled: ready,
  });

  const { data: refundsData, isLoading: refundsLoading } = useQuery({
    queryKey: ["admin-payments-refunds", refundPage, refundStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(refundPage), size: "20" });
      if (refundStatusFilter !== "all") params.set("status", refundStatusFilter);
      const res = await session.apiClient!.get<Paged<RefundRequest>>(`/admin/payments/refunds?${params.toString()}`);
      return res.data;
    },
    enabled: ready,
  });

  const { data: payoutsData, isLoading: payoutsLoading } = useQuery({
    queryKey: ["admin-payments-payouts", payoutPage, payoutStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(payoutPage), size: "20" });
      if (payoutStatusFilter !== "all") params.set("status", payoutStatusFilter);
      const res = await session.apiClient!.get<Paged<Payout>>(`/admin/payments/payouts?${params.toString()}`);
      return res.data;
    },
    enabled: ready,
  });

  const finalizeMutation = useMutation({
    mutationFn: async (payload: { refundId: string; approved: boolean; note: string }) => {
      await session.apiClient!.post(`/admin/payments/refunds/${payload.refundId}/finalize`, {
        approved: payload.approved,
        note: payload.note.trim() || null,
      });
    },
    onSuccess: () => {
      toast.success(finalizeApproved ? "Refund approved" : "Refund rejected");
      setFinalizingRefundId(null);
      setFinalizeNote("");
      void queryClient.invalidateQueries({ queryKey: ["admin-payments-refunds"] });
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Failed to finalize refund");
    },
  });

  const approvePayoutMutation = useMutation({
    mutationFn: async (payoutId: string) => {
      await session.apiClient!.post(`/admin/payments/payouts/${payoutId}/approve`);
    },
    onSuccess: () => {
      toast.success("Payout approved");
      void queryClient.invalidateQueries({ queryKey: ["admin-payments-payouts"] });
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Failed to approve payout");
    },
  });

  if (session.status === "loading" || session.status === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }

  if (!canManagePayments) {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Unauthorized.</p></div>;
  }

  const payments = paymentsData?.content ?? [];
  const refunds = refundsData?.content ?? [];
  const payouts = payoutsData?.content ?? [];
  const actionableRefundStatuses = new Set(["REQUESTED", "VENDOR_APPROVED", "VENDOR_REJECTED", "ESCALATED_TO_ADMIN", "REFUND_FAILED"]);

  const tabClasses = (value: Tab) =>
    `py-2.5 px-5 rounded-t-md border border-line-bright text-base font-bold cursor-pointer -mb-px ${tab === value ? "bg-[rgba(17,17,40,0.7)] text-white border-b-transparent" : "bg-transparent text-muted"}`;

  return (
    <AdminPageShell
      title="Payment Management"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Payments" }]}
    >
      <div className="flex gap-1">
        <button type="button" onClick={() => setTab("payments")} className={tabClasses("payments")}>
          Payments ({paymentsData?.totalElements ?? 0})
        </button>
        <button type="button" onClick={() => setTab("refunds")} className={tabClasses("refunds")}>
          Refunds ({refundsData?.totalElements ?? 0})
        </button>
        <button type="button" onClick={() => setTab("payouts")} className={tabClasses("payouts")}>
          Payouts ({payoutsData?.totalElements ?? 0})
        </button>
      </div>

      <div className="bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-line-bright rounded-[0_16px_16px_16px] p-6">
        {tab === "payments" && (
          <>
            <div className="flex gap-3 mb-5">
              <label className="flex items-center gap-1.5 text-sm text-muted">
                Status:
                <select
                  value={paymentStatusFilter}
                  onChange={(event) => {
                    setPaymentStatusFilter(event.target.value as (typeof PAYMENT_STATUS_OPTIONS)[number]);
                    setPaymentPage(0);
                  }}
                  className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm"
                >
                  {PAYMENT_STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "All" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {paymentsLoading ? (
              <TableSkeleton rows={4} cols={6} />
            ) : payments.length === 0 ? (
              <p className="text-center text-muted py-8">No payments found.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-[0.82rem]">
                  <thead>
                    <tr className="border-b border-line-bright">
                      {["Customer", "Amount", "Method", "Status", "Paid At", "Created"].map((header) => (
                        <th key={header} className="py-2 px-3 text-left text-muted font-semibold text-[0.72rem] uppercase">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {payments.map((payment) => (
                      <tr key={payment.id} className="border-b border-line">
                        <td className="py-2.5 px-3 text-ink-light">{payment.customerName || payment.customerEmail || "\u2014"}</td>
                        <td className="py-2.5 px-3 text-white font-semibold">{money(payment.amount)}</td>
                        <td className="py-2.5 px-3 text-ink-light">{payment.paymentMethod || "\u2014"}</td>
                        <td className="py-2.5 px-3">{statusBadge(payment.status)}</td>
                        <td className="py-2.5 px-3 text-muted">{payment.paidAt ? new Date(payment.paidAt).toLocaleDateString() : "\u2014"}</td>
                        <td className="py-2.5 px-3 text-muted">{new Date(payment.createdAt).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <PaginationBar page={paymentPage} totalPages={paymentsData?.totalPages ?? 0} setPage={setPaymentPage} />
          </>
        )}

        {tab === "refunds" && (
          <>
            <div className="flex gap-3 mb-5">
              <label className="flex items-center gap-1.5 text-sm text-muted">
                Status:
                <select
                  value={refundStatusFilter}
                  onChange={(event) => {
                    setRefundStatusFilter(event.target.value as (typeof REFUND_STATUS_OPTIONS)[number]);
                    setRefundPage(0);
                  }}
                  className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm"
                >
                  {REFUND_STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "All" : option.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {refundsLoading ? (
              <TableSkeleton rows={4} cols={6} />
            ) : refunds.length === 0 ? (
              <p className="text-center text-muted py-8">No refunds found.</p>
            ) : (
              <div className="flex flex-col gap-3">
                {refunds.map((refund) => {
                  const reviewable = actionableRefundStatuses.has(refund.status);
                  const expanded = finalizingRefundId === refund.id;
                  return (
                    <div key={refund.id} className="border border-line-bright rounded-[12px] py-4 px-5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1.5">
                            {statusBadge(refund.status)}
                            <span className="text-[0.78rem] text-white font-semibold">{money(refund.refundAmount)}</span>
                          </div>
                          {refund.customerReason && (
                            <p className="mb-1 text-[0.82rem] text-ink-light">Reason: {refund.customerReason}</p>
                          )}
                          <p className="text-[0.72rem] text-muted-2">
                            {refund.customerName || refund.customerEmail || "Customer"} · {new Date(refund.createdAt).toLocaleDateString()}
                          </p>
                          <p className="mt-1 text-[0.72rem] text-muted-2">
                            Order {refund.orderId} · Vendor order {refund.vendorOrderId || "\u2014"}
                          </p>
                          {refund.vendorResponseDeadline && (
                            <p className="mt-1 text-[0.72rem] text-muted-2">
                              Vendor response deadline: {new Date(refund.vendorResponseDeadline).toLocaleString()}
                            </p>
                          )}
                          {refund.vendorResponseNote && (
                            <p className="mt-1 text-[0.78rem] text-warning-text">Vendor: {refund.vendorResponseNote}</p>
                          )}
                          {refund.adminNote && (
                            <p className="mt-1 text-[0.78rem] text-brand italic">Admin: {refund.adminNote}</p>
                          )}
                        </div>

                        {reviewable && !expanded && (
                          <button
                            type="button"
                            onClick={() => {
                              setFinalizingRefundId(refund.id);
                              setFinalizeNote("");
                              setFinalizeApproved(true);
                            }}
                            className="py-1.5 px-3 rounded-[8px] text-[0.72rem] font-bold border border-line-bright bg-brand-soft text-brand cursor-pointer whitespace-nowrap"
                          >
                            Review
                          </button>
                        )}
                      </div>

                      {expanded && (
                        <div className="mt-3 p-3 rounded-md border border-line-bright bg-[rgba(0,212,255,0.03)]">
                          <div className="flex gap-3 mb-2 items-center">
                            <label className="flex items-center gap-1 text-sm cursor-pointer">
                              <input type="radio" checked={finalizeApproved} onChange={() => setFinalizeApproved(true)} />
                              <span className="text-success">Approve refund</span>
                            </label>
                            <label className="flex items-center gap-1 text-sm cursor-pointer">
                              <input type="radio" checked={!finalizeApproved} onChange={() => setFinalizeApproved(false)} />
                              <span className="text-danger">Reject refund</span>
                            </label>
                          </div>
                          <textarea
                            value={finalizeNote}
                            onChange={(event) => setFinalizeNote(event.target.value)}
                            maxLength={1000}
                            rows={2}
                            placeholder="Admin note (optional)..."
                            className="w-full py-2 px-3 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-[0.82rem] resize-y outline-none"
                          />
                          <div className="flex gap-2 mt-2 justify-end">
                            <button
                              type="button"
                              onClick={() => setFinalizingRefundId(null)}
                              className="py-1.5 px-3.5 rounded-[8px] border border-line-bright bg-transparent text-muted text-sm cursor-pointer"
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              onClick={() => finalizeMutation.mutate({ refundId: refund.id, approved: finalizeApproved, note: finalizeNote })}
                              disabled={finalizeMutation.isPending || (!finalizeApproved && !finalizeNote.trim())}
                              className={`py-1.5 px-3.5 rounded-[8px] border-none bg-[var(--gradient-brand)] text-white text-sm font-bold ${finalizeMutation.isPending ? "cursor-not-allowed opacity-60" : "cursor-pointer opacity-100"}`}
                            >
                              {finalizeMutation.isPending ? "Saving..." : "Confirm"}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            <PaginationBar page={refundPage} totalPages={refundsData?.totalPages ?? 0} setPage={setRefundPage} />
          </>
        )}

        {tab === "payouts" && (
          <>
            <div className="flex gap-3 mb-5">
              <label className="flex items-center gap-1.5 text-sm text-muted">
                Status:
                <select
                  value={payoutStatusFilter}
                  onChange={(event) => {
                    setPayoutStatusFilter(event.target.value as (typeof PAYOUT_STATUS_OPTIONS)[number]);
                    setPayoutPage(0);
                  }}
                  className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm"
                >
                  {PAYOUT_STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option === "all" ? "All" : option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {payoutsLoading ? (
              <TableSkeleton rows={4} cols={6} />
            ) : payouts.length === 0 ? (
              <p className="text-center text-muted py-8">No payouts found.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-[0.82rem]">
                  <thead>
                    <tr className="border-b border-line-bright">
                      {["Vendor", "Amount", "Fee", "Status", "Reference", "Created", "Actions"].map((header) => (
                        <th key={header} className="py-2 px-3 text-left text-muted font-semibold text-[0.72rem] uppercase">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {payouts.map((payout) => (
                      <tr key={payout.id} className="border-b border-line">
                        <td className="py-2.5 px-3 text-ink-light">{payout.vendorName || "\u2014"}</td>
                        <td className="py-2.5 px-3 text-white font-semibold">{money(payout.payoutAmount)}</td>
                        <td className="py-2.5 px-3 text-muted">{money(payout.platformFee)}</td>
                        <td className="py-2.5 px-3">{statusBadge(payout.status)}</td>
                        <td className="py-2.5 px-3 text-ink-light">{payout.referenceNumber || "\u2014"}</td>
                        <td className="py-2.5 px-3 text-muted">{new Date(payout.createdAt).toLocaleDateString()}</td>
                        <td className="py-2.5 px-3">
                          {payout.status === "PENDING" && (
                            <button
                              type="button"
                              onClick={() => approvePayoutMutation.mutate(payout.id)}
                              disabled={approvePayoutMutation.isPending}
                              className={`py-1 px-2.5 rounded-sm text-[0.72rem] font-bold border border-success-glow bg-success-soft text-success ${approvePayoutMutation.isPending ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
                            >
                              {approvePayoutMutation.isPending ? "..." : "Approve"}
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <PaginationBar page={payoutPage} totalPages={payoutsData?.totalPages ?? 0} setPage={setPayoutPage} />
          </>
        )}
      </div>
    </AdminPageShell>
  );
}
