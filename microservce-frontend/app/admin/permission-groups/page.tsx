"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import StatusBadge from "../../components/ui/StatusBadge";
import ConfirmModal from "../../components/ConfirmModal";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";

/* ───── types ───── */

type PermissionGroup = {
  id: string;
  name: string;
  description: string | null;
  permissions: string[];
  scope: "PLATFORM" | "VENDOR";
  createdAt: string;
};

type PageResponse = {
  content: PermissionGroup[];
  totalPages?: number;
  totalElements?: number;
  number?: number;
  size?: number;
  page?: { number?: number; size?: number; totalElements?: number; totalPages?: number };
};

type ScopeFilter = "ALL" | "PLATFORM" | "VENDOR";

type FormState = {
  name: string;
  description: string;
  scope: "PLATFORM" | "VENDOR";
  permissionsText: string;
};

const EMPTY_FORM: FormState = {
  name: "",
  description: "",
  scope: "PLATFORM",
  permissionsText: "",
};

const SCOPE_COLORS: Record<string, { bg: string; border: string; color: string }> = {
  PLATFORM: { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" },
  VENDOR: { bg: "var(--accent-soft)", border: "rgba(124,58,237,0.3)", color: "var(--accent)" },
};

/* ───── helpers ───── */


function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return iso;
  }
}

/* ───── page component ───── */

export default function AdminPermissionGroupsPage() {
  const session = useAuthSession();

  /* ── list state ── */
  const [groups, setGroups] = useState<PermissionGroup[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  /* ── filter ── */
  const [scopeFilter, setScopeFilter] = useState<ScopeFilter>("ALL");

  /* ── form ── */
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<PermissionGroup | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  /* ── delete ── */
  const [deleteTarget, setDeleteTarget] = useState<PermissionGroup | null>(null);
  const [deleting, setDeleting] = useState(false);

  /* ── data loading ── */
  const loadGroups = useCallback(
    async (p = 0) => {
      if (!session.apiClient) return;
      setLoading(true);
      try {
        const params = new URLSearchParams({ page: String(p), size: "20" });
        if (scopeFilter !== "ALL") {
          params.set("scope", scopeFilter);
        }
        const res = await session.apiClient.get(`/admin/permission-groups?${params.toString()}`);
        const data = res.data as PageResponse;
        setGroups(data.content || []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
        setPage(data.number ?? data.page?.number ?? 0);
      } catch (error) {
        toast.error(getErrorMessage(error));
      } finally {
        setLoading(false);
      }
    },
    [session.apiClient, scopeFilter],
  );

  useEffect(() => {
    if (session.status !== "ready") return;
    void loadGroups(0);
  }, [session.status, loadGroups]);

  /* ── open form for create ── */
  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  /* ── open form for edit ── */
  const openEdit = (g: PermissionGroup) => {
    setEditing(g);
    setForm({
      name: g.name,
      description: g.description || "",
      scope: g.scope,
      permissionsText: (g.permissions || []).join("\n"),
    });
    setFormOpen(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  /* ── close form ── */
  const closeForm = () => {
    setFormOpen(false);
    setEditing(null);
    setForm(EMPTY_FORM);
  };

  /* ── save (create / update) ── */
  const save = async () => {
    if (!session.apiClient || saving) return;
    if (!form.name.trim()) {
      toast.error("Name is required");
      return;
    }
    setSaving(true);
    try {
      const permissions = form.permissionsText
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean);

      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        permissions,
        scope: form.scope,
      };

      if (editing) {
        await session.apiClient.put(`/admin/permission-groups/${editing.id}`, payload);
        toast.success("Permission group updated");
      } else {
        await session.apiClient.post("/admin/permission-groups", payload);
        toast.success("Permission group created");
      }
      closeForm();
      await loadGroups(page);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  /* ── delete ── */
  const confirmDelete = async () => {
    if (!session.apiClient || !deleteTarget || deleting) return;
    setDeleting(true);
    try {
      await session.apiClient.delete(`/admin/permission-groups/${deleteTarget.id}`);
      toast.success("Permission group deleted");
      setDeleteTarget(null);
      await loadGroups(page);
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setDeleting(false);
    }
  };

  /* ── pagination ── */
  const goToPage = (p: number) => {
    if (p < 0 || p >= totalPages) return;
    void loadGroups(p);
  };

  /* ── render guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <div className="min-h-screen bg-bg grid place-items-center">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-muted text-base">Loading...</p>
        </div>
      </div>
    );
  }

  if (!session.isSuperAdmin) {
    return (
      <AdminPageShell
        title="Permission Groups"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Permission Groups" },
        ]}
      >
        <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg py-12 px-6 text-center">
          <p className="text-[1.1rem] font-bold text-ink mb-2">
            Unauthorized
          </p>
          <p className="text-[0.82rem] text-muted">
            You do not have permission to manage permission groups. Only Super Admins can access this page.
          </p>
        </div>
      </AdminPageShell>
    );
  }

  /* ── scope filter tabs ── */
  const scopeOptions: { label: string; value: ScopeFilter }[] = [
    { label: "All", value: "ALL" },
    { label: "Platform", value: "PLATFORM" },
    { label: "Vendor", value: "VENDOR" },
  ];

  return (
    <AdminPageShell
      title="Permission Groups"
      breadcrumbs={[
        { label: "Admin", href: "/admin/orders" },
        { label: "Permission Groups" },
      ]}
      actions={
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-1.5 py-2 px-[18px] rounded-[12px] text-sm font-bold border border-[rgba(0,212,255,0.25)] bg-brand-soft text-ink cursor-pointer transition-opacity duration-150"
        >
          + Add Permission Group
        </button>
      }
    >
      {/* ───── Create / Edit Form ───── */}
      {formOpen && (
        <section className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6 mb-6">
          <h2 className="text-lg font-bold text-ink mb-1">
            {editing ? "Edit Permission Group" : "Create Permission Group"}
          </h2>
          <p className="text-[0.75rem] text-muted mb-5">
            {editing
              ? "Update the permission group details below."
              : "Define a new permission group with a name, scope, and list of permissions."}
          </p>

          <div className="grid gap-4 max-w-[560px]">
            {/* Name */}
            <label className="block">
              <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
                Name
              </span>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))}
                placeholder="e.g. Order Managers"
                disabled={saving}
                className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-base outline-none"
              />
            </label>

            {/* Description */}
            <label className="block">
              <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
                Description
              </span>
              <textarea
                value={form.description}
                onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))}
                placeholder="Optional description of this group"
                rows={2}
                disabled={saving}
                className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-base resize-y outline-none"
              />
            </label>

            {/* Scope radio */}
            <fieldset className="border-none m-0 p-0">
              <legend className="text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-2">
                Scope
              </legend>
              <div className="flex gap-4">
                {(["PLATFORM", "VENDOR"] as const).map((s) => (
                  <label
                    key={s}
                    className="flex items-center gap-1.5 cursor-pointer text-[0.82rem] text-ink"
                  >
                    <input
                      type="radio"
                      name="scope"
                      value={s}
                      checked={form.scope === s}
                      onChange={() => setForm((f) => ({ ...f, scope: s }))}
                      disabled={saving}
                      style={{ accentColor: "var(--brand)" }}
                    />
                    {s}
                  </label>
                ))}
              </div>
            </fieldset>

            {/* Permissions textarea */}
            <label className="block">
              <span className="block text-[0.72rem] font-bold uppercase tracking-[0.06em] text-muted mb-1.5">
                Permissions (one per line)
              </span>
              <textarea
                value={form.permissionsText}
                onChange={(e) => setForm((s) => ({ ...s, permissionsText: e.target.value }))}
                placeholder={"platform.orders.manage\nplatform.products.read\nplatform.categories.manage"}
                rows={6}
                disabled={saving}
                className="w-full py-2.5 px-3.5 rounded-[12px] border border-line bg-[rgba(255,255,255,0.03)] text-ink text-[0.82rem] font-mono resize-y outline-none leading-[1.7]"
              />
            </label>
          </div>

          {/* Save / Cancel */}
          <div className="flex gap-2.5 mt-5">
            <button
              type="button"
              onClick={() => { void save(); }}
              disabled={saving}
              className="btn-brand py-[9px] px-[22px] rounded-[12px] text-[0.82rem] font-bold"
              style={{
                cursor: saving ? "not-allowed" : "pointer",
                opacity: saving ? 0.6 : 1,
              }}
            >
              {saving ? "Saving..." : editing ? "Update Group" : "Create Group"}
            </button>
            <button
              type="button"
              onClick={closeForm}
              disabled={saving}
              className="btn-ghost py-[9px] px-[18px] rounded-[12px] text-[0.82rem] font-semibold cursor-pointer"
            >
              Cancel
            </button>
          </div>
        </section>
      )}

      {/* ───── Scope Filter Tabs ───── */}
      <div className="flex gap-1.5 mb-[18px] flex-wrap">
        {scopeOptions.map((opt) => {
          const active = scopeFilter === opt.value;
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => setScopeFilter(opt.value)}
              className={`py-1.5 px-4 rounded-md text-[0.78rem] font-bold cursor-pointer transition-all duration-150 ${active ? "border border-brand bg-brand-soft text-brand" : "border border-line bg-transparent text-muted"}`}
            >
              {opt.label}
            </button>
          );
        })}
        <span className="ml-auto flex items-center text-[0.75rem] text-muted">
          {totalElements} group{totalElements !== 1 ? "s" : ""}
        </span>
      </div>

      {/* ───── Table ───── */}
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg overflow-auto">
        <table className="w-full border-collapse">
          <thead>
            <tr>
              {["Name", "Scope", "Permissions", "Created", "Actions"].map((h) => (
                <th
                  key={h}
                  className="bg-surface-2 text-muted text-[0.72rem] uppercase tracking-[0.05em] py-3 px-3.5 text-left font-bold whitespace-nowrap"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td
                  colSpan={5}
                  className="py-10 px-3.5 text-center text-[0.82rem] text-muted"
                >
                  Loading...
                </td>
              </tr>
            ) : groups.length === 0 ? (
              <tr>
                <td
                  colSpan={5}
                  className="py-10 px-3.5 text-center text-[0.82rem] text-muted"
                >
                  No permission groups found.
                </td>
              </tr>
            ) : (
              groups.map((g) => (
                <tr key={g.id}>
                  {/* Name */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line font-semibold">
                    <div>{g.name}</div>
                    {g.description && (
                      <div className="text-[0.72rem] text-muted mt-0.5 max-w-[260px] overflow-hidden text-ellipsis whitespace-nowrap">
                        {g.description}
                      </div>
                    )}
                  </td>

                  {/* Scope */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line">
                    <StatusBadge value={g.scope} colorMap={SCOPE_COLORS} />
                  </td>

                  {/* Permissions count */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line">
                    <span className="inline-block py-0.5 px-2.5 rounded-full text-[0.72rem] font-bold bg-[rgba(255,255,255,0.06)] border border-line text-ink">
                      {g.permissions.length}
                    </span>
                  </td>

                  {/* Created */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line whitespace-nowrap">
                    {formatDate(g.createdAt)}
                  </td>

                  {/* Actions */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line whitespace-nowrap">
                    <div className="flex gap-1.5">
                      <button
                        type="button"
                        className="btn-ghost text-[0.78rem] py-1 px-3 rounded-[8px] cursor-pointer"
                        onClick={() => openEdit(g)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn-ghost text-[0.78rem] py-1 px-3 rounded-[8px] text-[#f87171] cursor-pointer"
                        onClick={() => setDeleteTarget(g)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* ───── Pagination ───── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-5">
          <button
            type="button"
            onClick={() => goToPage(page - 1)}
            disabled={page <= 0 || loading}
            className={`py-[7px] px-4 rounded-md text-[0.78rem] font-semibold border border-line transition-opacity duration-150 ${page <= 0 ? "bg-transparent text-muted cursor-not-allowed opacity-50" : "bg-[rgba(255,255,255,0.04)] text-ink cursor-pointer opacity-100"}`}
          >
            Prev
          </button>
          <span className="text-[0.78rem] text-muted">
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => goToPage(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            className={`py-[7px] px-4 rounded-md text-[0.78rem] font-semibold border border-line transition-opacity duration-150 ${page >= totalPages - 1 ? "bg-transparent text-muted cursor-not-allowed opacity-50" : "bg-[rgba(255,255,255,0.04)] text-ink cursor-pointer opacity-100"}`}
          >
            Next
          </button>
        </div>
      )}

      {/* ───── Delete Confirmation ───── */}
      <ConfirmModal
        open={Boolean(deleteTarget)}
        title="Delete Permission Group"
        message={
          deleteTarget
            ? `Are you sure you want to delete "${deleteTarget.name}"? This action cannot be undone.`
            : ""
        }
        confirmLabel={deleting ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={deleting}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => { void confirmDelete(); }}
      />
    </AdminPageShell>
  );
}
