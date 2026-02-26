"use client";

type Props = {
  /** "cards" renders a card grid, "table" renders table rows, "form" renders form fields */
  variant?: "cards" | "table" | "form";
};

export default function PageSkeleton({ variant = "cards" }: Props) {
  return (
    <div style={{ padding: "24px", maxWidth: "1200px", margin: "0 auto" }}>
      {/* Title bar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <div>
          <div className="skeleton" style={{ height: "24px", width: "200px", marginBottom: "8px" }} />
          <div className="skeleton" style={{ height: "12px", width: "140px" }} />
        </div>
        <div className="skeleton" style={{ height: "36px", width: "120px", borderRadius: "10px" }} />
      </div>

      {variant === "cards" && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: "16px" }}>
          {Array.from({ length: 8 }, (_, i) => (
            <div
              key={i}
              style={{ borderRadius: "12px", overflow: "hidden", border: "1px solid var(--line)", background: "var(--surface)" }}
            >
              <div className="skeleton" style={{ height: "180px", width: "100%", borderRadius: 0 }} />
              <div style={{ padding: "12px", display: "flex", flexDirection: "column", gap: "6px" }}>
                <div className="skeleton" style={{ height: "12px", width: "80%" }} />
                <div className="skeleton" style={{ height: "12px", width: "55%" }} />
              </div>
            </div>
          ))}
        </div>
      )}

      {variant === "table" && (
        <div style={{ borderRadius: "12px", border: "1px solid var(--line)", overflow: "hidden" }}>
          {Array.from({ length: 6 }, (_, r) => (
            <div
              key={r}
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(4, 1fr)",
                gap: "12px",
                padding: "12px 16px",
                borderBottom: r < 5 ? "1px solid var(--line)" : "none",
                background: r === 0 ? "var(--surface-2)" : "transparent",
              }}
            >
              {Array.from({ length: 4 }, (_, c) => (
                <div key={c} className="skeleton" style={{ height: "12px", width: `${40 + ((r + c) % 3) * 20}%` }} />
              ))}
            </div>
          ))}
        </div>
      )}

      {variant === "form" && (
        <div style={{ display: "flex", flexDirection: "column", gap: "16px", maxWidth: "500px" }}>
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i}>
              <div className="skeleton" style={{ height: "12px", width: "90px", marginBottom: "6px" }} />
              <div className="skeleton" style={{ height: "38px", width: "100%", borderRadius: "8px" }} />
            </div>
          ))}
          <div className="skeleton" style={{ height: "40px", width: "140px", borderRadius: "10px", marginTop: "8px" }} />
        </div>
      )}
    </div>
  );
}
