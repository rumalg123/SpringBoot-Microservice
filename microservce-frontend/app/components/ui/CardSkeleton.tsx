export default function CardSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: "16px" }}>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)", background: "var(--card)" }}>
          <div className="skeleton" style={{ height: "200px", borderRadius: 0 }} />
          <div style={{ padding: "14px" }}>
            <div className="skeleton" style={{ height: "14px", width: "70%", marginBottom: "10px" }} />
            <div className="skeleton" style={{ height: "12px", width: "40%", marginBottom: "8px" }} />
            <div className="skeleton" style={{ height: "16px", width: "30%" }} />
          </div>
        </div>
      ))}
    </div>
  );
}
