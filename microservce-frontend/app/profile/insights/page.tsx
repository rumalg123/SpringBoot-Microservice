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

import type { CustomerInsights, MonthlySpendBucket } from "../../../lib/types/customer";
import { CHART_GRID, CHART_TEXT, TIER_COLORS } from "../../../lib/constants";

/* ───── styles ───── */

const cardCls = "rounded-lg border border-line bg-[rgba(255,255,255,0.03)] px-6 py-5";
const sectionCls = `${cardCls} mt-6 px-7 py-6`;
const labelCls = "m-0 text-xs uppercase tracking-[0.08em] text-muted";
const valueCls = "mt-2 text-[1.6rem] font-extrabold text-ink";
const sectionTitleCls = "mb-4 mt-0 text-lg font-bold text-ink";

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
    <div className="rounded-md border border-line bg-[rgba(17,17,40,0.95)] px-3.5 py-2.5 text-sm">
      <p className="mb-1.5 mt-0 text-xs text-muted">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="my-0.5 font-semibold" style={{ color: p.color }}>
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
        const res = await api.get<{ id: string }>("/customers/me");
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
        const res = await api.get<CustomerInsights>(`/analytics/customer/${customerId}/insights`);
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
      <main className="min-h-screen bg-bg px-6 pb-12 pt-[100px] text-ink">
        <div className="mx-auto max-w-[960px]">
          <nav className="mb-3 flex gap-1.5 text-xs text-muted">
            <a href="/profile" className="text-brand no-underline">Profile</a>
            <span className="mx-1">/</span>
            <span>Insights</span>
          </nav>
          <h1 className="mb-6 font-[var(--font-display,Syne,sans-serif)] text-[clamp(1.4rem,3vw,1.8rem)] font-extrabold" style={{ background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
            My Insights
          </h1>

          {loading && (
            <div className="grid gap-3.5" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
              {Array.from({ length: 4 }).map((_, i) => <div key={i} className="skeleton h-[100px] rounded-lg border border-line bg-[rgba(255,255,255,0.02)] p-5" />)}
            </div>
          )}

          {!loading && data && (
            <>
              {/* Loyalty card */}
              {p && (
                <div className={`${cardCls} mb-5 flex flex-wrap items-center gap-5`}>
                  <div className="flex h-14 w-14 items-center justify-center rounded-full text-[1.4rem] font-extrabold text-white" style={{ background: `linear-gradient(135deg, ${tierColor}, rgba(0,0,0,0.3))` }}>
                    {(p.loyaltyTier ?? "?")[0]}
                  </div>
                  <div>
                    <p className="m-0 text-[1.1rem] font-bold text-ink">{p.name || p.email}</p>
                    <p className="mt-1 mb-0 text-sm font-semibold uppercase tracking-[0.08em]" style={{ color: tierColor }}>
                      {(p.loyaltyTier ?? "").replace(/_/g, " ")} Tier
                    </p>
                  </div>
                  <div className="ml-auto text-right">
                    <p className={labelCls}>Loyalty Points</p>
                    <p className="mt-2 text-[1.4rem] font-extrabold" style={{ color: tierColor }}>{num(p.loyaltyPoints)}</p>
                  </div>
                </div>
              )}

              {/* Order summary cards */}
              <div className="grid gap-3.5" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
                <div className={cardCls}><p className={labelCls}>Total Orders</p><p className={valueCls}>{num(o?.totalOrders)}</p><p className="mt-1 mb-0 text-xs text-muted">{num(o?.activeOrders)} active</p></div>
                <div className={cardCls}><p className={labelCls}>Total Spent</p><p className={`${valueCls} !text-brand`}>{shortMoney(o?.totalSpent)}</p></div>
                <div className={cardCls}><p className={labelCls}>Total Saved</p><p className={`${valueCls} !text-[#34d399]`}>{shortMoney(o?.totalSaved)}</p></div>
                <div className={cardCls}><p className={labelCls}>Avg Order Value</p><p className={valueCls}>{shortMoney(o?.averageOrderValue)}</p></div>
              </div>

              {/* Spending trend chart */}
              {data.spendingTrend?.length > 0 && (
                <div className={sectionCls}>
                  <h2 className={sectionTitleCls}>Monthly Spending</h2>
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
              <div className="mt-4 grid grid-cols-2 gap-3.5">
                <div className={cardCls}>
                  <p className={labelCls}>Unique Vendors Ordered From</p>
                  <p className={`${valueCls} !text-[#7c3aed]`}>{num(o?.uniqueVendorsOrdered)}</p>
                </div>
                <div className={cardCls}>
                  <p className={labelCls}>Completed Orders</p>
                  <p className={`${valueCls} !text-[#34d399]`}>{num(o?.completedOrders)}</p>
                </div>
              </div>
            </>
          )}

          {!loading && !data && (
            <div className="flex min-h-[200px] items-center justify-center">
              <p className="text-[0.9rem] text-muted">No insights data available yet. Start shopping to see your insights!</p>
            </div>
          )}
        </div>
      </main>
      <Footer />
    </>
  );
}
