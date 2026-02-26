"use client";

import { useMemo, useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import ConfirmModal from "@/app/components/ConfirmModal";
import AccessAuditPanel from "@/app/components/admin/access/AccessAuditPanel";
import KeycloakUserLookupField from "@/app/components/admin/access/KeycloakUserLookupField";
import PermissionChecklist from "@/app/components/admin/access/PermissionChecklist";
import AdminPageShell from "@/app/components/ui/AdminPageShell";
import { useAuthSession } from "@/lib/authSession";
import { getErrorMessage } from "@/lib/error";
import { normalizePage, type PagedResponse } from "@/lib/types/pagination";

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
  const session = useAuthSession();
  const queryClient = useQueryClient();

  const [showDeleted, setShowDeleted] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<PlatformStaffRow | null>(null);
  const [confirmRestore, setConfirmRestore] = useState<PlatformStaffRow | null>(null);
  const [actionReason, setActionReason] = useState("");
  const [auditTargetRow, setAuditTargetRow] = useState<PlatformStaffRow | null>(null);
  const [accessAuditReloadKey, setAccessAuditReloadKey] = useState(0);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [page, setPage] = useState(0);
  const [deletedPage, setDeletedPage] = useState(0);
  const PAGE_SIZE = 25;

  const ready = session.status === "ready" && session.isAuthenticated;

  const { data: staffPage, isLoading: loading } = useQuery({
    queryKey: ["admin-platform-staff", page],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      const res = await session.apiClient!.get(`/admin/platform-staff?${params.toString()}`);
      return normalizePage(res.data as PagedResponse<PlatformStaffRow>);
    },
    enabled: ready && !!session.apiClient,
  });

  const rows = staffPage?.content ?? [];
  const totalPages = staffPage?.totalPages ?? 0;

  const { data: deletedStaffPage, isLoading: loadingDeleted } = useQuery({
    queryKey: ["admin-platform-staff-deleted", deletedPage],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(deletedPage), size: String(PAGE_SIZE) });
      const res = await session.apiClient!.get(`/admin/platform-staff/deleted?${params.toString()}`);
      return normalizePage(res.data as PagedResponse<PlatformStaffRow>);
    },
    enabled: ready && !!session.apiClient && showDeleted,
  });

  const deletedRows = deletedStaffPage?.content ?? [];
  const deletedTotalPages = deletedStaffPage?.totalPages ?? 0;

  const activeRows = useMemo(
    () => [...rows].sort((a, b) => `${a.email}`.localeCompare(`${b.email}`)),
    [rows]
  );

  const invalidateAll = () => {
    setPage(0);
    setDeletedPage(0);
    void queryClient.invalidateQueries({ queryKey: ["admin-platform-staff"] });
    void queryClient.invalidateQueries({ queryKey: ["admin-platform-staff-deleted"] });
    setAccessAuditReloadKey((n) => n + 1);
  };

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!form.keycloakUserId.trim() || !form.email.trim()) {
        throw new Error("Keycloak user ID and email are required");
      }
      const payload = {
        keycloakUserId: form.keycloakUserId.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim() || null,
        permissions: form.permissions,
        active: form.active,
      };
      if (form.id) {
        await session.apiClient!.put(`/admin/platform-staff/${form.id}`, payload);
        return "updated";
      } else {
        await session.apiClient!.post("/admin/platform-staff", payload);
        return "created";
      }
    },
    onSuccess: (result) => {
      toast.success(result === "updated" ? "Platform staff updated" : "Platform staff created");
      setForm(EMPTY_FORM);
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const removeMutation = useMutation({
    mutationFn: async (row: PlatformStaffRow) => {
      await session.apiClient!.delete(`/admin/platform-staff/${row.id}`, {
        headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined,
      });
      return row;
    },
    onSuccess: () => {
      toast.success("Platform staff removed");
      setConfirmDelete(null);
      setActionReason("");
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const restoreMutation = useMutation({
    mutationFn: async (row: PlatformStaffRow) => {
      await session.apiClient!.post(
        `/admin/platform-staff/${row.id}/restore`,
        {},
        { headers: actionReason.trim() ? { "X-Action-Reason": actionReason.trim() } : undefined }
      );
      return row;
    },
    onSuccess: () => {
      toast.success("Platform staff restored");
      setConfirmRestore(null);
      setActionReason("");
      invalidateAll();
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  });

  const saving = saveMutation.isPending;
  const deletingId = removeMutation.isPending ? (removeMutation.variables as PlatformStaffRow | undefined)?.id ?? null : null;
  const restoringId = restoreMutation.isPending ? (restoreMutation.variables as PlatformStaffRow | undefined)?.id ?? null : null;

  const rowsToRender = showDeleted ? deletedRows : activeRows;
  const listBusy = showDeleted ? loadingDeleted : loading;

  return (
    <>
    <AdminPageShell
      title="Platform Staff"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Platform Staff" }]}
    >
        <div className="grid gap-6 lg:grid-cols-[0.95fr,1.05fr]">
          <section className="space-y-4 rounded-2xl p-5 bg-surface-2 border border-line">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-lg font-semibold text-ink">
                  {form.id ? "Edit Platform Staff" : "Add Platform Staff"}
                </h1>
                <p className="text-xs text-[rgba(255,255,255,0.6)]">
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
                <span className="block text-xs font-semibold uppercase tracking-[0.12em] text-[rgba(255,255,255,0.65)]">
                  Keycloak User ID
                </span>
                <input
                  value={form.keycloakUserId}
                  onChange={(e) => setForm((old) => ({ ...old, keycloakUserId: e.target.value }))}
                  className="w-full rounded-xl px-3 py-2 bg-[rgba(255,255,255,0.03)] border border-line text-ink"
                  placeholder="keycloak user UUID / subject"
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
                  placeholder="staff@example.com"
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
                  placeholder="Platform Catalog Staff"
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
                title="Permissions"
                options={[...PLATFORM_PERMISSION_OPTIONS]}
                selected={form.permissions}
                disabled={saving}
                onChange={(next) => setForm((old) => ({ ...old, permissions: next }))}
              />
            </div>

            <button type="button"
              onClick={() => saveMutation.mutate()}
              disabled={saving}
              className="inline-flex items-center justify-center rounded-xl px-4 py-2 text-sm font-semibold disabled:opacity-60 bg-brand-soft text-ink border border-[rgba(0,212,255,0.25)]"
            >
              {saving ? "Saving..." : form.id ? "Update Platform Staff" : "Create Platform Staff"}
            </button>
          </section>

          <section className="space-y-4 rounded-2xl p-5 bg-surface-2 border border-line">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-ink">
                  {showDeleted ? "Deleted Platform Staff" : "Platform Staff"}
                </h2>
                <p className="text-xs text-[rgba(255,255,255,0.6)]">
                  {showDeleted ? "Restore soft-deleted staff access entries." : "Active platform staff access assignments."}
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" className="btn-ghost" onClick={() => { setShowDeleted(false); }} disabled={!showDeleted}>Active</button>
                <button type="button" className="btn-ghost" onClick={() => { setShowDeleted(true); }} disabled={showDeleted || listBusy}>Deleted</button>
              </div>
            </div>

            {listBusy ? (
              <div className="rounded-xl px-4 py-8 text-center text-sm border border-dashed border-line text-[rgba(255,255,255,0.65)]">
                Loading...
              </div>
            ) : rowsToRender.length === 0 ? (
              <div className="rounded-xl px-4 py-8 text-center text-sm border border-dashed border-line text-[rgba(255,255,255,0.65)]">
                No rows found.
              </div>
            ) : (
              <div className="space-y-3">
                {rowsToRender.map((row) => (
                  <div key={row.id} className="rounded-xl p-4 border border-line bg-[rgba(255,255,255,0.02)]">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-sm font-semibold text-ink">
                            {row.displayName || row.email}
                          </p>
                          <span className="rounded-full px-2 py-0.5 text-[10px] font-semibold border border-[rgba(255,255,255,0.08)]"
                            style={{
                              background: row.active ? "rgba(34,197,94,0.12)" : "rgba(251,191,36,0.12)",
                              color: row.active ? "#86efac" : "#fde68a",
                            }}>
                            {row.active ? "Active" : "Inactive"}
                          </span>
                        </div>
                        <p className="text-xs text-[rgba(255,255,255,0.65)]">{row.email}</p>
                        <p className="text-[11px] font-mono text-[rgba(255,255,255,0.5)]">{row.keycloakUserId}</p>
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

            {/* Pagination */}
            {(() => {
              const currentPage = showDeleted ? deletedPage : page;
              const currentTotalPages = showDeleted ? deletedTotalPages : totalPages;
              const setCurrentPage = showDeleted ? setDeletedPage : setPage;
              if (currentTotalPages <= 1) return null;
              return (
                <div className="flex items-center justify-center gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                    disabled={currentPage <= 0 || listBusy}
                    className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold border border-line ${currentPage <= 0 ? "text-[rgba(255,255,255,0.35)] cursor-not-allowed opacity-50" : "text-ink cursor-pointer bg-[rgba(255,255,255,0.04)]"}`}
                  >
                    Previous
                  </button>
                  <span className="text-xs text-[rgba(255,255,255,0.6)]">
                    Page {currentPage + 1} of {currentTotalPages}
                  </span>
                  <button
                    type="button"
                    onClick={() => setCurrentPage((p) => p + 1)}
                    disabled={currentPage >= currentTotalPages - 1 || listBusy}
                    className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold border border-line ${currentPage >= currentTotalPages - 1 ? "text-[rgba(255,255,255,0.35)] cursor-not-allowed opacity-50" : "text-ink cursor-pointer bg-[rgba(255,255,255,0.04)]"}`}
                  >
                    Next
                  </button>
                </div>
              );
            })()}

            <AccessAuditPanel
              apiClient={session.apiClient}
              title="Platform Staff Access Audit"
              targetType="PLATFORM_STAFF"
              targetId={auditTargetRow?.id || null}
              reloadKey={accessAuditReloadKey}
            />
          </section>
        </div>
    </AdminPageShell>

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
          if (confirmDelete) removeMutation.mutate(confirmDelete);
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
          if (confirmRestore) restoreMutation.mutate(confirmRestore);
        }}
      />
    </>
  );
}
