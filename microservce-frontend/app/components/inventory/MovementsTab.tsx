"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import type { AxiosInstance } from "axios";
import DataTable, { type Column } from "../ui/DataTable";
import FilterBar, { type FilterDef } from "../ui/FilterBar";
import StatusBadge, { MOVEMENT_TYPE_COLORS } from "../ui/StatusBadge";
import { type StockMovement, type PagedData, resolvePage } from "./types";
import { getErrorMessage } from "../../../lib/error";

type Props = {
  apiClient: AxiosInstance;
  apiPrefix: string;
  isAdmin?: boolean;
};

export default function MovementsTab({ apiClient, apiPrefix, isAdmin = false }: Props) {
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState<Record<string, string>>({});

  const load = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const params: Record<string, string | number> = { page: pg, size: 20 };
      if (filters.movementType) params.movementType = filters.movementType;
      if (filters.productId) params.productId = filters.productId;
      if (filters.warehouseId) params.warehouseId = filters.warehouseId;
      const res = await apiClient.get(`${apiPrefix}/movements`, { params });
      const resolved = resolvePage(res.data as PagedData<StockMovement>);
      setMovements(resolved.content);
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
    { key: "movementType", label: "Type", type: "select" as const, options: [
      { label: "Stock In", value: "STOCK_IN" },
      { label: "Stock Out", value: "STOCK_OUT" },
      { label: "Reservation", value: "RESERVATION" },
      { label: "Reservation Confirm", value: "RESERVATION_CONFIRM" },
      { label: "Reservation Release", value: "RESERVATION_RELEASE" },
      { label: "Adjustment", value: "ADJUSTMENT" },
      { label: "Bulk Import", value: "BULK_IMPORT" },
    ]},
    { key: "productId", label: "Product ID", type: "text" as const, placeholder: "Filter by product..." },
    ...(isAdmin ? [{ key: "warehouseId", label: "Warehouse ID", type: "text" as const, placeholder: "Filter by warehouse..." }] : []),
  ];

  const columns: Column<StockMovement>[] = [
    { key: "movementType", header: "Type", render: (v) => <StatusBadge value={String(v)} colorMap={MOVEMENT_TYPE_COLORS} /> },
    { key: "quantityChange", header: "Change", render: (v) => {
      const num = Number(v);
      const color = num > 0 ? "var(--success)" : num < 0 ? "var(--danger)" : "var(--muted)";
      return <span className="font-bold" style={{ color }}>{num > 0 ? `+${num}` : String(num)}</span>;
    }, width: "8%"},
    { key: "quantityBefore", header: "Before", width: "7%", render: (v) => String(v) },
    { key: "quantityAfter", header: "After", width: "7%", render: (v) => String(v) },
    { key: "productId", header: "Product", render: (v) => {
      const s = String(v || "");
      return <span title={s} className="text-[0.75rem] font-mono">{s.slice(0, 8)}...</span>;
    }},
    { key: "referenceType", header: "Reference", render: (_, row) => {
      const m = row as unknown as StockMovement;
      if (!m.referenceType) return "-";
      const refId = m.referenceId ? m.referenceId.slice(0, 8) + "..." : "";
      return <span className="text-[0.75rem]">{m.referenceType}{refId ? `: ${refId}` : ""}</span>;
    }},
    { key: "actorType", header: "Actor", render: (_, row) => {
      const m = row as unknown as StockMovement;
      return m.actorType ? <span className="text-[0.75rem]">{m.actorType}{m.actorId ? ` (${m.actorId.slice(0, 12)})` : ""}</span> : "-";
    }},
    { key: "note", header: "Note", render: (v) => {
      const s = String(v || "");
      return s ? <span title={s} className="text-[0.75rem]">{s.length > 40 ? s.slice(0, 40) + "..." : s}</span> : "-";
    }},
    { key: "createdAt", header: "Date", render: (v) => v ? new Date(String(v)).toLocaleString() : "-" },
  ];

  return (
    <div>
      <FilterBar
        filters={filterDefs}
        values={filters}
        onChange={(key, val) => setFilters((prev) => ({ ...prev, [key]: val }))}
        onClear={() => setFilters({})}
      />

      <DataTable
        columns={columns}
        data={movements}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => void load(p)}
        loading={loading}
        emptyTitle="No stock movements"
        emptyDescription="Stock movements will appear here as inventory changes are recorded."
      />
    </div>
  );
}
