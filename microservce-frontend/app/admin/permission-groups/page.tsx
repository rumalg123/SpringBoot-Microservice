"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import ConfirmModal from "../../components/ConfirmModal";
import PermissionGroupForm from "../../components/admin/permission-groups/PermissionGroupForm";
import PermissionGroupTable from "../../components/admin/permission-groups/PermissionGroupTable";
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
        <div className="bg-surface border border-line rounded-lg py-12 px-6 text-center">
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
          className="inline-flex items-center gap-1.5 py-2 px-[18px] rounded-xl text-sm font-bold border border-brand-soft bg-brand-soft text-ink cursor-pointer transition-opacity duration-150"
        >
          + Add Permission Group
        </button>
      }
    >
      {/* Create / Edit Form */}
      {formOpen && (
        <PermissionGroupForm
          form={form}
          editing={Boolean(editing)}
          saving={saving}
          onFormChange={setForm}
          onSave={save}
          onCancel={closeForm}
        />
      )}

      {/* Table with filters and pagination */}
      <PermissionGroupTable
        groups={groups}
        loading={loading}
        totalPages={totalPages}
        totalElements={totalElements}
        page={page}
        scopeFilter={scopeFilter}
        onScopeFilterChange={setScopeFilter}
        onEdit={openEdit}
        onDelete={setDeleteTarget}
        onPageChange={goToPage}
      />

      {/* Delete Confirmation */}
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
