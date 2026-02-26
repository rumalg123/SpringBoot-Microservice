"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import VendorPageShell from "../../components/ui/VendorPageShell";

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
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, isVendorAdmin, apiClient } = session;

  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("all");

  const ready = sessionStatus === "ready" && isAuthenticated && isVendorAdmin && !!apiClient;

  const { data: payoutsData, isLoading: loading } = useQuery({
    queryKey: ["vendor-payouts", page, statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      if (statusFilter !== "all") params.set("status", statusFilter);
      const res = await apiClient!.get(`/admin/payments/payouts?${params}`);
      return res.data as Paged<VendorPayout>;
    },
    enabled: ready,
  });

  const payouts = payoutsData?.content || [];
  const totalPages = payoutsData?.totalPages || 0;
  const totalElements = payoutsData?.totalElements || 0;

  const statusBadge = (status: string) => {
    const s = status.toUpperCase();
    const isGreen = ["COMPLETED"].includes(s);
    const isRed = ["CANCELLED"].includes(s);
    const isBlue = ["APPROVED"].includes(s);
    let colorCls = "text-warning-text bg-warning-soft";
    if (isGreen) colorCls = "text-success bg-success-soft";
    if (isRed) colorCls = "text-danger bg-danger-soft";
    if (isBlue) colorCls = "text-brand bg-brand-soft";
    return <span className={`text-[0.68rem] font-bold px-2 py-0.5 rounded-sm ${colorCls}`}>{s}</span>;
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <VendorPageShell title="Payout History" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Payouts" }]}>
        <p className="text-muted text-center py-10">Loading...</p>
      </VendorPageShell>
    );
  }

  return (
      <VendorPageShell
        title="Payout History"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Payouts" }]}
        actions={
          <>
            <span className="text-sm text-muted flex items-center">
              {totalElements} payout{totalElements !== 1 ? "s" : ""} total
            </span>
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
              className="filter-select"
            >
              <option value="all">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </>
        }
      >

        {loading ? (
          <p className="text-muted text-center py-10">Loading payouts...</p>
        ) : payouts.length === 0 ? (
          <div className="text-center px-5 py-[60px] rounded-[14px] bg-[var(--card)] border border-line-bright">
            <p className="text-muted text-[0.9rem]">No payouts found.</p>
          </div>
        ) : (
          <>
            <div className="rounded-[14px] overflow-hidden border border-line-bright">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="bg-[var(--card)]">
                    {["Amount", "Fee", "Net", "Status", "Bank", "Reference", "Created", "Completed"].map((h) => (
                      <th key={h} className="px-3.5 py-2.5 text-[0.68rem] font-extrabold uppercase tracking-[0.1em] text-muted text-left">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {payouts.map((p) => (
                    <tr key={p.id} className="border-t border-line-bright">
                      <td className="px-3.5 py-3 text-[0.82rem] font-semibold text-white">{money(p.payoutAmount)}</td>
                      <td className="px-3.5 py-3 text-[0.78rem] text-muted">{money(p.platformFee)}</td>
                      <td className="px-3.5 py-3 text-[0.82rem] font-semibold text-success">{money(p.payoutAmount - p.platformFee)}</td>
                      <td className="px-3.5 py-3">{statusBadge(p.status)}</td>
                      <td className="px-3.5 py-3 text-[0.75rem] text-muted">
                        {p.bankNameSnapshot || "\u2014"}
                        {p.accountNumberSnapshot && <span className="block text-[0.68rem]">****{p.accountNumberSnapshot.slice(-4)}</span>}
                      </td>
                      <td className="px-3.5 py-3 text-[0.75rem] text-muted font-mono">{p.referenceNumber || "\u2014"}</td>
                      <td className="px-3.5 py-3 text-[0.75rem] text-muted">{new Date(p.createdAt).toLocaleDateString()}</td>
                      <td className="px-3.5 py-3 text-[0.75rem] text-muted">{p.completedAt ? new Date(p.completedAt).toLocaleDateString() : "\u2014"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex justify-center gap-2 mt-4">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="px-3.5 py-1.5 rounded-[8px] text-[0.78rem] font-semibold bg-[var(--card)] border border-line-bright"
                  style={{ color: page === 0 ? "var(--muted)" : "#fff", cursor: page === 0 ? "default" : "pointer" }}
                >
                  Previous
                </button>
                <span className="px-3 py-1.5 text-[0.78rem] text-muted">
                  {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="px-3.5 py-1.5 rounded-[8px] text-[0.78rem] font-semibold bg-[var(--card)] border border-line-bright"
                  style={{ color: page >= totalPages - 1 ? "var(--muted)" : "#fff", cursor: page >= totalPages - 1 ? "default" : "pointer" }}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </VendorPageShell>
  );
}
