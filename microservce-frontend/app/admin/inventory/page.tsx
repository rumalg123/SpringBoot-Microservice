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

export default function AdminInventoryPage() {
  const session = useAuthSession();
  const [activeTab, setActiveTab] = useState<Tab>("Warehouses");

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading session...</p>
        </div>
      </AdminPageShell>
    );
  }

  if (!session.canViewAdmin) {
    return (
      <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300, flexDirection: "column", gap: 12 }}>
          <p style={{ color: "var(--danger)", fontSize: "1.1rem", fontWeight: 700, fontFamily: "var(--font-display, Syne, sans-serif)" }}>Unauthorized</p>
          <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>You do not have permission to manage inventory.</p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell title="Inventory" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Inventory" }]}>
      {/* ── Tab bar ── */}
      <div style={{ display: "flex", gap: 0, borderBottom: "1px solid var(--line)", marginBottom: 20 }}>
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            style={{
              ...tabBtnBase,
              color: activeTab === tab ? "var(--brand)" : "var(--muted)",
              borderBottomColor: activeTab === tab ? "var(--brand)" : "transparent",
            }}
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
