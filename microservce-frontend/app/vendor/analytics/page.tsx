"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import { money } from "../../../lib/format";
import VendorPageShell from "../../components/ui/VendorPageShell";
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";

/* ───── types ───── */

type VendorOrderSummary = {
  vendorId: string; totalOrders: number; activeOrders: number;
  completedOrders: number; cancelledOrders: number; refundedOrders: number;
  totalRevenue: number; totalPlatformFees: number; totalPayouts: number;
  averageOrderValue: number;
};
type DailyRevenueBucket = { date: string; revenue: number; orderCount: number };
type TopProductEntry = { productId: string; productName: string; vendorId: string; quantitySold: number; totalRevenue: number };
type VendorProductSummary = { vendorId: string; totalProducts: number; activeProducts: number; totalViews: number; totalSold: number };
type VendorInventoryHealth = { vendorId: string; totalSkus: number; inStockCount: number; lowStockCount: number; outOfStockCount: number };
type VendorPromotionSummary = { vendorId: string; totalCampaigns: number; activeCampaigns: number; totalBudget: number; totalBurned: number };
type VendorReviewSummary = { vendorId: string; totalReviews: number; avgRating: number; ratingDistribution: Record<string, number>; verifiedPurchasePercent: number; replyRate: number };
type VendorPerformanceSummary = { vendorId: string; name: string; status: string; averageRating: number; fulfillmentRate: number; disputeRate: number; responseTimeHours: number; totalOrdersCompleted: number; commissionRate: number };
type VendorDashboard = {
  orderSummary: VendorOrderSummary | null;
  revenueTrend: DailyRevenueBucket[];
  topProducts: TopProductEntry[];
  productSummary: VendorProductSummary | null;
  inventoryHealth: VendorInventoryHealth | null;
  promotionSummary: VendorPromotionSummary | null;
  reviewSummary: VendorReviewSummary | null;
  performance: VendorPerformanceSummary | null;
};

/* ───── constants ───── */

const CHART_GRID = "rgba(120,120,200,0.08)";
const CHART_TEXT = "#6868a0";

/* ───── helpers ───── */

function num(v: number | null | undefined): string { return (v ?? 0).toLocaleString(); }
function pct(v: number | null | undefined): string { return `${(v ?? 0).toFixed(1)}%`; }
function shortMoney(v: number | null | undefined): string {
  const n = v ?? 0;
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `$${(n / 1_000).toFixed(1)}K`;
  return money(n);
}

function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: { name: string; value: number; color: string }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-[rgba(17,17,40,0.95)] border border-line rounded-md px-3.5 py-2.5 text-sm">
      <p className="text-muted m-0 mb-1.5 text-xs">{label}</p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color }} className="my-0.5 font-semibold">{p.name}: {typeof p.value === "number" && p.name.toLowerCase().includes("revenue") ? money(p.value) : num(p.value)}</p>
      ))}
    </div>
  );
}

/* ───── component ───── */

export default function VendorAnalyticsPage() {
  const session = useAuthSession();
  const [vendorId, setVendorId] = useState<string | null>(null);
  const api = session.apiClient;

  const vendorReady = !!api && (session.isVendorAdmin || session.isVendorStaff);

  // First get vendor ID from /vendors/me
  useQuery({
    queryKey: ["vendor-analytics-id"],
    queryFn: async () => {
      const res = await api!.get("/vendors/me");
      const id = (res.data as { id: string }).id;
      setVendorId(id);
      return id;
    },
    enabled: vendorReady,
  });

  // Then fetch analytics
  const { data, isLoading: loading } = useQuery({
    queryKey: ["vendor-analytics", vendorId],
    queryFn: async () => {
      const res = await api!.get(`/analytics/vendor/${vendorId}/dashboard`);
      return res.data as VendorDashboard;
    },
    enabled: vendorReady && !!vendorId,
  });

  if (session.status === "ready" && !session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <VendorPageShell title="Vendor Analytics" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Analytics" }]}>
        <div className="flex items-center justify-center min-h-[320px]">
          <p className="text-muted">Unauthorized.</p>
        </div>
      </VendorPageShell>
    );
  }

  const o = data?.orderSummary;
  const perf = data?.performance;
  const inv = data?.inventoryHealth;
  const rev = data?.reviewSummary;
  const promo = data?.promotionSummary;

  return (
    <VendorPageShell title="Vendor Analytics" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Analytics" }]}>
      {loading && (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-3.5">
          {Array.from({ length: 5 }).map((_, i) => <div key={i} className="skeleton bg-[rgba(255,255,255,0.02)] border border-line rounded-lg px-6 py-5 h-[100px]" />)}
        </div>
      )}

      {!loading && data && (
        <>
          {/* KPI Cards */}
          <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-3.5">
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5"><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Revenue</p><p className="text-[1.6rem] font-extrabold text-brand mt-2 mb-0">{shortMoney(o?.totalRevenue)}</p></div>
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5"><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Total Orders</p><p className="text-[1.6rem] font-extrabold text-ink mt-2 mb-0">{num(o?.totalOrders)}</p><p className="text-muted text-xs mt-1 mb-0">{num(o?.activeOrders)} active</p></div>
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5"><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Payouts</p><p className="text-[1.6rem] font-extrabold text-[#34d399] mt-2 mb-0">{shortMoney(o?.totalPayouts)}</p></div>
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5"><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Platform Fees</p><p className="text-[1.6rem] font-extrabold text-[#fbbf24] mt-2 mb-0">{shortMoney(o?.totalPlatformFees)}</p></div>
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5"><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Avg Order Value</p><p className="text-[1.6rem] font-extrabold text-ink mt-2 mb-0">{shortMoney(o?.averageOrderValue)}</p></div>
          </div>

          {/* Revenue Trend */}
          {data.revenueTrend?.length > 0 && (
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-7 py-6 mt-6">
              <h2 className="text-lg font-bold text-ink mb-4">Revenue Trend (30 Days)</h2>
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={data.revenueTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
                  <XAxis dataKey="date" tick={{ fill: CHART_TEXT, fontSize: 11 }} tickFormatter={(v: string) => { const d = new Date(v); return `${d.getMonth() + 1}/${d.getDate()}`; }} />
                  <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} tickFormatter={(v: number) => shortMoney(v)} />
                  <Tooltip content={<ChartTooltip />} />
                  <Line type="monotone" dataKey="revenue" name="Revenue" stroke="#00d4ff" strokeWidth={2.5} dot={false} />
                  <Line type="monotone" dataKey="orderCount" name="Orders" stroke="#7c3aed" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* Top Products */}
          {data.topProducts?.length > 0 && (
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-7 py-6 mt-6">
              <h2 className="text-lg font-bold text-ink mb-4">Top Products by Revenue</h2>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-base">
                  <thead><tr><th className="px-3 py-2.5 text-left text-muted text-xs uppercase tracking-[0.06em] border-b border-line">#</th><th className="px-3 py-2.5 text-left text-muted text-xs uppercase tracking-[0.06em] border-b border-line">Product</th><th className="px-3 py-2.5 text-left text-muted text-xs uppercase tracking-[0.06em] border-b border-line">Qty Sold</th><th className="px-3 py-2.5 text-right text-muted text-xs uppercase tracking-[0.06em] border-b border-line">Revenue</th></tr></thead>
                  <tbody>
                    {data.topProducts.map((p, i) => (
                      <tr key={p.productId}><td className="px-3 py-2.5 border-b border-[rgba(120,120,200,0.06)] text-ink">{i + 1}</td><td className="px-3 py-2.5 border-b border-[rgba(120,120,200,0.06)] text-ink">{p.productName || p.productId}</td><td className="px-3 py-2.5 border-b border-[rgba(120,120,200,0.06)] text-ink">{num(p.quantitySold)}</td><td className="px-3 py-2.5 border-b border-[rgba(120,120,200,0.06)] text-right text-brand font-semibold">{money(p.totalRevenue)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Two-column: Inventory + Reviews */}
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-4 mt-6">
            {/* Inventory */}
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5">
              <h3 className="text-[0.88rem] font-bold text-ink mb-4">Inventory Health</h3>
              {inv ? (
                <ResponsiveContainer width="100%" height={200}>
                  <PieChart>
                    <Pie
                      data={[
                        { name: "In Stock", value: inv.inStockCount },
                        { name: "Low Stock", value: inv.lowStockCount },
                        { name: "Out of Stock", value: inv.outOfStockCount },
                      ].filter(d => d.value > 0)}
                      dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={70} innerRadius={35} paddingAngle={3}
                    >
                      <Cell fill="#34d399" /><Cell fill="#fbbf24" /><Cell fill="#f87171" />
                    </Pie>
                    <Tooltip content={<ChartTooltip />} />
                    <Legend wrapperStyle={{ fontSize: "0.75rem", color: CHART_TEXT }} />
                  </PieChart>
                </ResponsiveContainer>
              ) : <p className="text-muted text-base">No inventory data.</p>}
            </div>

            {/* Reviews */}
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5">
              <h3 className="text-[0.88rem] font-bold text-ink mb-4">Review Summary</h3>
              {rev ? (
                <div className="grid grid-cols-2 gap-3">
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Total</p><p className="text-[1.2rem] font-extrabold text-ink mt-2 mb-0">{num(rev.totalReviews)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Avg Rating</p><p className="text-[1.2rem] font-extrabold text-[#fbbf24] mt-2 mb-0">{(rev.avgRating ?? 0).toFixed(1)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Verified %</p><p className="text-[1.1rem] font-extrabold text-ink mt-2 mb-0">{pct(rev.verifiedPurchasePercent)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Reply Rate</p><p className="text-[1.1rem] font-extrabold text-ink mt-2 mb-0">{pct(rev.replyRate)}</p></div>
                </div>
              ) : <p className="text-muted text-base">No review data.</p>}
            </div>
          </div>

          {/* Performance + Promotions */}
          <div className="grid grid-cols-[repeat(auto-fit,minmax(320px,1fr))] gap-4 mt-4">
            {/* Performance */}
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5">
              <h3 className="text-[0.88rem] font-bold text-ink mb-4">Performance Metrics</h3>
              {perf ? (
                <div className="grid grid-cols-2 gap-3">
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Fulfillment Rate</p><p className="text-[1.2rem] font-extrabold text-[#34d399] mt-2 mb-0">{pct(perf.fulfillmentRate)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Dispute Rate</p><p className="text-[1.2rem] font-extrabold mt-2 mb-0" style={{ color: (perf.disputeRate ?? 0) > 5 ? "#f87171" : "var(--ink)" }}>{pct(perf.disputeRate)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Response Time</p><p className="text-[1.1rem] font-extrabold text-ink mt-2 mb-0">{(perf.responseTimeHours ?? 0).toFixed(1)}h</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Commission</p><p className="text-[1.1rem] font-extrabold text-ink mt-2 mb-0">{pct(perf.commissionRate)}</p></div>
                </div>
              ) : <p className="text-muted text-base">No performance data.</p>}
            </div>

            {/* Promotions */}
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-6 py-5">
              <h3 className="text-[0.88rem] font-bold text-ink mb-4">Promotions</h3>
              {promo ? (
                <div className="grid grid-cols-2 gap-3">
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Total Campaigns</p><p className="text-[1.2rem] font-extrabold text-ink mt-2 mb-0">{num(promo.totalCampaigns)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Active</p><p className="text-[1.2rem] font-extrabold text-[#34d399] mt-2 mb-0">{num(promo.activeCampaigns)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Total Budget</p><p className="text-[1.1rem] font-extrabold text-ink mt-2 mb-0">{shortMoney(promo.totalBudget)}</p></div>
                  <div><p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Burned</p><p className="text-[1.1rem] font-extrabold text-[#fbbf24] mt-2 mb-0">{shortMoney(promo.totalBurned)}</p></div>
                </div>
              ) : <p className="text-muted text-base">No promotion data.</p>}
            </div>
          </div>

          {/* Rating Distribution */}
          {rev?.ratingDistribution && Object.keys(rev.ratingDistribution).length > 0 && (
            <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg px-7 py-6 mt-6">
              <h2 className="text-lg font-bold text-ink mb-4">Rating Distribution</h2>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={[5, 4, 3, 2, 1].map((star) => ({ star: `${star} Star`, count: rev.ratingDistribution[star] ?? 0 }))}>
                  <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
                  <XAxis dataKey="star" tick={{ fill: CHART_TEXT, fontSize: 12 }} />
                  <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} />
                  <Tooltip content={<ChartTooltip />} />
                  <Bar dataKey="count" name="Reviews" radius={[6, 6, 0, 0]} barSize={36}>
                    {[5, 4, 3, 2, 1].map((star, i) => <Cell key={i} fill={star >= 4 ? "#34d399" : star === 3 ? "#fbbf24" : "#f87171"} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}

      {!loading && !data && (
        <div className="flex items-center justify-center min-h-[200px]">
          <p className="text-muted text-[0.9rem]">No analytics data available.</p>
        </div>
      )}
    </VendorPageShell>
  );
}
