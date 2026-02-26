"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

type ActiveSession = {
  id: string;
  keycloakId: string;
  ipAddress: string | null;
  userAgent: string | null;
  lastActivityAt: string;
  createdAt: string;
};

type Paged<T> = { content: T[]; totalPages: number; totalElements: number };

export default function AdminSessionsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [sessions, setSessions] = useState<ActiveSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lookupId, setLookupId] = useState("");
  const [searchKeycloakId, setSearchKeycloakId] = useState("");
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);

  const loadSessions = useCallback(async () => {
    if (!apiClient || !searchKeycloakId.trim()) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      const res = await apiClient.get(`/admin/sessions/by-keycloak/${encodeURIComponent(searchKeycloakId.trim())}?${params}`);
      const data = res.data as Paged<ActiveSession>;
      setSessions(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch {
      toast.error("Failed to load sessions");
    } finally {
      setLoading(false);
    }
  }, [apiClient, searchKeycloakId, page]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !isSuperAdmin) { router.replace("/"); return; }
  }, [sessionStatus, isAuthenticated, isSuperAdmin, router]);

  useEffect(() => {
    if (searchKeycloakId.trim()) void loadSessions();
  }, [searchKeycloakId, page, loadSessions]);

  const handleSearch = () => {
    if (!lookupId.trim()) return;
    setPage(0);
    setSearchKeycloakId(lookupId.trim());
  };

  const loadOwnSessions = () => {
    const sub = (profile?.sub as string) || "";
    if (!sub) return;
    setLookupId(sub);
    setPage(0);
    setSearchKeycloakId(sub);
  };

  const revokeSession = async (sessionId: string) => {
    if (!apiClient || revokingId) return;
    setRevokingId(sessionId);
    try {
      await apiClient.delete(`/admin/sessions/${sessionId}`);
      setSessions((old) => old.filter((s) => s.id !== sessionId));
      setTotalElements((t) => Math.max(0, t - 1));
      toast.success("Session revoked");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to revoke session");
    } finally {
      setRevokingId(null);
    }
  };

  const revokeAll = async () => {
    if (!apiClient || revokingAll || !searchKeycloakId.trim()) return;
    setRevokingAll(true);
    try {
      await apiClient.delete(`/admin/sessions/by-keycloak/${encodeURIComponent(searchKeycloakId.trim())}`);
      setSessions([]);
      setTotalElements(0);
      setTotalPages(0);
      toast.success("All sessions revoked for this user");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to revoke all sessions");
    } finally {
      setRevokingAll(false);
    }
  };

  const parseBrowser = (ua: string | null) => {
    if (!ua) return "Unknown";
    if (ua.includes("Chrome")) return "Chrome";
    if (ua.includes("Firefox")) return "Firefox";
    if (ua.includes("Safari")) return "Safari";
    if (ua.includes("Edge")) return "Edge";
    return ua.slice(0, 40) + (ua.length > 40 ? "..." : "");
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
        <div style={{ marginBottom: "24px" }}>
          <h1 className="text-2xl font-bold" style={{ color: "#fff" }}>Active Sessions</h1>
          <p style={{ color: "var(--muted)", fontSize: "0.85rem", marginTop: "4px" }}>View and manage active user sessions</p>
        </div>

        {/* Search */}
        <div style={{
          marginBottom: "24px", padding: "20px", borderRadius: "14px",
          background: "var(--card)", border: "1px solid var(--line-bright)",
        }}>
          <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, color: "var(--muted)", marginBottom: "6px" }}>Keycloak User ID</label>
          <div style={{ display: "flex", gap: "8px" }}>
            <input
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
              placeholder="Enter Keycloak user ID..."
              style={{
                flex: 1, padding: "8px 12px", borderRadius: "8px", fontSize: "0.82rem",
                background: "var(--bg)", border: "1px solid var(--line-bright)", color: "#fff",
              }}
            />
            <button
              type="button"
              onClick={handleSearch}
              style={{
                padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                background: "var(--gradient-brand)", color: "#fff", border: "none", cursor: "pointer",
              }}
            >
              Search
            </button>
            <button
              type="button"
              onClick={loadOwnSessions}
              style={{
                padding: "8px 18px", borderRadius: "10px", fontSize: "0.82rem", fontWeight: 700,
                background: "var(--accent-soft)", color: "var(--accent)", border: "1px solid var(--accent-glow)", cursor: "pointer",
              }}
            >
              My Sessions
            </button>
          </div>
        </div>

        {/* Results */}
        {!searchKeycloakId.trim() ? (
          <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
            <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>Enter a Keycloak user ID to view their active sessions.</p>
          </div>
        ) : loading ? (
          <p style={{ color: "var(--muted)", textAlign: "center", padding: "40px 0" }}>Loading sessions...</p>
        ) : sessions.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", borderRadius: "14px", background: "var(--card)", border: "1px solid var(--line-bright)" }}>
            <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No active sessions found for this user.</p>
          </div>
        ) : (
          <>
            {/* Header with count + revoke all */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
              <p style={{ fontSize: "0.8rem", color: "var(--muted)" }}>{totalElements} active session{totalElements !== 1 ? "s" : ""}</p>
              <button
                type="button"
                disabled={revokingAll}
                onClick={() => { void revokeAll(); }}
                style={{
                  padding: "6px 14px", borderRadius: "8px", fontSize: "0.72rem", fontWeight: 700,
                  background: "var(--danger-soft)", color: "var(--danger)",
                  border: "1px solid var(--danger-glow)", cursor: "pointer",
                  opacity: revokingAll ? 0.6 : 1,
                }}
              >
                {revokingAll ? "Revoking..." : "Revoke All Sessions"}
              </button>
            </div>

            <div style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "var(--card)" }}>
                    {["IP Address", "Browser", "Last Activity", "Created", "Actions"].map((h) => (
                      <th key={h} style={{ padding: "10px 14px", fontSize: "0.68rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", textAlign: "left" }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sessions.map((s) => (
                    <tr key={s.id} style={{ borderTop: "1px solid var(--line-bright)" }}>
                      <td style={{ padding: "12px 14px", fontSize: "0.82rem", color: "#fff", fontFamily: "monospace" }}>{s.ipAddress || "—"}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.78rem", color: "var(--muted)" }}>{parseBrowser(s.userAgent)}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{new Date(s.lastActivityAt).toLocaleString()}</td>
                      <td style={{ padding: "12px 14px", fontSize: "0.75rem", color: "var(--muted)" }}>{new Date(s.createdAt).toLocaleString()}</td>
                      <td style={{ padding: "12px 14px" }}>
                        <button
                          type="button"
                          disabled={revokingId === s.id}
                          onClick={() => { void revokeSession(s.id); }}
                          style={{
                            padding: "4px 12px", borderRadius: "6px", fontSize: "0.72rem", fontWeight: 600,
                            background: "var(--danger-soft)", color: "var(--danger)",
                            border: "1px solid var(--danger-glow)", cursor: "pointer",
                            opacity: revokingId === s.id ? 0.6 : 1,
                          }}
                        >
                          {revokingId === s.id ? "Revoking..." : "Revoke"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "16px" }}>
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  style={{
                    padding: "6px 14px", borderRadius: "8px", fontSize: "0.78rem", fontWeight: 600,
                    background: "var(--card)", color: page === 0 ? "var(--muted)" : "#fff",
                    border: "1px solid var(--line-bright)", cursor: page === 0 ? "default" : "pointer",
                  }}
                >
                  Previous
                </button>
                <span style={{ padding: "6px 12px", fontSize: "0.78rem", color: "var(--muted)" }}>
                  {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  style={{
                    padding: "6px 14px", borderRadius: "8px", fontSize: "0.78rem", fontWeight: 600,
                    background: "var(--card)", color: page >= totalPages - 1 ? "var(--muted)" : "#fff",
                    border: "1px solid var(--line-bright)", cursor: page >= totalPages - 1 ? "default" : "pointer",
                  }}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
