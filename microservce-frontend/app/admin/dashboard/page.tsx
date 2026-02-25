"use client";

import { useEffect, useState, useCallback } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";
import { money } from "../../../lib/format";
import AdminPageShell from "../../components/ui/AdminPageShell";
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";

/* ───── types ───── */

type PlatformOrderSummary = {
  totalOrders: number; pendingOrders: number; processingOrders: number;
  shippedOrders: number; deliveredOrders: number; cancelledOrders: number;
  refundedOrders: number; totalRevenue: number; totalDiscount: number;
  totalShipping: number; averageOrderValue: number; orderCompletionRate: number;
};
type CustomerPlatformSummary = {
  totalCustomers: number; activeCustomers: number; newCustomersThisMonth: number;
  loyaltyDistribution: Record<string, number>;
};
type ProductPlatformSummary = {
  totalProducts: number; activeProducts: number; draftProducts: number;
  pendingApproval: number; totalViews: number; totalSold: number;
};
type VendorPlatformSummary = {
  totalVendors: number; activeVendors: number; pendingVendors: number;
  suspendedVendors: number; verifiedVendors: number;
  avgCommissionRate: number; avgFulfillmentRate: number;
};
type PaymentPlatformSummary = {
  totalPayments: number; successfulPayments: number; failedPayments: number;
  totalSuccessAmount: number; totalRefundAmount: number;
  chargebackCount: number; avgPaymentAmount: number;
};
type InventoryHealthSummary = {
  totalSkus: number; inStockCount: number; lowStockCount: number;
  outOfStockCount: number; backorderCount: number;
  totalQuantityOnHand: number; totalQuantityReserved: number;
};
type PromotionPlatformSummary = {
  totalCampaigns: number; activeCampaigns: number; scheduledCampaigns: number;
  expiredCampaigns: number; flashSaleCount: number; totalBudget: number;
  totalBurnedBudget: number; budgetUtilizationPercent: number;
};
type ReviewPlatformSummary = {
  totalReviews: number; activeReviews: number; avgRating: number;
  verifiedPurchasePercent: number; totalReported: number; reviewsThisMonth: number;
};
type WishlistPlatformSummary = { totalWishlistItems: number; uniqueCustomers: number; uniqueProducts: number };
type CartPlatformSummary = { totalActiveCarts: number; totalCartItems: number; totalSavedForLater: number; avgCartValue: number; avgItemsPerCart: number };
type DailyRevenueBucket = { date: string; revenue: number; orderCount: number };
type MonthlyGrowthBucket = { month: string; newCustomers: number; totalActive: number };

type DashboardData = {
  orders: PlatformOrderSummary | null;
  customers: CustomerPlatformSummary | null;
  products: ProductPlatformSummary | null;
  vendors: VendorPlatformSummary | null;
  payments: PaymentPlatformSummary | null;
  inventory: InventoryHealthSummary | null;
  promotions: PromotionPlatformSummary | null;
  reviews: ReviewPlatformSummary | null;
  wishlist: WishlistPlatformSummary | null;
  cart: CartPlatformSummary | null;
  revenueTrend: DailyRevenueBucket[];
};

type TopProductEntry = { productId: string; productName: string; vendorId: string; quantitySold: number; totalRevenue: number };
type ProductViewEntry = { id: string; name: string; vendorId: string; viewCount: number };
type ProductSoldEntry = { id: string; name: string; vendorId: string; soldCount: number };
type MostWishedProduct = { productId: string; productName: string; wishlistCount: number };
type TopProductsData = { byRevenue: TopProductEntry[]; byViews: ProductViewEntry[]; bySold: ProductSoldEntry[]; byWishlisted: MostWishedProduct[] };
type VendorLeaderboardEntry = { id: string; name: string; totalOrdersCompleted: number; averageRating: number; fulfillmentRate: number; disputeRate: number; verified: boolean };
type VendorLeaderboardData = { summary: VendorPlatformSummary | null; leaderboard: VendorLeaderboardEntry[] };
type InventoryHealthData = { summary: InventoryHealthSummary | null; lowStockAlerts: { productId: string; vendorId: string; sku: string; quantityAvailable: number; lowStockThreshold: number; stockStatus: string }[] };
type CustomerSegmentationData = { summary: CustomerPlatformSummary | null; growthTrend: MonthlyGrowthBucket[] };
type PromotionRoiData = { summary: PromotionPlatformSummary | null; campaigns: { campaignId: string; name: string; vendorId: string; budgetAmount: number; burnedBudgetAmount: number; utilizationPercent: number; benefitType: string; isFlashSale: boolean }[] };
type ReviewAnalyticsData = { summary: ReviewPlatformSummary | null; ratingDistribution: Record<string, number> };
type RevenueTrendData = { trend: DailyRevenueBucket[]; statusBreakdown: Record<string, number> };

/* ───── chart palette ───── */

const COLORS = ["#00d4ff", "#7c3aed", "#34d399", "#fbbf24", "#f87171", "#fb923c", "#a78bfa", "#38bdf8", "#f472b6", "#4ade80"];
const CHART_GRID = "rgba(120,120,200,0.08)";
const CHART_TEXT = "#6868a0";

/* ───── tabs ───── */

type Tab = "overview" | "products" | "customers" | "vendors" | "inventory" | "promotions" | "reviews";
const TABS: { id: Tab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "products", label: "Products" },
  { id: "customers", label: "Customers" },
  { id: "vendors", label: "Vendors" },
  { id: "inventory", label: "Inventory" },
  { id: "promotions", label: "Promotions" },
  { id: "reviews", label: "Reviews" },
];

/* ───── styles ───── */

const cardStyle: React.CSSProperties = {
  background: "rgba(255,255,255,0.03)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: "20px 24px",
};
const sectionStyle: React.CSSProperties = { ...cardStyle, padding: "24px 28px", marginTop: 24 };
const labelStyle: React.CSSProperties = { color: "var(--muted)", fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.08em", margin: 0 };
const valueStyle: React.CSSProperties = { fontSize: "1.8rem", fontWeight: 800, color: "var(--ink)", margin: "8px 0 0" };
const sectionTitle: React.CSSProperties = { fontSize: "1rem", fontWeight: 700, color: "var(--ink)", margin: "0 0 16px" };
const tableStyle: React.CSSProperties = { width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" };
const thStyle: React.CSSProperties = { padding: "10px 12px", textAlign: "left", color: "var(--muted)", fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.06em", borderBottom: "1px solid var(--line)" };
const tdStyle: React.CSSProperties = { padding: "10px 12px", borderBottom: "1px solid rgba(120,120,200,0.06)", color: "var(--ink)" };
const tabStyle = (active: boolean): React.CSSProperties => ({
  padding: "8px 18px", borderRadius: 10, fontSize: "0.82rem", fontWeight: 600, cursor: "pointer", border: "1px solid transparent", transition: "all 0.2s",
  background: active ? "rgba(0,212,255,0.12)" : "transparent",
  color: active ? "var(--brand)" : "var(--muted)",
  borderColor: active ? "rgba(0,212,255,0.25)" : "transparent",
});
const pillTabStyle = (active: boolean): React.CSSProperties => ({
  padding: "6px 14px", borderRadius: 8, fontSize: "0.78rem", fontWeight: 600, cursor: "pointer", border: "none", transition: "all 0.2s",
  background: active ? "rgba(0,212,255,0.15)" : "rgba(255,255,255,0.04)",
  color: active ? "var(--brand)" : "var(--muted)",
});

/* ───── helpers ───── */

function num(v: number | null | undefined): string {
  return (v ?? 0).toLocaleString();
}
function pct(v: number | null | undefined): string {
  return `${(v ?? 0).toFixed(1)}%`;
}
function shortMoney(v: number | null | undefined): string {
  const n = v ?? 0;
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `$${(n / 1_000).toFixed(1)}K`;
  return money(n);
}

/* ───── custom tooltip ───── */

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

/* ───── KPI card ───── */

function KpiCard({ label, value, accent, sub }: { label: string; value: string; accent?: string; sub?: string }) {
  return (
    <div style={cardStyle}>
      <p style={labelStyle}>{label}</p>
      <p style={{ ...valueStyle, color: accent || "var(--ink)", fontSize: "1.6rem" }}>{value}</p>
      {sub && <p style={{ color: "var(--muted)", fontSize: "0.72rem", margin: "4px 0 0" }}>{sub}</p>}
    </div>
  );
}

/* ───── skeleton ───── */

function SkeletonGrid({ count, height = 100 }: { count: number; height?: number }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 16 }}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton" style={{ ...cardStyle, height, background: "rgba(255,255,255,0.02)" }} />
      ))}
    </div>
  );
}

/* ───── main component ───── */

export default function AdminDashboardPage() {
  const session = useAuthSession();
  const [dash, setDash] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<Tab>("overview");
  const [periodDays, setPeriodDays] = useState(30);

  // detail data (loaded on tab change)
  const [revenueTrend, setRevenueTrend] = useState<RevenueTrendData | null>(null);
  const [topProducts, setTopProducts] = useState<TopProductsData | null>(null);
  const [customerSeg, setCustomerSeg] = useState<CustomerSegmentationData | null>(null);
  const [vendorBoard, setVendorBoard] = useState<VendorLeaderboardData | null>(null);
  const [invHealth, setInvHealth] = useState<InventoryHealthData | null>(null);
  const [promoRoi, setPromoRoi] = useState<PromotionRoiData | null>(null);
  const [reviewData, setReviewData] = useState<ReviewAnalyticsData | null>(null);
  const [productTab, setProductTab] = useState<"revenue" | "views" | "sold" | "wishlisted">("revenue");

  const api = session.apiClient;

  // Fetch main dashboard
  useEffect(() => {
    if (!api || !session.canViewAdmin) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await api.get("/analytics/admin/dashboard");
        if (!cancelled) setDash(res.data);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [api, session.canViewAdmin]);

  // Fetch detail data when tab changes
  const fetchDetail = useCallback(async (tab: Tab) => {
    if (!api) return;
    try {
      if (tab === "overview" && !revenueTrend) {
        const res = await api.get(`/analytics/admin/revenue-trend?days=${periodDays}`);
        setRevenueTrend(res.data);
      } else if (tab === "products" && !topProducts) {
        const res = await api.get("/analytics/admin/top-products");
        setTopProducts(res.data);
      } else if (tab === "customers" && !customerSeg) {
        const res = await api.get("/analytics/admin/customer-segmentation");
        setCustomerSeg(res.data);
      } else if (tab === "vendors" && !vendorBoard) {
        const res = await api.get("/analytics/admin/vendor-leaderboard?sortBy=ORDERS_COMPLETED");
        setVendorBoard(res.data);
      } else if (tab === "inventory" && !invHealth) {
        const res = await api.get("/analytics/admin/inventory-health");
        setInvHealth(res.data);
      } else if (tab === "promotions" && !promoRoi) {
        const res = await api.get("/analytics/admin/promotion-roi");
        setPromoRoi(res.data);
      } else if (tab === "reviews" && !reviewData) {
        const res = await api.get("/analytics/admin/review-analytics");
        setReviewData(res.data);
      }
    } catch (e) {
      toast.error(getErrorMessage(e));
    }
  }, [api, periodDays, revenueTrend, topProducts, customerSeg, vendorBoard, invHealth, promoRoi, reviewData]);

  useEffect(() => {
    if (api && !loading) void fetchDetail(activeTab);
  }, [activeTab, api, loading, fetchDetail]);

  // Re-fetch revenue when period changes
  useEffect(() => {
    if (!api || activeTab !== "overview") return;
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get(`/analytics/admin/revenue-trend?days=${periodDays}`);
        if (!cancelled) setRevenueTrend(res.data);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e));
      }
    })();
    return () => { cancelled = true; };
  }, [api, periodDays, activeTab]);

  const handleEvictCache = async () => {
    if (!api) return;
    try {
      await api.post("/analytics/admin/cache/evict");
      toast.success("All analytics caches evicted");
      setDash(null); setRevenueTrend(null); setTopProducts(null); setCustomerSeg(null);
      setVendorBoard(null); setInvHealth(null); setPromoRoi(null); setReviewData(null);
      setLoading(true);
      const res = await api.get("/analytics/admin/dashboard");
      setDash(res.data);
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  /* unauthorized guard */
  if (session.status === "ready" && !session.canViewAdmin) {
    return (
      <AdminPageShell title="Analytics" breadcrumbs={[{ label: "Admin", href: "/admin/orders" }, { label: "Analytics" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 320 }}>
          <p style={{ color: "var(--muted)", fontSize: "1rem", textAlign: "center" }}>Unauthorized. You do not have permission to view admin analytics.</p>
        </div>
      </AdminPageShell>
    );
  }

  const o = dash?.orders;
  const c = dash?.customers;
  const p = dash?.products;
  const v = dash?.vendors;
  const pay = dash?.payments;
  const inv = dash?.inventory;
  const promo = dash?.promotions;
  const rev = dash?.reviews;

  return (
    <AdminPageShell
      title="Analytics"
      breadcrumbs={[{ label: "Admin", href: "/admin/orders" }, { label: "Analytics" }]}
      actions={
        <button type="button" onClick={handleEvictCache} style={{ padding: "8px 16px", borderRadius: 10, fontSize: "0.78rem", fontWeight: 600, background: "rgba(255,255,255,0.05)", border: "1px solid var(--line)", color: "var(--muted)", cursor: "pointer" }}>
          Refresh Data
        </button>
      }
    >
      {/* Loading */}
      {loading && <SkeletonGrid count={6} />}

      {!loading && dash && (
        <>
          {/* ── KPI cards ── */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 14 }}>
            <KpiCard label="Total Revenue" value={shortMoney(o?.totalRevenue)} accent="var(--brand)" sub={`Avg order: ${shortMoney(o?.averageOrderValue)}`} />
            <KpiCard label="Total Orders" value={num(o?.totalOrders)} sub={`${pct(o?.orderCompletionRate)} completion`} />
            <KpiCard label="Customers" value={num(c?.totalCustomers)} accent="#34d399" sub={`${num(c?.newCustomersThisMonth)} new this month`} />
            <KpiCard label="Products" value={num(p?.totalProducts)} sub={`${num(p?.activeProducts)} active`} />
            <KpiCard label="Active Vendors" value={num(v?.activeVendors)} accent="#7c3aed" sub={`${num(v?.totalVendors)} total`} />
            <KpiCard label="Active Promotions" value={num(promo?.activeCampaigns)} accent="#fbbf24" sub={`${num(promo?.totalCampaigns)} total`} />
          </div>

          {/* ── Tabs ── */}
          <div style={{ display: "flex", gap: 6, marginTop: 24, flexWrap: "wrap" }}>
            {TABS.map((t) => (
              <button key={t.id} type="button" onClick={() => setActiveTab(t.id)} style={tabStyle(activeTab === t.id)}>{t.label}</button>
            ))}
          </div>

          {/* ── Tab Content ── */}
          {activeTab === "overview" && (
            <>
              {/* Revenue Trend */}
              <div style={sectionStyle}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, flexWrap: "wrap", gap: 8 }}>
                  <h2 style={sectionTitle}>Revenue Trend</h2>
                  <select
                    value={periodDays}
                    onChange={(e) => { setPeriodDays(Number(e.target.value)); setRevenueTrend(null); }}
                    style={{ background: "rgba(255,255,255,0.05)", border: "1px solid var(--line)", borderRadius: 8, padding: "6px 12px", color: "var(--ink)", fontSize: "0.8rem" }}
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
                  <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No revenue data available.</p>
                )}
              </div>

              {/* Order Status Breakdown */}
              {revenueTrend?.statusBreakdown && Object.keys(revenueTrend.statusBreakdown).length > 0 && (
                <div style={sectionStyle}>
                  <h2 style={sectionTitle}>Orders by Status</h2>
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
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 16, marginTop: 24 }}>
                {/* Payments */}
                <div style={cardStyle}>
                  <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Payments</h3>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                    <div><p style={labelStyle}>Successful</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{num(pay?.successfulPayments)}</p></div>
                    <div><p style={labelStyle}>Failed</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#f87171" }}>{num(pay?.failedPayments)}</p></div>
                    <div><p style={labelStyle}>Total Amount</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{shortMoney(pay?.totalSuccessAmount)}</p></div>
                    <div><p style={labelStyle}>Refunds</p><p style={{ ...valueStyle, fontSize: "1.1rem", color: "#fbbf24" }}>{shortMoney(pay?.totalRefundAmount)}</p></div>
                  </div>
                </div>
                {/* Reviews */}
                <div style={cardStyle}>
                  <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Reviews</h3>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                    <div><p style={labelStyle}>Total Reviews</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(rev?.totalReviews)}</p></div>
                    <div><p style={labelStyle}>Avg Rating</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{(rev?.avgRating ?? 0).toFixed(1)}</p></div>
                    <div><p style={labelStyle}>This Month</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{num(rev?.reviewsThisMonth)}</p></div>
                    <div><p style={labelStyle}>Verified %</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{pct(rev?.verifiedPurchasePercent)}</p></div>
                  </div>
                </div>
                {/* Inventory */}
                <div style={cardStyle}>
                  <h3 style={{ ...sectionTitle, fontSize: "0.88rem" }}>Inventory</h3>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                    <div><p style={labelStyle}>In Stock</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{num(inv?.inStockCount)}</p></div>
                    <div><p style={labelStyle}>Low Stock</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{num(inv?.lowStockCount)}</p></div>
                    <div><p style={labelStyle}>Out of Stock</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#f87171" }}>{num(inv?.outOfStockCount)}</p></div>
                    <div><p style={labelStyle}>Total SKUs</p><p style={{ ...valueStyle, fontSize: "1.1rem" }}>{num(inv?.totalSkus)}</p></div>
                  </div>
                </div>
              </div>
            </>
          )}

          {activeTab === "products" && (
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Top Products</h2>
              <div style={{ display: "flex", gap: 4, marginBottom: 16 }}>
                {(["revenue", "views", "sold", "wishlisted"] as const).map((t) => (
                  <button key={t} type="button" onClick={() => setProductTab(t)} style={pillTabStyle(productTab === t)}>
                    {t === "revenue" ? "By Revenue" : t === "views" ? "By Views" : t === "sold" ? "By Sold" : "By Wishlisted"}
                  </button>
                ))}
              </div>
              {!topProducts ? (
                <SkeletonGrid count={3} height={60} />
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={tableStyle}>
                    <thead>
                      <tr>
                        <th style={thStyle}>#</th>
                        <th style={thStyle}>Product</th>
                        {productTab === "revenue" && <><th style={thStyle}>Qty Sold</th><th style={{ ...thStyle, textAlign: "right" }}>Revenue</th></>}
                        {productTab === "views" && <th style={{ ...thStyle, textAlign: "right" }}>Views</th>}
                        {productTab === "sold" && <th style={{ ...thStyle, textAlign: "right" }}>Sold</th>}
                        {productTab === "wishlisted" && <th style={{ ...thStyle, textAlign: "right" }}>Wishlisted</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {productTab === "revenue" && topProducts.byRevenue?.map((p, i) => (
                        <tr key={p.productId}>
                          <td style={tdStyle}>{i + 1}</td>
                          <td style={tdStyle}>{p.productName || p.productId}</td>
                          <td style={tdStyle}>{num(p.quantitySold)}</td>
                          <td style={{ ...tdStyle, textAlign: "right", color: "var(--brand)", fontWeight: 600 }}>{money(p.totalRevenue)}</td>
                        </tr>
                      ))}
                      {productTab === "views" && topProducts.byViews?.map((p, i) => (
                        <tr key={p.id}>
                          <td style={tdStyle}>{i + 1}</td>
                          <td style={tdStyle}>{p.name || p.id}</td>
                          <td style={{ ...tdStyle, textAlign: "right", fontWeight: 600 }}>{num(p.viewCount)}</td>
                        </tr>
                      ))}
                      {productTab === "sold" && topProducts.bySold?.map((p, i) => (
                        <tr key={p.id}>
                          <td style={tdStyle}>{i + 1}</td>
                          <td style={tdStyle}>{p.name || p.id}</td>
                          <td style={{ ...tdStyle, textAlign: "right", fontWeight: 600 }}>{num(p.soldCount)}</td>
                        </tr>
                      ))}
                      {productTab === "wishlisted" && topProducts.byWishlisted?.map((p, i) => (
                        <tr key={p.productId}>
                          <td style={tdStyle}>{i + 1}</td>
                          <td style={tdStyle}>{p.productName || p.productId}</td>
                          <td style={{ ...tdStyle, textAlign: "right", fontWeight: 600 }}>{num(p.wishlistCount)}</td>
                        </tr>
                      ))}
                      {((productTab === "revenue" && !topProducts.byRevenue?.length) ||
                        (productTab === "views" && !topProducts.byViews?.length) ||
                        (productTab === "sold" && !topProducts.bySold?.length) ||
                        (productTab === "wishlisted" && !topProducts.byWishlisted?.length)) && (
                        <tr><td colSpan={4} style={{ ...tdStyle, textAlign: "center", color: "var(--muted)" }}>No data available.</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {activeTab === "customers" && (
            <>
              {!customerSeg ? (
                <div style={{ marginTop: 24 }}><SkeletonGrid count={2} height={200} /></div>
              ) : (
                <>
                  {/* Loyalty Distribution */}
                  {customerSeg.summary?.loyaltyDistribution && Object.keys(customerSeg.summary.loyaltyDistribution).length > 0 && (
                    <div style={sectionStyle}>
                      <h2 style={sectionTitle}>Loyalty Tier Distribution</h2>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: 32, alignItems: "center" }}>
                        <ResponsiveContainer width={280} height={280}>
                          <PieChart>
                            <Pie data={Object.entries(customerSeg.summary.loyaltyDistribution).map(([name, value]) => ({ name: name.replace(/_/g, " "), value }))} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} innerRadius={50} paddingAngle={3}>
                              {Object.keys(customerSeg.summary.loyaltyDistribution).map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                            </Pie>
                            <Tooltip content={<ChartTooltip />} />
                            <Legend wrapperStyle={{ fontSize: "0.78rem", color: CHART_TEXT }} />
                          </PieChart>
                        </ResponsiveContainer>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                          <div><p style={labelStyle}>Total</p><p style={{ ...valueStyle, fontSize: "1.3rem" }}>{num(customerSeg.summary.totalCustomers)}</p></div>
                          <div><p style={labelStyle}>Active</p><p style={{ ...valueStyle, fontSize: "1.3rem", color: "#34d399" }}>{num(customerSeg.summary.activeCustomers)}</p></div>
                          <div><p style={labelStyle}>New This Month</p><p style={{ ...valueStyle, fontSize: "1.3rem", color: "var(--brand)" }}>{num(customerSeg.summary.newCustomersThisMonth)}</p></div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Customer Growth Trend */}
                  {customerSeg.growthTrend?.length > 0 && (
                    <div style={sectionStyle}>
                      <h2 style={sectionTitle}>Customer Growth Trend</h2>
                      <ResponsiveContainer width="100%" height={300}>
                        <LineChart data={customerSeg.growthTrend}>
                          <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
                          <XAxis dataKey="month" tick={{ fill: CHART_TEXT, fontSize: 11 }} />
                          <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} />
                          <Tooltip content={<ChartTooltip />} />
                          <Legend wrapperStyle={{ fontSize: "0.78rem" }} />
                          <Line type="monotone" dataKey="newCustomers" name="New Customers" stroke="#34d399" strokeWidth={2} dot={{ r: 3, fill: "#34d399" }} />
                          <Line type="monotone" dataKey="totalActive" name="Total Active" stroke="#00d4ff" strokeWidth={2} dot={{ r: 3, fill: "#00d4ff" }} />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </>
              )}
            </>
          )}

          {activeTab === "vendors" && (
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Vendor Leaderboard</h2>
              {!vendorBoard ? (
                <SkeletonGrid count={3} height={60} />
              ) : (
                <>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12, marginBottom: 20 }}>
                    <div><p style={labelStyle}>Total</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(vendorBoard.summary?.totalVendors)}</p></div>
                    <div><p style={labelStyle}>Active</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{num(vendorBoard.summary?.activeVendors)}</p></div>
                    <div><p style={labelStyle}>Pending</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{num(vendorBoard.summary?.pendingVendors)}</p></div>
                    <div><p style={labelStyle}>Verified</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "var(--brand)" }}>{num(vendorBoard.summary?.verifiedVendors)}</p></div>
                  </div>
                  <div style={{ overflowX: "auto" }}>
                    <table style={tableStyle}>
                      <thead>
                        <tr>
                          <th style={thStyle}>#</th>
                          <th style={thStyle}>Vendor</th>
                          <th style={thStyle}>Orders</th>
                          <th style={thStyle}>Rating</th>
                          <th style={thStyle}>Fulfillment</th>
                          <th style={thStyle}>Disputes</th>
                          <th style={thStyle}>Verified</th>
                        </tr>
                      </thead>
                      <tbody>
                        {vendorBoard.leaderboard?.length > 0 ? vendorBoard.leaderboard.map((v, i) => (
                          <tr key={v.id}>
                            <td style={tdStyle}>{i + 1}</td>
                            <td style={{ ...tdStyle, fontWeight: 600 }}>{v.name}</td>
                            <td style={tdStyle}>{num(v.totalOrdersCompleted)}</td>
                            <td style={{ ...tdStyle, color: "#fbbf24" }}>{(v.averageRating ?? 0).toFixed(1)}</td>
                            <td style={tdStyle}>{pct(v.fulfillmentRate)}</td>
                            <td style={{ ...tdStyle, color: (v.disputeRate ?? 0) > 5 ? "#f87171" : "var(--ink)" }}>{pct(v.disputeRate)}</td>
                            <td style={tdStyle}>{v.verified ? "Yes" : "No"}</td>
                          </tr>
                        )) : (
                          <tr><td colSpan={7} style={{ ...tdStyle, textAlign: "center", color: "var(--muted)" }}>No vendor data.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {activeTab === "inventory" && (
            <>
              {!invHealth ? (
                <div style={{ marginTop: 24 }}><SkeletonGrid count={2} height={200} /></div>
              ) : (
                <>
                  <div style={sectionStyle}>
                    <h2 style={sectionTitle}>Inventory Health</h2>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 32, alignItems: "center" }}>
                      <ResponsiveContainer width={280} height={280}>
                        <PieChart>
                          <Pie
                            data={[
                              { name: "In Stock", value: invHealth.summary?.inStockCount ?? 0 },
                              { name: "Low Stock", value: invHealth.summary?.lowStockCount ?? 0 },
                              { name: "Out of Stock", value: invHealth.summary?.outOfStockCount ?? 0 },
                              { name: "Backorder", value: invHealth.summary?.backorderCount ?? 0 },
                            ].filter(d => d.value > 0)}
                            dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} innerRadius={50} paddingAngle={3}
                          >
                            <Cell fill="#34d399" /><Cell fill="#fbbf24" /><Cell fill="#f87171" /><Cell fill="#fb923c" />
                          </Pie>
                          <Tooltip content={<ChartTooltip />} />
                          <Legend wrapperStyle={{ fontSize: "0.78rem", color: CHART_TEXT }} />
                        </PieChart>
                      </ResponsiveContainer>
                      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
                        <div><p style={labelStyle}>Total On Hand</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(invHealth.summary?.totalQuantityOnHand)}</p></div>
                        <div><p style={labelStyle}>Reserved</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#7c3aed" }}>{num(invHealth.summary?.totalQuantityReserved)}</p></div>
                      </div>
                    </div>
                  </div>

                  {invHealth.lowStockAlerts?.length > 0 && (
                    <div style={sectionStyle}>
                      <h2 style={sectionTitle}>Low Stock Alerts</h2>
                      <div style={{ overflowX: "auto" }}>
                        <table style={tableStyle}>
                          <thead>
                            <tr>
                              <th style={thStyle}>SKU</th>
                              <th style={thStyle}>Available</th>
                              <th style={thStyle}>Threshold</th>
                              <th style={thStyle}>Status</th>
                            </tr>
                          </thead>
                          <tbody>
                            {invHealth.lowStockAlerts.slice(0, 20).map((a, i) => (
                              <tr key={i}>
                                <td style={{ ...tdStyle, fontWeight: 600 }}>{a.sku || a.productId}</td>
                                <td style={{ ...tdStyle, color: a.quantityAvailable === 0 ? "#f87171" : "#fbbf24", fontWeight: 600 }}>{num(a.quantityAvailable)}</td>
                                <td style={tdStyle}>{num(a.lowStockThreshold)}</td>
                                <td style={tdStyle}>
                                  <span style={{ padding: "3px 10px", borderRadius: 6, fontSize: "0.72rem", fontWeight: 600, background: a.stockStatus === "OUT_OF_STOCK" ? "rgba(239,68,68,0.15)" : "rgba(245,158,11,0.15)", color: a.stockStatus === "OUT_OF_STOCK" ? "#f87171" : "#fbbf24" }}>
                                    {(a.stockStatus ?? "").replace(/_/g, " ")}
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </>
              )}
            </>
          )}

          {activeTab === "promotions" && (
            <div style={sectionStyle}>
              <h2 style={sectionTitle}>Promotion ROI</h2>
              {!promoRoi ? (
                <SkeletonGrid count={3} height={60} />
              ) : (
                <>
                  <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 12, marginBottom: 20 }}>
                    <div><p style={labelStyle}>Total Campaigns</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(promoRoi.summary?.totalCampaigns)}</p></div>
                    <div><p style={labelStyle}>Active</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#34d399" }}>{num(promoRoi.summary?.activeCampaigns)}</p></div>
                    <div><p style={labelStyle}>Total Budget</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{shortMoney(promoRoi.summary?.totalBudget)}</p></div>
                    <div><p style={labelStyle}>Burned</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{shortMoney(promoRoi.summary?.totalBurnedBudget)}</p></div>
                    <div><p style={labelStyle}>Utilization</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "var(--brand)" }}>{pct(promoRoi.summary?.budgetUtilizationPercent)}</p></div>
                  </div>
                  <div style={{ overflowX: "auto" }}>
                    <table style={tableStyle}>
                      <thead>
                        <tr>
                          <th style={thStyle}>Campaign</th>
                          <th style={thStyle}>Type</th>
                          <th style={thStyle}>Budget</th>
                          <th style={thStyle}>Burned</th>
                          <th style={thStyle}>Utilization</th>
                          <th style={thStyle}>Flash Sale</th>
                        </tr>
                      </thead>
                      <tbody>
                        {promoRoi.campaigns?.length > 0 ? promoRoi.campaigns.map((c) => (
                          <tr key={c.campaignId}>
                            <td style={{ ...tdStyle, fontWeight: 600 }}>{c.name}</td>
                            <td style={tdStyle}>{(c.benefitType ?? "").replace(/_/g, " ")}</td>
                            <td style={tdStyle}>{money(c.budgetAmount)}</td>
                            <td style={tdStyle}>{money(c.burnedBudgetAmount)}</td>
                            <td style={tdStyle}>
                              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                <div style={{ flex: 1, height: 6, borderRadius: 3, background: "rgba(255,255,255,0.06)", overflow: "hidden" }}>
                                  <div style={{ width: `${Math.min(100, c.utilizationPercent)}%`, height: "100%", borderRadius: 3, background: c.utilizationPercent > 80 ? "#f87171" : c.utilizationPercent > 50 ? "#fbbf24" : "#34d399" }} />
                                </div>
                                <span style={{ fontSize: "0.75rem", color: "var(--muted)", minWidth: 40 }}>{pct(c.utilizationPercent)}</span>
                              </div>
                            </td>
                            <td style={tdStyle}>{c.isFlashSale ? <span style={{ color: "#f87171", fontWeight: 600 }}>Yes</span> : "No"}</td>
                          </tr>
                        )) : (
                          <tr><td colSpan={6} style={{ ...tdStyle, textAlign: "center", color: "var(--muted)" }}>No campaigns.</td></tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </div>
          )}

          {activeTab === "reviews" && (
            <>
              {!reviewData ? (
                <div style={{ marginTop: 24 }}><SkeletonGrid count={2} height={200} /></div>
              ) : (
                <>
                  <div style={sectionStyle}>
                    <h2 style={sectionTitle}>Review Analytics</h2>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12, marginBottom: 20 }}>
                      <div><p style={labelStyle}>Total</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(reviewData.summary?.totalReviews)}</p></div>
                      <div><p style={labelStyle}>Active</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{num(reviewData.summary?.activeReviews)}</p></div>
                      <div><p style={labelStyle}>Avg Rating</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#fbbf24" }}>{(reviewData.summary?.avgRating ?? 0).toFixed(1)}</p></div>
                      <div><p style={labelStyle}>Verified %</p><p style={{ ...valueStyle, fontSize: "1.2rem" }}>{pct(reviewData.summary?.verifiedPurchasePercent)}</p></div>
                      <div><p style={labelStyle}>Reported</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "#f87171" }}>{num(reviewData.summary?.totalReported)}</p></div>
                      <div><p style={labelStyle}>This Month</p><p style={{ ...valueStyle, fontSize: "1.2rem", color: "var(--brand)" }}>{num(reviewData.summary?.reviewsThisMonth)}</p></div>
                    </div>
                  </div>

                  {reviewData.ratingDistribution && Object.keys(reviewData.ratingDistribution).length > 0 && (
                    <div style={sectionStyle}>
                      <h2 style={sectionTitle}>Rating Distribution</h2>
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
              )}
            </>
          )}
        </>
      )}

      {/* Empty state */}
      {!loading && !dash && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No analytics data available.</p>
        </div>
      )}
    </AdminPageShell>
  );
}
