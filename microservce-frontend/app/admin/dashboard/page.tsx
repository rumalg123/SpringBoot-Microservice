"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";

/* ───── types ───── */

type DashboardSummary = {
  totalOrders: number;
  pendingOrders: number;
  processingOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  totalVendors: number;
  activeVendors: number;
  totalProducts: number;
  activePromotions: number;
  ordersByStatus: Record<string, number>;
  generatedAt: string;
};

/* ───── card definitions ───── */

type CardDef = {
  label: string;
  key: keyof DashboardSummary;
  accent?: string;
};

const CARDS: CardDef[] = [
  { label: "Total Orders", key: "totalOrders" },
  { label: "Pending Orders", key: "pendingOrders", accent: "#fbbf24" },
  { label: "Processing Orders", key: "processingOrders" },
  { label: "Completed Orders", key: "completedOrders", accent: "#34d399" },
  { label: "Cancelled Orders", key: "cancelledOrders", accent: "#f87171" },
  { label: "Total Vendors", key: "totalVendors" },
  { label: "Active Vendors", key: "activeVendors", accent: "var(--brand)" },
  { label: "Total Products", key: "totalProducts" },
  { label: "Active Promotions", key: "activePromotions", accent: "var(--brand)" },
];

/* ───── helpers ───── */

function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as {
      response?: { data?: { error?: string; message?: string } };
      message?: string;
    };
    return (
      maybe.response?.data?.error ||
      maybe.response?.data?.message ||
      maybe.message ||
      "Request failed"
    );
  }
  return "Request failed";
}

function formatGeneratedAt(iso: string): string {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return iso;
  }
}

/* ───── styles ───── */

const cardStyle: React.CSSProperties = {
  background: "rgba(255,255,255,0.03)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: "20px 24px",
};

const labelStyle: React.CSSProperties = {
  color: "var(--muted)",
  fontSize: "0.72rem",
  textTransform: "uppercase",
  letterSpacing: "0.08em",
  margin: 0,
};

const valueStyle: React.CSSProperties = {
  fontSize: "1.8rem",
  fontWeight: 800,
  color: "var(--ink)",
  margin: "8px 0 0",
};

const skeletonCardStyle: React.CSSProperties = {
  ...cardStyle,
  height: 100,
  background: "rgba(255,255,255,0.02)",
};

/* ───── component ───── */

export default function AdminDashboardPage() {
  const session = useAuthSession();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!session.apiClient) return;
    if (!session.canViewAdmin) return;

    let cancelled = false;

    const fetchSummary = async () => {
      setLoading(true);
      try {
        const res = await session.apiClient!.get("/admin/dashboard/summary");
        if (!cancelled) {
          setSummary(res.data as DashboardSummary);
        }
      } catch (error) {
        if (!cancelled) {
          toast.error(getErrorMessage(error));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void fetchSummary();

    return () => {
      cancelled = true;
    };
  }, [session.apiClient, session.canViewAdmin]);

  /* ── unauthorized guard ── */
  if (session.status === "ready" && !session.canViewAdmin) {
    return (
      <AdminPageShell
        title="Dashboard"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Dashboard" },
        ]}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: 320,
          }}
        >
          <p
            style={{
              color: "var(--muted)",
              fontSize: "1rem",
              textAlign: "center",
            }}
          >
            Unauthorized. You do not have permission to view the admin dashboard.
          </p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Dashboard"
      breadcrumbs={[
        { label: "Admin", href: "/admin/orders" },
        { label: "Dashboard" },
      ]}
    >
      {/* ── loading skeleton ── */}
      {loading && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
            gap: 16,
          }}
        >
          {Array.from({ length: 9 }).map((_, i) => (
            <div key={i} className="skeleton" style={skeletonCardStyle} />
          ))}
        </div>
      )}

      {/* ── summary cards ── */}
      {!loading && summary && (
        <>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
              gap: 16,
            }}
          >
            {CARDS.map((card) => {
              const raw = summary[card.key];
              const value = typeof raw === "number" ? raw : 0;

              return (
                <div key={card.key} style={cardStyle}>
                  <p style={labelStyle}>{card.label}</p>
                  <p
                    style={{
                      ...valueStyle,
                      color: card.accent || "var(--ink)",
                    }}
                  >
                    {value.toLocaleString()}
                  </p>
                </div>
              );
            })}
          </div>

          {/* ── orders by status breakdown ── */}
          {summary.ordersByStatus &&
            Object.keys(summary.ordersByStatus).length > 0 && (
              <div style={{ marginTop: 32 }}>
                <h2
                  style={{
                    fontSize: "1rem",
                    fontWeight: 700,
                    color: "var(--ink)",
                    margin: "0 0 12px",
                  }}
                >
                  Orders by Status
                </h2>
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns:
                      "repeat(auto-fill, minmax(180px, 1fr))",
                    gap: 12,
                  }}
                >
                  {Object.entries(summary.ordersByStatus).map(
                    ([status, count]) => (
                      <div
                        key={status}
                        style={{
                          ...cardStyle,
                          padding: "14px 18px",
                        }}
                      >
                        <p style={labelStyle}>{status.replace(/_/g, " ")}</p>
                        <p
                          style={{
                            ...valueStyle,
                            fontSize: "1.4rem",
                          }}
                        >
                          {count.toLocaleString()}
                        </p>
                      </div>
                    )
                  )}
                </div>
              </div>
            )}

          {/* ── generated-at footer ── */}
          {summary.generatedAt && (
            <p
              style={{
                marginTop: 24,
                fontSize: "0.75rem",
                color: "var(--muted)",
              }}
            >
              Generated at: {formatGeneratedAt(summary.generatedAt)}
            </p>
          )}
        </>
      )}

      {/* ── empty state after load ── */}
      {!loading && !summary && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: 200,
          }}
        >
          <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>
            No dashboard data available.
          </p>
        </div>
      )}
    </AdminPageShell>
  );
}
