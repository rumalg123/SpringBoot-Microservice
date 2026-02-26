export default function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div style={{ borderRadius: "14px", overflow: "hidden", border: "1px solid var(--line-bright)" }}>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr style={{ background: "var(--card)" }}>
            {Array.from({ length: cols }, (_, i) => (
              <th key={i} style={{ padding: "12px 14px" }}>
                <div className="skeleton" style={{ height: "10px", width: "60%" }} />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }, (_, r) => (
            <tr key={r} style={{ borderTop: "1px solid var(--line-bright)" }}>
              {Array.from({ length: cols }, (_, c) => (
                <td key={c} style={{ padding: "14px" }}>
                  <div className="skeleton" style={{ height: "12px", width: c === 0 ? "80%" : "50%" }} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
