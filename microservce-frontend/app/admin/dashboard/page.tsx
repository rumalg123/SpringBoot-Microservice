"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";
import AdminPageShell from "../../components/ui/AdminPageShell";

import type {
  DashboardData, RevenueTrendData, TopProductsData,
  CustomerSegmentationData, VendorLeaderboardData,
  InventoryHealthData, PromotionRoiData, ReviewAnalyticsData, Tab,
} from "../../components/admin/dashboard/types";
import { num, pct, shortMoney, SkeletonGrid } from "../../components/admin/dashboard/helpers";
import KpiCard from "../../components/admin/dashboard/KpiCard";
import OverviewTab from "../../components/admin/dashboard/OverviewTab";
import ProductsTab from "../../components/admin/dashboard/ProductsTab";
import CustomersTab from "../../components/admin/dashboard/CustomersTab";
import VendorsTab from "../../components/admin/dashboard/VendorsTab";
import InventoryTab from "../../components/admin/dashboard/InventoryTab";
import PromotionsTab from "../../components/admin/dashboard/PromotionsTab";
import ReviewsTab from "../../components/admin/dashboard/ReviewsTab";

/* ───── tabs ───── */

const TABS: { id: Tab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "products", label: "Products" },
  { id: "customers", label: "Customers" },
  { id: "vendors", label: "Vendors" },
  { id: "inventory", label: "Inventory" },
  { id: "promotions", label: "Promotions" },
  { id: "reviews", label: "Reviews" },
];

/* ───── main component ───── */

export default function AdminDashboardPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<Tab>("overview");
  const [periodDays, setPeriodDays] = useState(30);

  const api = session.apiClient;

  // Main dashboard query
  const { data: dash, isLoading: loading } = useQuery<DashboardData>({
    queryKey: ["admin-dashboard"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/dashboard");
      return res.data;
    },
    enabled: !!api && !!session.canViewAdmin,
  });

  // Detail tab queries - each enabled only when its tab is active and dashboard is loaded
  const { data: revenueTrend } = useQuery<RevenueTrendData>({
    queryKey: ["admin-dashboard", "revenue-trend", periodDays],
    queryFn: async () => {
      const res = await api!.get(`/analytics/admin/revenue-trend?days=${periodDays}`);
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "overview",
  });

  const { data: topProducts } = useQuery<TopProductsData>({
    queryKey: ["admin-dashboard", "top-products"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/top-products");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "products",
  });

  const { data: customerSeg } = useQuery<CustomerSegmentationData>({
    queryKey: ["admin-dashboard", "customer-segmentation"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/customer-segmentation");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "customers",
  });

  const { data: vendorBoard } = useQuery<VendorLeaderboardData>({
    queryKey: ["admin-dashboard", "vendor-leaderboard"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/vendor-leaderboard?sortBy=ORDERS_COMPLETED");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "vendors",
  });

  const { data: invHealth } = useQuery<InventoryHealthData>({
    queryKey: ["admin-dashboard", "inventory-health"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/inventory-health");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "inventory",
  });

  const { data: promoRoi } = useQuery<PromotionRoiData>({
    queryKey: ["admin-dashboard", "promotion-roi"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/promotion-roi");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "promotions",
  });

  const { data: reviewData } = useQuery<ReviewAnalyticsData>({
    queryKey: ["admin-dashboard", "review-analytics"],
    queryFn: async () => {
      const res = await api!.get("/analytics/admin/review-analytics");
      return res.data;
    },
    enabled: !!api && !loading && activeTab === "reviews",
  });

  // Cache eviction mutation
  const evictCacheMutation = useMutation({
    mutationFn: async () => {
      await api!.post("/analytics/admin/cache/evict");
    },
    onSuccess: () => {
      toast.success("All analytics caches evicted");
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard"] });
    },
    onError: (e) => {
      toast.error(getErrorMessage(e));
    },
  });

  const handleEvictCache = () => {
    if (!api) return;
    evictCacheMutation.mutate();
  };

  /* unauthorized guard */
  if (session.status === "ready" && !session.canViewAdmin) {
    return (
      <AdminPageShell title="Analytics" breadcrumbs={[{ label: "Admin", href: "/admin/orders" }, { label: "Analytics" }]}>
        <div className="flex items-center justify-center min-h-[320px]">
          <p className="text-muted text-lg text-center">Unauthorized. You do not have permission to view admin analytics.</p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Analytics"
      breadcrumbs={[{ label: "Admin", href: "/admin/orders" }, { label: "Analytics" }]}
      actions={
        <button type="button" onClick={handleEvictCache} className="py-2 px-4 rounded-md text-[0.78rem] font-semibold bg-[rgba(255,255,255,0.05)] border border-line text-muted cursor-pointer">
          Refresh Data
        </button>
      }
    >
      {/* Loading */}
      {loading && <SkeletonGrid count={6} />}

      {!loading && dash && (
        <>
          {/* ── KPI cards ── */}
          <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-3.5">
            <KpiCard label="Total Revenue" value={shortMoney(dash.orders?.totalRevenue)} accent="var(--brand)" sub={`Avg order: ${shortMoney(dash.orders?.averageOrderValue)}`} />
            <KpiCard label="Total Orders" value={num(dash.orders?.totalOrders)} sub={`${pct(dash.orders?.orderCompletionRate)} completion`} />
            <KpiCard label="Customers" value={num(dash.customers?.totalCustomers)} accent="#34d399" sub={`${num(dash.customers?.newCustomersThisMonth)} new this month`} />
            <KpiCard label="Products" value={num(dash.products?.totalProducts)} sub={`${num(dash.products?.activeProducts)} active`} />
            <KpiCard label="Active Vendors" value={num(dash.vendors?.activeVendors)} accent="#7c3aed" sub={`${num(dash.vendors?.totalVendors)} total`} />
            <KpiCard label="Active Promotions" value={num(dash.promotions?.activeCampaigns)} accent="#fbbf24" sub={`${num(dash.promotions?.totalCampaigns)} total`} />
          </div>

          {/* ── Tabs ── */}
          <div className="flex gap-1.5 mt-6 flex-wrap">
            {TABS.map((t) => (
              <button key={t.id} type="button" onClick={() => setActiveTab(t.id)} className={`cursor-pointer rounded-md border px-4.5 py-2 text-sm font-semibold transition-all duration-200 ${activeTab === t.id ? "border-[rgba(0,212,255,0.25)] bg-[rgba(0,212,255,0.12)] text-brand" : "border-transparent bg-transparent text-muted"}`}>{t.label}</button>
            ))}
          </div>

          {/* ── Tab Content ── */}
          {activeTab === "overview" && (
            <OverviewTab
              dash={dash}
              revenueTrend={revenueTrend ?? null}
              periodDays={periodDays}
              setPeriodDays={setPeriodDays}
              setRevenueTrend={() => {}}
            />
          )}
          {activeTab === "products" && <ProductsTab topProducts={topProducts ?? null} />}
          {activeTab === "customers" && <CustomersTab customerSeg={customerSeg ?? null} />}
          {activeTab === "vendors" && <VendorsTab vendorBoard={vendorBoard ?? null} />}
          {activeTab === "inventory" && <InventoryTab invHealth={invHealth ?? null} />}
          {activeTab === "promotions" && <PromotionsTab promoRoi={promoRoi ?? null} />}
          {activeTab === "reviews" && <ReviewsTab reviewData={reviewData ?? null} />}
        </>
      )}

      {/* Empty state */}
      {!loading && !dash && (
        <div className="flex items-center justify-center min-h-[200px]">
          <p className="text-muted text-[0.9rem]">No analytics data available.</p>
        </div>
      )}
    </AdminPageShell>
  );
}
