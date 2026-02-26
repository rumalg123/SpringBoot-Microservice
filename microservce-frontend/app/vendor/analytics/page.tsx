"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";
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

/* ───── styles ───── */

const cardStyle: React.CSSProperties = { background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", borderRadius: 16, padding: "20px 24px" };
const sectionStyle: React.CSSProperties = { ...cardStyle, padding: "24px 28px", marginTop: 24 };
const labelStyle: React.CSSProperties = { color: "var(--muted)", fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.08em", margin: 0 };
const valueStyle: React.CSSProperties = { fontSize: "1.6rem", fontWeight: 800, color: "var(--ink)", margin: "8px 0 0" };
const sectionTitle: React.CSSProperties = { fontSize: "1rem", fontWeight: 700, color: "var(--ink)", margin: "0 0 16px" };
const tableStyle: React.CSSProperties = { width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" };
const thStyle: React.CSSProperties = { padding: "10px 12px", textAlign: "left", color: "var(--muted)", fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.06em", borderBottom: "1px solid var(--line)" };
const tdStyle: React.CSSProperties = { padding: "10px 12px", borderBottom: "1px solid rgba(120,120,200,0.06)", color: "var(--ink)" };

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
    <div style={{ background: "rgba(17,17,40,0.95)", border: "1px solid var(--line)", borderRadius: 10, padding: "10px 14px", fontSize: "0.8rem" }}>
      <p style={{ color: "var(--muted)", margin: "0 0 6px", fontSize: "0.72rem" }}>{label}</p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color, margin: "2px 0", fontWeight: 600 }}>{p.name}: {typeof p.value === "number" && p.name.toLowerCase().includes("revenue") ? money(p.value) : num(p.value)}</p>
      ))}
    </div>
  );
}

/* ───── component ───── */

export default function VendorAnalyticsPage() {
  const session = useAuthSession();
  const [vendorId, setVendorId] = useState<string | null>(null);
  const [data, setData] = useState<VendorDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const api = session.apiClient;

  // First get vendor ID from /vendors/me
  useEffect(() => {
    if (!api || (!session.isVendorAdmin && !session.isVendorStaff)) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get("/vendors/me");
        if (!cancelled) setVendorId(res.data.id);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e));
      }
    })();
    return () => { cancelled = true; };
  }, [api, session.isVendorAdmin, session.isVendorStaff]);

  // Then fetch analytics
  useEffect(() => {
    if (!api || !vendorId) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await api.get(`/analytics/vendor/${vendorId}/dashboard`);
        if (!cancelled) setData(res.data);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [api, vendorId]);

  if (session.status === "ready" && !session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <VendorPageShell title="Vendor Analytics" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Analytics" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 320 }}>
          <p style={{ color: "var(--muted)" }}>Unauthorized.</p>
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
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 14 }}>
          {Array.from({ length: 5 }).map((_, i) => <div key={i} className="skeleton" style={{ ...cardStyle, height: 100, background: "rgba(255,255,255,0.02)" }} />)}
        </div>
      )}

      {!loading && data && (
        <>
          {/* KPI Cards */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 14 }}>
            <div style={cardStyle}><p style={labelStyle}>Revenue</p><p style={{ ...valueStyle, color: "var(--brand)" }}>{shortMoney(o?.totalRevenue)}</p></div>
            <div style={cardStyle}><p style={labelStyle}>Total Orders</p><p style={valueStyle}>{num(o?.totalOrders)}</p><p style={{ color: "var(--muted)", fontSize: "0.72rem", margin: "4px 0 0" }}>{num(o?.activeOrders)} active</p></div>
            <div style={cardStyle}><p style={labelStyle}>Payouts</p><p style={{ ...valueStyle, color: "#34d399" }}>{shortMoney(o?.totalPayouts)}</p></div>
            <div style={cardStyle}><p style={labelStyle}>Platform Fees</p><p style={{ ...valueStyle, color: "#fbbf24" }}>{shortMoney(o?.totalPlatformFees)}</p></div>
            <div style={cardStyle}><p style={labelStyle}>Avg Order Value</p><p style={valueStyle}>{shortMoney(o?.averageOrderValue)}</p></div>
          </div>

          {/* Revenue Trend */}
          {data.revenueTrend?.length > 0 && (
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Revenue Trend (30 Days)</h2>
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
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Top Products by Revenue</h2>
              <div style={{ overflowX: "auto" }}>
                <table style={tableStyle}>
                  <thead><tr><th style={thStyle}>#</th><th style={thStyle}>Product</th><th style={thStyle}>Qty Sold</th><th style={{ ...thStyle, textAlign: "right" }}>Revenue</th></tr></thead>
                  <tbody>
                    {data.topProducts.map((p, i) => (
                      <tr key={p.productId}><td style={tdStyle}>{i + 1}</td><td style={tdStyle}>{p.productName || p.productId}</td><td style={tdStyle}>{num(p.quantitySold)}</td><td style={{ ...tdStyle, textAlign: "right", color: "var(--brand)", fontWeight: 600 }}>{money(p.totalRevenue)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Two-column: Inventory + Reviews */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16, marginTop: 24 }}>
            {/* Inventory */}
            <div style={cardStyle}>
              <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Inventory Health</h3>
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
              ) : <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No inventory data.</p>}
            </div>

            {/* Reviews */}
            <div style={cardStyle}>
              <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Review Summary</h3>
              {rev ? (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div><p style={labelStyle}>Total</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(rev.totalReviews)}</p></div>
                  <div><p style={labelStyle}>Avg Rating</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{(rev.avgRating ?? 0).toFixed(1)}</p></div>
                  <div><p style={labelStyle}>Verified %</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{pct(rev.verifiedPurchasePercent)}</p></div>
                  <div><p style={labelStyle}>Reply Rate</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{pct(rev.replyRate)}</p></div>
                </div>
              ) : <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No review data.</p>}
            </div>
          </div>

          {/* Performance + Promotions */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16, marginTop: 16 }}>
            {/* Performance */}
            <div style={cardStyle}>
              <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Performance Metrics</h3>
              {perf ? (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div><p style={labelStyle}>Fulfillment Rate</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{pct(perf.fulfillmentRate)}</p></div>
                  <div><p style={labelStyle}>Dispute Rate</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: (perf.disputeRate ?? 0) > 5 ? "#f87171" : "var(--ink)" }}>{pct(perf.disputeRate)}</p></div>
                  <div><p style={labelStyle}>Response Time</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{(perf.responseTimeHours ?? 0).toFixed(1)}h</p></div>
                  <div><p style={labelStyle}>Commission</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{pct(perf.commissionRate)}</p></div>
                </div>
              ) : <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No performance data.</p>}
            </div>

            {/* Promotions */}
            <div style={cardStyle}>
              <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Promotions</h3>
              {promo ? (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                  <div><p style={labelStyle}>Total Campaigns</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(promo.totalCampaigns)}</p></div>
                  <div><p style={labelStyle}>Active</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{num(promo.activeCampaigns)}</p></div>
                  <div><p style={labelStyle}>Total Budget</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{shortMoney(promo.totalBudget)}</p></div>
                  <div><p style={labelStyle}>Burned</p><p style={{ ...valueStyle, fontSize: "1.1rem", color: "#fbbf24" }}>{shortMoney(promo.totalBurned)}</p></div>
                </div>
              ) : <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No promotion data.</p>}
            </div>
          </div>

          {/* Rating Distribution */}
          {rev?.ratingDistribution && Object.keys(rev.ratingDistribution).length > 0 && (
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Rating Distribution</h2>
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
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No analytics data available.</p>
        </div>
      )}
    </VendorPageShell>
  );
}
