"use client";

import type { ActivityLogEntry } from "../../../lib/types/customer";

type ActivityLogData = {
  content: ActivityLogEntry[];
  totalPages: number;
  totalElements: number;
  number: number;
};

type ActivityLogTabProps = {
  activityLog: ActivityLogData | null;
  activityLogLoading: boolean;
  activityLogPage: number;
  onPageChange: (page: number) => void;
};

export default function ActivityLogTab({
  activityLog,
  activityLogLoading,
  activityLogPage,
  onPageChange,
}: ActivityLogTabProps) {
  return (
    <article className="glass-card p-6">
      <div className="flex items-center gap-3 mb-5">
        <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[image:var(--gradient-brand)] grid place-items-center">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
        </div>
        <div>
          <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-1 mt-0">Activity Log</h2>
          <p className="text-[0.75rem] text-muted m-0">
            Recent actions on your account.
            {activityLog ? ` ${activityLog.totalElements} total entries.` : ""}
          </p>
        </div>
      </div>

      {activityLogLoading && !activityLog && (
        <div className="text-center py-6">
          <div className="spinner-lg" />
          <p className="mt-3 text-muted text-[0.82rem]">Loading activity log...</p>
        </div>
      )}

      {activityLog && activityLog.content.length === 0 && (
        <p className="text-[0.82rem] text-muted">No activity recorded yet.</p>
      )}

      {activityLog && activityLog.content.length > 0 && (
        <>
          {/* Timeline */}
          <div className="relative pl-7">
            {/* Vertical line */}
            <div className="absolute left-[9px] top-[6px] bottom-[6px] w-[2px] bg-line-bright" />

            {activityLog.content.map((entry, idx) => (
              <div
                key={entry.id}
                className="relative"
                style={{ paddingBottom: idx < activityLog.content.length - 1 ? "20px" : "0" }}
              >
                {/* Dot */}
                <div className="absolute -left-[23px] top-[6px] w-[10px] h-[10px] rounded-full bg-brand border-2 border-surface-2" />

                <div className="rounded-[12px] bg-brand-soft border border-brand-soft px-4 py-3">
                  <p className="text-[0.85rem] font-bold text-white mb-1">
                    {entry.action}
                  </p>
                  {entry.details && (
                    <p className="text-[0.78rem] text-ink-light mb-[6px] leading-[1.5]">
                      {entry.details}
                    </p>
                  )}
                  <div className="flex flex-wrap gap-3 items-center">
                    {entry.ipAddress && (
                      <span className="text-[0.68rem] text-muted font-mono">
                        IP: {entry.ipAddress}
                      </span>
                    )}
                    <span className="text-[0.68rem] text-muted">
                      {new Date(entry.createdAt).toLocaleString("en-US", {
                        year: "numeric", month: "short", day: "numeric",
                        hour: "2-digit", minute: "2-digit",
                      })}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {activityLog.totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <button
                onClick={() => onPageChange(activityLogPage - 1)}
                disabled={activityLogPage === 0 || activityLogLoading}
                className={`px-[14px] py-2 rounded-[8px] border border-line-bright bg-brand-soft text-brand text-[0.78rem] font-bold ${activityLogPage === 0 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}
              >
                Previous
              </button>
              <span className="text-[0.78rem] text-muted px-2">
                Page {activityLogPage + 1} of {activityLog.totalPages}
              </span>
              <button
                onClick={() => onPageChange(activityLogPage + 1)}
                disabled={activityLogPage >= activityLog.totalPages - 1 || activityLogLoading}
                className={`px-[14px] py-2 rounded-[8px] border border-line-bright bg-brand-soft text-brand text-[0.78rem] font-bold ${activityLogPage >= activityLog.totalPages - 1 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}
              >
                Next
              </button>
            </div>
          )}

          {/* Loading overlay for pagination */}
          {activityLogLoading && activityLog && (
            <div className="text-center py-3">
              <div className="spinner-sm inline-block" />
              <span className="ml-2 text-muted text-[0.78rem]">Loading...</span>
            </div>
          )}
        </>
      )}
    </article>
  );
}
