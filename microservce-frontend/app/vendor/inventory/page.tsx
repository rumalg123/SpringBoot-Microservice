"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import VendorPageShell from "../../components/ui/VendorPageShell";
import WarehousesTab from "../../components/inventory/WarehousesTab";
import StockTab from "../../components/inventory/StockTab";
import MovementsTab from "../../components/inventory/MovementsTab";

const TABS = ["Warehouses", "Stock", "Movements"] as const;
type Tab = (typeof TABS)[number];

export default function VendorInventoryPage() {
  const session = useAuthSession();
  const [activeTab, setActiveTab] = useState<Tab>("Stock");

  const ready = session.status === "ready" && !!session.apiClient && session.isVendorAdmin;

  /* ── Resolve vendor ID ── */
  const { data: vendorId = "", isLoading: loadingVendor } = useQuery({
    queryKey: ["vendor-inventory-id"],
    queryFn: async () => {
      const res = await session.apiClient!.get("/vendor/me");
      const data = res.data as { id?: string };
      return data.id || "";
    },
    enabled: ready,
  });

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <VendorPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading session...</p>
        </div>
      </VendorPageShell>
    );
  }

  if (!session.isVendorAdmin) {
    return (
      <VendorPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div className="flex items-center justify-center min-h-[300px] flex-col gap-3">
          <p className="text-danger text-[1.1rem] font-bold font-[var(--font-display,Syne,sans-serif)]">Unauthorized</p>
          <p className="text-muted text-sm">Only vendor admins can manage inventory.</p>
        </div>
      </VendorPageShell>
    );
  }

  if (loadingVendor) {
    return (
      <VendorPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading vendor profile...</p>
        </div>
      </VendorPageShell>
    );
  }

  return (
    <VendorPageShell title="Inventory" breadcrumbs={[{ label: "Vendor", href: "/vendor" }, { label: "Inventory" }]}>
      {/* ── Tab bar ── */}
      <div className="flex border-b border-line mb-5">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className="px-5 py-2.5 bg-transparent border-none cursor-pointer text-base font-semibold transition-colors duration-200"
            style={{
              color: activeTab === tab ? "#34d399" : "var(--muted)",
              borderBottom: activeTab === tab ? "2px solid #34d399" : "2px solid transparent",
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
    </VendorPageShell>
  );
}
