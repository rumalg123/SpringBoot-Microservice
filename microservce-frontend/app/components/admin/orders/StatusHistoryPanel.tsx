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

function accentStyles(accent: "cyan" | "violet") {
  if (accent === "violet") {
    return {
      panelBorder: "1px solid rgba(124,58,237,0.12)",
      panelBg: "rgba(124,58,237,0.03)",
      titleColor: "#ddd6fe",
      controlBorder: "1px solid rgba(124,58,237,0.18)",
      controlBg: "rgba(124,58,237,0.06)",
      controlText: "#e9d5ff",
      buttonBg: "rgba(124,58,237,0.08)",
      buttonText: "#c4b5fd",
      rowBorder: "1px solid rgba(255,255,255,0.06)",
      rowBg: "rgba(255,255,255,0.015)",
    };
  }
  return {
    panelBorder: "1px solid rgba(0,212,255,0.08)",
    panelBg: "rgba(0,212,255,0.03)",
    titleColor: "#c8c8e8",
    controlBorder: "1px solid rgba(0,212,255,0.12)",
    controlBg: "rgba(0,212,255,0.04)",
    controlText: "#c8c8e8",
    buttonBg: "rgba(0,212,255,0.04)",
    buttonText: "#67e8f9",
    rowBorder: "1px solid rgba(0,212,255,0.07)",
    rowBg: "rgba(255,255,255,0.01)",
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
  const styles = accentStyles(accent);

  return (
    <div
      style={{
        marginBottom: "12px",
        borderRadius: "12px",
        border: styles.panelBorder,
        background: styles.panelBg,
        padding: "12px",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px" }}>
        <div>
          <p style={{ margin: 0, color: styles.titleColor, fontWeight: 700, fontSize: "0.85rem" }}>{title}</p>
          <p style={{ margin: "4px 0 0", color: "var(--muted)", fontSize: "0.7rem", fontFamily: "monospace" }}>{entityId}</p>
        </div>
        <div style={{ display: "flex", gap: "8px" }}>
          <button
            type="button"
            onClick={() => { void onRefresh(); }}
            disabled={loading}
            style={{
              padding: "6px 10px",
              borderRadius: "8px",
              border: styles.controlBorder,
              background: styles.buttonBg,
              color: styles.buttonText,
              fontSize: "0.72rem",
            }}
          >
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: "6px 10px",
              borderRadius: "8px",
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.02)",
              color: "var(--muted)",
              fontSize: "0.72rem",
            }}
          >
            Close
          </button>
        </div>
      </div>

      <div style={{ marginTop: "10px", display: "flex", flexWrap: "wrap", gap: "8px", alignItems: "center" }}>
        <select
          value={actorTypeFilter}
          onChange={(e) => onActorTypeFilterChange(e.target.value)}
          style={{
            borderRadius: "8px",
            border: styles.controlBorder,
            background: styles.controlBg,
            color: styles.controlText,
            padding: "6px 8px",
            fontSize: "0.72rem",
          }}
        >
          <option value="ALL">All actors</option>
          {actorTypeOptions.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
        <select
          value={sourceFilter}
          onChange={(e) => onSourceFilterChange(e.target.value)}
          style={{
            borderRadius: "8px",
            border: styles.controlBorder,
            background: styles.controlBg,
            color: styles.controlText,
            padding: "6px 8px",
            fontSize: "0.72rem",
          }}
        >
          <option value="ALL">All sources</option>
          {sourceOptions.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
        <span style={{ color: "var(--muted)", fontSize: "0.72rem" }}>
          Showing {visibleRows.length} of {filteredRows.length} filtered entries ({rows.length} total)
        </span>
        {filteredRows.length > 6 && (
          <button
            type="button"
            onClick={onToggleExpanded}
            style={{
              padding: "6px 8px",
              borderRadius: "8px",
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.02)",
              color: "var(--muted)",
              fontSize: "0.72rem",
            }}
          >
            {expanded ? "Collapse" : "Show All"}
          </button>
        )}
      </div>

      <div style={{ marginTop: "10px", display: "grid", gap: "8px" }}>
        {loading && <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>Loading history...</div>}
        {!loading && filteredRows.length === 0 && (
          <div style={{ color: "var(--muted)", fontSize: "0.75rem" }}>No history entries found.</div>
        )}
        {!loading && visibleRows.map((row) => (
          <div
            key={row.id}
            style={{
              border: styles.rowBorder,
              background: styles.rowBg,
              borderRadius: "10px",
              padding: "10px",
            }}
          >
            <div style={{ display: "flex", flexWrap: "wrap", gap: "8px", alignItems: "center" }}>
              <span style={{ color: "#c8c8e8", fontWeight: 700, fontSize: "0.75rem" }}>
                {(row.fromStatus || "NEW").replaceAll("_", " ")} {"->"} {row.toStatus.replaceAll("_", " ")}
              </span>
              <span style={{ color: "var(--muted)", fontSize: "0.7rem" }}>{new Date(row.createdAt).toLocaleString()}</span>
            </div>
            <div style={{ marginTop: "4px", color: "var(--muted)", fontSize: "0.7rem" }}>
              {row.actorType} | {row.changeSource}{row.actorSub ? ` | ${row.actorSub}` : ""}
            </div>
            {row.note && <div style={{ marginTop: "4px", color: "var(--muted)", fontSize: "0.7rem" }}>{row.note}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

