"use client";

type Props = { count?: number };

export default function ProductCardSkeleton({ count = 4 }: Props) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          style={{
            borderRadius: "16px",
            overflow: "hidden",
            background: "var(--surface)",
            border: "1px solid var(--line)",
          }}
        >
          <div className="skeleton" style={{ height: "220px", width: "100%", borderRadius: 0 }} />
          <div style={{ padding: "14px 16px", display: "flex", flexDirection: "column", gap: "8px" }}>
            <div className="skeleton" style={{ height: "13px", width: "80%" }} />
            <div className="skeleton" style={{ height: "13px", width: "60%" }} />
            <div className="skeleton" style={{ height: "18px", width: "45%" }} />
          </div>
        </div>
      ))}
    </>
  );
}
