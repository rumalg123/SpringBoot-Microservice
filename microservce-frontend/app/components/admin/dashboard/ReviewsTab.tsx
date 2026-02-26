"use client";

import {
  BarChart, Bar, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from "recharts";
import type { ReviewAnalyticsData } from "./types";
import {
  CHART_GRID, CHART_TEXT,
  num, pct, ChartTooltip, SkeletonGrid,
} from "./helpers";

interface ReviewsTabProps {
  reviewData: ReviewAnalyticsData | null;
}

export default function ReviewsTab({ reviewData }: ReviewsTabProps) {
  if (!reviewData) {
    return <div className="mt-6"><SkeletonGrid count={2} height={200} /></div>;
  }

  return (
    <>
      <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
        <h2 className="mb-4 text-lg font-bold text-ink">Review Analytics</h2>
        <div className="mb-5 grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3">
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(reviewData.summary?.totalReviews)}</p></div>
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Active</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(reviewData.summary?.activeReviews)}</p></div>
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Avg Rating</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#fbbf24]">{(reviewData.summary?.avgRating ?? 0).toFixed(1)}</p></div>
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Verified %</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{pct(reviewData.summary?.verifiedPurchasePercent)}</p></div>
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Reported</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#f87171]">{num(reviewData.summary?.totalReported)}</p></div>
          <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">This Month</p><p className="mt-2 text-[1.2rem] font-extrabold text-brand">{num(reviewData.summary?.reviewsThisMonth)}</p></div>
        </div>
      </div>

      {reviewData.ratingDistribution && Object.keys(reviewData.ratingDistribution).length > 0 && (
        <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
          <h2 className="mb-4 text-lg font-bold text-ink">Rating Distribution</h2>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={[5, 4, 3, 2, 1].map((star) => ({ star: `${star} Star`, count: reviewData.ratingDistribution[star] ?? 0 }))}>
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
              <XAxis dataKey="star" tick={{ fill: CHART_TEXT, fontSize: 12 }} />
              <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} />
              <Tooltip content={<ChartTooltip />} />
              <Bar dataKey="count" name="Reviews" radius={[6, 6, 0, 0]} barSize={40}>
                {[5, 4, 3, 2, 1].map((star, i) => (
                  <Cell key={i} fill={star >= 4 ? "#34d399" : star === 3 ? "#fbbf24" : "#f87171"} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </>
  );
}
