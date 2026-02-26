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
    <div className="flex flex-wrap gap-8 items-start p-6 rounded-lg border border-line bg-surface">
      {/* Left: Average */}
      <div className="flex flex-col items-center gap-1 min-w-[100px]">
        <span className="text-[2.8rem] font-extrabold text-ink leading-none">{averageRating.toFixed(1)}</span>
        <div className="flex gap-[2px]">
          {[1, 2, 3, 4, 5].map((s) => (
            <svg key={s} width="16" height="16" viewBox="0 0 24 24" fill={s <= Math.round(averageRating) ? "#facc15" : "none"} stroke="#facc15" strokeWidth="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          ))}
        </div>
        <span className="text-[0.78rem] text-muted">{totalReviews} review{totalReviews !== 1 ? "s" : ""}</span>
      </div>

      {/* Center: Distribution bars */}
      <div className="flex-1 min-w-[200px] flex flex-col gap-[6px]">
        {[5, 4, 3, 2, 1].map((star) => {
          const count = ratingDistribution[star] || 0;
          const pct = totalReviews > 0 ? (count / totalReviews) * 100 : 0;
          return (
            <div key={star} className="flex items-center gap-2">
              <span className="text-[0.75rem] text-muted min-w-[12px] text-right">{star}</span>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="#facc15" stroke="none"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" /></svg>
              <div className="flex-1 h-2 rounded-[4px] bg-line">
                <div className="h-full rounded-[4px] bg-[#facc15] transition-[width] duration-300" style={{ width: `${pct}%` }} />
              </div>
              <span className="text-[0.7rem] text-muted min-w-[28px]">{count}</span>
            </div>
          );
        })}
      </div>

      {/* Right: Write review button */}
      {canWriteReview && (
        <div className="flex items-center">
          <button
            onClick={onWriteReview}
            className="px-5 py-[10px] rounded-md border-none bg-[image:var(--gradient-brand)] text-white text-[0.85rem] font-bold cursor-pointer"
          >
            Write a Review
          </button>
        </div>
      )}
    </div>
  );
}
