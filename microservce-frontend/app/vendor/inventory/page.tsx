"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import WarehousesTab from "../../components/inventory/WarehousesTab";
import StockTab from "../../components/inventory/StockTab";
import MovementsTab from "../../components/inventory/MovementsTab";
import { getErrorMessage } from "../../../lib/error";

const TABS = ["Warehouses", "Stock", "Movements"] as const;
type Tab = (typeof TABS)[number];

const tabBtnBase: React.CSSProperties = {
  padding: "10px 20px",
  background: "transparent",
  border: "none",
  cursor: "pointer",
  fontSize: "0.85rem",
  fontWeight: 600,
  transition: "color 0.2s",
  borderBottom: "2px solid transparent",
};

export default function VendorInventoryPage() {
  const session = useAuthSession();
  const [activeTab, setActiveTab] = useState<Tab>("Stock");
  const [vendorId, setVendorId] = useState<string>("");
  const [loadingVendor, setLoadingVendor] = useState(true);

  /* ── Resolve vendor ID ── */
  useEffect(() => {
    if (session.status !== "ready" || !session.apiClient || !session.isVendorAdmin) return;
    const fetchVendor = async () => {
      try {
        const res = await session.apiClient!.get("/vendor/me");
        const data = res.data as { id?: string };
        setVendorId(data.id || "");
      } catch (err) {
        toast.error(getErrorMessage(err));
      } finally {
        setLoadingVendor(false);
      }
    };
    void fetchVendor();
  }, [session.status, session.apiClient, session.isVendorAdmin]);

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading session...</p>
        </div>
      </AdminPageShell>
    );
  }

  if (!session.isVendorAdmin) {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300, flexDirection: "column", gap: 12 }}>
          <p style={{ color: "var(--danger)", fontSize: "1.1rem", fontWeight: 700, fontFamily: "var(--font-display, Syne, sans-serif)" }}>Unauthorized</p>
          <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>Only vendor admins can manage inventory.</p>
        </div>
      </AdminPageShell>
    );
  }

  if (loadingVendor) {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading vendor profile...</p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
      {/* ── Tab bar ── */}
      <div style={{ display: "flex", gap: 0, borderBottom: "1px solid var(--line)", marginBottom: 20 }}>
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            style={{
              ...tabBtnBase,
              color: activeTab === tab ? "#34d399" : "var(--muted)",
              borderBottomColor: activeTab === tab ? "#34d399" : "transparent",
            }}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      {session.apiClient && activeTab === "Warehouses" && (
        <WarehousesTab apiClient={session.apiClient} apiPrefix="/inventory/vendor/me" vendorId={vendorId} />
      )}
      {session.apiClient && activeTab === "Stock" && (
        <StockTab apiClient={session.apiClient} apiPrefix="/inventory/vendor/me" vendorId={vendorId} />
      )}
      {session.apiClient && activeTab === "Movements" && (
        <MovementsTab apiClient={session.apiClient} apiPrefix="/inventory/vendor/me" />
      )}
    </AdminPageShell>
  );
}
