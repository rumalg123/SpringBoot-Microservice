"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import TableSkeleton from "../../components/ui/TableSkeleton";

type ApiKey = {
  id: string;
  keycloakId: string;
  name: string;
  scope: "PLATFORM" | "VENDOR";
  permissions: string[];
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
};

type CreateApiKeyResponse = ApiKey & { rawKey: string };

export default function AdminApiKeysPage() {
  const session = useAuthSession();
  const { status: sessionStatus, profile, apiClient } = session;

  // Create form
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [newScope, setNewScope] = useState<"PLATFORM" | "VENDOR">("PLATFORM");
  const [newPermissions, setNewPermissions] = useState("");
  const [newExpiry, setNewExpiry] = useState("");
  const [rawKeyRevealed, setRawKeyRevealed] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const keycloakId = (profile?.sub as string) || "";

  const { data: keys = [], isLoading: loading, refetch } = useQuery({
    queryKey: ["admin-api-keys", keycloakId],
    queryFn: async () => {
      const res = await apiClient!.get(`/admin/api-keys/by-keycloak/${keycloakId}`);
      return (res.data as ApiKey[]) || [];
    },
    enabled: sessionStatus === "ready" && !!apiClient && !!keycloakId,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const body: Record<string, unknown> = {
        keycloakId,
        name: newName.trim(),
        scope: newScope,
      };
      const perms = newPermissions.split(",").map((p) => p.trim()).filter(Boolean);
      if (perms.length > 0) body.permissions = perms;
      if (newExpiry) body.expiresAt = new Date(newExpiry).toISOString();

      const res = await apiClient!.post("/admin/api-keys", body);
      return res.data as CreateApiKeyResponse;
    },
    onSuccess: (created) => {
      setRawKeyRevealed(created.rawKey);
      setNewName("");
      setNewPermissions("");
      setNewExpiry("");
      void refetch();
      toast.success("API key created");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to create key");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient!.delete(`/admin/api-keys/${id}`);
      return id;
    },
    onMutate: (id) => {
      setDeletingId(id);
    },
    onSuccess: () => {
      void refetch();
      toast.success("API key revoked");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to revoke key");
    },
    onSettled: () => {
      setDeletingId(null);
    },
  });

  const statusBadge = (active: boolean, expiresAt: string | null) => {
    const expired = expiresAt && new Date(expiresAt) < new Date();
    if (!active || expired) {
      return (
        <span className="text-[0.68rem] font-bold py-0.5 px-2 rounded-sm bg-danger-soft text-danger">
          {expired ? "EXPIRED" : "REVOKED"}
        </span>
      );
    }
    return (
      <span className="text-[0.68rem] font-bold py-0.5 px-2 rounded-sm bg-success-soft text-success">
        ACTIVE
      </span>
    );
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }

  return (
    <AdminPageShell
      title="API Keys"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "API Keys" }]}
      actions={
        <button
          type="button"
          onClick={() => { setShowCreate(!showCreate); setRawKeyRevealed(null); }}
          className="py-2 px-[18px] rounded-md text-[0.82rem] font-bold bg-[var(--gradient-brand)] text-white border-none cursor-pointer"
        >
          {showCreate ? "Cancel" : "+ New Key"}
        </button>
      }
    >
        <p className="text-muted text-base -mt-4 mb-6">Manage API keys for programmatic access</p>

        {/* Raw Key Alert */}
        {rawKeyRevealed && (
          <div className="mb-5 p-4 rounded-[12px] bg-warning-soft border border-warning-border">
            <p className="text-sm font-bold text-warning-text mb-2">
              Copy your API key now — it won&apos;t be shown again!
            </p>
            <code className="block py-2.5 px-3.5 rounded-[8px] bg-[rgba(0,0,0,0.3)] text-white text-[0.78rem] break-all select-all">
              {rawKeyRevealed}
            </code>
            <button
              type="button"
              onClick={() => { void navigator.clipboard.writeText(rawKeyRevealed); toast.success("Copied!"); }}
              className="mt-2 py-1 px-3 rounded-sm text-[0.72rem] font-semibold bg-accent-soft text-accent border border-accent-glow cursor-pointer"
            >
              Copy to Clipboard
            </button>
          </div>
        )}

        {/* Create Form */}
        {showCreate && (
          <div className="mb-6 p-5 rounded-[14px] bg-[var(--card)] border border-line-bright">
            <h3 className="text-white text-[0.95rem] font-bold mb-4">Create New API Key</h3>
            <div className="grid grid-cols-2 gap-3 mb-3">
              <div>
                <label className="block text-[0.72rem] font-bold text-muted mb-1">Name *</label>
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="My API Key"
                  maxLength={120}
                  className="w-full py-2 px-3 rounded-[8px] text-[0.82rem] bg-bg border border-line-bright text-white"
                />
              </div>
              <div>
                <label className="block text-[0.72rem] font-bold text-muted mb-1">Scope</label>
                <select
                  value={newScope}
                  onChange={(e) => setNewScope(e.target.value as "PLATFORM" | "VENDOR")}
                  className="w-full py-2 px-3 rounded-[8px] text-[0.82rem] bg-bg border border-line-bright text-white"
                >
                  <option value="PLATFORM">Platform</option>
                  <option value="VENDOR">Vendor</option>
                </select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3 mb-4">
              <div>
                <label className="block text-[0.72rem] font-bold text-muted mb-1">Permissions (comma-separated)</label>
                <input
                  value={newPermissions}
                  onChange={(e) => setNewPermissions(e.target.value)}
                  placeholder="read:products, write:orders"
                  className="w-full py-2 px-3 rounded-[8px] text-[0.82rem] bg-bg border border-line-bright text-white"
                />
              </div>
              <div>
                <label className="block text-[0.72rem] font-bold text-muted mb-1">Expires At (optional)</label>
                <input
                  type="datetime-local"
                  value={newExpiry}
                  onChange={(e) => setNewExpiry(e.target.value)}
                  className="w-full py-2 px-3 rounded-[8px] text-[0.82rem] bg-bg border border-line-bright text-white"
                />
              </div>
            </div>
            <button
              type="button"
              disabled={createMutation.isPending || !newName.trim()}
              onClick={() => { createMutation.mutate(); }}
              className={`py-2 px-6 rounded-md text-[0.82rem] font-bold bg-[var(--gradient-brand)] text-white border-none ${createMutation.isPending ? "cursor-not-allowed opacity-60" : "cursor-pointer opacity-100"}`}
            >
              {createMutation.isPending ? "Creating..." : "Create API Key"}
            </button>
          </div>
        )}

        {/* Keys Table */}
        {loading ? (
          <TableSkeleton rows={3} cols={7} />
        ) : keys.length === 0 ? (
          <div className="text-center py-[60px] px-5 rounded-[14px] bg-[var(--card)] border border-line-bright">
            <p className="text-muted text-[0.9rem]">No API keys found. Create one to get started.</p>
          </div>
        ) : (
          <div className="rounded-[14px] overflow-hidden border border-line-bright">
            <table className="w-full border-collapse">
              <thead>
                <tr className="bg-[var(--card)]">
                  {["Name", "Scope", "Permissions", "Status", "Created", "Expires", "Actions"].map((h) => (
                    <th key={h} className="py-2.5 px-3.5 text-[0.68rem] font-extrabold uppercase tracking-[0.1em] text-muted text-left">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {keys.map((key) => (
                  <tr key={key.id} className="border-t border-line-bright">
                    <td className="py-3 px-3.5 text-[0.82rem] font-semibold text-white">{key.name}</td>
                    <td className="py-3 px-3.5">
                      <span className={`text-[0.68rem] font-bold py-0.5 px-2 rounded-sm ${key.scope === "PLATFORM" ? "bg-accent-soft text-accent" : "bg-[rgba(52,211,153,0.1)] text-[#34d399]"}`}>
                        {key.scope}
                      </span>
                    </td>
                    <td className="py-3 px-3.5 text-[0.75rem] text-muted max-w-[180px] overflow-hidden text-ellipsis whitespace-nowrap">
                      {key.permissions.length > 0 ? key.permissions.join(", ") : "—"}
                    </td>
                    <td className="py-3 px-3.5">{statusBadge(key.active, key.expiresAt)}</td>
                    <td className="py-3 px-3.5 text-[0.75rem] text-muted">{new Date(key.createdAt).toLocaleDateString()}</td>
                    <td className="py-3 px-3.5 text-[0.75rem] text-muted">{key.expiresAt ? new Date(key.expiresAt).toLocaleDateString() : "Never"}</td>
                    <td className="py-3 px-3.5">
                      {key.active && (
                        <button
                          type="button"
                          disabled={deletingId === key.id}
                          onClick={() => { deleteMutation.mutate(key.id); }}
                          className={`py-1 px-3 rounded-sm text-[0.72rem] font-semibold bg-danger-soft text-danger border border-danger-glow cursor-pointer ${deletingId === key.id ? "opacity-60" : "opacity-100"}`}
                        >
                          {deletingId === key.id ? "Revoking..." : "Revoke"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </AdminPageShell>
  );
}
