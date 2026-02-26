"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";

type VendorPayout = {
  id: string;
  vendorId: string;
  payoutAmount: number;
  platformFee: number;
  currency: string;
  vendorOrderIds: string | null;
  bankNameSnapshot: string | null;
  accountNumberSnapshot: string | null;
  accountHolderSnapshot: string | null;
  status: string;
  referenceNumber: string | null;
  adminNote: string | null;
  approvedAt: string | null;
  completedAt: string | null;
  createdAt: string;
};

type Paged<T> = { content: T[]; totalPages: number; totalElements: number };

export default function VendorPayoutsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [payouts, setPayouts] = useState<VendorPayout[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState("all");

  const loadPayouts = useCallback(async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      if (statusFilter !== "all") params.set("status", statusFilter);
      const res = await apiClient.get(`/admin/payments/payouts?${params}`);
      const data = res.data as Paged<VendorPayout>;
      setPayouts(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch {
      toast.error("Failed to load payouts");
    } finally {
      setLoading(false);
    }
  }, [apiClient, page, statusFilter]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !isVendorAdmin) { router.replace("/"); return; }
    void loadPayouts();
  }, [sessionStatus, isAuthenticated, isVendorAdmin, router, loadPayouts]);

  const statusBadge = (status: string) => {
    const s = status.toUpperCase();
    const isGreen = ["COMPLETED"].includes(s);
    const isRed = ["CANCELLED"].includes(s);
    const isBlue = ["APPROVED"].includes(s);
    let color = "var(--warning-text)";
    let bg = "var(--warning-soft)";
    if (isGreen) { color = "var(--success)"; bg = "var(--success-soft)"; }
    if (isRed) { color = "var(--danger)"; bg = "var(--danger-soft)"; }
    if (isBlue) { color = "var(--brand)"; bg = "var(--brand-soft)"; }
    return <span style={{ fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", background: bg, color }}>{s}</span>;
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><p style={{ color: "var(--muted)" }}>Loading...</p></div>;
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""} isSuperAdmin={isSuperAdmin} isVendorAdmin={isVendorAdmin}
        canViewAdmin={canViewAdmin} canManageAdminOrders={canManageAdminOrders}
        canManageAdminProducts={canManageAdminProducts} canManageAdminCategories={canManageAdminCategories}
        canManageAdminVendors={canManageAdminVendors} canManageAdminPosters={canManageAdminPosters}
        apiClient={apiClient} emailVerified={emailVerified} onLogout={logout}
      />

      <main className="mx-auto max-w-5xl px-4 py-10">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
          <div>
            <h1 className="text-2xl font-bold" style={{ color: "#fff" }}>Payout History</h1>
            <p style={{ color: "var(--muted)", fontSize: "0.85rem", marginTop: "4px" }}>
              {totalElements} payout{totalElements !== 1 ? "s" : ""} total
            </p>
          </div>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            style={{
              padding: "8px 14px", borderRadius: "8px", fontSize: "0.82rem",
              background: "var(--card)", border: "1px solid var(--line-bright)", color: "#fff",
            }}
          >
            <option value="all">All Statuses</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        {loading ? (
          <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading payouts...</p>
        ) : payouts.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
            <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No payouts found.</p>
          </div>
        ) : (
          <>
            <div style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "var(--card)" }}>
                    {["Amount", "Fee", "Net", "Status", "Bank", "Reference", "Created", "Completed"].map((h) => (
                      <th key={h} style={{ padding: "10px 14px", fontSize: "0.68rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", textAlign: "left" }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {payouts.map((p) => (
                    <tr key={p.id} style={{ borderTop: "1px solid var(--line-bright)" }}>
                      <td style={{ padding: "12px 14px", fontSize: "0.82rem", fontWeight: 600, color: "#fff" }}>{money(p.payoutAmount)}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.78rem", color: "var(--muted)" }}>{money(p.platformFee)}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.82rem", fontWeight: 600, color: "var(--success)" }}>{money(p.payoutAmount - p.platformFee)}</td>
                      <td style={{ padding: "12px 14px" }}>{statusBadge(p.status)}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>
                        {p.bankNameSnapshot || "—"}
                        {p.accountNumberSnapshot && <span style={{ display: "block", fontSize: "0.68rem" }}>****{p.accountNumberSnapshot.slice(-4)}</span>}
                      </td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)", fontFamily: "monospace" }}>{p.referenceNumber || "—"}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{new Date(p.createdAt).toLocaleDateString()}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{p.completedAt ? new Date(p.completedAt).toLocaleDateString() : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "16px" }}>
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  style={{
                    padding: "6px 14px", borderRadius: "8px", fontSize: "0.78rem", fontWeight: 600,
                    background: "var(--card)", color: page === 0 ? "var(--muted)" : "#fff",
                    border: "1px solid var(--line-bright)", cursor: page === 0 ? "default" : "pointer",
                  }}
                >
                  Previous
                </button>
                <span style={{ padding: "6px 12px", fontSize: "0.78rem", color: "var(--muted)" }}>
                  {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  style={{
                    padding: "6px 14px", borderRadius: "8px", fontSize: "0.78rem", fontWeight: 600,
                    background: "var(--card)", color: page >= totalPages - 1 ? "var(--muted)" : "#fff",
                    border: "1px solid var(--line-bright)", cursor: page >= totalPages - 1 ? "default" : "pointer",
                  }}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
