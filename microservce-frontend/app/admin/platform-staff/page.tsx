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

type PlatformStaffRow = {
  id: string;
  keycloakUserId: string;
  email: string;
  displayName?: string | null;
  permissions: string[];
  active: boolean;
  deleted: boolean;
  updatedAt?: string;
};

type FormState = {
  id: string | null;
  keycloakUserId: string;
  email: string;
  displayName: string;
  active: boolean;
  permissions: string[];
};

const EMPTY_FORM: FormState = {
  id: null,
  keycloakUserId: "",
  email: "",
  displayName: "",
  active: true,
  permissions: [],
};

const PLATFORM_PERMISSION_OPTIONS = [
  { value: "PRODUCTS_MANAGE", label: "Manage Products", description: "Can create/edit/delete products in admin." },
  { value: "CATEGORIES_MANAGE", label: "Manage Categories", description: "Can create/edit/delete product categories." },
  { value: "ORDERS_READ", label: "View Orders", description: "Can open and review admin orders." },
  { value: "ORDERS_MANAGE", label: "Manage Orders", description: "Can process/update admin orders." },
  { value: "POSTERS_MANAGE", label: "Manage Posters", description: "Can create/edit/delete posters and uploads." },
] as const;


export default function AdminPlatformStaffPage() {
  const router = useRouter();
  const session = useAuthSession();

  const [rows, setRows] = useState<PlatformStaffRow[]>([]);
  const [deletedRows, setDeletedRows] = useState<PlatformStaffRow[]>([]);
  const [deletedLoaded, setDeletedLoaded] = useState(false);
  const [showDeleted, setShowDeleted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingDeleted, setLoadingDeleted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<PlatformStaffRow | null>(null);
  const [confirmRestore, setConfirmRestore] = useState<PlatformStaffRow | null>(null);
  const [actionReason, setActionReason] = useState("");
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [restoringId, setRestoringId] = useState<string | null>(null);
  const [auditTargetRow, setAuditTargetRow] = useState<PlatformStaffRow | null>(null);
  const [accessAuditReloadKey, setAccessAuditReloadKey] = useState(0);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  const activeRows = useMemo(
    () => [...rows].sort((a, b) => `${a.email}`.localeCompare(`${b.email}`)),
    [rows]
  );

  const loadActive = useCallback(async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const res = await session.apiClient.get("/admin/platform-staff");
      const raw = res.data as { content?: PlatformStaffRow[] };
      setRows(raw.content || []);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [session.apiClient]);

  const loadDeleted = useCallback(async () => {
    if (!session.apiClient) return;
    setLoadingDeleted(true);
    try {
      const res = await session.apiClient.get("/admin/platform-staff/deleted");
      const raw = res.data as { content?: PlatformStaffRow[] };
      setDeletedRows(raw.content || []);
      setDeletedLoaded(true);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoadingDeleted(false);
    }
  }, [session.apiClient]);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.canManageAdminVendors) {
      router.replace("/products");
      return;
    }
    void loadActive();
  }, [router, session.status, session.isAuthenticated, session.canManageAdminVendors, loadActive]);

  useEffect(() => {
    if (!showDeleted || deletedLoaded) return;
    void loadDeleted();
  }, [showDeleted, deletedLoaded, loadDeleted]);

  const save = useCallback(async () => {
    if (!session.apiClient || saving) return;
    if (!form.keycloakUserId.trim() || !form.email.trim()) {
      toast.error("Keycloak user ID and email are required");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        keycloakUserId: form.keycloakUserId.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim() || null,
        permissions: form.permissions,
        active: form.active,
      };
      if (form.id) {
        await session.apiClient.put(`/admin/platform-staff/${form.id}`, payload);
        toast.success("Platform staff updated");
      } else {
        await session.apiClient.post("/admin/platform-staff", payload);
        toast.success("Platform staff created");
      }
      setForm(EMPTY_FORM);
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive();
      if (deletedLoaded) {
        await loadDeleted();
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }, [session.apiClient, saving, form, loadActive, loadDeleted, deletedLoaded]);

  const remove = useCallback(async (row: PlatformStaffRow) => {
    if (!session.apiClient) return;
    setDeletingId(row.id);
    try {
      await session.apiClient.delete(`/admin/platform-staff/${row.id}`, {
        headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined,
      });
      toast.success("Platform staff removed");
      setConfirmDelete(null);
      setActionReason("");
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive();
      if (deletedLoaded) {
        await loadDeleted();
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setDeletingId(null);
    }
  }, [session.apiClient, loadActive, loadDeleted, deletedLoaded, actionReason]);

  const restore = useCallback(async (row: PlatformStaffRow) => {
    if (!session.apiClient) return;
    setRestoringId(row.id);
    try {
      await session.apiClient.post(
        `/admin/platform-staff/${row.id}/restore`,
        {},
        { headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined }
      );
      toast.success("Platform staff restored");
      setConfirmRestore(null);
      setActionReason("");
      setAccessAuditReloadKey((n) => n + 1);
      await loadActive();
      if (deletedLoaded) {
        await loadDeleted();
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setRestoringId(null);
    }
  }, [session.apiClient, loadActive, loadDeleted, deletedLoaded, actionReason]);

  const rowsToRender = showDeleted ? deletedRows : activeRows;
  const listBusy = showDeleted ? loadingDeleted : loading;

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
          <Link href="/admin/vendors">Admin</Link><span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Platform Staff</span>
        </nav>

        <div className="grid gap-6 lg:grid-cols-[0.95fr,1.05fr]">
          <section className="space-y-4 rounded-2xl p-5" style={{ background: "var(--surface-2)", border: "1px solid var(--line)" }}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-lg font-semibold" style={{ color: "var(--ink)" }}>
                  {form.id ? "Edit Platform Staff" : "Add Platform Staff"}
                </h1>
                <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                  Manage platform-level permissions for internal staff users.
                </p>
              </div>
              {form.id && (
                <button type="button" className="btn-ghost" onClick={() => setForm(EMPTY_FORM)} disabled={saving}>
                  Cancel Edit
                </button>
              )}
            </div>

            <div className="grid gap-3">
              <KeycloakUserLookupField
                apiClient={session.apiClient}
                disabled={saving}
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
                  placeholder="keycloak user UUID / subject"
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
                  placeholder="staff@example.com"
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
                  placeholder="Platform Catalog Staff"
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
                title="Permissions"
                options={[...PLATFORM_PERMISSION_OPTIONS]}
                selected={form.permissions}
                disabled={saving}
                onChange={(next) => setForm((old) => ({ ...old, permissions: next }))}
              />
            </div>

            <button type="button"
              onClick={() => { void save(); }}
              disabled={saving}
              className="inline-flex items-center justify-center rounded-xl px-4 py-2 text-sm font-semibold disabled:opacity-60"
              style={{ background: "var(--brand-soft)", color: "var(--ink)", border: "1px solid rgba(0,212,255,0.25)" }}
            >
              {saving ? "Saving..." : form.id ? "Update Platform Staff" : "Create Platform Staff"}
            </button>
          </section>

          <section className="space-y-4 rounded-2xl p-5" style={{ background: "var(--surface-2)", border: "1px solid var(--line)" }}>
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold" style={{ color: "var(--ink)" }}>
                  {showDeleted ? "Deleted Platform Staff" : "Platform Staff"}
                </h2>
                <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                  {showDeleted ? "Restore soft-deleted staff access entries." : "Active platform staff access assignments."}
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(false)} disabled={!showDeleted}>Active</button>
                <button type="button" className="btn-ghost" onClick={() => setShowDeleted(true)} disabled={showDeleted || listBusy}>Deleted</button>
              </div>
            </div>

            {listBusy ? (
              <div className="rounded-xl px-4 py-8 text-center text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
                Loading...
              </div>
            ) : rowsToRender.length === 0 ? (
              <div className="rounded-xl px-4 py-8 text-center text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
                No rows found.
              </div>
            ) : (
              <div className="space-y-3">
                {rowsToRender.map((row) => (
                  <div key={row.id} className="rounded-xl p-4" style={{ border: "1px solid var(--line)", background: "rgba(255,255,255,0.02)" }}>
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-sm font-semibold" style={{ color: "var(--ink)" }}>
                            {row.displayName || row.email}
                          </p>
                          <span className="rounded-full px-2 py-0.5 text-[10px] font-semibold" style={{
                            background: row.active ? "rgba(34,197,94,0.12)" : "rgba(251,191,36,0.12)",
                            color: row.active ? "#86efac" : "#fde68a",
                            border: "1px solid rgba(255,255,255,0.08)"
                          }}>
                            {row.active ? "Active" : "Inactive"}
                          </span>
                        </div>
                        <p className="text-xs" style={{ color: "rgba(255,255,255,0.65)" }}>{row.email}</p>
                        <p className="text-[11px] font-mono" style={{ color: "rgba(255,255,255,0.5)" }}>{row.keycloakUserId}</p>
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
                            <button type="button" className="btn-ghost" onClick={() => { setActionReason(""); setConfirmDelete(row); }} disabled={deletingId === row.id}>
                              {deletingId === row.id ? "Deleting..." : "Delete"}
                            </button>
                          </>
                        )}
                        {showDeleted && (
                          <>
                            <button type="button" className="btn-ghost" onClick={() => setAuditTargetRow(row)}>
                              History
                            </button>
                            <button type="button" className="btn-ghost" onClick={() => { setActionReason(""); setConfirmRestore(row); }} disabled={restoringId === row.id}>
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
              title="Platform Staff Access Audit"
              targetType="PLATFORM_STAFF"
              targetId={auditTargetRow?.id || null}
              reloadKey={accessAuditReloadKey}
            />
          </section>
        </div>
      </main>

      <ConfirmModal
        open={Boolean(confirmDelete)}
        title="Delete Platform Staff"
        message={confirmDelete ? `Soft delete access for ${confirmDelete.displayName || confirmDelete.email}?` : ""}
        confirmLabel={deletingId ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={Boolean(deletingId)}
        reasonEnabled
        reasonLabel="Reason for access audit (optional)"
        reasonPlaceholder="Why are you removing this platform staff access?"
        reasonValue={actionReason}
        onReasonChange={setActionReason}
        onCancel={() => { setConfirmDelete(null); setActionReason(""); }}
        onConfirm={() => {
          if (confirmDelete) void remove(confirmDelete);
        }}
      />
      <ConfirmModal
        open={Boolean(confirmRestore)}
        title="Restore Platform Staff"
        message={confirmRestore ? `Restore access for ${confirmRestore.displayName || confirmRestore.email}?` : ""}
        confirmLabel={restoringId ? "Restoring..." : "Restore"}
        cancelLabel="Cancel"
        loading={Boolean(restoringId)}
        reasonEnabled
        reasonLabel="Reason for access audit (optional)"
        reasonPlaceholder="Why are you restoring this platform staff access?"
        reasonValue={actionReason}
        onReasonChange={setActionReason}
        onCancel={() => { setConfirmRestore(null); setActionReason(""); }}
        onConfirm={() => {
          if (confirmRestore) void restore(confirmRestore);
        }}
      />
    </div>
  );
}

