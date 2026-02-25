"use client";

import { useMemo } from "react";

type Props = {
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<number, number>;
  onWriteReview?: () => void;
  canWriteReview: boolean;
};

export default function ReviewSummaryBar({ averageRating, totalReviews, ratingDistribution, onWriteReview, canWriteReview }: Props) {
  const maxCount = useMemo(() => Math.max(...Object.values(ratingDistribution), 1), [ratingDistribution]);

  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: "32px", alignItems: "flex-start", padding: "24px", borderRadius: "16px", border: "1px solid var(--line)", background: "var(--surface)" }}>
      {/* Left: Average */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "4px", minWidth: "100px" }}>
        <span style={{ fontSize: "2.8rem", fontWeight: 800, color: "var(--ink)", lineHeight: 1 }}>{averageRating.toFixed(1)}</span>
        <div style={{ display: "flex", gap: "2px" }}>
          {[1, 2, 3, 4, 5].map((s) => (
            <svg key={s} width="16" height="16" viewBox="0 0 24 24" fill={s <= Math.round(averageRating) ? "#facc15" : "none"} stroke="#facc15" strokeWidth="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          ))}
        </div>
        <span style={{ fontSize: "0.78rem", color: "var(--muted)" }}>{totalReviews} review{totalReviews !== 1 ? "s" : ""}</span>
      </div>

      {/* Center: Distribution bars */}
      <div style={{ flex: 1, minWidth: "200px", display: "flex", flexDirection: "column", gap: "6px" }}>
        {[5, 4, 3, 2, 1].map((star) => {
          const count = ratingDistribution[star] || 0;
          const pct = totalReviews > 0 ? (count / totalReviews) * 100 : 0;
          return (
            <div key={star} style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span style={{ fontSize: "0.75rem", color: "var(--muted)", minWidth: "12px", textAlign: "right" }}>{star}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="#facc15" stroke="none"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" /></svg>
              <div style={{ flex: 1, height: "8px", borderRadius: "4px", background: "var(--line)" }}>
                <div style={{ width: `${pct}%`, height: "100%", borderRadius: "4px", background: "#facc15", transition: "width 0.3s" }} />
              </div>
              <span style={{ fontSize: "0.7rem", color: "var(--muted)", minWidth: "28px" }}>{count}</span>
            </div>
          );
        })}
      </div>

      {/* Right: Write review button */}
      {canWriteReview && (
        <div style={{ display: "flex", alignItems: "center" }}>
          <button
            onClick={onWriteReview}
            style={{
              padding: "10px 20px", borderRadius: "10px", border: "none",
              background: "var(--gradient-brand)", color: "#fff",
              fontSize: "0.85rem", fontWeight: 700, cursor: "pointer",
            }}
          >
            Write a Review
          </button>
        </div>
      )}
    </div>
  );
}
