"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

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
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);

  // Create form
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [newScope, setNewScope] = useState<"PLATFORM" | "VENDOR">("PLATFORM");
  const [newPermissions, setNewPermissions] = useState("");
  const [newExpiry, setNewExpiry] = useState("");
  const [creating, setCreating] = useState(false);
  const [rawKeyRevealed, setRawKeyRevealed] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const keycloakId = (profile?.sub as string) || "";

  const loadKeys = useCallback(async () => {
    if (!apiClient || !keycloakId) return;
    setLoading(true);
    try {
      const res = await apiClient.get(`/admin/api-keys/by-keycloak/${keycloakId}`);
      setKeys((res.data as ApiKey[]) || []);
    } catch {
      toast.error("Failed to load API keys");
    } finally {
      setLoading(false);
    }
  }, [apiClient, keycloakId]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !isSuperAdmin) { router.replace("/"); return; }
    void loadKeys();
  }, [sessionStatus, isAuthenticated, isSuperAdmin, router, loadKeys]);

  const createKey = async () => {
    if (!apiClient || creating || !newName.trim()) return;
    setCreating(true);
    try {
      const body: Record<string, unknown> = {
        keycloakId,
        name: newName.trim(),
        scope: newScope,
      };
      const perms = newPermissions.split(",").map((p) => p.trim()).filter(Boolean);
      if (perms.length > 0) body.permissions = perms;
      if (newExpiry) body.expiresAt = new Date(newExpiry).toISOString();

      const res = await apiClient.post("/admin/api-keys", body);
      const created = res.data as CreateApiKeyResponse;
      setRawKeyRevealed(created.rawKey);
      setKeys((old) => [{ ...created }, ...old]);
      setNewName("");
      setNewPermissions("");
      setNewExpiry("");
      toast.success("API key created");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create key");
    } finally {
      setCreating(false);
    }
  };

  const deleteKey = async (id: string) => {
    if (!apiClient || deletingId) return;
    setDeletingId(id);
    try {
      await apiClient.delete(`/admin/api-keys/${id}`);
      setKeys((old) => old.map((k) => (k.id === id ? { ...k, active: false } : k)));
      toast.success("API key revoked");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to revoke key");
    } finally {
      setDeletingId(null);
    }
  };

  const statusBadge = (active: boolean, expiresAt: string | null) => {
    const expired = expiresAt && new Date(expiresAt) < new Date();
    if (!active || expired) {
      return (
        <span style={{ fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", background: "var(--danger-soft)", color: "var(--danger)" }}>
          {expired ? "EXPIRED" : "REVOKED"}
        </span>
      );
    }
    return (
      <span style={{ fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", background: "var(--success-soft)", color: "var(--success)" }}>
        ACTIVE
      </span>
    );
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><p style={{ color: "var(--muted)" }}>Loading...</p></div>;
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""} isSuperAdmin={isSuperAdmin} isVendorAdmin={isVendorAdmin}
        canViewAdmin={canViewAdmin} canManageAdminOrders={canManageAdminOrders}
        canManageAdminProducts={canManageAdminProducts} canManageAdminCategories={canManageAdminCategories}
        canManageAdminVendors={canManageAdminVendors} canManageAdminPosters={canManageAdminPosters}
        apiClient={apiClient} emailVerified={emailVerified} onLogout={logout}
      />

      <main className="mx-auto max-w-5xl px-4 py-10">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
          <div>
            <h1 className="text-2xl font-bold" style={{ color: "#fff" }}>API Keys</h1>
            <p style={{ color: "var(--muted)", fontSize: "0.85rem", marginTop: "4px" }}>Manage API keys for programmatic access</p>
          </div>
          <button
            type="button"
            onClick={() => { setShowCreate(!showCreate); setRawKeyRevealed(null); }}
            style={{
              padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
              background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer",
            }}
          >
            {showCreate ? "Cancel" : "+ New Key"}
          </button>
        </div>

        {/* Raw Key Alert */}
        {rawKeyRevealed && (
          <div style={{
            marginBottom: "20px", padding: "16px", borderRadius: "12px",
            background: "var(--warning-soft)", border: "1px solid var(--warning-border)",
          }}>
            <p style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--warning-text)", marginBottom: "8px" }}>
              Copy your API key now — it won&apos;t be shown again!
            </p>
            <code style={{
              display: "block", padding: "10px 14px", borderRadius: "8px",
              background: "rgba(0,0,0,0.3)", color: "#fff", fontSize: "0.78rem",
              wordBreak: "break-all", userSelect: "all",
            }}>
              {rawKeyRevealed}
            </code>
            <button
              type="button"
              onClick={() => { void navigator.clipboard.writeText(rawKeyRevealed); toast.success("Copied!"); }}
              style={{
                marginTop: "8px", padding: "4px 12px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                background: "var(--accent-soft)", color: "var(--accent)", border: "1px solid var(--accent-glow)", cursor: "pointer",
              }}
            >
              Copy to Clipboard
            </button>
          </div>
        )}

        {/* Create Form */}
        {showCreate && (
          <div style={{
            marginBottom: "24px", padding: "20px", borderRadius: "14px",
            background: "var(--card)", border: "1px solid var(--line-bright)",
          }}>
            <h3 style={{ color: "#fff", fontSize: "0.95rem", fontWeight: 700, marginBottom: "16px" }}>Create New API Key</h3>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "12px" }}>
              <div>
                <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px" }}>Name *</label>
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="My API Key"
                  maxLength={120}
                  style={{
                    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
                    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
                  }}
                />
              </div>
              <div>
                <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px" }}>Scope</label>
                <select
                  value={newScope}
                  onChange={(e) => setNewScope(e.target.value as "PLATFORM" | "VENDOR")}
                  style={{
                    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
                    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
                  }}
                >
                  <option value="PLATFORM">Platform</option>
                  <option value="VENDOR">Vendor</option>
                </select>
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px", marginBottom: "16px" }}>
              <div>
                <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px" }}>Permissions (comma-separated)</label>
                <input
                  value={newPermissions}
                  onChange={(e) => setNewPermissions(e.target.value)}
                  placeholder="read:products, write:orders"
                  style={{
                    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
                    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
                  }}
                />
              </div>
              <div>
                <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "4px" }}>Expires At (optional)</label>
                <input
                  type="datetime-local"
                  value={newExpiry}
                  onChange={(e) => setNewExpiry(e.target.value)}
                  style={{
                    width: "100%", padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
                    background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
                  }}
                />
              </div>
            </div>
            <button
              type="button"
              disabled={creating || !newName.trim()}
              onClick={() => { void createKey(); }}
              style={{
                padding: "8px 24px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                background: "var(--gradient-brand)", color: "#fff", border: "none",
                cursor: creating ? "not-allowed" : "pointer", opacity: creating ? 0.6 : 1,
              }}
            >
              {creating ? "Creating..." : "Create API Key"}
            </button>
          </div>
        )}

        {/* Keys Table */}
        {loading ? (
          <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading API keys...</p>
        ) : keys.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
            <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No API keys found. Create one to get started.</p>
          </div>
        ) : (
          <div style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ background: "var(--card)" }}>
                  {["Name", "Scope", "Permissions", "Status", "Created", "Expires", "Actions"].map((h) => (
                    <th key={h} style={{ padding: "10px 14px", fontSize: "0.68rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", textAlign: "left" }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {keys.map((key) => (
                  <tr key={key.id} style={{ borderTop: "1px solid var(--line-bright)" }}>
                    <td style={{ padding: "12px 14px", fontSize: "0.82rem", fontWeight: 600, color: "#fff" }}>{key.name}</td>
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{
                        fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px",
                        background: key.scope === "PLATFORM" ? "var(--accent-soft)" : "rgba(52,211,153,0.1)",
                        color: key.scope === "PLATFORM" ? "var(--accent)" : "#34d399",
                      }}>
                        {key.scope}
                      </span>
                    </td>
                    <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)", maxWidth: "180px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {key.permissions.length > 0 ? key.permissions.join(", ") : "—"}
                    </td>
                    <td style={{ padding: "12px 14px" }}>{statusBadge(key.active, key.expiresAt)}</td>
                    <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{new Date(key.createdAt).toLocaleDateString()}</td>
                    <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{key.expiresAt ? new Date(key.expiresAt).toLocaleDateString() : "Never"}</td>
                    <td style={{ padding: "12px 14px" }}>
                      {key.active && (
                        <button
                          type="button"
                          disabled={deletingId === key.id}
                          onClick={() => { void deleteKey(key.id); }}
                          style={{
                            padding: "4px 12px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                            background: "var(--danger-soft)", color: "var(--danger)",
                            border: "1px solid var(--danger-glow)", cursor: "pointer",
                            opacity: deletingId === key.id ? 0.6 : 1,
                          }}
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
      </main>

      <Footer />
    </div>
  );
}
