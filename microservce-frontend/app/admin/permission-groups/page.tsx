"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import StatusBadge from "../../components/ui/StatusBadge";
import ConfirmModal from "../../components/ConfirmModal";
import { useAuthSession } from "../../../lib/authSession";

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

function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as { response?: { data?: { error?: string; message?: string } }; message?: string };
    return maybe.response?.data?.error || maybe.response?.data?.message || maybe.message || "Request failed";
  }
  return "Request failed";
}

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
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: 16, color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
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
        <div
          style={{
            background: "rgba(255,255,255,0.03)",
            border: "1px solid var(--line)",
            borderRadius: 16,
            padding: "48px 24px",
            textAlign: "center",
          }}
        >
          <p style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 8 }}>
            Unauthorized
          </p>
          <p style={{ fontSize: "0.82rem", color: "var(--muted)" }}>
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
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            padding: "8px 18px",
            borderRadius: 12,
            fontSize: "0.8rem",
            fontWeight: 700,
            border: "1px solid rgba(0,212,255,0.25)",
            background: "var(--brand-soft)",
            color: "var(--ink)",
            cursor: "pointer",
            transition: "opacity 0.15s",
          }}
        >
          + Add Permission Group
        </button>
      }
    >
      {/* ───── Create / Edit Form ───── */}
      {formOpen && (
        <section
          style={{
            background: "rgba(255,255,255,0.03)",
            border: "1px solid var(--line)",
            borderRadius: 16,
            padding: 24,
            marginBottom: 24,
          }}
        >
          <h2
            style={{
              fontSize: "1rem",
              fontWeight: 700,
              color: "var(--ink)",
              marginBottom: 4,
            }}
          >
            {editing ? "Edit Permission Group" : "Create Permission Group"}
          </h2>
          <p style={{ fontSize: "0.75rem", color: "var(--muted)", marginBottom: 20 }}>
            {editing
              ? "Update the permission group details below."
              : "Define a new permission group with a name, scope, and list of permissions."}
          </p>

          <div style={{ display: "grid", gap: 16, maxWidth: 560 }}>
            {/* Name */}
            <label style={{ display: "block" }}>
              <span
                style={{
                  display: "block",
                  fontSize: "0.72rem",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.06em",
                  color: "var(--muted)",
                  marginBottom: 6,
                }}
              >
                Name
              </span>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))}
                placeholder="e.g. Order Managers"
                disabled={saving}
                style={{
                  width: "100%",
                  padding: "10px 14px",
                  borderRadius: 12,
                  border: "1px solid var(--line)",
                  background: "rgba(255,255,255,0.03)",
                  color: "var(--ink)",
                  fontSize: "0.85rem",
                  outline: "none",
                }}
              />
            </label>

            {/* Description */}
            <label style={{ display: "block" }}>
              <span
                style={{
                  display: "block",
                  fontSize: "0.72rem",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.06em",
                  color: "var(--muted)",
                  marginBottom: 6,
                }}
              >
                Description
              </span>
              <textarea
                value={form.description}
                onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))}
                placeholder="Optional description of this group"
                rows={2}
                disabled={saving}
                style={{
                  width: "100%",
                  padding: "10px 14px",
                  borderRadius: 12,
                  border: "1px solid var(--line)",
                  background: "rgba(255,255,255,0.03)",
                  color: "var(--ink)",
                  fontSize: "0.85rem",
                  resize: "vertical",
                  outline: "none",
                }}
              />
            </label>

            {/* Scope radio */}
            <fieldset style={{ border: "none", margin: 0, padding: 0 }}>
              <legend
                style={{
                  fontSize: "0.72rem",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.06em",
                  color: "var(--muted)",
                  marginBottom: 8,
                }}
              >
                Scope
              </legend>
              <div style={{ display: "flex", gap: 16 }}>
                {(["PLATFORM", "VENDOR"] as const).map((s) => (
                  <label
                    key={s}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                      cursor: "pointer",
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                    }}
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
            <label style={{ display: "block" }}>
              <span
                style={{
                  display: "block",
                  fontSize: "0.72rem",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.06em",
                  color: "var(--muted)",
                  marginBottom: 6,
                }}
              >
                Permissions (one per line)
              </span>
              <textarea
                value={form.permissionsText}
                onChange={(e) => setForm((s) => ({ ...s, permissionsText: e.target.value }))}
                placeholder={"platform.orders.manage\nplatform.products.read\nplatform.categories.manage"}
                rows={6}
                disabled={saving}
                style={{
                  width: "100%",
                  padding: "10px 14px",
                  borderRadius: 12,
                  border: "1px solid var(--line)",
                  background: "rgba(255,255,255,0.03)",
                  color: "var(--ink)",
                  fontSize: "0.82rem",
                  fontFamily: "monospace",
                  resize: "vertical",
                  outline: "none",
                  lineHeight: 1.7,
                }}
              />
            </label>
          </div>

          {/* Save / Cancel */}
          <div style={{ display: "flex", gap: 10, marginTop: 20 }}>
            <button
              type="button"
              onClick={() => { void save(); }}
              disabled={saving}
              className="btn-brand"
              style={{
                padding: "9px 22px",
                borderRadius: 12,
                fontSize: "0.82rem",
                fontWeight: 700,
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
              className="btn-ghost"
              style={{
                padding: "9px 18px",
                borderRadius: 12,
                fontSize: "0.82rem",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Cancel
            </button>
          </div>
        </section>
      )}

      {/* ───── Scope Filter Tabs ───── */}
      <div style={{ display: "flex", gap: 6, marginBottom: 18, flexWrap: "wrap" }}>
        {scopeOptions.map((opt) => {
          const active = scopeFilter === opt.value;
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => setScopeFilter(opt.value)}
              style={{
                padding: "6px 16px",
                borderRadius: 10,
                fontSize: "0.78rem",
                fontWeight: 700,
                border: active ? "1px solid var(--brand)" : "1px solid var(--line)",
                background: active ? "var(--brand-soft)" : "transparent",
                color: active ? "var(--brand)" : "var(--muted)",
                cursor: "pointer",
                transition: "all 0.15s ease",
              }}
            >
              {opt.label}
            </button>
          );
        })}
        <span
          style={{
            marginLeft: "auto",
            display: "flex",
            alignItems: "center",
            fontSize: "0.75rem",
            color: "var(--muted)",
          }}
        >
          {totalElements} group{totalElements !== 1 ? "s" : ""}
        </span>
      </div>

      {/* ───── Table ───── */}
      <div
        style={{
          background: "rgba(255,255,255,0.03)",
          border: "1px solid var(--line)",
          borderRadius: 16,
          overflow: "auto",
        }}
      >
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              {["Name", "Scope", "Permissions", "Created", "Actions"].map((h) => (
                <th
                  key={h}
                  style={{
                    background: "var(--surface-2)",
                    color: "var(--muted)",
                    fontSize: "0.72rem",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                    padding: "12px 14px",
                    textAlign: "left",
                    fontWeight: 700,
                    whiteSpace: "nowrap",
                  }}
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
                  style={{
                    padding: "40px 14px",
                    textAlign: "center",
                    fontSize: "0.82rem",
                    color: "var(--muted)",
                  }}
                >
                  Loading...
                </td>
              </tr>
            ) : groups.length === 0 ? (
              <tr>
                <td
                  colSpan={5}
                  style={{
                    padding: "40px 14px",
                    textAlign: "center",
                    fontSize: "0.82rem",
                    color: "var(--muted)",
                  }}
                >
                  No permission groups found.
                </td>
              </tr>
            ) : (
              groups.map((g) => (
                <tr key={g.id}>
                  {/* Name */}
                  <td
                    style={{
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                      padding: "12px 14px",
                      borderBottom: "1px solid var(--line)",
                      fontWeight: 600,
                    }}
                  >
                    <div>{g.name}</div>
                    {g.description && (
                      <div
                        style={{
                          fontSize: "0.72rem",
                          color: "var(--muted)",
                          marginTop: 2,
                          maxWidth: 260,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {g.description}
                      </div>
                    )}
                  </td>

                  {/* Scope */}
                  <td
                    style={{
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                      padding: "12px 14px",
                      borderBottom: "1px solid var(--line)",
                    }}
                  >
                    <StatusBadge value={g.scope} colorMap={SCOPE_COLORS} />
                  </td>

                  {/* Permissions count */}
                  <td
                    style={{
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                      padding: "12px 14px",
                      borderBottom: "1px solid var(--line)",
                    }}
                  >
                    <span
                      style={{
                        display: "inline-block",
                        padding: "2px 10px",
                        borderRadius: 999,
                        fontSize: "0.72rem",
                        fontWeight: 700,
                        background: "rgba(255,255,255,0.06)",
                        border: "1px solid var(--line)",
                        color: "var(--ink)",
                      }}
                    >
                      {g.permissions.length}
                    </span>
                  </td>

                  {/* Created */}
                  <td
                    style={{
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                      padding: "12px 14px",
                      borderBottom: "1px solid var(--line)",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {formatDate(g.createdAt)}
                  </td>

                  {/* Actions */}
                  <td
                    style={{
                      fontSize: "0.82rem",
                      color: "var(--ink)",
                      padding: "12px 14px",
                      borderBottom: "1px solid var(--line)",
                      whiteSpace: "nowrap",
                    }}
                  >
                    <div style={{ display: "flex", gap: 6 }}>
                      <button
                        type="button"
                        className="btn-ghost"
                        onClick={() => openEdit(g)}
                        style={{
                          fontSize: "0.78rem",
                          padding: "4px 12px",
                          borderRadius: 8,
                          cursor: "pointer",
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn-ghost"
                        onClick={() => setDeleteTarget(g)}
                        style={{
                          fontSize: "0.78rem",
                          padding: "4px 12px",
                          borderRadius: 8,
                          color: "#f87171",
                          cursor: "pointer",
                        }}
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
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 12,
            marginTop: 20,
          }}
        >
          <button
            type="button"
            onClick={() => goToPage(page - 1)}
            disabled={page <= 0 || loading}
            style={{
              padding: "7px 16px",
              borderRadius: 10,
              fontSize: "0.78rem",
              fontWeight: 600,
              border: "1px solid var(--line)",
              background: page <= 0 ? "transparent" : "rgba(255,255,255,0.04)",
              color: page <= 0 ? "var(--muted)" : "var(--ink)",
              cursor: page <= 0 ? "not-allowed" : "pointer",
              opacity: page <= 0 ? 0.5 : 1,
              transition: "opacity 0.15s",
            }}
          >
            Prev
          </button>
          <span style={{ fontSize: "0.78rem", color: "var(--muted)" }}>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => goToPage(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            style={{
              padding: "7px 16px",
              borderRadius: 10,
              fontSize: "0.78rem",
              fontWeight: 600,
              border: "1px solid var(--line)",
              background: page >= totalPages - 1 ? "transparent" : "rgba(255,255,255,0.04)",
              color: page >= totalPages - 1 ? "var(--muted)" : "var(--ink)",
              cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
              opacity: page >= totalPages - 1 ? 0.5 : 1,
              transition: "opacity 0.15s",
            }}
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
