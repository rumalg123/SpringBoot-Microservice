"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import TableSkeleton from "../../components/ui/TableSkeleton";

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
  const session = useAuthSession();
  const { status: sessionStatus, profile, apiClient } = session;

  const [page, setPage] = useState(0);
  const [lookupId, setLookupId] = useState("");
  const [searchKeycloakId, setSearchKeycloakId] = useState("");
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const { data: sessionsData, isLoading: loading, refetch } = useQuery({
    queryKey: ["admin-sessions", searchKeycloakId, page],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      const res = await apiClient!.get(`/admin/sessions/by-keycloak/${encodeURIComponent(searchKeycloakId.trim())}?${params}`);
      return res.data as Paged<ActiveSession>;
    },
    enabled: sessionStatus === "ready" && !!apiClient && !!searchKeycloakId.trim(),
  });

  const sessions = sessionsData?.content ?? [];
  const totalPages = sessionsData?.totalPages ?? 0;
  const totalElements = sessionsData?.totalElements ?? 0;

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

  const revokeSessionMutation = useMutation({
    mutationFn: async (sessionId: string) => {
      await apiClient!.delete(`/admin/sessions/${sessionId}`);
      return sessionId;
    },
    onMutate: (sessionId) => {
      setRevokingId(sessionId);
    },
    onSuccess: () => {
      void refetch();
      toast.success("Session revoked");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to revoke session");
    },
    onSettled: () => {
      setRevokingId(null);
    },
  });

  const revokeAllMutation = useMutation({
    mutationFn: async () => {
      await apiClient!.delete(`/admin/sessions/by-keycloak/${encodeURIComponent(searchKeycloakId.trim())}`);
    },
    onSuccess: () => {
      void refetch();
      toast.success("All sessions revoked for this user");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to revoke all sessions");
    },
  });

  const parseBrowser = (ua: string | null) => {
    if (!ua) return "Unknown";
    if (ua.includes("Chrome")) return "Chrome";
    if (ua.includes("Firefox")) return "Firefox";
    if (ua.includes("Safari")) return "Safari";
    if (ua.includes("Edge")) return "Edge";
    return ua.slice(0, 40) + (ua.length > 40 ? "..." : "");
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }
  if (!session.isSuperAdmin) {
    return (
      <AdminPageShell title="Active Sessions" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Sessions" }]}>
        <p className="text-center text-muted py-20">You do not have permission to manage sessions.</p>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Active Sessions"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Sessions" }]}
    >
        <p className="text-muted text-sm -mt-4 mb-6">View and manage active user sessions</p>

        {/* Search */}
        <div className="mb-6 p-5 rounded-[14px] bg-[var(--card)] border border-line-bright">
          <label className="block text-xs font-bold text-muted mb-1.5">Keycloak User ID</label>
          <div className="flex gap-2">
            <input
              value={lookupId}
              onChange={(e) => setLookupId(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") handleSearch(); }}
              placeholder="Enter Keycloak user ID..."
              className="flex-1 px-3 py-2 rounded-sm text-base bg-bg border border-line-bright text-white"
            />
            <button
              type="button"
              onClick={handleSearch}
              className="btn-primary px-4 py-2 rounded-md text-base font-bold"
            >
              Search
            </button>
            <button
              type="button"
              onClick={loadOwnSessions}
              className="px-4 py-2 rounded-md text-base font-bold bg-accent-soft text-accent border border-accent-glow cursor-pointer"
            >
              My Sessions
            </button>
          </div>
        </div>

        {/* Results */}
        {!searchKeycloakId.trim() ? (
          <div className="text-center px-5 py-[60px] rounded-[14px] bg-[var(--card)] border border-line-bright">
            <p className="text-muted text-base">Enter a Keycloak user ID to view their active sessions.</p>
          </div>
        ) : loading ? (
          <TableSkeleton rows={3} cols={5} />
        ) : sessions.length === 0 ? (
          <div className="text-center px-5 py-[60px] rounded-[14px] bg-[var(--card)] border border-line-bright">
            <p className="text-muted text-base">No active sessions found for this user.</p>
          </div>
        ) : (
          <>
            {/* Header with count + revoke all */}
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm text-muted">{totalElements} active session{totalElements !== 1 ? "s" : ""}</p>
              <button
                type="button"
                disabled={revokeAllMutation.isPending}
                onClick={() => { revokeAllMutation.mutate(); }}
                className={`px-3.5 py-1.5 rounded-sm text-xs font-bold bg-danger-soft text-danger border border-danger-glow cursor-pointer ${revokeAllMutation.isPending ? "opacity-60" : ""}`}
              >
                {revokeAllMutation.isPending ? "Revoking..." : "Revoke All Sessions"}
              </button>
            </div>

            <div className="rounded-[14px] overflow-hidden border border-line-bright">
              <table className="admin-table w-full">
                <thead>
                  <tr className="bg-[var(--card)]">
                    {["IP Address", "Browser", "Last Activity", "Created", "Actions"].map((h) => (
                      <th key={h} className="px-3.5 py-2.5 text-xs font-extrabold uppercase tracking-[0.1em] text-muted text-left">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sessions.map((s) => (
                    <tr key={s.id} className="border-t border-line-bright">
                      <td className="px-3.5 py-3 text-base text-white font-mono">{s.ipAddress || "—"}</td>
                      <td className="px-3.5 py-3 text-sm text-muted">{parseBrowser(s.userAgent)}</td>
                      <td className="px-3.5 py-3 text-xs text-muted">{new Date(s.lastActivityAt).toLocaleString()}</td>
                      <td className="px-3.5 py-3 text-xs text-muted">{new Date(s.createdAt).toLocaleString()}</td>
                      <td className="px-3.5 py-3">
                        <button
                          type="button"
                          disabled={revokingId === s.id}
                          onClick={() => { revokeSessionMutation.mutate(s.id); }}
                          className={`px-3 py-1 rounded-sm text-xs font-semibold bg-danger-soft text-danger border border-danger-glow cursor-pointer ${revokingId === s.id ? "opacity-60" : ""}`}
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
              <div className="flex justify-center gap-2 mt-4">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  className={`px-3.5 py-1.5 rounded-sm text-sm font-semibold bg-[var(--card)] border border-line-bright ${page === 0 ? "text-muted cursor-default" : "text-white cursor-pointer"}`}
                >
                  Previous
                </button>
                <span className="px-3 py-1.5 text-sm text-muted">
                  {page + 1} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className={`px-3.5 py-1.5 rounded-sm text-sm font-semibold bg-[var(--card)] border border-line-bright ${page >= totalPages - 1 ? "text-muted cursor-default" : "text-white cursor-pointer"}`}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
    </AdminPageShell>
  );
}
