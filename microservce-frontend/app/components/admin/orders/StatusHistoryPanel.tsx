"use client";

type StatusHistoryRow = {
  id: string;
  fromStatus: string | null;
  toStatus: string;
  actorSub: string | null;
  actorType: string;
  changeSource: string;
  note: string | null;
  createdAt: string;
};

type Props<T extends StatusHistoryRow> = {
  title: string;
  entityId: string | null;
  rows: T[];
  loading: boolean;
  actorTypeFilter: string;
  sourceFilter: string;
  actorTypeOptions: string[];
  sourceOptions: string[];
  expanded: boolean;
  accent: "cyan" | "violet";
  onActorTypeFilterChange: (value: string) => void;
  onSourceFilterChange: (value: string) => void;
  onToggleExpanded: () => void;
  onRefresh: () => void | Promise<void>;
  onClose: () => void;
};

function accentClasses(accent: "cyan" | "violet") {
  if (accent === "violet") {
    return {
      panel: "border-[rgba(124,58,237,0.12)] bg-[rgba(124,58,237,0.03)]",
      titleColor: "text-[#ddd6fe]",
      control: "border-[rgba(124,58,237,0.18)] bg-[rgba(124,58,237,0.06)] text-[#e9d5ff]",
      button: "border-[rgba(124,58,237,0.18)] bg-[rgba(124,58,237,0.08)] text-[#c4b5fd]",
      row: "border-white/[0.06] bg-white/[0.015]",
    };
  }
  return {
    panel: "border-[rgba(0,212,255,0.08)] bg-[rgba(0,212,255,0.03)]",
    titleColor: "text-[#c8c8e8]",
    control: "border-[rgba(0,212,255,0.12)] bg-[rgba(0,212,255,0.04)] text-[#c8c8e8]",
    button: "border-[rgba(0,212,255,0.12)] bg-[rgba(0,212,255,0.04)] text-[#67e8f9]",
    row: "border-[rgba(0,212,255,0.07)] bg-white/[0.01]",
  };
}

export default function StatusHistoryPanel<T extends StatusHistoryRow>({
  title,
  entityId,
  rows,
  loading,
  actorTypeFilter,
  sourceFilter,
  actorTypeOptions,
  sourceOptions,
  expanded,
  accent,
  onActorTypeFilterChange,
  onSourceFilterChange,
  onToggleExpanded,
  onRefresh,
  onClose,
}: Props<T>) {
  if (!entityId) return null;

  const filteredRows = rows.filter((row) => {
    if (actorTypeFilter !== "ALL" && row.actorType !== actorTypeFilter) return false;
    if (sourceFilter !== "ALL" && row.changeSource !== sourceFilter) return false;
    return true;
  });
  const visibleRows = expanded ? filteredRows : filteredRows.slice(0, 6);
  const cls = accentClasses(accent);

  return (
    <div className={`mb-3 rounded-xl border p-3 ${cls.panel}`}>
      <div className="flex items-center justify-between gap-2.5">
        <div>
          <p className={`m-0 text-base font-bold ${cls.titleColor}`}>{title}</p>
          <p className="mt-1 text-[0.7rem] font-mono text-muted">{entityId}</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => { void onRefresh(); }}
            disabled={loading}
            className={`rounded-md px-2.5 py-1.5 text-xs ${cls.button}`}
          >
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-white/[0.08] bg-white/[0.02] px-2.5 py-1.5 text-xs text-muted"
          >
            Close
          </button>
        </div>
      </div>

      <div className="mt-2.5 flex flex-wrap items-center gap-2">
        <select
          value={actorTypeFilter}
          onChange={(e) => onActorTypeFilterChange(e.target.value)}
          className={`rounded-md px-2 py-1.5 text-xs ${cls.control}`}
        >
          <option value="ALL">All actors</option>
          {actorTypeOptions.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
        <select
          value={sourceFilter}
          onChange={(e) => onSourceFilterChange(e.target.value)}
          className={`rounded-md px-2 py-1.5 text-xs ${cls.control}`}
        >
          <option value="ALL">All sources</option>
          {sourceOptions.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
        <span className="text-xs text-muted">
          Showing {visibleRows.length} of {filteredRows.length} filtered entries ({rows.length} total)
        </span>
        {filteredRows.length > 6 && (
          <button
            type="button"
            onClick={onToggleExpanded}
            className="rounded-md border border-white/[0.08] bg-white/[0.02] px-2 py-1.5 text-xs text-muted"
          >
            {expanded ? "Collapse" : "Show All"}
          </button>
        )}
      </div>

      <div className="mt-2.5 grid gap-2">
        {loading && <div className="text-xs text-muted">Loading history...</div>}
        {!loading && filteredRows.length === 0 && (
          <div className="text-xs text-muted">No history entries found.</div>
        )}
        {!loading && visibleRows.map((row) => (
          <div
            key={row.id}
            className={`rounded-md border p-2.5 ${cls.row}`}
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-bold text-[#c8c8e8]">
                {(row.fromStatus || "NEW").replaceAll("_", " ")} {"->"} {row.toStatus.replaceAll("_", " ")}
              </span>
              <span className="text-[0.7rem] text-muted">{new Date(row.createdAt).toLocaleString()}</span>
            </div>
            <div className="mt-1 text-[0.7rem] text-muted">
              {row.actorType} | {row.changeSource}{row.actorSub ? ` | ${row.actorSub}` : ""}
            </div>
            {row.note && <div className="mt-1 text-[0.7rem] text-muted">{row.note}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}
