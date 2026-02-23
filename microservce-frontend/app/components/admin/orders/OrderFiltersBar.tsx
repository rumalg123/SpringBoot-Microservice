"use client";

type Props = {
  customerEmailInput: string;
  filterBusy: boolean;
  onCustomerEmailInputChange: (value: string) => void;
  onSubmit: () => void | Promise<void>;
  onClear: () => void | Promise<void>;
};

const darkInput: React.CSSProperties = {
  flex: 1,
  padding: "10px 14px",
  borderRadius: "10px",
  border: "1px solid rgba(0,212,255,0.15)",
  background: "rgba(0,212,255,0.04)",
  color: "#c8c8e8",
  fontSize: "0.85rem",
  outline: "none",
};

export default function OrderFiltersBar({
  customerEmailInput,
  filterBusy,
  onCustomerEmailInputChange,
  onSubmit,
  onClear,
}: Props) {
  return (
    <>
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "10px", marginBottom: "12px" }}>
        <div style={{ position: "relative", display: "flex", alignItems: "center", flex: 1, minWidth: "260px", ...darkInput, padding: 0, overflow: "hidden" }}>
          <span style={{ padding: "0 12px", color: "var(--muted)", flexShrink: 0 }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
          </span>
          <input
            value={customerEmailInput}
            onChange={(e) => onCustomerEmailInputChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                void onSubmit();
              }
            }}
            placeholder="Filter by customer email..."
            disabled={filterBusy}
            style={{ flex: 1, border: "none", background: "transparent", color: "#c8c8e8", fontSize: "0.85rem", outline: "none", padding: "10px 0" }}
          />
          {customerEmailInput && (
            <button
              type="button"
              onClick={() => { void onClear(); }}
              disabled={filterBusy}
              style={{ marginRight: "10px", width: "20px", height: "20px", borderRadius: "50%", background: "rgba(0,212,255,0.15)", border: "none", color: "var(--muted)", fontSize: "0.75rem", cursor: "pointer", display: "grid", placeItems: "center", flexShrink: 0 }}
            >
              x
            </button>
          )}
        </div>
        <button
          type="button"
          onClick={() => { void onSubmit(); }}
          disabled={filterBusy}
          style={{
            padding: "10px 20px", borderRadius: "10px", border: "none",
            background: filterBusy ? "rgba(0,212,255,0.2)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
            color: "#fff", fontSize: "0.82rem", fontWeight: 700,
            cursor: filterBusy ? "not-allowed" : "pointer",
          }}
        >
          {filterBusy ? "Applying..." : "Apply Filter"}
        </button>
      </div>
      <p style={{ fontSize: "0.68rem", color: "var(--muted-2)", marginBottom: "16px" }}>Use full customer email, e.g. user@example.com</p>
    </>
  );
}
