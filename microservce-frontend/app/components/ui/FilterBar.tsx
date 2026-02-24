"use client";

export type FilterDef = {
  key: string;
  label: string;
  type: "text" | "select" | "date" | "dateRange";
  placeholder?: string;
  options?: { label: string; value: string }[];
};

type Props = {
  filters: FilterDef[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  onClear: () => void;
};

export default function FilterBar({ filters, values, onChange, onClear }: Props) {
  const activeCount = Object.values(values).filter((v) => v.trim() !== "").length;
  const hasAnyValue = activeCount > 0;

  const inputBase: React.CSSProperties = {
    padding: "8px 12px",
    borderRadius: 8,
    border: "1px solid var(--line)",
    background: "var(--surface-2)",
    color: "var(--ink)",
    fontSize: "0.8rem",
    outline: "none",
    transition: "border-color 0.15s",
    minWidth: 0,
  };

  const labelStyle: React.CSSProperties = {
    display: "block",
    marginBottom: 4,
    fontSize: "0.7rem",
    fontWeight: 600,
    color: "var(--muted)",
    textTransform: "uppercase",
    letterSpacing: "0.05em",
  };

  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        gap: 12,
        padding: "14px 16px",
        borderRadius: 12,
        background: "var(--surface-2)",
        border: "1px solid var(--line)",
        marginBottom: 16,
      }}
    >
      {filters.map((filter) => {
        const currentValue = values[filter.key] || "";

        if (filter.type === "text") {
          return (
            <div key={filter.key} style={{ flex: "1 1 180px", minWidth: 160 }}>
              <label style={labelStyle}>{filter.label}</label>
              <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
                <span
                  style={{
                    position: "absolute",
                    left: 10,
                    color: "var(--muted)",
                    pointerEvents: "none",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                  </svg>
                </span>
                <input
                  value={currentValue}
                  onChange={(e) => onChange(filter.key, e.target.value)}
                  placeholder={filter.placeholder || `Filter ${filter.label.toLowerCase()}...`}
                  style={{ ...inputBase, width: "100%", paddingLeft: 30 }}
                  onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                  onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
                />
              </div>
            </div>
          );
        }

        if (filter.type === "select") {
          return (
            <div key={filter.key} style={{ flex: "1 1 160px", minWidth: 140 }}>
              <label style={labelStyle}>{filter.label}</label>
              <select
                value={currentValue}
                onChange={(e) => onChange(filter.key, e.target.value)}
                style={{
                  ...inputBase,
                  width: "100%",
                  cursor: "pointer",
                  appearance: "none",
                  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`,
                  backgroundRepeat: "no-repeat",
                  backgroundPosition: "right 10px center",
                  paddingRight: 30,
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
              >
                <option value="">{filter.placeholder || `All ${filter.label}`}</option>
                {(filter.options || []).map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          );
        }

        if (filter.type === "date") {
          return (
            <div key={filter.key} style={{ flex: "1 1 160px", minWidth: 140 }}>
              <label style={labelStyle}>{filter.label}</label>
              <input
                type="date"
                value={currentValue}
                onChange={(e) => onChange(filter.key, e.target.value)}
                style={{ ...inputBase, width: "100%", colorScheme: "dark" }}
                onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
              />
            </div>
          );
        }

        if (filter.type === "dateRange") {
          const fromKey = `${filter.key}_from`;
          const toKey = `${filter.key}_to`;
          const fromValue = values[fromKey] || "";
          const toValue = values[toKey] || "";
          return (
            <div key={filter.key} style={{ flex: "2 1 280px", minWidth: 240 }}>
              <label style={labelStyle}>{filter.label}</label>
              <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                <input
                  type="date"
                  value={fromValue}
                  onChange={(e) => onChange(fromKey, e.target.value)}
                  style={{ ...inputBase, flex: 1, colorScheme: "dark" }}
                  onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                  onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
                />
                <span style={{ fontSize: "0.72rem", color: "var(--muted)", flexShrink: 0 }}>to</span>
                <input
                  type="date"
                  value={toValue}
                  onChange={(e) => onChange(toKey, e.target.value)}
                  style={{ ...inputBase, flex: 1, colorScheme: "dark" }}
                  onFocus={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; }}
                  onBlur={(e) => { e.currentTarget.style.borderColor = "var(--line)"; }}
                />
              </div>
            </div>
          );
        }

        return null;
      })}

      {/* Clear All button + active filter count */}
      <div style={{ display: "flex", alignItems: "center", gap: 8, paddingBottom: 1 }}>
        {hasAnyValue && (
          <>
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                minWidth: 20,
                height: 20,
                borderRadius: 999,
                background: "var(--brand-soft)",
                border: "1px solid var(--line-bright)",
                color: "var(--brand)",
                fontSize: "0.68rem",
                fontWeight: 800,
                padding: "0 5px",
              }}
            >
              {activeCount}
            </span>
            <button
              type="button"
              onClick={onClear}
              className="btn-ghost"
              style={{
                fontSize: "0.76rem",
                padding: "7px 14px",
                display: "inline-flex",
                alignItems: "center",
                gap: 5,
                color: "var(--danger)",
                whiteSpace: "nowrap",
              }}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
              Clear All
            </button>
          </>
        )}
      </div>
    </div>
  );
}
