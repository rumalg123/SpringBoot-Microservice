"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import VendorPageShell from "../../components/ui/VendorPageShell";
import VendorOrderRow from "../../components/vendor/orders/VendorOrderRow";
import type { VendorOrder, PageResponse, VendorOrderDetail } from "../../components/vendor/orders/types";
import {
  extractErrorMessage,
  STATUS_OPTIONS,
} from "../../components/vendor/orders/types";

/* ── Component ── */

export default function VendorOrdersPage() {
  const session = useAuthSession();

  /* ── State ── */
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);

  const ready = session.status === "ready" && session.isAuthenticated && (session.isVendorAdmin || session.isVendorStaff) && !!session.apiClient;

  /* ── Orders query ── */
  const { data: ordersData, isLoading: loading } = useQuery({
    queryKey: ["vendor-orders", page, statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (statusFilter !== "ALL") params.set("status", statusFilter);
      params.set("page", String(page));
      params.set("size", "20");
      const res = await session.apiClient!.get(`/orders/vendor/me?${params.toString()}`);
      return res.data as PageResponse;
    },
    enabled: ready,
  });

  const orders = ordersData?.content || [];
  const totalPages = ordersData?.totalPages ?? ordersData?.page?.totalPages ?? 0;
  const totalElements = ordersData?.totalElements ?? ordersData?.page?.totalElements ?? 0;

  /* ── Order detail query (enabled only when an order is selected) ── */
  const { data: orderDetail = null, isLoading: detailLoading } = useQuery({
    queryKey: ["vendor-order-detail", selectedOrderId],
    queryFn: async () => {
      const res = await session.apiClient!.get(`/orders/vendor/me/${selectedOrderId}`);
      return res.data as VendorOrderDetail;
    },
    enabled: ready && !!selectedOrderId,
  });

  /* ── Handlers ── */
  const handleStatusFilterChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setPage(0);
    setSelectedOrderId(null);
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
    setSelectedOrderId(null);
  };

  const handleViewClick = (vendorOrderId: string) => {
    if (selectedOrderId === vendorOrderId) {
      setSelectedOrderId(null);
    } else {
      setSelectedOrderId(vendorOrderId);
    }
  };

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <VendorPageShell
        title="Vendor Orders"
        breadcrumbs={[
          { label: "Vendor Portal", href: "/vendor" },
          { label: "Orders" },
        ]}
      >
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading session...</p>
        </div>
      </VendorPageShell>
    );
  }

  if (!session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <VendorPageShell
        title="Vendor Orders"
        breadcrumbs={[
          { label: "Vendor Portal", href: "/vendor" },
          { label: "Orders" },
        ]}
      >
        <div className="flex items-center justify-center min-h-[300px] flex-col gap-3">
          <p className="text-danger text-[1.1rem] font-bold font-[var(--font-display,Syne,sans-serif)]">
            Unauthorized
          </p>
          <p className="text-muted text-sm">
            You do not have permission to view vendor orders.
          </p>
        </div>
      </VendorPageShell>
    );
  }

  /* ── Render ── */
  return (
    <VendorPageShell
      title="Vendor Orders"
      breadcrumbs={[
        { label: "Vendor Portal", href: "/vendor" },
        { label: "Orders" },
      ]}
      actions={
        <span className="bg-[linear-gradient(135deg,#00d4ff,#7c3aed)] text-white px-3.5 py-[3px] rounded-xl text-[0.75rem] font-extrabold">
          {totalElements} total
        </span>
      }
    >
      {/* ── Filter bar ── */}
      <div className="mb-4 flex items-center gap-3 flex-wrap">
        <label className="text-xs font-bold uppercase tracking-[0.08em] text-muted">
          Status
        </label>
        <select
          value={statusFilter}
          onChange={(e) => handleStatusFilterChange(e.target.value)}
          className="filter-select"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === "ALL" ? "All Statuses" : s.replace(/_/g, " ")}
            </option>
          ))}
        </select>
      </div>

      {/* ── Main card ── */}
      <section className="bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-[rgba(0,212,255,0.1)] rounded-lg p-5 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center min-h-[200px]">
            <p className="text-muted text-base">Loading orders...</p>
          </div>
        ) : orders.length === 0 ? (
          <div className="flex items-center justify-center min-h-[200px] flex-col gap-2">
            <p className="text-muted text-[0.95rem] font-semibold">No orders found</p>
            <p className="text-muted text-[0.78rem]">
              {statusFilter !== "ALL"
                ? "Try selecting a different status filter."
                : "Orders placed to your store will appear here."}
            </p>
          </div>
        ) : (
          <>
            {/* ── Orders table ── */}
            <div className="overflow-x-auto">
              <table className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Order ID</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Items</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Qty</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Total</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Payout</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Status</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Date</th>
                    <th className="bg-surface-2 px-3 py-2.5 text-[0.7rem] font-bold uppercase tracking-[0.08em] text-muted text-center whitespace-nowrap border-b border-line">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order) => (
                    <VendorOrderRow
                      key={order.id}
                      order={order}
                      isExpanded={selectedOrderId === order.id}
                      onToggle={() => handleViewClick(order.id)}
                      orderDetail={selectedOrderId === order.id ? orderDetail : null}
                      detailLoading={selectedOrderId === order.id ? detailLoading : false}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            {/* ── Pagination ── */}
            <div className="flex items-center justify-between mt-4 flex-wrap gap-3">
              <p className="text-[0.75rem] text-muted">
                Page {page + 1} of {totalPages} ({totalElements} orders)
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page <= 0}
                  className="px-4 py-1.5 rounded-[8px] text-[0.78rem] font-bold border border-line bg-surface-2 text-ink transition-opacity duration-150"
                  style={{
                    opacity: page <= 0 ? 0.4 : 1,
                    cursor: page <= 0 ? "not-allowed" : "pointer",
                  }}
                >
                  Prev
                </button>
                <button
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page + 1 >= totalPages}
                  className="px-4 py-1.5 rounded-[8px] text-[0.78rem] font-bold border border-line bg-surface-2 text-ink transition-opacity duration-150"
                  style={{
                    opacity: page + 1 >= totalPages ? 0.4 : 1,
                    cursor: page + 1 >= totalPages ? "not-allowed" : "pointer",
                  }}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </VendorPageShell>
  );
}
