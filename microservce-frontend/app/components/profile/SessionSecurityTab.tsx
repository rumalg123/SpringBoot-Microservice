"use client";

import { useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import { useMutation, useQuery } from "@tanstack/react-query";
import TableSkeleton from "../ui/TableSkeleton";

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

type SessionSecurityTabProps = {
  apiClient: AxiosInstance | null;
  currentKeycloakSessionId?: string | null;
};

function parseBrowser(userAgent: string | null): string {
  if (!userAgent) return "Unknown";
  if (userAgent.includes("Edg")) return "Edge";
  if (userAgent.includes("Chrome")) return "Chrome";
  if (userAgent.includes("Firefox")) return "Firefox";
  if (userAgent.includes("Safari")) return "Safari";
  return userAgent.slice(0, 48) + (userAgent.length > 48 ? "..." : "");
}

function isCurrentSession(session: ActiveSession, currentKeycloakSessionId?: string | null): boolean {
  if (!currentKeycloakSessionId || !session.keycloakSessionId) {
    return false;
  }
  return session.keycloakSessionId.trim().toLowerCase() === currentKeycloakSessionId.trim().toLowerCase();
}

export default function SessionSecurityTab({
  apiClient,
  currentKeycloakSessionId,
}: SessionSecurityTabProps) {
  const [page, setPage] = useState(0);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const {
    data: sessionsData,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["me-sessions", page],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: "20" });
      const res = await apiClient!.get(`/me/sessions?${params.toString()}`);
      return res.data as Paged<ActiveSession>;
    },
    enabled: !!apiClient,
  });

  const sessions = sessionsData?.content ?? [];
  const totalPages = sessionsData?.totalPages ?? 0;
  const totalElements = sessionsData?.totalElements ?? 0;

  const revokeSessionMutation = useMutation({
    mutationFn: async (session: ActiveSession) => {
      await apiClient!.delete(`/me/sessions/${session.id}`);
      return session;
    },
    onMutate: (session) => {
      setRevokingId(session.id);
    },
    onSuccess: async (session) => {
      if (isCurrentSession(session, currentKeycloakSessionId)) {
        toast.success("Current session revoked. Redirecting...");
        window.location.assign("/");
        return;
      }
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
      await apiClient!.delete("/me/sessions");
    },
    onSuccess: () => {
      toast.success("All sessions revoked. Redirecting...");
      window.location.assign("/");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to revoke all sessions");
    },
  });

  return (
    <section className="space-y-4 rounded-[18px] border border-line bg-surface px-5 py-5 shadow-[0_18px_42px_rgba(0,0,0,0.18)]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="m-0 text-[1.05rem] font-extrabold text-ink">Session Security</h2>
          <p className="mt-1 text-sm text-muted">
            Review where your account is active and revoke devices you do not recognize.
          </p>
        </div>
        <button
          type="button"
          disabled={!apiClient || revokeAllMutation.isPending}
          onClick={() => { revokeAllMutation.mutate(); }}
          className={`rounded-md border border-danger-glow bg-danger-soft px-3.5 py-2 text-xs font-bold text-danger ${revokeAllMutation.isPending ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
        >
          {revokeAllMutation.isPending ? "Signing out..." : "Sign Out All Sessions"}
        </button>
      </div>

      {!apiClient ? (
        <div className="rounded-xl border border-line bg-white/[0.02] px-4 py-12 text-center text-sm text-muted">
          Session management is unavailable right now.
        </div>
      ) : isLoading ? (
        <TableSkeleton rows={3} cols={5} />
      ) : sessions.length === 0 ? (
        <div className="rounded-xl border border-line bg-white/[0.02] px-4 py-12 text-center text-sm text-muted">
          No active sessions were found for this account.
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted">
              {totalElements} active session{totalElements !== 1 ? "s" : ""}
            </p>
            <p className="text-xs text-muted">
              The current browser is marked when the session identifier matches this tab.
            </p>
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
                {sessions.map((session) => {
                  const current = isCurrentSession(session, currentKeycloakSessionId);
                  return (
                    <tr key={session.id} className="border-t border-line-bright">
                      <td className="px-3.5 py-3 text-sm text-white font-mono">
                        <div className="flex flex-col gap-1">
                          <span>{session.ipAddress || "\u2014"}</span>
                          {current && (
                            <span className="inline-flex w-fit rounded-full border border-brand/30 bg-brand-soft px-2 py-0.5 text-[11px] font-bold uppercase tracking-[0.08em] text-brand">
                              Current Session
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-3.5 py-3 text-sm text-muted">{parseBrowser(session.userAgent)}</td>
                      <td className="px-3.5 py-3 text-xs text-muted">{new Date(session.lastActivityAt).toLocaleString()}</td>
                      <td className="px-3.5 py-3 text-xs text-muted">{new Date(session.createdAt).toLocaleString()}</td>
                      <td className="px-3.5 py-3">
                        <button
                          type="button"
                          disabled={revokingId === session.id}
                          onClick={() => { revokeSessionMutation.mutate(session); }}
                          className={`rounded-sm border border-danger-glow bg-danger-soft px-3 py-1 text-xs font-semibold text-danger ${revokingId === session.id ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
                        >
                          {revokingId === session.id ? "Revoking..." : current ? "Sign Out Here" : "Revoke"}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
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
    </section>
  );
}
