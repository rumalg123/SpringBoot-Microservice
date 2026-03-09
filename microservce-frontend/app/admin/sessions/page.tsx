"use client";

import { useMemo, useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import TableSkeleton from "../../components/ui/TableSkeleton";
import KeycloakUserLookupField, {
  type KeycloakUserLookupResult,
} from "../../components/admin/access/KeycloakUserLookupField";

type ActiveSession = {
  id: string;
  keycloakId: string;
  keycloakSessionId?: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  lastActivityAt: string;
  createdAt: string;
};

type Paged<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
};

function parseBrowser(ua: string | null): string {
  if (!ua) return "Unknown";
  if (ua.includes("Edg")) return "Edge";
  if (ua.includes("Chrome")) return "Chrome";
  if (ua.includes("Firefox")) return "Firefox";
  if (ua.includes("Safari")) return "Safari";
  return ua.slice(0, 40) + (ua.length > 40 ? "..." : "");
}

export default function AdminSessionsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, profile, apiClient } = session;

  const [page, setPage] = useState(0);
  const [searchKeycloakId, setSearchKeycloakId] = useState("");
  const [selectedUser, setSelectedUser] = useState<KeycloakUserLookupResult | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const selectedDisplayName = useMemo(() => {
    if (!selectedUser) return "";
    return (selectedUser.displayName || "").trim()
      || (selectedUser.email || "").trim()
      || (selectedUser.username || "").trim()
      || selectedUser.id;
  }, [selectedUser]);

  const {
    data: sessionsData,
    isLoading: loading,
    refetch,
  } = useQuery({
    queryKey: ["admin-sessions", searchKeycloakId, page],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      const res = await apiClient!.get(`/admin/sessions/by-keycloak/${encodeURIComponent(searchKeycloakId.trim())}?${params.toString()}`);
      return res.data as Paged<ActiveSession>;
    },
    enabled: sessionStatus === "ready" && !!apiClient && !!searchKeycloakId.trim(),
  });

  const sessions = sessionsData?.content ?? [];
  const totalPages = sessionsData?.totalPages ?? 0;
  const totalElements = sessionsData?.totalElements ?? 0;

  const loadOwnSessions = () => {
    const sub = (profile?.sub as string) || "";
    if (!sub) return;
    setSelectedUser({
      id: sub,
      email: (profile?.email as string) || "",
      username: (profile?.preferred_username as string) || "",
      displayName: (profile?.name as string) || "",
      emailVerified: typeof profile?.email_verified === "boolean" ? (profile.email_verified as boolean) : true,
      enabled: true,
    });
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
    onSuccess: async () => {
      await refetch();
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
    onSuccess: async () => {
      await refetch();
      toast.success("All sessions revoked for this user");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to revoke all sessions");
    },
  });

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><p className="text-muted">Loading...</p></div>;
  }

  if (!session.isSuperAdmin) {
    return (
      <AdminPageShell title="Active Sessions" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Sessions" }]}>
        <p className="py-20 text-center text-muted">You do not have permission to manage sessions.</p>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Active Sessions"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Sessions" }]}
    >
      <p className="mb-6 -mt-4 text-sm text-muted">Search by email, username, or display name to inspect a user&rsquo;s active sessions.</p>

      <div className="mb-6 rounded-[14px] border border-line-bright bg-[var(--card)] p-5">
        <div className="space-y-4">
          <KeycloakUserLookupField
            apiClient={apiClient}
            onSelect={(user) => {
              setSelectedUser(user);
              setPage(0);
              setSearchKeycloakId(user.id);
            }}
            label="Find User"
            helperText="Search Keycloak users by email, name, or username. Selecting a result loads their active sessions."
          />

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-white/[0.02] px-4 py-3">
            <div className="min-w-0">
              {selectedUser ? (
                <>
                  <p className="truncate text-sm font-semibold text-white">{selectedDisplayName}</p>
                  <p className="truncate text-xs text-muted">
                    {selectedUser.email || selectedUser.username || "No email or username"}
                  </p>
                  <p className="truncate font-mono text-[11px] text-white/45">{selectedUser.id}</p>
                </>
              ) : (
                <p className="text-sm text-muted">No user selected yet.</p>
              )}
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={loadOwnSessions}
                className="rounded-md border border-accent-glow bg-accent-soft px-4 py-2 text-sm font-bold text-accent"
              >
                My Sessions
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelectedUser(null);
                  setSearchKeycloakId("");
                  setPage(0);
                }}
                className="rounded-md border border-line-bright bg-bg px-4 py-2 text-sm font-bold text-muted"
              >
                Clear
              </button>
            </div>
          </div>
        </div>
      </div>

      {!searchKeycloakId.trim() ? (
        <div className="rounded-[14px] border border-line-bright bg-[var(--card)] px-5 py-[60px] text-center">
          <p className="text-base text-muted">Select a user to view their active sessions.</p>
        </div>
      ) : loading ? (
        <TableSkeleton rows={3} cols={5} />
      ) : sessions.length === 0 ? (
        <div className="rounded-[14px] border border-line-bright bg-[var(--card)] px-5 py-[60px] text-center">
          <p className="text-base text-muted">No active sessions found for this user.</p>
        </div>
      ) : (
        <>
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm text-muted">{totalElements} active session{totalElements !== 1 ? "s" : ""}</p>
            <button
              type="button"
              disabled={revokeAllMutation.isPending}
              onClick={() => { revokeAllMutation.mutate(); }}
              className={`rounded-sm border border-danger-glow bg-danger-soft px-3.5 py-1.5 text-xs font-bold text-danger ${revokeAllMutation.isPending ? "opacity-60" : "cursor-pointer"}`}
            >
              {revokeAllMutation.isPending ? "Revoking..." : "Revoke All Sessions"}
            </button>
          </div>

          <div className="overflow-hidden rounded-[14px] border border-line-bright">
            <table className="admin-table w-full">
              <thead>
                <tr className="bg-[var(--card)]">
                  {["IP Address", "Browser", "Last Activity", "Created", "Actions"].map((header) => (
                    <th
                      key={header}
                      className="px-3.5 py-2.5 text-left text-xs font-extrabold uppercase tracking-[0.1em] text-muted"
                    >
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sessions.map((sessionRow) => (
                  <tr key={sessionRow.id} className="border-t border-line-bright">
                    <td className="px-3.5 py-3 font-mono text-base text-white">{sessionRow.ipAddress || "\u2014"}</td>
                    <td className="px-3.5 py-3 text-sm text-muted">{parseBrowser(sessionRow.userAgent)}</td>
                    <td className="px-3.5 py-3 text-xs text-muted">{new Date(sessionRow.lastActivityAt).toLocaleString()}</td>
                    <td className="px-3.5 py-3 text-xs text-muted">{new Date(sessionRow.createdAt).toLocaleString()}</td>
                    <td className="px-3.5 py-3">
                      <button
                        type="button"
                        disabled={revokingId === sessionRow.id}
                        onClick={() => { revokeSessionMutation.mutate(sessionRow.id); }}
                        className={`rounded-sm border border-danger-glow bg-danger-soft px-3 py-1 text-xs font-semibold text-danger ${revokingId === sessionRow.id ? "opacity-60" : "cursor-pointer"}`}
                      >
                        {revokingId === sessionRow.id ? "Revoking..." : "Revoke"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="mt-4 flex justify-center gap-2">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
                className={`rounded-sm border border-line-bright bg-[var(--card)] px-3.5 py-1.5 text-sm font-semibold ${page === 0 ? "cursor-default text-muted" : "cursor-pointer text-white"}`}
              >
                Previous
              </button>
              <span className="px-3 py-1.5 text-sm text-muted">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
                className={`rounded-sm border border-line-bright bg-[var(--card)] px-3.5 py-1.5 text-sm font-semibold ${page >= totalPages - 1 ? "cursor-default text-muted" : "cursor-pointer text-white"}`}
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
