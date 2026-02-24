"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import toast from "react-hot-toast";
import { useAuthSession } from "../../lib/authSession";
import AdminPageShell from "../components/ui/AdminPageShell";
import StatusBadge, { VENDOR_STATUS_COLORS } from "../components/ui/StatusBadge";

/* ───── types ───── */

type VendorProfile = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  supportEmail: string | null;
  contactPhone: string | null;
  contactPersonName: string | null;
  logoImage: string | null;
  description: string | null;
  status: string;
  active: boolean;
  acceptingOrders: boolean;
  verificationStatus: string;
  verified: boolean;
  averageRating: number | null;
  totalRatings: number;
  fulfillmentRate: number | null;
  totalOrdersCompleted: number;
  commissionRate: number | null;
  createdAt: string;
  updatedAt: string;
};

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

/* ───── verification color map ───── */

const VERIFICATION_STATUS_COLORS: Record<string, { bg: string; border: string; color: string }> = {
  UNVERIFIED: { bg: "rgba(255,255,255,0.03)", border: "var(--line)", color: "var(--muted)" },
  PENDING_VERIFICATION: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  VERIFIED: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  VERIFICATION_REJECTED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

/* ───── styles ───── */

const glassCard: React.CSSProperties = {
  background: "rgba(255,255,255,0.03)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: "24px",
};

const statCard: React.CSSProperties = {
  background: "rgba(255,255,255,0.03)",
  border: "1px solid var(--line)",
  borderRadius: 12,
  padding: "16px 20px",
};

const statLabel: React.CSSProperties = {
  color: "var(--muted)",
  fontSize: "0.72rem",
  textTransform: "uppercase",
  letterSpacing: "0.08em",
  margin: 0,
};

const statValue: React.CSSProperties = {
  fontSize: "1.6rem",
  fontWeight: 800,
  color: "var(--ink)",
  margin: "6px 0 0",
};

const skeletonCard: React.CSSProperties = {
  ...glassCard,
  height: 200,
  background: "rgba(255,255,255,0.02)",
};

const sectionTitle: React.CSSProperties = {
  fontSize: "0.85rem",
  fontWeight: 700,
  color: "var(--muted)",
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  margin: "0 0 12px",
};

/* ───── component ───── */

export default function VendorDashboardPage() {
  const session = useAuthSession();
  const [vendor, setVendor] = useState<VendorProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!session.apiClient) return;
    if (!session.isVendorAdmin && !session.isVendorStaff) return;

    let cancelled = false;

    const fetchVendor = async () => {
      setLoading(true);
      try {
        const res = await session.apiClient!.get("/vendors/me");
        if (!cancelled) {
          setVendor(res.data as VendorProfile);
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

    void fetchVendor();

    return () => {
      cancelled = true;
    };
  }, [session.apiClient, session.isVendorAdmin, session.isVendorStaff]);

  /* ── unauthorized guard ── */
  if (session.status === "ready" && !session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <AdminPageShell
        title="Vendor Portal"
        breadcrumbs={[{ label: "Vendor Portal" }]}
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
            Unauthorized. You do not have permission to access the vendor portal.
          </p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Vendor Portal"
      breadcrumbs={[{ label: "Vendor Portal" }]}
    >
      {/* ── loading skeleton ── */}
      {loading && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="skeleton" style={skeletonCard} />
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
              gap: 16,
            }}
          >
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="skeleton"
                style={{ ...statCard, height: 90, background: "rgba(255,255,255,0.02)" }}
              />
            ))}
          </div>
        </div>
      )}

      {/* ── vendor profile ── */}
      {!loading && vendor && (
        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          {/* header card */}
          <div style={glassCard}>
            {/* vendor name + status */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                flexWrap: "wrap",
                marginBottom: 16,
              }}
            >
              <h2
                style={{
                  fontSize: "1.4rem",
                  fontWeight: 800,
                  color: "var(--ink)",
                  margin: 0,
                }}
              >
                {vendor.name}
              </h2>
              <StatusBadge value={vendor.status} colorMap={VENDOR_STATUS_COLORS} />
            </div>

            {/* badges row */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                flexWrap: "wrap",
                marginBottom: 20,
              }}
            >
              <StatusBadge
                value={vendor.verificationStatus}
                colorMap={VERIFICATION_STATUS_COLORS}
              />
              <span
                style={{
                  display: "inline-block",
                  padding: "2px 8px",
                  borderRadius: 999,
                  fontSize: "0.68rem",
                  fontWeight: 700,
                  whiteSpace: "nowrap",
                  border: vendor.acceptingOrders
                    ? "1px solid rgba(34,197,94,0.3)"
                    : "1px solid rgba(239,68,68,0.25)",
                  color: vendor.acceptingOrders ? "var(--success)" : "#f87171",
                  background: vendor.acceptingOrders
                    ? "var(--success-soft)"
                    : "var(--danger-soft)",
                }}
              >
                {vendor.acceptingOrders ? "Accepting Orders" : "Orders Paused"}
              </span>
            </div>

            {/* contact info */}
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <p style={{ margin: 0, fontSize: "0.85rem", color: "var(--muted)" }}>
                <span style={{ color: "var(--ink)", fontWeight: 600 }}>Email: </span>
                {vendor.contactEmail}
              </p>
              {vendor.contactPhone && (
                <p style={{ margin: 0, fontSize: "0.85rem", color: "var(--muted)" }}>
                  <span style={{ color: "var(--ink)", fontWeight: 600 }}>Phone: </span>
                  {vendor.contactPhone}
                </p>
              )}
            </div>
          </div>

          {/* stats grid */}
          <div>
            <h3 style={sectionTitle}>Performance</h3>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
                gap: 16,
              }}
            >
              <div style={statCard}>
                <p style={statLabel}>Total Orders Completed</p>
                <p style={statValue}>{vendor.totalOrdersCompleted.toLocaleString()}</p>
              </div>
              <div style={statCard}>
                <p style={statLabel}>Average Rating</p>
                <p style={statValue}>
                  {vendor.averageRating !== null
                    ? vendor.averageRating.toFixed(1)
                    : "N/A"}
                </p>
              </div>
              <div style={statCard}>
                <p style={statLabel}>Fulfillment Rate</p>
                <p style={statValue}>
                  {vendor.fulfillmentRate !== null
                    ? `${(vendor.fulfillmentRate * 100).toFixed(1)}%`
                    : "N/A"}
                </p>
              </div>
              <div style={statCard}>
                <p style={statLabel}>Commission Rate</p>
                <p style={statValue}>
                  {vendor.commissionRate !== null
                    ? `${(vendor.commissionRate * 100).toFixed(1)}%`
                    : "N/A"}
                </p>
              </div>
            </div>
          </div>

          {/* quick actions */}
          <div>
            <h3 style={sectionTitle}>Quick Actions</h3>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <Link
                href="/vendor/orders"
                className="btn-brand"
                style={{
                  padding: "12px 24px",
                  borderRadius: 10,
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  textDecoration: "none",
                  display: "inline-block",
                }}
              >
                View Orders
              </Link>
              <Link
                href="/vendor/settings"
                className="btn-outline"
                style={{
                  padding: "12px 24px",
                  borderRadius: 10,
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  textDecoration: "none",
                  display: "inline-block",
                }}
              >
                Settings
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* ── empty state ── */}
      {!loading && !vendor && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: 200,
          }}
        >
          <p style={{ color: "var(--muted)", fontSize: "0.9rem" }}>
            No vendor profile found.
          </p>
        </div>
      )}
    </AdminPageShell>
  );
}
