"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";
import { money } from "../../../lib/format";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
} from "recharts";

/* ───── types ───── */

type CustomerOrderSummary = {
  customerId: string; totalOrders: number; activeOrders: number;
  completedOrders: number; totalSpent: number; totalSaved: number;
  averageOrderValue: number; uniqueVendorsOrdered: number;
};
type MonthlySpendBucket = { month: string; amount: number; orderCount: number };
type CustomerProfileSummary = {
  id: string; name: string; email: string; loyaltyTier: string;
  loyaltyPoints: number; memberSince: string; active: boolean;
};
type CustomerInsights = {
  orderSummary: CustomerOrderSummary | null;
  spendingTrend: MonthlySpendBucket[];
  profile: CustomerProfileSummary | null;
};

/* ───── constants ───── */

const CHART_GRID = "rgba(120,120,200,0.08)";
const CHART_TEXT = "#6868a0";

const TIER_COLORS: Record<string, string> = {
  BRONZE: "#cd7f32",
  SILVER: "#c0c0c0",
  GOLD: "#fbbf24",
  PLATINUM: "#a78bfa",
  DIAMOND: "#00d4ff",
};

/* ───── styles ───── */

const cardStyle: React.CSSProperties = { background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", borderRadius: 16, padding: "20px 24px" };
const sectionStyle: React.CSSProperties = { ...cardStyle, padding: "24px 28px", marginTop: 24 };
const labelStyle: React.CSSProperties = { color: "var(--muted)", fontSize: "0.72rem", textTransform: "uppercase", letterSpacing: "0.08em", margin: 0 };
const valueStyle: React.CSSProperties = { fontSize: "1.6rem", fontWeight: 800, color: "var(--ink)", margin: "8px 0 0" };
const sectionTitle: React.CSSProperties = { fontSize: "1rem", fontWeight: 700, color: "var(--ink)", margin: "0 0 16px" };

/* ───── helpers ───── */

function num(v: number | null | undefined): string { return (v ?? 0).toLocaleString(); }
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
        <p key={i} style={{ color: p.color, margin: "2px 0", fontWeight: 600 }}>
          {p.name}: {p.name.toLowerCase().includes("amount") || p.name.toLowerCase().includes("spent") ? money(p.value) : num(p.value)}
        </p>
      ))}
    </div>
  );
}

/* ───── component ───── */

export default function CustomerInsightsPage() {
  const session = useAuthSession();
  const [data, setData] = useState<CustomerInsights | null>(null);
  const [loading, setLoading] = useState(true);
  const [customerId, setCustomerId] = useState<string | null>(null);
  const api = session.apiClient;

  // Get customer ID from /customers/me
  useEffect(() => {
    if (!api || !session.isAuthenticated) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get("/customers/me");
        if (!cancelled) setCustomerId(res.data.id);
      } catch {
        // Not a registered customer
      }
    })();
    return () => { cancelled = true; };
  }, [api, session.isAuthenticated]);

  // Fetch insights
  useEffect(() => {
    if (!api || !customerId) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await api.get(`/analytics/customer/${customerId}/insights`);
        if (!cancelled) setData(res.data);
      } catch (e) {
        if (!cancelled) toast.error(getErrorMessage(e));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [api, customerId]);

  const o = data?.orderSummary;
  const p = data?.profile;
  const tierColor = TIER_COLORS[(p?.loyaltyTier ?? "").toUpperCase()] || "var(--brand)";

  return (
    <>
      <AppNav
        email={(session.profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />
      <main style={{ minHeight: "100vh", background: "var(--bg)", color: "var(--ink)", padding: "100px 24px 48px" }}>
        <div style={{ maxWidth: 960, margin: "0 auto" }}>
          <nav style={{ display: "flex", gap: 6, fontSize: "0.75rem", color: "var(--muted)", marginBottom: 12 }}>
            <a href="/profile" style={{ color: "var(--brand)", textDecoration: "none" }}>Profile</a>
            <span style={{ margin: "0 4px" }}>/</span>
            <span>Insights</span>
          </nav>
          <h1 style={{ fontFamily: "var(--font-display, Syne, sans-serif)", fontSize: "clamp(1.4rem,3vw,1.8rem)", fontWeight: 800, background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", marginBottom: 24 }}>
            My Insights
          </h1>

          {loading && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 14 }}>
              {Array.from({ length: 4 }).map((_, i) => <div key={i} className="skeleton" style={{ ...cardStyle, height: 100, background: "rgba(255,255,255,0.02)" }} />)}
            </div>
          )}

          {!loading && data && (
            <>
              {/* Loyalty card */}
              {p && (
                <div style={{ ...cardStyle, display: "flex", alignItems: "center", gap: 20, flexWrap: "wrap", marginBottom: 20 }}>
                  <div style={{ width: 56, height: 56, borderRadius: "50%", background: `linear-gradient(135deg, ${tierColor}, rgba(0,0,0,0.3))`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: "1.4rem", fontWeight: 800, color: "#fff" }}>
                    {(p.loyaltyTier ?? "?")[0]}
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: "1.1rem", fontWeight: 700, color: "var(--ink)" }}>{p.name || p.email}</p>
                    <p style={{ margin: "4px 0 0", fontSize: "0.82rem", color: tierColor, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.08em" }}>
                      {(p.loyaltyTier ?? "").replace(/_/g, " ")} Tier
                    </p>
                  </div>
                  <div style={{ marginLeft: "auto", textAlign: "right" }}>
                    <p style={labelStyle}>Loyalty Points</p>
                    <p style={{ ...valueStyle, fontSize: "1.4rem", color: tierColor }}>{num(p.loyaltyPoints)}</p>
                  </div>
                </div>
              )}

              {/* Order summary cards */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 14 }}>
                <div style={cardStyle}><p style={labelStyle}>Total Orders</p><p style={valueStyle}>{num(o?.totalOrders)}</p><p style={{ color: "var(--muted)", fontSize: "0.72rem", margin: "4px 0 0" }}>{num(o?.activeOrders)} active</p></div>
                <div style={cardStyle}><p style={labelStyle}>Total Spent</p><p style={{ ...valueStyle, color: "var(--brand)" }}>{shortMoney(o?.totalSpent)}</p></div>
                <div style={cardStyle}><p style={labelStyle}>Total Saved</p><p style={{ ...valueStyle, color: "#34d399" }}>{shortMoney(o?.totalSaved)}</p></div>
                <div style={cardStyle}><p style={labelStyle}>Avg Order Value</p><p style={valueStyle}>{shortMoney(o?.averageOrderValue)}</p></div>
              </div>

              {/* Spending trend chart */}
              {data.spendingTrend?.length > 0 && (
                <div style={sectionStyle}>
                  <h2 style={sectionTitle}>Monthly Spending</h2>
                  <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={data.spendingTrend}>
                      <CartesianGrid strokeDasharray="3 3" stroke={CHART_GRID} />
                      <XAxis dataKey="month" tick={{ fill: CHART_TEXT, fontSize: 11 }} />
                      <YAxis tick={{ fill: CHART_TEXT, fontSize: 11 }} tickFormatter={(v: number) => shortMoney(v)} />
                      <Tooltip content={<ChartTooltip />} />
                      <Bar dataKey="amount" name="Amount Spent" radius={[6, 6, 0, 0]} barSize={32}>
                        {data.spendingTrend.map((_, i) => <Cell key={i} fill={i === data.spendingTrend.length - 1 ? "#00d4ff" : "rgba(0,212,255,0.4)"} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}

              {/* Extra info */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginTop: 16 }}>
                <div style={cardStyle}>
                  <p style={labelStyle}>Unique Vendors Ordered From</p>
                  <p style={{ ...valueStyle, color: "#7c3aed" }}>{num(o?.uniqueVendorsOrdered)}</p>
                </div>
                <div style={cardStyle}>
                  <p style={labelStyle}>Completed Orders</p>
                  <p style={{ ...valueStyle, color: "#34d399" }}>{num(o?.completedOrders)}</p>
                </div>
              </div>
            </>
          )}

          {!loading && !data && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200 }}>
              <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>No insights data available yet. Start shopping to see your insights!</p>
            </div>
          )}
        </div>
      </main>
      <Footer />
    </>
  );
}
