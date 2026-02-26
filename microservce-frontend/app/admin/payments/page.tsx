"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import AdminPageShell from "../../components/ui/AdminPageShell";
import TableSkeleton from "../../components/ui/TableSkeleton";

type Payment = {
  id: string; orderId: string; customerId: string; amount: number; currency: string;
  status: string; paymentMethod: string; cardNoMasked: string | null;
  payherePaymentId: string | null; paidAt: string | null; createdAt: string;
  customerName?: string; customerEmail?: string;
};
type RefundRequest = {
  id: string; paymentId: string; orderId: string; vendorOrderId: string | null; vendorId: string | null;
  customerId: string; refundAmount: number; currency: string; customerReason: string | null;
  vendorResponseNote: string | null; adminNote: string | null; status: string;
  createdAt: string; customerName?: string; customerEmail?: string;
};
type Payout = {
  id: string; vendorId: string; payoutAmount: number; platformFee: number; currency: string;
  status: string; referenceNumber: string | null; adminNote: string | null;
  approvedAt: string | null; completedAt: string | null; createdAt: string;
  vendorName?: string;
};
type Paged<T> = { content: T[]; totalPages: number; totalElements: number };
type Tab = "payments" | "refunds" | "payouts";

export default function AdminPaymentsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, apiClient } = session;
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<Tab>("payments");

  // Pagination & filters
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentStatusFilter, setPaymentStatusFilter] = useState("all");
  const [refundPage, setRefundPage] = useState(0);
  const [refundStatusFilter, setRefundStatusFilter] = useState("all");
  const [payoutPage, setPayoutPage] = useState(0);
  const [payoutStatusFilter, setPayoutStatusFilter] = useState("all");

  // Refund finalize UI state
  const [finalizingId, setFinalizingId] = useState<string | null>(null);
  const [finalizeNote, setFinalizeNote] = useState("");
  const [finalizeApproved, setFinalizeApproved] = useState(true);

  const ready = sessionStatus === "ready" && !!apiClient;

  // --- Payments query ---
  const { data: paymentsData, isLoading: paymentLoading } = useQuery({
    queryKey: ["admin-payments", paymentPage, paymentStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(paymentPage), size: "20" });
      if (paymentStatusFilter !== "all") params.set("status", paymentStatusFilter);
      const res = await apiClient!.get(`/admin/payments?${params}`);
      return res.data as Paged<Payment>;
    },
    enabled: ready,
  });
  const payments = paymentsData?.content || [];
  const paymentTotalPages = paymentsData?.totalPages || 0;
  const paymentTotal = paymentsData?.totalElements || 0;

  // --- Refunds query ---
  const { data: refundsData, isLoading: refundLoading } = useQuery({
    queryKey: ["admin-payments-refunds", refundPage, refundStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(refundPage), size: "20" });
      if (refundStatusFilter !== "all") params.set("status", refundStatusFilter);
      const res = await apiClient!.get(`/admin/payments/refunds?${params}`);
      return res.data as Paged<RefundRequest>;
    },
    enabled: ready,
  });
  const refunds = refundsData?.content || [];
  const refundTotalPages = refundsData?.totalPages || 0;
  const refundTotal = refundsData?.totalElements || 0;

  // --- Payouts query ---
  const { data: payoutsData, isLoading: payoutLoading } = useQuery({
    queryKey: ["admin-payments-payouts", payoutPage, payoutStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(payoutPage), size: "20" });
      if (payoutStatusFilter !== "all") params.set("status", payoutStatusFilter);
      const res = await apiClient!.get(`/admin/payments/payouts?${params}`);
      return res.data as Paged<Payout>;
    },
    enabled: ready,
  });
  const payouts = payoutsData?.content || [];
  const payoutTotalPages = payoutsData?.totalPages || 0;
  const payoutTotal = payoutsData?.totalElements || 0;

  // --- Finalize refund mutation ---
  const finalizeMutation = useMutation({
    mutationFn: async ({ refundId, approved, note }: { refundId: string; approved: boolean; note: string }) => {
      await apiClient!.post(`/admin/payments/refunds/${refundId}/finalize`, { approved, note: note.trim() || null });
      return { approved };
    },
    onSuccess: (result) => {
      setFinalizingId(null);
      setFinalizeNote("");
      toast.success(result.approved ? "Refund approved" : "Refund rejected");
      void queryClient.invalidateQueries({ queryKey: ["admin-payments-refunds"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to finalize refund"),
  });
  const savingFinalize = finalizeMutation.isPending;

  // --- Approve payout mutation ---
  const approvePayoutMutation = useMutation({
    mutationFn: async (payoutId: string) => {
      await apiClient!.post(`/admin/payments/payouts/${payoutId}/approve`);
      return payoutId;
    },
    onSuccess: () => {
      toast.success("Payout approved");
      void queryClient.invalidateQueries({ queryKey: ["admin-payments-payouts"] });
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Failed to approve payout"),
  });
  const approvingPayoutId = approvePayoutMutation.isPending ? (approvePayoutMutation.variables as string | undefined) ?? null : null;

  const statusBadge = (status: string) => {
    const s = status.toUpperCase();
    const isGreen = ["COMPLETED", "PAID", "REFUND_COMPLETED", "ADMIN_APPROVED", "VENDOR_APPROVED"].includes(s);
    const isRed = ["FAILED", "CANCELLED", "ADMIN_REJECTED", "VENDOR_REJECTED", "REFUND_FAILED"].includes(s);
    const color = isGreen ? "text-success" : isRed ? "text-danger" : "text-warning-text";
    const bg = isGreen ? "bg-success-soft" : isRed ? "bg-danger-soft" : "bg-warning-soft";
    return <span className={`text-[0.68rem] font-bold py-0.5 px-2 rounded-sm ${bg} ${color}`}>{s.replace(/_/g, " ")}</span>;
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }

  const tabClasses = (t: Tab) =>
    `py-2.5 px-5 rounded-t-md border border-line-bright text-base font-bold cursor-pointer -mb-px ${tab === t ? "bg-[rgba(17,17,40,0.7)] text-white border-b-transparent" : "bg-transparent text-muted"}`;

  const PaginationBar = ({ page, totalPages, setPage }: { page: number; totalPages: number; setPage: (fn: (p: number) => number) => void }) =>
    totalPages > 1 ? (
      <div className="flex justify-center gap-2 mt-5">
        <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
          className={`py-2 px-4 rounded-md border border-line-bright text-sm font-semibold ${page === 0 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}>← Prev</button>
        <span className="flex items-center text-sm text-muted">{page + 1} / {totalPages}</span>
        <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
          className={`py-2 px-4 rounded-md border border-line-bright text-sm font-semibold ${page >= totalPages - 1 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}>Next →</button>
      </div>
    ) : null;

  return (
    <AdminPageShell
      title="Payment Management"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Payments" }]}
    >

        <div className="flex gap-1">
          <button onClick={() => setTab("payments")} className={tabClasses("payments")}>Payments ({paymentTotal})</button>
          <button onClick={() => setTab("refunds")} className={tabClasses("refunds")}>Refunds ({refundTotal})</button>
          <button onClick={() => setTab("payouts")} className={tabClasses("payouts")}>Payouts ({payoutTotal})</button>
        </div>

        <div className="bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-line-bright rounded-[0_16px_16px_16px] p-6">

          {/* PAYMENTS TAB */}
          {tab === "payments" && (
            <>
              <div className="flex gap-3 mb-5">
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Status:
                  <select value={paymentStatusFilter} onChange={(e) => { setPaymentStatusFilter(e.target.value); setPaymentPage(0); }}
                    className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm">
                    <option value="all">All</option>
                    {["PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED"].map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              {paymentLoading ? <TableSkeleton rows={4} cols={6} /> : payments.length === 0 ? <p className="text-center text-muted py-8">No payments found.</p> : (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-[0.82rem]">
                    <thead><tr className="border-b border-line-bright">
                      {["Customer", "Amount", "Method", "Status", "Paid At", "Created"].map((h) => <th key={h} className="py-2 px-3 text-left text-muted font-semibold text-[0.72rem] uppercase">{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {payments.map((p) => (
                        <tr key={p.id} className="border-b border-line">
                          <td className="py-2.5 px-3 text-ink-light">{p.customerName || p.customerEmail || "—"}</td>
                          <td className="py-2.5 px-3 text-white font-semibold">{money(p.amount)}</td>
                          <td className="py-2.5 px-3 text-ink-light">{p.paymentMethod || "—"}</td>
                          <td className="py-2.5 px-3">{statusBadge(p.status)}</td>
                          <td className="py-2.5 px-3 text-muted">{p.paidAt ? new Date(p.paidAt).toLocaleDateString() : "—"}</td>
                          <td className="py-2.5 px-3 text-muted">{new Date(p.createdAt).toLocaleDateString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <PaginationBar page={paymentPage} totalPages={paymentTotalPages} setPage={setPaymentPage} />
            </>
          )}

          {/* REFUNDS TAB */}
          {tab === "refunds" && (
            <>
              <div className="flex gap-3 mb-5">
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Status:
                  <select value={refundStatusFilter} onChange={(e) => { setRefundStatusFilter(e.target.value); setRefundPage(0); }}
                    className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm">
                    <option value="all">All</option>
                    {["REQUESTED", "VENDOR_APPROVED", "VENDOR_REJECTED", "ESCALATED_TO_ADMIN", "ADMIN_APPROVED", "ADMIN_REJECTED", "REFUND_PROCESSING", "REFUND_COMPLETED", "REFUND_FAILED"].map((s) => <option key={s} value={s}>{s.replace(/_/g, " ")}</option>)}
                  </select>
                </label>
              </div>
              {refundLoading ? <TableSkeleton rows={4} cols={6} /> : refunds.length === 0 ? <p className="text-center text-muted py-8">No refunds found.</p> : (
                <div className="flex flex-col gap-3">
                  {refunds.map((r) => (
                    <div key={r.id} className="border border-line-bright rounded-[12px] py-4 px-5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2 mb-1.5">{statusBadge(r.status)}<span className="text-[0.78rem] text-white font-semibold">{money(r.refundAmount)}</span></div>
                          {r.customerReason && <p className="mb-1 text-[0.82rem] text-ink-light">Reason: {r.customerReason}</p>}
                          <p className="text-[0.72rem] text-muted-2">{r.customerName || r.customerEmail || "Customer"} · {new Date(r.createdAt).toLocaleDateString()}</p>
                          {r.adminNote && <p className="mt-1 text-[0.78rem] text-brand italic">Admin: {r.adminNote}</p>}
                        </div>
                        {["ESCALATED_TO_ADMIN", "REQUESTED"].includes(r.status) && finalizingId !== r.id && (
                          <button onClick={() => { setFinalizingId(r.id); setFinalizeNote(""); setFinalizeApproved(true); }}
                            className="py-1.5 px-3 rounded-[8px] text-[0.72rem] font-bold border border-line-bright bg-brand-soft text-brand cursor-pointer whitespace-nowrap">Review</button>
                        )}
                      </div>
                      {finalizingId === r.id && (
                        <div className="mt-3 p-3 rounded-md border border-line-bright bg-[rgba(0,212,255,0.03)]">
                          <div className="flex gap-3 mb-2 items-center">
                            <label className="flex items-center gap-1 text-sm cursor-pointer"><input type="radio" checked={finalizeApproved} onChange={() => setFinalizeApproved(true)} /><span className="text-success">Approve Refund</span></label>
                            <label className="flex items-center gap-1 text-sm cursor-pointer"><input type="radio" checked={!finalizeApproved} onChange={() => setFinalizeApproved(false)} /><span className="text-danger">Reject</span></label>
                          </div>
                          <textarea value={finalizeNote} onChange={(e) => setFinalizeNote(e.target.value)} maxLength={1000} rows={2} placeholder="Admin note (optional)..."
                            className="w-full py-2 px-3 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-[0.82rem] resize-y outline-none" />
                          <div className="flex gap-2 mt-2 justify-end">
                            <button onClick={() => setFinalizingId(null)} className="py-1.5 px-3.5 rounded-[8px] border border-line-bright bg-transparent text-muted text-sm cursor-pointer">Cancel</button>
                            <button onClick={() => finalizeMutation.mutate({ refundId: r.id, approved: finalizeApproved, note: finalizeNote })} disabled={savingFinalize}
                              className={`py-1.5 px-3.5 rounded-[8px] border-none bg-[var(--gradient-brand)] text-white text-sm font-bold ${savingFinalize ? "cursor-not-allowed opacity-60" : "cursor-pointer opacity-100"}`}>{savingFinalize ? "Saving..." : "Confirm"}</button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
              <PaginationBar page={refundPage} totalPages={refundTotalPages} setPage={setRefundPage} />
            </>
          )}

          {/* PAYOUTS TAB */}
          {tab === "payouts" && (
            <>
              <div className="flex gap-3 mb-5">
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Status:
                  <select value={payoutStatusFilter} onChange={(e) => { setPayoutStatusFilter(e.target.value); setPayoutPage(0); }}
                    className="py-1.5 px-2.5 rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-sm">
                    <option value="all">All</option>
                    {["PENDING", "APPROVED", "COMPLETED", "CANCELLED"].map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              {payoutLoading ? <TableSkeleton rows={4} cols={6} /> : payouts.length === 0 ? <p className="text-center text-muted py-8">No payouts found.</p> : (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-[0.82rem]">
                    <thead><tr className="border-b border-line-bright">
                      {["Vendor", "Amount", "Fee", "Status", "Ref #", "Created", "Actions"].map((h) => <th key={h} className="py-2 px-3 text-left text-muted font-semibold text-[0.72rem] uppercase">{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {payouts.map((p) => (
                        <tr key={p.id} className="border-b border-line">
                          <td className="py-2.5 px-3 text-ink-light">{p.vendorName || "—"}</td>
                          <td className="py-2.5 px-3 text-white font-semibold">{money(p.payoutAmount)}</td>
                          <td className="py-2.5 px-3 text-muted">{money(p.platformFee)}</td>
                          <td className="py-2.5 px-3">{statusBadge(p.status)}</td>
                          <td className="py-2.5 px-3 text-ink-light">{p.referenceNumber || "—"}</td>
                          <td className="py-2.5 px-3 text-muted">{new Date(p.createdAt).toLocaleDateString()}</td>
                          <td className="py-2.5 px-3">
                            {p.status === "PENDING" && (
                              <button onClick={() => approvePayoutMutation.mutate(p.id)} disabled={approvingPayoutId === p.id}
                                className={`py-1 px-2.5 rounded-sm text-[0.72rem] font-bold border border-success-glow bg-success-soft text-success ${approvingPayoutId === p.id ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}>
                                {approvingPayoutId === p.id ? "..." : "Approve"}
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <PaginationBar page={payoutPage} totalPages={payoutTotalPages} setPage={setPayoutPage} />
            </>
          )}
        </div>
    </AdminPageShell>
  );
}
