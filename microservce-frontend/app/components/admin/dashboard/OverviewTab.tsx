"use client";

import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from "recharts";
import type {
  DashboardData, RevenueTrendData,
} from "./types";
import {
  CHART_GRID, CHART_TEXT,
  num, pct, shortMoney, ChartTooltip,
} from "./helpers";

interface OverviewTabProps {
  dash: DashboardData;
  revenueTrend: RevenueTrendData | null;
  periodDays: number;
  setPeriodDays: (days: number) => void;
  setRevenueTrend: (data: RevenueTrendData | null) => void;
}

export default function OverviewTab({ dash, revenueTrend, periodDays, setPeriodDays, setRevenueTrend }: OverviewTabProps) {
  const pay = dash.payments;
  const inv = dash.inventory;
  const rev = dash.reviews;

  return (
    <>
      {/* Revenue Trend */}
      <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold text-ink">Revenue Trend</h2>
          <select
            value={periodDays}
            onChange={(e) => { setPeriodDays(Number(e.target.value)); setRevenueTrend(null); }}
            className="rounded-md border border-line bg-white/5 px-3 py-1.5 text-sm text-ink"
          >
            <option value={7}>Last 7 Days</option>
            <option value={30}>Last 30 Days</option>
            <option value={90}>Last 90 Days</option>
            <option value={365}>Last Year</option>
          </select>
        </div>
        {(revenueTrend?.trend ?? dash.revenueTrend)?.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={revenueTrend?.trend ?? dash.revenueTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
              <XAxis dataKey="date" tick={{ fill: CHART_TEXT, fontSize: 11 }} tickFormatter={(v: string) => { const d = new Date(v); return `${d.getMonth() + 1}/${d.getDate()}`; }} />
              <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} tickFormatter={(v: number) => shortMoney(v)} />
              <Tooltip content={<ChartTooltip />} />
              <Line type="monotone" dataKey="revenue" name="Revenue" stroke="#00d4ff" strokeWidth={2.5} dot={false} />
              <Line type="monotone" dataKey="orderCount" name="Orders" stroke="#7c3aed" strokeWidth={2} dot={false} yAxisId={0} />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-base text-muted">No revenue data available.</p>
        )}
      </div>

      {/* Order Status Breakdown */}
      {revenueTrend?.statusBreakdown && Object.keys(revenueTrend.statusBreakdown).length > 0 && (
        <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
          <h2 className="mb-4 text-lg font-bold text-ink">Orders by Status</h2>
          <ResponsiveContainer width="100%" height={Math.max(200, Object.keys(revenueTrend.statusBreakdown).length * 36)}>
            <BarChart data={Object.entries(revenueTrend.statusBreakdown).map(([status, count]) => ({ status: status.replace(/_/g, " "), count }))} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
              <XAxis type="number" tick={{ fill: CHART_TEXT, fontSize: 11 }} />
              <YAxis dataKey="status" type="category" width={120} tick={{ fill: CHART_TEXT, fontSize: 11 }} />
              <Tooltip content={<ChartTooltip />} />
              <Bar dataKey="count" name="Orders" fill="#00d4ff" radius={[0, 6, 6, 0]} barSize={20} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Quick stats row */}
      <div className="mt-6 grid grid-cols-[repeat(auto-fill,minmax(280px,1fr))] gap-4">
        {/* Payments */}
        <div className="rounded-lg border border-line bg-white/[0.03] px-6 py-5">
          <h3 className="mb-4 text-[0.88rem] font-bold text-ink">Payments</h3>
          <div className="grid grid-cols-2 gap-3">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Successful</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#34d399]">{num(pay?.successfulPayments)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Failed</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#f87171]">{num(pay?.failedPayments)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total Amount</p><p className="mt-2 text-[1.1rem] font-extrabold text-ink">{shortMoney(pay?.totalSuccessAmount)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Refunds</p><p className="mt-2 text-[1.1rem] font-extrabold text-[#fbbf24]">{shortMoney(pay?.totalRefundAmount)}</p></div>
          </div>
        </div>
        {/* Reviews */}
        <div className="rounded-lg border border-line bg-white/[0.03] px-6 py-5">
          <h3 className="mb-4 text-[0.88rem] font-bold text-ink">Reviews</h3>
          <div className="grid grid-cols-2 gap-3">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total Reviews</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(rev?.totalReviews)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Avg Rating</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#fbbf24]">{(rev?.avgRating ?? 0).toFixed(1)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">This Month</p><p className="mt-2 text-[1.1rem] font-extrabold text-ink">{num(rev?.reviewsThisMonth)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Verified %</p><p className="mt-2 text-[1.1rem] font-extrabold text-ink">{pct(rev?.verifiedPurchasePercent)}</p></div>
          </div>
        </div>
        {/* Inventory */}
        <div className="rounded-lg border border-line bg-white/[0.03] px-6 py-5">
          <h3 className="mb-4 text-[0.88rem] font-bold text-ink">Inventory</h3>
          <div className="grid grid-cols-2 gap-3">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">In Stock</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#34d399]">{num(inv?.inStockCount)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Low Stock</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#fbbf24]">{num(inv?.lowStockCount)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Out of Stock</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#f87171]">{num(inv?.outOfStockCount)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total SKUs</p><p className="mt-2 text-[1.1rem] font-extrabold text-ink">{num(inv?.totalSkus)}</p></div>
          </div>
        </div>
      </div>
    </>
  );
}
