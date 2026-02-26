"use client";

type Props = {
  rows?: number;
  columns?: number;
};

export default function TableSkeleton({ rows = 5, columns = 4 }: Props) {
  return (
    <div style={{ borderRadius: "12px", border: "1px solid var(--line)", overflow: "hidden" }}>
      {/* Header row */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: `repeat(${columns}, 1fr)`,
          gap: "12px",
          padding: "12px 16px",
          background: "var(--surface-2)",
          borderBottom: "1px solid var(--line)",
        }}
      >
        {Array.from({ length: columns }, (_, i) => (
          <div key={i} className="skeleton" style={{ height: "12px", width: `${50 + Math.random() * 30}%` }} />
        ))}
      </div>
      {/* Data rows */}
      {Array.from({ length: rows }, (_, r) => (
        <div
          key={r}
          style={{
            display: "grid",
            gridTemplateColumns: `repeat(${columns}, 1fr)`,
            gap: "12px",
            padding: "12px 16px",
            borderBottom: r < rows - 1 ? "1px solid var(--line)" : "none",
          }}
        >
          {Array.from({ length: columns }, (_, c) => (
            <div key={c} className="skeleton" style={{ height: "12px", width: `${40 + ((r + c) % 3) * 20}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
}
