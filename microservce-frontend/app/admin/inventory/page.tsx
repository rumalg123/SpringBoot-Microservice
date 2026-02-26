"use client";

import { useState } from "react";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import WarehousesTab from "../../components/inventory/WarehousesTab";
import StockTab from "../../components/inventory/StockTab";
import MovementsTab from "../../components/inventory/MovementsTab";
import ReservationsTab from "../../components/inventory/ReservationsTab";

const TABS = ["Warehouses", "Stock", "Movements", "Reservations"] as const;
type Tab = (typeof TABS)[number];

export default function AdminInventoryPage() {
  const session = useAuthSession();
  const [activeTab, setActiveTab] = useState<Tab>("Warehouses");

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading session...</p>
        </div>
      </AdminPageShell>
    );
  }

  if (!session.canViewAdmin) {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
        <div className="flex items-center justify-center min-h-[300px] flex-col gap-3">
          <p className="text-danger text-[1.1rem] font-bold font-[var(--font-display,Syne,sans-serif)]">Unauthorized</p>
          <p className="text-muted text-sm">You do not have permission to manage inventory.</p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
      {/* ── Tab bar ── */}
      <div className="flex gap-0 border-b border-line mb-5">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`py-2.5 px-5 bg-transparent border-none cursor-pointer text-base font-semibold transition-colors duration-200 border-b-2 ${activeTab === tab ? "text-brand border-b-brand" : "text-muted border-b-transparent"}`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      {session.apiClient && activeTab === "Warehouses" && (
        <WarehousesTab apiClient={session.apiClient} apiPrefix="/admin/inventory" isAdmin />
      )}
      {session.apiClient && activeTab === "Stock" && (
        <StockTab apiClient={session.apiClient} apiPrefix="/admin/inventory" isAdmin />
      )}
      {session.apiClient && activeTab === "Movements" && (
        <MovementsTab apiClient={session.apiClient} apiPrefix="/admin/inventory" isAdmin />
      )}
      {session.apiClient && activeTab === "Reservations" && (
        <ReservationsTab apiClient={session.apiClient} apiPrefix="/admin/inventory" />
      )}
    </AdminPageShell>
  );
}
