"use client";

import {
  LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import type { CustomerSegmentationData } from "./types";
import {
  COLORS, CHART_GRID, CHART_TEXT,
  num, ChartTooltip, SkeletonGrid,
} from "./helpers";

interface CustomersTabProps {
  customerSeg: CustomerSegmentationData | null;
}

export default function CustomersTab({ customerSeg }: CustomersTabProps) {
  if (!customerSeg) {
    return <div className="mt-6"><SkeletonGrid count={2} height={200} /></div>;
  }

  return (
    <>
      {/* Loyalty Distribution */}
      {customerSeg.summary?.loyaltyDistribution && Object.keys(customerSeg.summary.loyaltyDistribution).length > 0 && (
        <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
          <h2 className="mb-4 text-lg font-bold text-ink">Loyalty Tier Distribution</h2>
          <div className="flex flex-wrap items-center gap-8">
            <ResponsiveContainer width={280} height={280}>
              <PieChart>
                <Pie data={Object.entries(customerSeg.summary.loyaltyDistribution).map(([name, value]) => ({ name: name.replace(/_/g, " "), value }))} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} innerRadius={50} paddingAngle={3}>
                  {Object.keys(customerSeg.summary.loyaltyDistribution).map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip content={<ChartTooltip />} />
                <Legend wrapperStyle={{ fontSize: "0.78rem", color: CHART_TEXT }} />
              </PieChart>
            </ResponsiveContainer>
            <div className="grid grid-cols-2 gap-4">
              <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total</p><p className="mt-2 text-[1.3rem] font-extrabold text-ink">{num(customerSeg.summary.totalCustomers)}</p></div>
              <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Active</p><p className="mt-2 text-[1.3rem] font-extrabold text-success">{num(customerSeg.summary.activeCustomers)}</p></div>
              <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">New This Month</p><p className="mt-2 text-[1.3rem] font-extrabold text-brand">{num(customerSeg.summary.newCustomersThisMonth)}</p></div>
            </div>
          </div>
        </div>
      )}

      {/* Customer Growth Trend */}
      {customerSeg.growthTrend?.length > 0 && (
        <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
          <h2 className="mb-4 text-lg font-bold text-ink">Customer Growth Trend</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={customerSeg.growthTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
              <XAxis dataKey="month" tick={{ fill: CHART_TEXT, fontSize: 11 }} />
              <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} />
              <Tooltip content={<ChartTooltip />} />
              <Legend wrapperStyle={{ fontSize: "0.78rem" }} />
              <Line type="monotone" dataKey="newCustomers" name="New Customers" stroke={COLORS[2]} strokeWidth={2} dot={{ r: 3, fill: COLORS[2] }} />
              <Line type="monotone" dataKey="totalActive" name="Total Active" stroke={COLORS[0]} strokeWidth={2} dot={{ r: 3, fill: COLORS[0] }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </>
  );
}
