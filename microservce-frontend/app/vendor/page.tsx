"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import toast from "react-hot-toast";
import { useAuthSession } from "../../lib/authSession";
import VendorPageShell from "../components/ui/VendorPageShell";
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
      <VendorPageShell
        title="Vendor Portal"
        breadcrumbs={[{ label: "Vendor Portal" }]}
      >
        <div className="flex items-center justify-center min-h-[320px]">
          <p className="text-muted text-lg text-center">
            Unauthorized. You do not have permission to access the vendor portal.
          </p>
        </div>
      </VendorPageShell>
    );
  }

  return (
    <VendorPageShell
      title="Vendor Portal"
      breadcrumbs={[{ label: "Vendor Portal" }]}
    >
      {/* ── loading skeleton ── */}
      {loading && (
        <div className="flex flex-col gap-4">
          <div className="skeleton bg-[rgba(255,255,255,0.02)] border border-line rounded-lg p-6 h-[200px]" />
          <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="skeleton bg-[rgba(255,255,255,0.02)] border border-line rounded-[12px] px-5 py-4 h-[90px]"
              />
            ))}
          </div>
        </div>
      )}

      {/* ── vendor profile ── */}
      {!loading && vendor && (
        <div className="flex flex-col gap-6">
          {/* header card */}
          <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg p-6">
            {/* vendor name + status */}
            <div className="flex items-center gap-3 flex-wrap mb-4">
              <h2 className="text-[1.4rem] font-extrabold text-ink m-0">
                {vendor.name}
              </h2>
              <StatusBadge value={vendor.status} colorMap={VENDOR_STATUS_COLORS} />
            </div>

            {/* badges row */}
            <div className="flex items-center gap-2.5 flex-wrap mb-5">
              <StatusBadge
                value={vendor.verificationStatus}
                colorMap={VERIFICATION_STATUS_COLORS}
              />
              <span
                className={`inline-block px-2 py-0.5 rounded-full text-[0.68rem] font-bold whitespace-nowrap ${
                  vendor.acceptingOrders
                    ? "border border-[rgba(34,197,94,0.3)] text-success bg-success-soft"
                    : "border border-[rgba(239,68,68,0.25)] text-[#f87171] bg-danger-soft"
                }`}
              >
                {vendor.acceptingOrders ? "Accepting Orders" : "Orders Paused"}
              </span>
            </div>

            {/* contact info */}
            <div className="flex flex-col gap-1.5">
              <p className="m-0 text-base text-muted">
                <span className="text-ink font-semibold">Email: </span>
                {vendor.contactEmail}
              </p>
              {vendor.contactPhone && (
                <p className="m-0 text-base text-muted">
                  <span className="text-ink font-semibold">Phone: </span>
                  {vendor.contactPhone}
                </p>
              )}
            </div>
          </div>

          {/* stats grid */}
          <div>
            <h3 className="text-base font-bold text-muted uppercase tracking-[0.06em] mb-3">Performance</h3>
            <div className="grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] gap-4">
              <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-[12px] px-5 py-4">
                <p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Total Orders Completed</p>
                <p className="text-[1.6rem] font-extrabold text-ink mt-1.5 mb-0">{vendor.totalOrdersCompleted.toLocaleString()}</p>
              </div>
              <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-[12px] px-5 py-4">
                <p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Average Rating</p>
                <p className="text-[1.6rem] font-extrabold text-ink mt-1.5 mb-0">
                  {vendor.averageRating !== null
                    ? vendor.averageRating.toFixed(1)
                    : "N/A"}
                </p>
              </div>
              <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-[12px] px-5 py-4">
                <p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Fulfillment Rate</p>
                <p className="text-[1.6rem] font-extrabold text-ink mt-1.5 mb-0">
                  {vendor.fulfillmentRate !== null
                    ? `${(vendor.fulfillmentRate * 100).toFixed(1)}%`
                    : "N/A"}
                </p>
              </div>
              <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-[12px] px-5 py-4">
                <p className="text-muted text-xs uppercase tracking-[0.08em] m-0">Commission Rate</p>
                <p className="text-[1.6rem] font-extrabold text-ink mt-1.5 mb-0">
                  {vendor.commissionRate !== null
                    ? `${(vendor.commissionRate * 100).toFixed(1)}%`
                    : "N/A"}
                </p>
              </div>
            </div>
          </div>

          {/* quick actions */}
          <div>
            <h3 className="text-base font-bold text-muted uppercase tracking-[0.06em] mb-3">Quick Actions</h3>
            <div className="flex gap-3 flex-wrap">
              <Link
                href="/vendor/orders"
                className="btn-brand px-6 py-3 rounded-md font-semibold text-base no-underline inline-block"
              >
                View Orders
              </Link>
              <Link
                href="/vendor/settings"
                className="btn-outline px-6 py-3 rounded-md font-semibold text-base no-underline inline-block"
              >
                Settings
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* ── empty state ── */}
      {!loading && !vendor && (
        <div className="flex items-center justify-center min-h-[200px]">
          <p className="text-muted text-[0.9rem]">
            No vendor profile found.
          </p>
        </div>
      )}
    </VendorPageShell>
  );
}
