export default function PageSkeleton() {
  return (
    <div style={{ maxWidth: "1200px", margin: "0 auto", padding: "40px 16px" }}>
      <div className="skeleton" style={{ height: "28px", width: "200px", marginBottom: "8px" }} />
      <div className="skeleton" style={{ height: "14px", width: "300px", marginBottom: "32px" }} />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: "16px" }}>
        {Array.from({ length: 8 }, (_, i) => (
          <div key={i} style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)", background: "var(--card)" }}>
            <div className="skeleton" style={{ height: "180px", borderRadius: 0 }} />
            <div style={{ padding: "12px" }}>
              <div className="skeleton" style={{ height: "14px", width: "70%", marginBottom: "8px" }} />
              <div className="skeleton" style={{ height: "12px", width: "40%" }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
