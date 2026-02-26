"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import type { AxiosInstance } from "axios";
import DataTable, { type Column } from "../ui/DataTable";
import FilterBar, { type FilterDef } from "../ui/FilterBar";
import StatusBadge, { WAREHOUSE_TYPE_COLORS, ACTIVE_INACTIVE_COLORS } from "../ui/StatusBadge";
import ConfirmModal from "../ConfirmModal";
import WarehouseForm from "./WarehouseForm";
import { type Warehouse, type WarehouseFormData, type PagedData, EMPTY_WAREHOUSE_FORM, resolvePage } from "./types";
import { getErrorMessage } from "../../../lib/error";

type Props = {
  apiClient: AxiosInstance;
  apiPrefix: string;
  isAdmin?: boolean;
  vendorId?: string;
};

export default function WarehousesTab({ apiClient, apiPrefix, isAdmin = false, vendorId }: Props) {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [filters, setFilters] = useState<Record<string, string>>({});
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<WarehouseFormData>({ ...EMPTY_WAREHOUSE_FORM });

  const [statusTarget, setStatusTarget] = useState<Warehouse | null>(null);
  const [statusLoading, setStatusLoading] = useState(false);

  const load = useCallback(async (pg = 0) => {
    setLoading(true);
    try {
      const params: Record<string, string | number> = { page: pg, size: 20 };
      if (filters.warehouseType) params.warehouseType = filters.warehouseType;
      if (filters.active) params.active = filters.active;
      if (filters.vendorId) params.vendorId = filters.vendorId;
      const res = await apiClient.get(`${apiPrefix}/warehouses`, { params });
      const resolved = resolvePage(res.data as PagedData<Warehouse>);
      setWarehouses(resolved.content);
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

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error("Warehouse name is required"); return; }
    setSaving(true);
    try {
      if (form.id) {
        const { name, description, addressLine1, addressLine2, city, state, postalCode, countryCode, contactName, contactPhone, contactEmail } = form;
        await apiClient.put(`${apiPrefix}/warehouses/${form.id}`, { name, description, addressLine1, addressLine2, city, state, postalCode, countryCode, contactName, contactPhone, contactEmail });
        toast.success("Warehouse updated");
      } else {
        const payload: Record<string, unknown> = { ...form };
        if (!isAdmin) { delete payload.vendorId; delete payload.warehouseType; }
        await apiClient.post(`${apiPrefix}/warehouses`, payload);
        toast.success("Warehouse created");
      }
      setShowForm(false);
      setForm({ ...EMPTY_WAREHOUSE_FORM });
      await load(page);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (w: Warehouse) => {
    setForm({
      id: w.id,
      name: w.name,
      description: w.description || "",
      vendorId: w.vendorId || "",
      warehouseType: w.warehouseType,
      addressLine1: w.addressLine1 || "",
      addressLine2: w.addressLine2 || "",
      city: w.city || "",
      state: w.state || "",
      postalCode: w.postalCode || "",
      countryCode: w.countryCode || "",
      contactName: w.contactName || "",
      contactPhone: w.contactPhone || "",
      contactEmail: w.contactEmail || "",
    });
    setShowForm(true);
  };

  const handleStatusToggle = async () => {
    if (!statusTarget) return;
    setStatusLoading(true);
    try {
      await apiClient.patch(`${apiPrefix}/warehouses/${statusTarget.id}/status`, { active: !statusTarget.active });
      toast.success(`Warehouse ${statusTarget.active ? "deactivated" : "activated"}`);
      setStatusTarget(null);
      await load(page);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setStatusLoading(false);
    }
  };

  const filterDefs: FilterDef[] = [
    ...(isAdmin ? [{ key: "vendorId", label: "Vendor ID", type: "text" as const, placeholder: "Filter by vendor..." }] : []),
    { key: "warehouseType", label: "Type", type: "select" as const, options: [{ label: "Vendor Owned", value: "VENDOR_OWNED" }, { label: "Platform Managed", value: "PLATFORM_MANAGED" }] },
    { key: "active", label: "Status", type: "select" as const, options: [{ label: "Active", value: "true" }, { label: "Inactive", value: "false" }] },
  ];

  const columns: Column<Warehouse>[] = [
    { key: "name", header: "Name", width: "20%" },
    { key: "warehouseType", header: "Type", render: (v) => <StatusBadge value={String(v)} colorMap={WAREHOUSE_TYPE_COLORS} /> },
    { key: "city", header: "Location", render: (_, row) => [row.city, row.state, row.countryCode].filter(Boolean).join(", ") || "-" },
    { key: "contactName", header: "Contact", render: (v) => String(v || "-") },
    { key: "active", header: "Status", render: (v) => <StatusBadge value={v ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} /> },
    { key: "createdAt", header: "Created", render: (v) => v ? new Date(String(v)).toLocaleDateString() : "-" },
  ];

  if (showForm) {
    return (
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-5">
        <WarehouseForm
          form={form}
          onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          saving={saving}
          onSave={handleSave}
          onCancel={() => { setShowForm(false); setForm({ ...EMPTY_WAREHOUSE_FORM }); }}
          showVendorId={isAdmin}
          showTypeSelect={isAdmin}
        />
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-end mb-3">
        <button type="button" className="btn-primary text-[0.82rem] px-4.5 py-2" onClick={() => { setForm({ ...EMPTY_WAREHOUSE_FORM, vendorId: vendorId || "" }); setShowForm(true); }}>
          + Add Warehouse
        </button>
      </div>

      <FilterBar
        filters={filterDefs}
        values={filters}
        onChange={(key, val) => setFilters((prev) => ({ ...prev, [key]: val }))}
        onClear={() => setFilters({})}
      />

      <DataTable
        columns={columns}
        data={warehouses}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={(p) => void load(p)}
        loading={loading}
        emptyTitle="No warehouses found"
        emptyDescription="Create your first warehouse to start managing inventory."
        renderActions={(row) => {
          const w = row as unknown as Warehouse;
          return (
            <>
              <button type="button" className="btn-ghost text-[0.75rem] px-2.5 py-1" onClick={() => handleEdit(w)}>Edit</button>
              {isAdmin && (
                <button type="button" className={`btn-ghost text-[0.75rem] px-2.5 py-1 ${w.active ? "text-danger" : "text-success"}`} onClick={() => setStatusTarget(w)}>
                  {w.active ? "Deactivate" : "Activate"}
                </button>
              )}
            </>
          );
        }}
      />

      <ConfirmModal
        open={Boolean(statusTarget)}
        title={statusTarget?.active ? "Deactivate Warehouse" : "Activate Warehouse"}
        message={`Are you sure you want to ${statusTarget?.active ? "deactivate" : "activate"} "${statusTarget?.name ?? ""}"?`}
        confirmLabel={statusTarget?.active ? "Deactivate" : "Activate"}
        danger={statusTarget?.active ?? false}
        loading={statusLoading}
        onConfirm={handleStatusToggle}
        onCancel={() => setStatusTarget(null)}
      />
    </div>
  );
}
