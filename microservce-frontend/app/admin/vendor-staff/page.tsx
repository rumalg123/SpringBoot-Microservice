"use client";

import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import ConfirmModal from "@/app/components/ConfirmModal";
import AccessAuditPanel from "@/app/components/admin/access/AccessAuditPanel";
import KeycloakUserLookupField from "@/app/components/admin/access/KeycloakUserLookupField";
import PermissionChecklist from "@/app/components/admin/access/PermissionChecklist";
import AdminPageShell from "@/app/components/ui/AdminPageShell";
import { useAuthSession } from "@/lib/authSession";
import { getErrorMessage } from "@/lib/error";

type VendorOption = {
  id: string;
  name: string;
  slug?: string;
};

type VendorStaffRow = {
  id: string;
  vendorId: string;
  keycloakUserId: string;
  email: string;
  displayName?: string | null;
  permissions: string[];
  active: boolean;
  deleted: boolean;
};

type FormState = {
  id: string | null;
  vendorId: string;
  keycloakUserId: string;
  email: string;
  displayName: string;
  active: boolean;
  permissions: string[];
};

type CapabilitiesResponse = {
  vendorMemberships?: Array<{ vendorId?: string; vendorName?: string; vendorSlug?: string }>;
};

const EMPTY_FORM: FormState = {
  id: null,
  vendorId: "",
  keycloakUserId: "",
  email: "",
  displayName: "",
  active: true,
  permissions: [],
};

const VENDOR_PERMISSION_OPTIONS = [
  { value: "PRODUCTS_MANAGE", label: "Manage Products", description: "Can create/edit vendor products." },
  { value: "ORDERS_READ", label: "View Orders", description: "Can read vendor orders." },
  { value: "ORDERS_MANAGE", label: "Manage Orders", description: "Can update/process vendor orders." },
] as const;


export default function AdminVendorStaffPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();

  const [showDeleted, setShowDeleted] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<VendorStaffRow | null>(null);
  const [confirmRestore, setConfirmRestore] = useState<VendorStaffRow | null>(null);
  const [actionReason, setActionReason] = useState("");
  const [auditTargetRow, setAuditTargetRow] = useState<VendorStaffRow | null>(null);
  const [accessAuditReloadKey, setAccessAuditReloadKey] = useState(0);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [vendorFilterId, setVendorFilterId] = useState("");

  const ready = session.status === "ready" && session.isAuthenticated;
  const canManagePage = session.isSuperAdmin || session.isVendorAdmin;

  // --- Vendors query ---
  const { data: vendors = [], isLoading: vendorsLoading } = useQuery({
    queryKey: ["admin-vendor-staff-vendors", session.isSuperAdmin],
    queryFn: async () => {
      if (session.isSuperAdmin) {
        const res = await session.apiClient!.get("/admin/vendors");
        return ((res.data as Array<{ id: string; name: string; slug?: string; deleted?: boolean }>) || [])
          .filter((row) => row && !row.deleted)
          .map((row) => ({ id: row.id, name: row.name, slug: row.slug }))
          .sort((a, b) => a.name.localeCompare(b.name));
      }
      const res = await session.apiClient!.get("/admin/me/capabilities");
      const caps = (res.data as CapabilitiesResponse) || {};
      const memberships = (caps.vendorMemberships || [])
        .map((m) => ({
          id: String(m.vendorId || "").trim(),
          name: String(m.vendorName || m.vendorSlug || m.vendorId || "").trim(),
          slug: m.vendorSlug,
        }))
        .filter((v) => v.id && v.name);
      const unique = new Map<string, VendorOption>();
      memberships.forEach((v) => unique.set(v.id, v));
      return Array.from(unique.values()).sort((a, b) => a.name.localeCompare(b.name));
    },
    enabled: ready && !!session.apiClient,
  });

  const vendorNameMap = useMemo(() => new Map(vendors.map((v) => [v.id, v.name])), [vendors]);
  const multipleVendorOptions = vendors.length > 1;

  // Auto-select vendor when only one option
  useEffect(() => {
    if (vendors.length === 0) return;
    setVendorFilterId((current) => current || (vendors.length === 1 ? vendors[0].id : current));
    setForm((current) => {
      if (current.vendorId) return current;
      if (vendors.length === 1) return { ...current, vendorId: vendors[0].id };
      return current;
    });
  }, [vendors]);

  const vendorSelectionRequired = session.isVendorAdmin && multipleVendorOptions && !vendorFilterId.trim();

  // --- Active vendor staff query ---
  const { data: rows = [], isLoading: loading } = useQuery({
    queryKey: ["admin-vendor-staff", vendorFilterId],
    queryFn: async () => {
      const params = new URLSearchParams();
      const effectiveVendorId = vendorFilterId.trim();
      if (effectiveVendorId) params.set("vendorId", effectiveVendorId);
      const res = await session.apiClient!.get(`/admin/vendor-staff${params.toString() ? `?${params.toString()}` : ""}`);
      const raw = res.data as { content?: VendorStaffRow[] };
      return raw.content || [];
    },
    enabled: ready && !!session.apiClient && canManagePage && !vendorSelectionRequired,
  });

  // --- Deleted vendor staff query ---
  const { data: deletedRows = [], isLoading: loadingDeleted } = useQuery({
    queryKey: ["admin-vendor-staff-deleted", vendorFilterId],
    queryFn: async () => {
      const params = new URLSearchParams();
      const effectiveVendorId = vendorFilterId.trim();
      if (effectiveVendorId) params.set("vendorId", effectiveVendorId);
      const res = await session.apiClient!.get(`/admin/vendor-staff/deleted${params.toString() ? `?${params.toString()}` : ""}`);
      const raw = res.data as { content?: VendorStaffRow[] };
      return raw.content || [];
    },
    enabled: ready && !!session.apiClient && canManagePage && showDeleted && !vendorSelectionRequired,
  });

  const invalidateAll = () => {
    void queryClient.invalidateQueries({ queryKey: ["admin-vendor-staff"] });
    void queryClient.invalidateQueries({ queryKey: ["admin-vendor-staff-deleted"] });
    setAccessAuditReloadKey((n) => n + 1);
  };

  const saveMutation = useMutation({
    mutationFn: async () => {
      const vendorId = form.vendorId.trim();
      if (!vendorId || !form.keycloakUserId.trim() || !form.email.trim()) {
        throw new Error("Vendor, Keycloak user ID and email are required");
      }
      const payload = {
        vendorId,
        keycloakUserId: form.keycloakUserId.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim() || null,
        permissions: form.permissions,
        active: form.active,
      };
      if (form.id) {
        await session.apiClient!.put(`/admin/vendor-staff/${form.id}`, payload);
        return "updated";
      } else {
        await session.apiClient!.post("/admin/vendor-staff", payload);
        return "created";
      }
    },
    onSuccess: (result) => {
      toast.success(result === "updated" ? "Vendor staff updated" : "Vendor staff created");
      setForm((old) => ({ ...EMPTY_FORM, vendorId: old.vendorId || form.vendorId.trim() }));
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const removeMutation = useMutation({
    mutationFn: async (row: VendorStaffRow) => {
      await session.apiClient!.delete(`/admin/vendor-staff/${row.id}`, {
        headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined,
      });
      return row;
    },
    onSuccess: () => {
      toast.success("Vendor staff removed");
      setConfirmDelete(null);
      setActionReason("");
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const restoreMutation = useMutation({
    mutationFn: async (row: VendorStaffRow) => {
      await session.apiClient!.post(
        `/admin/vendor-staff/${row.id}/restore`,
        {},
        { headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined }
      );
      return row;
    },
    onSuccess: () => {
      toast.success("Vendor staff restored");
      setConfirmRestore(null);
      setActionReason("");
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const saving = saveMutation.isPending;
  const deletingId = removeMutation.isPending ? (removeMutation.variables as VendorStaffRow | undefined)?.id ?? null : null;
  const restoringId = restoreMutation.isPending ? (restoreMutation.variables as VendorStaffRow | undefined)?.id ?? null : null;

  const rowsToRender = showDeleted ? deletedRows : rows;
  const listBusy = showDeleted ? loadingDeleted : loading;

  return (
    <>
    <AdminPageShell
      title="Vendor Staff"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Vendor Staff" }]}
    >
        <div className="grid gap-6 lg:grid-cols-[0.95fr,1.05fr]">
          <section className="space-y-4 rounded-2xl p-5 bg-surface-2 border border-line">
            <div>
              <h1 className="text-lg font-semibold text-ink">
                {form.id ? "Edit Vendor Staff" : "Add Vendor Staff"}
              </h1>
              <p className="text-xs text-[rgba(255,255,255,0.6)]">
                {session.isSuperAdmin
                  ? "Assign vendor-scoped permissions to staff users."
                  : "Manage staff access only for your vendor(s)."}
              </p>
            </div>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                Vendor
              </span>
              <select
                value={form.vendorId}
                onChange={(e) => setForm((old) => ({ ...old, vendorId: e.target.value }))}
                disabled={vendorsLoading || (session.isVendorAdmin && vendors.length === 1)}
                className="w-full rounded-xl px-3 py-2 bg-surface-2 border border-line text-ink"
              >
                <option value="">Select vendor</option>
                {vendors.map((vendor) => (
                  <option key={vendor.id} value={vendor.id}>{vendor.name}</option>
                ))}
              </select>
            </label>

            <KeycloakUserLookupField
              apiClient={session.apiClient}
              disabled={saving}
              helperText="Search existing Keycloak users and autofill the fields below for vendor staff access."
              onSelect={(user) => setForm((old) => ({
                ...old,
                keycloakUserId: user.id || old.keycloakUserId,
                email: (user.email || "").trim() || old.email,
                displayName: (user.displayName || user.email || old.displayName || "").trim(),
              }))}
            />

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                Keycloak User ID
              </span>
              <input
                value={form.keycloakUserId}
                onChange={(e) => setForm((old) => ({ ...old, keycloakUserId: e.target.value }))}
                className="w-full rounded-xl px-3 py-2 bg-[rgba(255,255,255,0.03)] border border-line text-ink"
              />
            </label>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                Email
              </span>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm((old) => ({ ...old, email: e.target.value }))}
                className="w-full rounded-xl px-3 py-2 bg-[rgba(255,255,255,0.03)] border border-line text-ink"
              />
            </label>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                Display Name
              </span>
              <input
                value={form.displayName}
                onChange={(e) => setForm((old) => ({ ...old, displayName: e.target.value }))}
                className="w-full rounded-xl px-3 py-2 bg-[rgba(255,255,255,0.03)] border border-line text-ink"
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl px-3 py-2 border border-line bg-[rgba(255,255,255,0.02)]">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((old) => ({ ...old, active: e.target.checked }))}
              />
              <span className="text-sm text-ink">Active</span>
            </label>

            <PermissionChecklist
              title="Vendor Permissions"
              options={[...VENDOR_PERMISSION_OPTIONS]}
              selected={form.permissions}
              disabled={saving}
              onChange={(next) => setForm((old) => ({ ...old, permissions: next }))}
            />

            <div className="flex gap-2">
              <button type="button"
                onClick={() => saveMutation.mutate()}
                disabled={saving}
                className="inline-flex items-center justify-center rounded-xl px-4 py-2 text-sm font-semibold disabled:opacity-60 bg-brand-soft text-ink border border-[rgba(0,212,255,0.25)]"
              >
                {saving ? "Saving..." : form.id ? "Update Vendor Staff" : "Create Vendor Staff"}
              </button>
              {form.id && (
                <button type="button" className="btn-ghost" onClick={() => setForm((old) => ({ ...EMPTY_FORM, vendorId: old.vendorId }))} disabled={saving}>
                  Cancel Edit
                </button>
              )}
            </div>
          </section>

          <section className="space-y-4 rounded-2xl p-5 bg-surface-2 border border-line">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-ink">
                  {showDeleted ? "Deleted Vendor Staff" : "Vendor Staff"}
                </h2>
                <p className="text-xs text-[rgba(255,255,255,0.6)]">
                  Manage vendor-scoped staff permission rows.
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(false)} disabled={!showDeleted}>Active</button>
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(true)} disabled={showDeleted || listBusy}>Deleted</button>
              </div>
            </div>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                Vendor Filter
              </span>
              <select
                value={vendorFilterId}
                onChange={(e) => {
                  setVendorFilterId(e.target.value);
                }}
                disabled={vendorsLoading || (session.isVendorAdmin && vendors.length === 1)}
                className="w-full rounded-xl px-3 py-2 bg-surface-2 border border-line text-ink"
              >
                <option value="">{multipleVendorOptions ? "Select vendor" : "All vendors"}</option>
                {vendors.map((vendor) => (
                  <option key={vendor.id} value={vendor.id}>{vendor.name}</option>
                ))}
              </select>
            </label>

            {vendorSelectionRequired ? (
              <div className="rounded-xl px-4 py-6 text-sm border border-dashed border-line text-[rgba(255,255,255,0.7)]">
                Select a vendor to manage vendor staff rows.
              </div>
            ) : listBusy ? (
              <div className="rounded-xl px-4 py-6 text-sm border border-dashed border-line text-[rgba(255,255,255,0.7)]">
                Loading...
              </div>
            ) : rowsToRender.length === 0 ? (
              <div className="rounded-xl px-4 py-6 text-sm border border-dashed border-line text-[rgba(255,255,255,0.7)]">
                No vendor staff rows found.
              </div>
            ) : (
              <div className="space-y-3">
                {rowsToRender.map((row) => (
                  <div key={row.id} className="rounded-xl p-4 border border-line bg-[rgba(255,255,255,0.02)]">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <p className="text-sm font-semibold text-ink">
                          {row.displayName || row.email}
                        </p>
                        <p className="text-xs text-[rgba(255,255,255,0.65)]">{row.email}</p>
                        <p className="text-[11px] font-mono text-[rgba(255,255,255,0.5)]">{row.keycloakUserId}</p>
                        <p className="text-xs text-[rgba(0,212,255,0.8)]">
                          Vendor: {vendorNameMap.get(row.vendorId) || row.vendorId}
                        </p>
                        <div className="flex flex-wrap gap-2 pt-1">
                          {(row.permissions || []).map((permission) => (
                            <span key={permission} className="rounded-full px-2 py-0.5 text-[10px] border border-[rgba(0,212,255,0.15)] text-[rgba(0,212,255,0.85)]">
                              {permission}
                            </span>
                          ))}
                        </div>
                      </div>
                      <div className="flex gap-2">
                        {!showDeleted && (
                          <>
                            <button type="button"
                              className="btn-ghost"
                              onClick={() => setForm({
                                id: row.id,
                                vendorId: row.vendorId,
                                keycloakUserId: row.keycloakUserId,
                                email: row.email,
                                displayName: row.displayName || "",
                                active: row.active,
                                permissions: [...(row.permissions || [])],
                              })}
                            >
                              Edit
                            </button>
                            <button type="button" className="btn-ghost" onClick={() => setAuditTargetRow(row)}>
                              History
                            </button>
                            <button type="button" className="btn-ghost" disabled={deletingId === row.id} onClick={() => { setActionReason(""); setConfirmDelete(row); }}>
                              {deletingId === row.id ? "Deleting..." : "Delete"}
                            </button>
                          </>
                        )}
                        {showDeleted && (
                          <>
                            <button type="button" className="btn-ghost" onClick={() => setAuditTargetRow(row)}>
                              History
                            </button>
                            <button type="button" className="btn-ghost" disabled={restoringId === row.id} onClick={() => { setActionReason(""); setConfirmRestore(row); }}>
                              {restoringId === row.id ? "Restoring..." : "Restore"}
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            <AccessAuditPanel
              apiClient={session.apiClient}
              title="Vendor Staff Access Audit"
              targetType="VENDOR_STAFF"
              targetId={auditTargetRow?.id || null}
              vendorId={auditTargetRow?.vendorId || vendorFilterId || null}
              reloadKey={accessAuditReloadKey}
            />
          </section>
        </div>
    </AdminPageShell>

      <ConfirmModal
        open={Boolean(confirmDelete)}
        title="Delete Vendor Staff"
        message={confirmDelete ? `Soft delete vendor staff access for ${confirmDelete.displayName || confirmDelete.email}?` : ""}
        confirmLabel={deletingId ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={Boolean(deletingId)}
        reasonEnabled
        reasonLabel="Reason for access audit (optional)"
        reasonPlaceholder="Why are you removing this vendor staff access?"
        reasonValue={actionReason}
        onReasonChange={setActionReason}
        onCancel={() => { setConfirmDelete(null); setActionReason(""); }}
        onConfirm={() => { if (confirmDelete) removeMutation.mutate(confirmDelete); }}
      />
      <ConfirmModal
        open={Boolean(confirmRestore)}
        title="Restore Vendor Staff"
        message={confirmRestore ? `Restore vendor staff access for ${confirmRestore.displayName || confirmRestore.email}?` : ""}
        confirmLabel={restoringId ? "Restoring..." : "Restore"}
        cancelLabel="Cancel"
        loading={Boolean(restoringId)}
        reasonEnabled
        reasonLabel="Reason for access audit (optional)"
        reasonPlaceholder="Why are you restoring this vendor staff access?"
        reasonValue={actionReason}
        onReasonChange={setActionReason}
        onCancel={() => { setConfirmRestore(null); setActionReason(""); }}
        onConfirm={() => { if (confirmRestore) restoreMutation.mutate(confirmRestore); }}
      />
    </>
  );
}

