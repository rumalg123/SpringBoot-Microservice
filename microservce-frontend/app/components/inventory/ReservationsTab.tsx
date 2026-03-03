"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import type { AxiosInstance } from "axios";
import { useQuery } from "@tanstack/react-query";
import type { VendorSummary } from "../admin/products/types";
import DataTable, { type Column } from "../ui/DataTable";
import FilterBar, { type FilterDef } from "../ui/FilterBar";
import StatusBadge, { RESERVATION_STATUS_COLORS } from "../ui/StatusBadge";
import { type StockReservation, type PagedData, resolvePage } from "./types";
import { getErrorMessage } from "../../../lib/error";

type Props = {
  apiClient: AxiosInstance;
  apiPrefix: string;
  isAdmin?: boolean;
  vendorId?: string;
};

export default function ReservationsTab({ apiClient, apiPrefix, isAdmin = false, vendorId }: Props) {
  const [reservations, setReservations] = useState<StockReservation[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<Record<string, string>>({});

  const vendorsQuery = useQuery<VendorSummary[]>({
    queryKey: ["admin-vendors"],
    queryFn: async () => {
      const res = await apiClient.get("/admin/vendors");
      return (((res.data as VendorSummary[]) || []).filter((v) => !v.deleted)).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
    },
    enabled: isAdmin,
  });

  const productVendorId = isAdmin ? (filters.vendorId || "") : (vendorId || "");
  const productEndpoint = productVendorId
    ? `/admin/products?vendorId=${encodeURIComponent(productVendorId)}`
    : "/admin/products";

  const load = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const params: Record<string, string | number> = { page: pg, size: 20 };
      if (filters.status) params.status = filters.status;
      if (filters.orderId) params.orderId = filters.orderId;
      if (filters.productId) params.productId = filters.productId;
      const res = await apiClient.get(`${apiPrefix}/reservations`, { params });
      const resolved = resolvePage(res.data as PagedData<StockReservation>);
      setReservations(resolved.content);
      setPage(resolved.page);
      setTotalPages(resolved.totalPages);
      setTotalElements(resolved.totalElements);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [apiClient, apiPrefix, filters]);

  useEffect(() => { void load(0); }, [load]);

  const filterDefs: FilterDef[] = [
    ...(isAdmin ? [{ key: "vendorId", label: "Vendor", type: "select" as const, options: (vendorsQuery.data || []).map((v) => ({ label: v.name, value: v.id })) }] : []),
    { key: "status", label: "Status", type: "select" as const, options: [
      { label: "Reserved", value: "RESERVED" },
      { label: "Confirmed", value: "CONFIRMED" },
      { label: "Released", value: "RELEASED" },
      { label: "Expired", value: "EXPIRED" },
    ]},
    { key: "orderId", label: "Order", type: "text" as const, placeholder: "Paste order ID..." },
    { key: "productId", label: "Product", type: "searchable-select" as const, apiClient, endpoint: productEndpoint, searchParam: "q", labelField: "name", valueField: "id", placeholder: "Search products..." },
  ];

  const columns: Column<StockReservation>[] = [
    { key: "status", header: "Status", render: (v) => <StatusBadge value={String(v)} colorMap={RESERVATION_STATUS_COLORS} /> },
    { key: "orderId", header: "Order", render: (v) => {
      const s = String(v || "—");
      return <span className="text-[0.75rem]">{s}</span>;
    }},
    { key: "productId", header: "Product", render: (_, row) => {
      const r = row as unknown as StockReservation;
      const label = r.productName || r.productSku || "—";
      return <span title={r.productName || r.productId} className="text-[0.75rem]">{label}</span>;
    }},
    { key: "quantityReserved", header: "Qty Reserved", width: "9%", render: (v) => <span className="font-bold">{String(v)}</span> },
    { key: "reservedAt", header: "Reserved At", render: (v) => v ? new Date(String(v)).toLocaleString() : "-" },
    { key: "expiresAt", header: "Expires At", render: (v) => {
      if (!v) return "-";
      const d = new Date(String(v));
      const isExpired = d < new Date();
      return <span className={isExpired ? "text-danger" : "text-ink"}>{d.toLocaleString()}</span>;
    }},
    { key: "confirmedAt", header: "Confirmed", render: (v) => v ? new Date(String(v)).toLocaleString() : "-" },
    { key: "releaseReason", header: "Release Reason", render: (v) => {
      const s = String(v || "");
      return s ? <span title={s} className="text-[0.75rem]">{s.length > 30 ? s.slice(0, 30) + "..." : s}</span> : "-";
    }},
  ];

  return (
    <div>
      <FilterBar
        filters={filterDefs}
        values={filters}
        onChange={(key, val) => setFilters((prev) => {
          const next = { ...prev, [key]: val };
          if (key === "vendorId") {
            next.productId = "";
          }
          return next;
        })}
        onClear={() => setFilters({})}
      />

      <DataTable
        columns={columns}
        data={reservations}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => void load(p)}
        loading={loading}
        emptyTitle="No reservations found"
        emptyDescription="Stock reservations created during checkout will appear here."
      />
    </div>
  );
}
