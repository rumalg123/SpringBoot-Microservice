"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import type { AxiosInstance } from "axios";
import DataTable, { type Column } from "../ui/DataTable";
import FilterBar, { type FilterDef } from "../ui/FilterBar";
import StatusBadge, { STOCK_STATUS_COLORS } from "../ui/StatusBadge";
import StockItemForm from "./StockItemForm";
import StockAdjustModal from "./StockAdjustModal";
import BulkImportModal, { type BulkStockItem } from "./BulkImportModal";
import { type StockItem, type StockItemFormData, type Warehouse, type PagedData, EMPTY_STOCK_FORM, resolvePage } from "./types";
import { getErrorMessage } from "../../../lib/error";

type Props = {
  apiClient: AxiosInstance;
  apiPrefix: string;
  isAdmin?: boolean;
  vendorId?: string;
};

export default function StockTab({ apiClient, apiPrefix, isAdmin = false, vendorId }: Props) {
  const [items, setItems] = useState<StockItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [filters, setFilters] = useState<Record<string, string>>({});
  const [lowStockOnly, setLowStockOnly] = useState(false);

  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<StockItemFormData>({ ...EMPTY_STOCK_FORM });

  const [adjustTarget, setAdjustTarget] = useState<StockItem | null>(null);
  const [adjustLoading, setAdjustLoading] = useState(false);

  const [showBulkImport, setShowBulkImport] = useState(false);
  const [bulkLoading, setBulkLoading] = useState(false);

  /* ── Load warehouses (for forms/dropdowns) ── */
  useEffect(() => {
    const fetchWarehouses = async () => {
      try {
        const res = await apiClient.get(`${apiPrefix}/warehouses`, { params: { size: 100 } });
        const data = res.data as PagedData<Warehouse>;
        setWarehouses(data.content ?? []);
      } catch { /* ignore */ }
    };
    void fetchWarehouses();
  }, [apiClient, apiPrefix]);

  /* ── Load stock items ── */
  const load = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const endpoint = lowStockOnly ? `${apiPrefix}/stock/low-stock` : `${apiPrefix}/stock`;
      const params: Record<string, string | number> = { page: pg, size: 20 };
      if (filters.stockStatus) params.stockStatus = filters.stockStatus;
      if (filters.sku) params.sku = filters.sku;
      if (filters.warehouseId) params.warehouseId = filters.warehouseId;
      if (filters.productId) params.productId = filters.productId;
      if (isAdmin && filters.vendorId) params.vendorId = filters.vendorId;
      const res = await apiClient.get(endpoint, { params });
      const resolved = resolvePage(res.data as PagedData<StockItem>);
      setItems(resolved.content);
      setPage(resolved.page);
      setTotalPages(resolved.totalPages);
      setTotalElements(resolved.totalElements);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [apiClient, apiPrefix, filters, lowStockOnly, isAdmin]);

  useEffect(() => { void load(0); }, [load]);

  /* ── Save stock item ── */
  const handleSave = async () => {
    if (!form.productId.trim()) { toast.error("Product ID is required"); return; }
    if (!form.warehouseId) { toast.error("Warehouse is required"); return; }
    setSaving(true);
    try {
      if (form.id) {
        await apiClient.put(`${apiPrefix}/stock/${form.id}`, {
          sku: form.sku || null,
          lowStockThreshold: Number(form.lowStockThreshold) || 10,
          backorderable: form.backorderable,
        });
        toast.success("Stock item updated");
      } else {
        await apiClient.post(`${apiPrefix}/stock`, {
          productId: form.productId,
          vendorId: vendorId || form.vendorId,
          warehouseId: form.warehouseId,
          sku: form.sku || null,
          quantityOnHand: Number(form.quantityOnHand) || 0,
          lowStockThreshold: Number(form.lowStockThreshold) || 10,
          backorderable: form.backorderable,
        });
        toast.success("Stock item created");
      }
      setShowForm(false);
      setForm({ ...EMPTY_STOCK_FORM });
      await load(page);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  /* ── Edit ── */
  const handleEdit = (item: StockItem) => {
    setForm({
      id: item.id,
      productId: item.productId,
      vendorId: item.vendorId,
      warehouseId: item.warehouseId,
      sku: item.sku || "",
      quantityOnHand: item.quantityOnHand,
      lowStockThreshold: item.lowStockThreshold,
      backorderable: item.backorderable,
    });
    setShowForm(true);
  };

  /* ── Adjust stock ── */
  const handleAdjust = async (quantityChange: number, reason: string) => {
    if (!adjustTarget) return;
    setAdjustLoading(true);
    try {
      await apiClient.post(`${apiPrefix}/stock/${adjustTarget.id}/adjust`, { quantityChange, reason });
      toast.success("Stock adjusted");
      setAdjustTarget(null);
      await load(page);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setAdjustLoading(false);
    }
  };

  /* ── Bulk import ── */
  const handleBulkImport = async (bulkItems: BulkStockItem[]) => {
    setBulkLoading(true);
    try {
      const res = await apiClient.post(`${apiPrefix}/stock/bulk-import`, { items: bulkItems });
      const result = res.data as { totalProcessed: number; created: number; updated: number; errors: string[] };
      toast.success(`Imported: ${result.created} created, ${result.updated} updated`);
      if (result.errors?.length > 0) {
        result.errors.forEach((e) => toast.error(e));
      }
      setShowBulkImport(false);
      await load(page);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBulkLoading(false);
    }
  };

  /* ── Columns & filters ── */
  const filterDefs: FilterDef[] = [
    ...(isAdmin ? [{ key: "vendorId", label: "Vendor ID", type: "text" as const, placeholder: "Filter by vendor..." }] : []),
    { key: "productId", label: "Product ID", type: "text" as const, placeholder: "Filter by product..." },
    { key: "sku", label: "SKU", type: "text" as const, placeholder: "Filter by SKU..." },
    { key: "stockStatus", label: "Status", type: "select" as const, options: [
      { label: "In Stock", value: "IN_STOCK" },
      { label: "Low Stock", value: "LOW_STOCK" },
      { label: "Out of Stock", value: "OUT_OF_STOCK" },
      { label: "Backorder", value: "BACKORDER" },
    ]},
    { key: "warehouseId", label: "Warehouse", type: "select" as const, options: warehouses.map((w) => ({ label: w.name, value: w.id })) },
  ];

  const columns: Column<StockItem>[] = [
    { key: "sku", header: "SKU", render: (v) => String(v || "-"), width: "12%" },
    { key: "productId", header: "Product", render: (v) => {
      const s = String(v || "");
      return <span title={s} className="text-[0.75rem] font-mono">{s.slice(0, 8)}...</span>;
    }},
    { key: "warehouseName", header: "Warehouse" },
    { key: "quantityOnHand", header: "On Hand", width: "8%", render: (v) => <span className="font-bold">{String(v)}</span> },
    { key: "quantityReserved", header: "Reserved", width: "8%", render: (v) => String(v) },
    { key: "quantityAvailable", header: "Available", width: "8%", render: (v, row) => {
      const item = row as unknown as StockItem;
      const avail = item.quantityAvailable;
      const threshold = item.lowStockThreshold;
      const color = avail <= 0 ? "var(--danger)" : avail <= threshold ? "var(--warning-text)" : "var(--success)";
      return <span className="font-bold" style={{ color }}>{avail}</span>;
    }},
    { key: "stockStatus", header: "Status", render: (v) => <StatusBadge value={String(v)} colorMap={STOCK_STATUS_COLORS} /> },
    { key: "backorderable", header: "Backorder", width: "7%", render: (v) => v ? "Yes" : "No" },
  ];

  if (showForm) {
    return (
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-5">
        <StockItemForm
          form={form}
          onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          warehouses={warehouses}
          saving={saving}
          onSave={handleSave}
          onCancel={() => { setShowForm(false); setForm({ ...EMPTY_STOCK_FORM }); }}
          showVendorId={isAdmin}
        />
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-end gap-2 mb-3 flex-wrap">
        <button
          type="button"
          className={`${lowStockOnly ? "btn-primary" : "btn-ghost"} text-[0.78rem] px-3.5 py-[7px]`}
          onClick={() => setLowStockOnly((v) => !v)}
        >
          {lowStockOnly ? "Show All Stock" : "Low Stock Only"}
        </button>
        <button type="button" className="btn-ghost text-[0.78rem] px-3.5 py-[7px]" onClick={() => setShowBulkImport(true)}>
          Bulk Import
        </button>
        <button type="button" className="btn-primary text-[0.82rem] px-4.5 py-2" onClick={() => { setForm({ ...EMPTY_STOCK_FORM, vendorId: vendorId || "" }); setShowForm(true); }}>
          + Add Stock
        </button>
      </div>

      {!lowStockOnly && (
        <FilterBar
          filters={filterDefs}
          values={filters}
          onChange={(key, val) => setFilters((prev) => ({ ...prev, [key]: val }))}
          onClear={() => setFilters({})}
        />
      )}

      <DataTable
        columns={columns}
        data={items}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => void load(p)}
        loading={loading}
        emptyTitle={lowStockOnly ? "No low stock items" : "No stock items found"}
        emptyDescription={lowStockOnly ? "All items are above their low stock threshold." : "Add stock items to start tracking inventory."}
        renderActions={(row) => {
          const item = row as unknown as StockItem;
          return (
            <>
              <button type="button" className="btn-ghost text-[0.75rem] px-2.5 py-1" onClick={() => handleEdit(item)}>Edit</button>
              <button type="button" className="btn-ghost text-[0.75rem] px-2.5 py-1 text-accent" onClick={() => setAdjustTarget(item)}>Adjust</button>
            </>
          );
        }}
      />

      <StockAdjustModal
        open={Boolean(adjustTarget)}
        itemLabel={adjustTarget ? `${adjustTarget.sku || adjustTarget.productId} (${adjustTarget.warehouseName})` : ""}
        loading={adjustLoading}
        onConfirm={handleAdjust}
        onCancel={() => setAdjustTarget(null)}
      />

      <BulkImportModal
        open={showBulkImport}
        warehouses={warehouses}
        loading={bulkLoading}
        vendorId={vendorId}
        onConfirm={handleBulkImport}
        onCancel={() => setShowBulkImport(false)}
      />
    </div>
  );
}
