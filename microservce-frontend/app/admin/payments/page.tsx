"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import TableSkeleton from "../../components/ui/TableSkeleton";

type Payment = {
  id: string; orderId: string; customerId: string; amount: number; currency: string;
  status: string; paymentMethod: string; cardNoMasked: string | null;
  payherePaymentId: string | null; paidAt: string | null; createdAt: string;
};
type RefundRequest = {
  id: string; paymentId: string; orderId: string; vendorOrderId: string | null; vendorId: string | null;
  customerId: string; refundAmount: number; currency: string; customerReason: string | null;
  vendorResponseNote: string | null; adminNote: string | null; status: string;
  createdAt: string;
};
type Payout = {
  id: string; vendorId: string; payoutAmount: number; platformFee: number; currency: string;
  status: string; referenceNumber: string | null; adminNote: string | null;
  approvedAt: string | null; completedAt: string | null; createdAt: string;
};
type Paged<T> = { content: T[]; totalPages: number; totalElements: number };
type Tab = "payments" | "refunds" | "payouts";

export default function AdminPaymentsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, apiClient } = session;

  const [tab, setTab] = useState<Tab>("payments");

  // Payments
  const [payments, setPayments] = useState<Payment[]>([]);
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentTotal, setPaymentTotal] = useState(0);
  const [paymentTotalPages, setPaymentTotalPages] = useState(0);
  const [paymentLoading, setPaymentLoading] = useState(true);
  const [paymentStatusFilter, setPaymentStatusFilter] = useState("all");

  // Refunds
  const [refunds, setRefunds] = useState<RefundRequest[]>([]);
  const [refundPage, setRefundPage] = useState(0);
  const [refundTotal, setRefundTotal] = useState(0);
  const [refundTotalPages, setRefundTotalPages] = useState(0);
  const [refundLoading, setRefundLoading] = useState(true);
  const [refundStatusFilter, setRefundStatusFilter] = useState("all");
  const [finalizingId, setFinalizingId] = useState<string | null>(null);
  const [finalizeNote, setFinalizeNote] = useState("");
  const [finalizeApproved, setFinalizeApproved] = useState(true);
  const [savingFinalize, setSavingFinalize] = useState(false);

  // Payouts
  const [payouts, setPayouts] = useState<Payout[]>([]);
  const [payoutPage, setPayoutPage] = useState(0);
  const [payoutTotal, setPayoutTotal] = useState(0);
  const [payoutTotalPages, setPayoutTotalPages] = useState(0);
  const [payoutLoading, setPayoutLoading] = useState(true);
  const [payoutStatusFilter, setPayoutStatusFilter] = useState("all");
  const [approvingPayoutId, setApprovingPayoutId] = useState<string | null>(null);

  const loadPayments = useCallback(async () => {
    if (!apiClient) return;
    setPaymentLoading(true);
    try {
      const params = new URLSearchParams({ page: String(paymentPage), size: "20" });
      if (paymentStatusFilter !== "all") params.set("status", paymentStatusFilter);
      const res = await apiClient.get(`/admin/payments?${params}`);
      const data = res.data as Paged<Payment>;
      setPayments(data.content || []);
      setPaymentTotalPages(data.totalPages || 0);
      setPaymentTotal(data.totalElements || 0);
    } catch { toast.error("Failed to load payments"); }
    finally { setPaymentLoading(false); }
  }, [apiClient, paymentPage, paymentStatusFilter]);

  const loadRefunds = useCallback(async () => {
    if (!apiClient) return;
    setRefundLoading(true);
    try {
      const params = new URLSearchParams({ page: String(refundPage), size: "20" });
      if (refundStatusFilter !== "all") params.set("status", refundStatusFilter);
      const res = await apiClient.get(`/admin/payments/refunds?${params}`);
      const data = res.data as Paged<RefundRequest>;
      setRefunds(data.content || []);
      setRefundTotalPages(data.totalPages || 0);
      setRefundTotal(data.totalElements || 0);
    } catch { toast.error("Failed to load refunds"); }
    finally { setRefundLoading(false); }
  }, [apiClient, refundPage, refundStatusFilter]);

  const loadPayouts = useCallback(async () => {
    if (!apiClient) return;
    setPayoutLoading(true);
    try {
      const params = new URLSearchParams({ page: String(payoutPage), size: "20" });
      if (payoutStatusFilter !== "all") params.set("status", payoutStatusFilter);
      const res = await apiClient.get(`/admin/payments/payouts?${params}`);
      const data = res.data as Paged<Payout>;
      setPayouts(data.content || []);
      setPayoutTotalPages(data.totalPages || 0);
      setPayoutTotal(data.totalElements || 0);
    } catch { toast.error("Failed to load payouts"); }
    finally { setPayoutLoading(false); }
  }, [apiClient, payoutPage, payoutStatusFilter]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    void loadPayments();
    void loadRefunds();
    void loadPayouts();
  }, [sessionStatus, loadPayments, loadRefunds, loadPayouts]);

  const finalizeRefund = async (refundId: string) => {
    if (!apiClient || savingFinalize) return;
    setSavingFinalize(true);
    try {
      await apiClient.post(`/admin/payments/refunds/${refundId}/finalize`, { approved: finalizeApproved, note: finalizeNote.trim() || null });
      setRefunds((old) => old.map((r) => (r.id === refundId ? { ...r, status: finalizeApproved ? "ADMIN_APPROVED" : "ADMIN_REJECTED", adminNote: finalizeNote.trim() || null } : r)));
      setFinalizingId(null);
      setFinalizeNote("");
      toast.success(finalizeApproved ? "Refund approved" : "Refund rejected");
    } catch (err) { toast.error(err instanceof Error ? err.message : "Failed to finalize refund"); }
    finally { setSavingFinalize(false); }
  };

  const approvePayout = async (payoutId: string) => {
    if (!apiClient || approvingPayoutId) return;
    setApprovingPayoutId(payoutId);
    try {
      await apiClient.post(`/admin/payments/payouts/${payoutId}/approve`);
      setPayouts((old) => old.map((p) => (p.id === payoutId ? { ...p, status: "APPROVED" } : p)));
      toast.success("Payout approved");
    } catch (err) { toast.error(err instanceof Error ? err.message : "Failed to approve payout"); }
    finally { setApprovingPayoutId(null); }
  };

  const statusBadge = (status: string) => {
    const s = status.toUpperCase();
    const isGreen = ["COMPLETED", "PAID", "REFUND_COMPLETED", "ADMIN_APPROVED", "VENDOR_APPROVED"].includes(s);
    const isRed = ["FAILED", "CANCELLED", "ADMIN_REJECTED", "VENDOR_REJECTED", "REFUND_FAILED"].includes(s);
    const color = isGreen ? "var(--success)" : isRed ? "var(--danger)" : "var(--warning-text)";
    const bg = isGreen ? "var(--success-soft)" : isRed ? "var(--danger-soft)" : "var(--warning-soft)";
    return <span style={{ fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", background: bg, color }}>{s.replace(/_/g, " ")}</span>;
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><p style={{ color: "var(--muted)" }}>Loading...</p></div>;
  }

  const tabStyle = (t: Tab) => ({
    padding: "10px 20px", borderRadius: "10px 10px 0 0", border: "1px solid var(--line-bright)",
    borderBottom: tab === t ? "none" : "1px solid var(--line-bright)",
    background: tab === t ? "rgba(17,17,40,0.7)" : "transparent",
    color: tab === t ? "#fff" : "var(--muted)", fontSize: "0.85rem", fontWeight: 700 as const,
    cursor: "pointer" as const, marginBottom: "-1px",
  });

  const Pagination = ({ page, totalPages, setPage }: { page: number; totalPages: number; setPage: (fn: (p: number) => number) => void }) =>
    totalPages > 1 ? (
      <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "20px" }}>
        <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
          style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: page === 0 ? "transparent" : "var(--brand-soft)", color: page === 0 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: page === 0 ? "not-allowed" : "pointer" }}>← Prev</button>
        <span style={{ display: "flex", alignItems: "center", fontSize: "0.8rem", color: "var(--muted)" }}>{page + 1} / {totalPages}</span>
        <button onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
          style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: page >= totalPages - 1 ? "transparent" : "var(--brand-soft)", color: page >= totalPages - 1 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: page >= totalPages - 1 ? "not-allowed" : "pointer" }}>Next →</button>
      </div>
    ) : null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <main className="mx-auto max-w-7xl px-4 py-8">
        <nav className="breadcrumb"><Link href="/">Home</Link><span className="breadcrumb-sep">›</span><span className="breadcrumb-current">Admin Payments</span></nav>
        <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 900, color: "#fff", margin: "0 0 20px" }}>Payment Management</h1>

        <div style={{ display: "flex", gap: "4px" }}>
          <button onClick={() => setTab("payments")} style={tabStyle("payments")}>Payments ({paymentTotal})</button>
          <button onClick={() => setTab("refunds")} style={tabStyle("refunds")}>Refunds ({refundTotal})</button>
          <button onClick={() => setTab("payouts")} style={tabStyle("payouts")}>Payouts ({payoutTotal})</button>
        </div>

        <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "0 16px 16px 16px", padding: "24px" }}>

          {/* PAYMENTS TAB */}
          {tab === "payments" && (
            <>
              <div style={{ display: "flex", gap: "12px", marginBottom: "20px" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Status:
                  <select value={paymentStatusFilter} onChange={(e) => { setPaymentStatusFilter(e.target.value); setPaymentPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}>
                    <option value="all">All</option>
                    {["PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED"].map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              {paymentLoading ? <TableSkeleton rows={4} cols={6} /> : payments.length === 0 ? <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>No payments found.</p> : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.82rem" }}>
                    <thead><tr style={{ borderBottom: "1px solid var(--line-bright)" }}>
                      {["Order", "Amount", "Method", "Status", "Paid At", "Created"].map((h) => <th key={h} style={{ padding: "8px 12px", textAlign: "left", color: "var(--muted)", fontWeight: 600, fontSize: "0.72rem", textTransform: "uppercase" }}>{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {payments.map((p) => (
                        <tr key={p.id} style={{ borderBottom: "1px solid var(--line)" }}>
                          <td style={{ padding: "10px 12px", color: "var(--ink-light)" }}>{p.orderId.slice(0, 8)}...</td>
                          <td style={{ padding: "10px 12px", color: "#fff", fontWeight: 600 }}>{money(p.amount)}</td>
                          <td style={{ padding: "10px 12px", color: "var(--ink-light)" }}>{p.paymentMethod || "—"}</td>
                          <td style={{ padding: "10px 12px" }}>{statusBadge(p.status)}</td>
                          <td style={{ padding: "10px 12px", color: "var(--muted)" }}>{p.paidAt ? new Date(p.paidAt).toLocaleDateString() : "—"}</td>
                          <td style={{ padding: "10px 12px", color: "var(--muted)" }}>{new Date(p.createdAt).toLocaleDateString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <Pagination page={paymentPage} totalPages={paymentTotalPages} setPage={setPaymentPage} />
            </>
          )}

          {/* REFUNDS TAB */}
          {tab === "refunds" && (
            <>
              <div style={{ display: "flex", gap: "12px", marginBottom: "20px" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Status:
                  <select value={refundStatusFilter} onChange={(e) => { setRefundStatusFilter(e.target.value); setRefundPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}>
                    <option value="all">All</option>
                    {["REQUESTED", "VENDOR_APPROVED", "VENDOR_REJECTED", "ESCALATED_TO_ADMIN", "ADMIN_APPROVED", "ADMIN_REJECTED", "REFUND_PROCESSING", "REFUND_COMPLETED", "REFUND_FAILED"].map((s) => <option key={s} value={s}>{s.replace(/_/g, " ")}</option>)}
                  </select>
                </label>
              </div>
              {refundLoading ? <TableSkeleton rows={4} cols={6} /> : refunds.length === 0 ? <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>No refunds found.</p> : (
                <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  {refunds.map((r) => (
                    <div key={r.id} style={{ border: "1px solid var(--line-bright)", borderRadius: "12px", padding: "16px 20px" }}>
                      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                        <div>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px" }}>{statusBadge(r.status)}<span style={{ fontSize: "0.78rem", color: "#fff", fontWeight: 600 }}>{money(r.refundAmount)}</span></div>
                          {r.customerReason && <p style={{ margin: "0 0 4px", fontSize: "0.82rem", color: "var(--ink-light)" }}>Reason: {r.customerReason}</p>}
                          <p style={{ margin: 0, fontSize: "0.72rem", color: "var(--muted-2)" }}>Order: {r.orderId.slice(0, 8)}... · {new Date(r.createdAt).toLocaleDateString()}</p>
                          {r.adminNote && <p style={{ margin: "4px 0 0", fontSize: "0.78rem", color: "var(--brand)", fontStyle: "italic" }}>Admin: {r.adminNote}</p>}
                        </div>
                        {["ESCALATED_TO_ADMIN", "REQUESTED"].includes(r.status) && finalizingId !== r.id && (
                          <button onClick={() => { setFinalizingId(r.id); setFinalizeNote(""); setFinalizeApproved(true); }}
                            style={{ padding: "5px 12px", borderRadius: "8px", fontSize: "0.72rem", fontWeight: 700, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", cursor: "pointer", whiteSpace: "nowrap" }}>Review</button>
                        )}
                      </div>
                      {finalizingId === r.id && (
                        <div style={{ marginTop: "12px", padding: "12px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: "rgba(0,212,255,0.03)" }}>
                          <div style={{ display: "flex", gap: "12px", marginBottom: "8px", alignItems: "center" }}>
                            <label style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.8rem", cursor: "pointer" }}><input type="radio" checked={finalizeApproved} onChange={() => setFinalizeApproved(true)} /><span style={{ color: "var(--success)" }}>Approve Refund</span></label>
                            <label style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.8rem", cursor: "pointer" }}><input type="radio" checked={!finalizeApproved} onChange={() => setFinalizeApproved(false)} /><span style={{ color: "var(--danger)" }}>Reject</span></label>
                          </div>
                          <textarea value={finalizeNote} onChange={(e) => setFinalizeNote(e.target.value)} maxLength={1000} rows={2} placeholder="Admin note (optional)..."
                            style={{ width: "100%", padding: "8px 12px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.82rem", resize: "vertical", outline: "none" }} />
                          <div style={{ display: "flex", gap: "8px", marginTop: "8px", justifyContent: "flex-end" }}>
                            <button onClick={() => setFinalizingId(null)} style={{ padding: "6px 14px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "transparent", color: "var(--muted)", fontSize: "0.8rem", cursor: "pointer" }}>Cancel</button>
                            <button onClick={() => void finalizeRefund(r.id)} disabled={savingFinalize}
                              style={{ padding: "6px 14px", borderRadius: "8px", border: "none", background: "var(--gradient-brand)", color: "#fff", fontSize: "0.8rem", fontWeight: 700, cursor: savingFinalize ? "not-allowed" : "pointer", opacity: savingFinalize ? 0.6 : 1 }}>{savingFinalize ? "Saving..." : "Confirm"}</button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
              <Pagination page={refundPage} totalPages={refundTotalPages} setPage={setRefundPage} />
            </>
          )}

          {/* PAYOUTS TAB */}
          {tab === "payouts" && (
            <>
              <div style={{ display: "flex", gap: "12px", marginBottom: "20px" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Status:
                  <select value={payoutStatusFilter} onChange={(e) => { setPayoutStatusFilter(e.target.value); setPayoutPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}>
                    <option value="all">All</option>
                    {["PENDING", "APPROVED", "COMPLETED", "CANCELLED"].map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              {payoutLoading ? <TableSkeleton rows={4} cols={6} /> : payouts.length === 0 ? <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>No payouts found.</p> : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.82rem" }}>
                    <thead><tr style={{ borderBottom: "1px solid var(--line-bright)" }}>
                      {["Vendor", "Amount", "Fee", "Status", "Ref #", "Created", "Actions"].map((h) => <th key={h} style={{ padding: "8px 12px", textAlign: "left", color: "var(--muted)", fontWeight: 600, fontSize: "0.72rem", textTransform: "uppercase" }}>{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {payouts.map((p) => (
                        <tr key={p.id} style={{ borderBottom: "1px solid var(--line)" }}>
                          <td style={{ padding: "10px 12px", color: "var(--ink-light)" }}>{p.vendorId.slice(0, 8)}...</td>
                          <td style={{ padding: "10px 12px", color: "#fff", fontWeight: 600 }}>{money(p.payoutAmount)}</td>
                          <td style={{ padding: "10px 12px", color: "var(--muted)" }}>{money(p.platformFee)}</td>
                          <td style={{ padding: "10px 12px" }}>{statusBadge(p.status)}</td>
                          <td style={{ padding: "10px 12px", color: "var(--ink-light)" }}>{p.referenceNumber || "—"}</td>
                          <td style={{ padding: "10px 12px", color: "var(--muted)" }}>{new Date(p.createdAt).toLocaleDateString()}</td>
                          <td style={{ padding: "10px 12px" }}>
                            {p.status === "PENDING" && (
                              <button onClick={() => void approvePayout(p.id)} disabled={approvingPayoutId === p.id}
                                style={{ padding: "4px 10px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 700, border: "1px solid var(--success-glow)", background: "var(--success-soft)", color: "var(--success)", cursor: approvingPayoutId === p.id ? "not-allowed" : "pointer", opacity: approvingPayoutId === p.id ? 0.5 : 1 }}>
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
              <Pagination page={payoutPage} totalPages={payoutTotalPages} setPage={setPayoutPage} />
            </>
          )}
        </div>
      </main>
    </div>
  );
}
