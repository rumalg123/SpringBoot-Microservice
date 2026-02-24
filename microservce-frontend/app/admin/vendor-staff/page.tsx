"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "@/app/components/AppNav";
import ConfirmModal from "@/app/components/ConfirmModal";
import AccessAuditPanel from "@/app/components/admin/access/AccessAuditPanel";
import KeycloakUserLookupField from "@/app/components/admin/access/KeycloakUserLookupField";
import PermissionChecklist from "@/app/components/admin/access/PermissionChecklist";
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
  const router = useRouter();
  const session = useAuthSession();

  const [vendors, setVendors] = useState<VendorOption[]>([]);
  const [vendorsLoading, setVendorsLoading] = useState(false);
  const [rows, setRows] = useState<VendorStaffRow[]>([]);
  const [deletedRows, setDeletedRows] = useState<VendorStaffRow[]>([]);
  const [deletedLoaded, setDeletedLoaded] = useState(false);
  const [showDeleted, setShowDeleted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingDeleted, setLoadingDeleted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [restoringId, setRestoringId] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<VendorStaffRow | null>(null);
  const [confirmRestore, setConfirmRestore] = useState<VendorStaffRow | null>(null);
  const [actionReason, setActionReason] = useState("");
  const [auditTargetRow, setAuditTargetRow] = useState<VendorStaffRow | null>(null);
  const [accessAuditReloadKey, setAccessAuditReloadKey] = useState(0);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [vendorFilterId, setVendorFilterId] = useState("");

  const vendorNameMap = useMemo(() => new Map(vendors.map((v) => [v.id, v.name])), [vendors]);
  const canManagePage = session.isSuperAdmin || session.isVendorAdmin;
  const multipleVendorOptions = vendors.length > 1;

  const loadVendors = useCallback(async () => {
    if (!session.apiClient) return;
    setVendorsLoading(true);
    try {
      if (session.isSuperAdmin) {
        const res = await session.apiClient.get("/admin/vendors");
        const rows = ((res.data as Array<{ id: string; name: string; slug?: string; deleted?: boolean }>) || [])
          .filter((row) => row && !row.deleted)
          .map((row) => ({ id: row.id, name: row.name, slug: row.slug }))
          .sort((a, b) => a.name.localeCompare(b.name));
        setVendors(rows);
        return;
      }
      const res = await session.apiClient.get("/admin/me/capabilities");
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
      const options = Array.from(unique.values()).sort((a, b) => a.name.localeCompare(b.name));
      setVendors(options);
    } catch (error) {
      toast.error(getErrorMessage(error));
      setVendors([]);
    } finally {
      setVendorsLoading(false);
    }
  }, [session.apiClient, session.isSuperAdmin]);

  const loadActive = useCallback(async (vendorIdOverride?: string) => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const params = new URLSearchParams();
      const effectiveVendorId = (vendorIdOverride ?? vendorFilterId).trim();
      if (effectiveVendorId) params.set("vendorId", effectiveVendorId);
      const res = await session.apiClient.get(`/admin/vendor-staff${params.toString() ? `?${params.toString()}` : ""}`);
      const raw = res.data as { content?: VendorStaffRow[] };
      setRows(raw.content || []);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [session.apiClient, vendorFilterId]);

  const loadDeleted = useCallback(async (vendorIdOverride?: string) => {
    if (!session.apiClient) return;
    setLoadingDeleted(true);
    try {
      const params = new URLSearchParams();
      const effectiveVendorId = (vendorIdOverride ?? vendorFilterId).trim();
      if (effectiveVendorId) params.set("vendorId", effectiveVendorId);
      const res = await session.apiClient.get(`/admin/vendor-staff/deleted${params.toString() ? `?${params.toString()}` : ""}`);
      const raw = res.data as { content?: VendorStaffRow[] };
      setDeletedRows(raw.content || []);
      setDeletedLoaded(true);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoadingDeleted(false);
    }
  }, [session.apiClient, vendorFilterId]);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!canManagePage) {
      router.replace("/products");
      return;
    }
    void loadVendors();
  }, [router, session.status, session.isAuthenticated, canManagePage, loadVendors]);

  useEffect(() => {
    if (vendors.length === 0) return;
    setVendorFilterId((current) => current || (vendors.length === 1 ? vendors[0].id : current));
    setForm((current) => {
      if (current.vendorId) return current;
      if (vendors.length === 1) return { ...current, vendorId: vendors[0].id };
      return current;
    });
  }, [vendors]);

  useEffect(() => {
    if (!canManagePage || session.status !== "ready") return;
    const effectiveVendorId = vendorFilterId.trim();
    if (session.isVendorAdmin && vendors.length > 1 && !effectiveVendorId) {
      setRows([]);
      return;
    }
    void loadActive(effectiveVendorId);
  }, [canManagePage, session.status, session.isVendorAdmin, vendors.length, vendorFilterId, loadActive]);

  useEffect(() => {
    if (!showDeleted || deletedLoaded === false && loadingDeleted) return;
    if (!showDeleted || !canManagePage || session.status !== "ready") return;
    const effectiveVendorId = vendorFilterId.trim();
    if (session.isVendorAdmin && vendors.length > 1 && !effectiveVendorId) {
      setDeletedRows([]);
      return;
    }
    if (!deletedLoaded) {
      void loadDeleted(effectiveVendorId);
    }
  }, [showDeleted, deletedLoaded, canManagePage, session.status, session.isVendorAdmin, vendors.length, vendorFilterId, loadDeleted, loadingDeleted]);

  useEffect(() => {
    if (!showDeleted || !deletedLoaded) return;
    if (!canManagePage) return;
    const effectiveVendorId = vendorFilterId.trim();
    if (session.isVendorAdmin && vendors.length > 1 && !effectiveVendorId) {
      setDeletedRows([]);
      return;
    }
    void loadDeleted(effectiveVendorId);
  }, [vendorFilterId, showDeleted]); // intentional lightweight refresh

  const save = useCallback(async () => {
    if (!session.apiClient || saving) return;
    const vendorId = form.vendorId.trim();
    if (!vendorId || !form.keycloakUserId.trim() || !form.email.trim()) {
      toast.error("Vendor, Keycloak user ID and email are required");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        vendorId,
        keycloakUserId: form.keycloakUserId.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim() || null,
        permissions: form.permissions,
        active: form.active,
      };
      if (form.id) {
        await session.apiClient.put(`/admin/vendor-staff/${form.id}`, payload);
        toast.success("Vendor staff updated");
      } else {
        await session.apiClient.post("/admin/vendor-staff", payload);
        toast.success("Vendor staff created");
      }
      setForm((old) => ({ ...EMPTY_FORM, vendorId: old.vendorId || vendorId }));
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive(vendorId);
      if (deletedLoaded) {
        await loadDeleted(vendorId);
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }, [session.apiClient, saving, form, loadActive, loadDeleted, deletedLoaded]);

  const remove = useCallback(async (row: VendorStaffRow) => {
    if (!session.apiClient) return;
    setDeletingId(row.id);
    try {
      await session.apiClient.delete(`/admin/vendor-staff/${row.id}`, {
        headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined,
      });
      toast.success("Vendor staff removed");
      setConfirmDelete(null);
      setActionReason("");
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive(row.vendorId);
      if (deletedLoaded) await loadDeleted(row.vendorId);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setDeletingId(null);
    }
  }, [session.apiClient, loadActive, loadDeleted, deletedLoaded, actionReason]);

  const restore = useCallback(async (row: VendorStaffRow) => {
    if (!session.apiClient) return;
    setRestoringId(row.id);
    try {
      await session.apiClient.post(
        `/admin/vendor-staff/${row.id}/restore`,
        {},
        { headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined }
      );
      toast.success("Vendor staff restored");
      setConfirmRestore(null);
      setActionReason("");
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive(row.vendorId);
      if (deletedLoaded) await loadDeleted(row.vendorId);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setRestoringId(null);
    }
  }, [session.apiClient, loadActive, loadDeleted, deletedLoaded, actionReason]);

  const rowsToRender = showDeleted ? deletedRows : rows;
  const listBusy = showDeleted ? loadingDeleted : loading;
  const vendorSelectionRequired = session.isVendorAdmin && multipleVendorOptions && !vendorFilterId.trim();

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link><span className="breadcrumb-sep">›</span>
          <Link href="/admin/products">Admin</Link><span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Vendor Staff</span>
        </nav>

        <div className="grid gap-6 lg:grid-cols-[0.95fr,1.05fr]">
          <section className="space-y-4 rounded-2xl p-5" style={{ background: "var(--surface-2)", border: "1px solid var(--line)" }}>
            <div>
              <h1 className="text-lg font-semibold" style={{ color: "var(--ink)" }}>
                {form.id ? "Edit Vendor Staff" : "Add Vendor Staff"}
              </h1>
              <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                {session.isSuperAdmin
                  ? "Assign vendor-scoped permissions to staff users."
                  : "Manage staff access only for your vendor(s)."}
              </p>
            </div>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
                Vendor
              </span>
              <select
                value={form.vendorId}
                onChange={(e) => setForm((old) => ({ ...old, vendorId: e.target.value }))}
                disabled={vendorsLoading || (session.isVendorAdmin && vendors.length === 1)}
                className="w-full rounded-xl px-3 py-2"
                style={{ background: "var(--surface-2)", border: "1px solid var(--line)", color: "var(--ink)" }}
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
              <span className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
                Keycloak User ID
              </span>
              <input
                value={form.keycloakUserId}
                onChange={(e) => setForm((old) => ({ ...old, keycloakUserId: e.target.value }))}
                className="w-full rounded-xl px-3 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
                Email
              </span>
              <input
                type="email"
                value={form.email}
                onChange={(e) => setForm((old) => ({ ...old, email: e.target.value }))}
                className="w-full rounded-xl px-3 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
                Display Name
              </span>
              <input
                value={form.displayName}
                onChange={(e) => setForm((old) => ({ ...old, displayName: e.target.value }))}
                className="w-full rounded-xl px-3 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl px-3 py-2" style={{ border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm((old) => ({ ...old, active: e.target.checked }))}
              />
              <span className="text-sm" style={{ color: "var(--ink)" }}>Active</span>
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
                onClick={() => { void save(); }}
                disabled={saving}
                className="inline-flex items-center justify-center rounded-xl px-4 py-2 text-sm font-semibold disabled:opacity-60"
                style={{ background: "var(--brand-soft)", color: "var(--ink)", border: "1px solid rgba(0,212,255,0.25)" }}
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

          <section className="space-y-4 rounded-2xl p-5" style={{ background: "var(--surface-2)", border: "1px solid var(--line)" }}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold" style={{ color: "var(--ink)" }}>
                  {showDeleted ? "Deleted Vendor Staff" : "Vendor Staff"}
                </h2>
                <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                  Manage vendor-scoped staff permission rows.
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(false)} disabled={!showDeleted}>Active</button>
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(true)} disabled={showDeleted || listBusy}>Deleted</button>
              </div>
            </div>

            <label className="space-y-1 text-sm">
              <span className="block text-xs font-semibold uppercase tracking-[0.12em]" style={{ color: "rgba(255,255,255,0.65)" }}>
                Vendor Filter
              </span>
              <select
                value={vendorFilterId}
                onChange={(e) => {
                  setVendorFilterId(e.target.value);
                  setDeletedLoaded(false);
                }}
                disabled={vendorsLoading || (session.isVendorAdmin && vendors.length === 1)}
                className="w-full rounded-xl px-3 py-2"
                style={{ background: "var(--surface-2)", border: "1px solid var(--line)", color: "var(--ink)" }}
              >
                <option value="">{multipleVendorOptions ? "Select vendor" : "All vendors"}</option>
                {vendors.map((vendor) => (
                  <option key={vendor.id} value={vendor.id}>{vendor.name}</option>
                ))}
              </select>
            </label>

            {vendorSelectionRequired ? (
              <div className="rounded-xl px-4 py-6 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.7)" }}>
                Select a vendor to manage vendor staff rows.
              </div>
            ) : listBusy ? (
              <div className="rounded-xl px-4 py-6 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.7)" }}>
                Loading...
              </div>
            ) : rowsToRender.length === 0 ? (
              <div className="rounded-xl px-4 py-6 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.7)" }}>
                No vendor staff rows found.
              </div>
            ) : (
              <div className="space-y-3">
                {rowsToRender.map((row) => (
                  <div key={row.id} className="rounded-xl p-4" style={{ border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <p className="text-sm font-semibold" style={{ color: "var(--ink)" }}>
                          {row.displayName || row.email}
                        </p>
                        <p className="text-xs" style={{ color: "rgba(255,255,255,0.65)" }}>{row.email}</p>
                        <p className="text-[11px] font-mono" style={{ color: "rgba(255,255,255,0.5)" }}>{row.keycloakUserId}</p>
                        <p className="text-xs" style={{ color: "rgba(0,212,255,0.8)" }}>
                          Vendor: {vendorNameMap.get(row.vendorId) || row.vendorId}
                        </p>
                        <div className="flex flex-wrap gap-2 pt-1">
                          {(row.permissions || []).map((permission) => (
                            <span key={permission} className="rounded-full px-2 py-0.5 text-[10px]" style={{ border: "1px solid rgba(0,212,255,0.15)", color: "rgba(0,212,255,0.85)" }}>
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
      </main>

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
        onConfirm={() => { if (confirmDelete) void remove(confirmDelete); }}
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
        onConfirm={() => { if (confirmRestore) void restore(confirmRestore); }}
      />
    </div>
  );
}

