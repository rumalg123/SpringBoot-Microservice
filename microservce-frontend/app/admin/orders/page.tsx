"use client";

import Link from "next/link";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
import ExportButton from "../../components/ui/ExportButton";
import OrderBulkActionsBar from "../../components/admin/orders/OrderBulkActionsBar";
import OrderFiltersBar from "../../components/admin/orders/OrderFiltersBar";
import OrdersTable from "../../components/admin/orders/OrdersTable";
import StatusHistoryPanel from "../../components/admin/orders/StatusHistoryPanel";
import VendorOrdersPanel from "../../components/admin/orders/VendorOrdersPanel";
import useAdminOrders from "../../components/admin/orders/useAdminOrders";

const glassCard: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)",
  backdropFilter: "blur(16px)",
  border: "1px solid rgba(0,212,255,0.1)",
  borderRadius: "16px",
};

export default function AdminOrdersPage() {
  const vm = useAdminOrders();
  const {
    session,
    status,
    orders,
    currentPage,
    totalPages,
    totalElements,
    customerEmailInput,
    customerEmailFilter,
    filterBusy,
    ordersLoading,
    bulkSaving,
    bulkStatus,
    statusSavingId,
    selectedOrderIds,
    allCurrentSelected,
    someCurrentSelected,
    bulkInvalidSelectionCount,
    isVendorScopedActor,
    statusDrafts,
    historyOrderId,
    historyLoading,
    historyRows,
    historyActorTypeFilter,
    historySourceFilter,
    historyExpanded,
    historyActorTypeOptions,
    historySourceOptions,
    vendorOrdersOrderId,
    vendorOrdersLoading,
    vendorOrdersRows,
    vendorOrderStatusDrafts,
    vendorOrderStatusSavingId,
    vendorHistoryVendorOrderId,
    vendorHistoryLoading,
    vendorHistoryRows,
    vendorHistoryActorTypeFilter,
    vendorHistorySourceFilter,
    vendorHistoryExpanded,
    vendorHistoryActorTypeOptions,
    vendorHistorySourceOptions,
    setCustomerEmailInput,
    setBulkStatus,
    setSelectedOrderIds,
    setStatusDrafts,
    setVendorOrderStatusDrafts,
    setHistoryActorTypeFilter,
    setHistorySourceFilter,
    setHistoryExpanded,
    setVendorHistoryActorTypeFilter,
    setVendorHistorySourceFilter,
    setVendorHistoryExpanded,
    setHistoryOrderId,
    setHistoryRows,
    setVendorHistoryVendorOrderId,
    setVendorHistoryRows,
    applyFilter,
    clearFilter,
    goToPage,
    applyBulkStatus,
    toggleOrderSelection,
    toggleSelectAllCurrentPage,
    loadOrderHistory,
    refreshOpenOrderHistory,
    loadVendorOrders,
    refreshOpenVendorOrders,
    closeVendorOrdersPanel,
    updateOrderStatus,
    updateVendorOrderStatus,
    loadVendorOrderHistory,
    refreshOpenVendorOrderHistory,
  } = vm;

  if (session.status === "loading" || session.status === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }
  if (!session.isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
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

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <Link href="/admin/products">Admin</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <span className="breadcrumb-current">Orders</span>
        </nav>

        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.12em", color: "#00d4ff", margin: "0 0 4px" }}>ADMIN</p>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>All Orders</h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>Manage and inspect all customer orders</p>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
            <ExportButton
              apiClient={session.apiClient}
              endpoint="/admin/orders/export"
              filename={`orders-export-${new Date().toISOString().slice(0, 10)}.csv`}
              label="Export CSV"
              params={{
                format: "csv",
                ...(customerEmailFilter ? { customerEmail: customerEmailFilter } : {}),
              }}
            />
            <span style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", padding: "3px 14px", borderRadius: "20px", fontSize: "0.75rem", fontWeight: 800 }}>
              {totalElements} total
            </span>
          </div>
        </div>

        <section className="animate-rise" style={{ ...glassCard, padding: "20px" }}>
          <OrderFiltersBar
            customerEmailInput={customerEmailInput}
            filterBusy={filterBusy}
            onCustomerEmailInputChange={setCustomerEmailInput}
            onSubmit={applyFilter}
            onClear={clearFilter}
          />

          <OrderBulkActionsBar
            isVendorScopedActor={isVendorScopedActor}
            selectedCount={selectedOrderIds.length}
            bulkStatus={bulkStatus}
            bulkSaving={bulkSaving}
            statusSavingId={statusSavingId}
            bulkInvalidSelectionCount={bulkInvalidSelectionCount}
            onBulkStatusChange={setBulkStatus}
            onApplyBulkStatus={applyBulkStatus}
            onClearSelection={() => setSelectedOrderIds([])}
          />

          <VendorOrdersPanel
            orderId={vendorOrdersOrderId}
            loading={vendorOrdersLoading}
            rows={vendorOrdersRows}
            statusDrafts={vendorOrderStatusDrafts}
            statusSavingId={vendorOrderStatusSavingId}
            activeHistoryVendorOrderId={vendorHistoryVendorOrderId}
            historyLoading={vendorHistoryLoading}
            onDraftChange={(vendorOrderId, nextStatus) =>
              setVendorOrderStatusDrafts((prev) => ({ ...prev, [vendorOrderId]: nextStatus }))
            }
            onToggleHistory={loadVendorOrderHistory}
            onSaveStatus={updateVendorOrderStatus}
            onRefresh={refreshOpenVendorOrders}
            onClose={closeVendorOrdersPanel}
          />

          <StatusHistoryPanel
            title="Vendor Order Status History"
            entityId={vendorHistoryVendorOrderId}
            rows={vendorHistoryRows}
            loading={vendorHistoryLoading}
            actorTypeFilter={vendorHistoryActorTypeFilter}
            sourceFilter={vendorHistorySourceFilter}
            actorTypeOptions={vendorHistoryActorTypeOptions}
            sourceOptions={vendorHistorySourceOptions}
            expanded={vendorHistoryExpanded}
            accent="violet"
            onActorTypeFilterChange={setVendorHistoryActorTypeFilter}
            onSourceFilterChange={setVendorHistorySourceFilter}
            onToggleExpanded={() => setVendorHistoryExpanded((v) => !v)}
            onRefresh={refreshOpenVendorOrderHistory}
            onClose={() => { setVendorHistoryVendorOrderId(null); setVendorHistoryRows([]); setVendorHistoryExpanded(false); }}
          />

          <StatusHistoryPanel
            title="Order Status History"
            entityId={historyOrderId}
            rows={historyRows}
            loading={historyLoading}
            actorTypeFilter={historyActorTypeFilter}
            sourceFilter={historySourceFilter}
            actorTypeOptions={historyActorTypeOptions}
            sourceOptions={historySourceOptions}
            expanded={historyExpanded}
            accent="cyan"
            onActorTypeFilterChange={setHistoryActorTypeFilter}
            onSourceFilterChange={setHistorySourceFilter}
            onToggleExpanded={() => setHistoryExpanded((v) => !v)}
            onRefresh={refreshOpenOrderHistory}
            onClose={() => { setHistoryOrderId(null); setHistoryRows([]); setHistoryExpanded(false); }}
          />

          <OrdersTable
            orders={orders}
            isVendorScopedActor={isVendorScopedActor}
            ordersLoading={ordersLoading}
            bulkSaving={bulkSaving}
            statusSavingId={statusSavingId}
            vendorOrderStatusSavingId={vendorOrderStatusSavingId}
            selectedOrderIds={selectedOrderIds}
            allCurrentSelected={allCurrentSelected}
            someCurrentSelected={someCurrentSelected}
            statusDrafts={statusDrafts}
            historyOrderId={historyOrderId}
            historyLoading={historyLoading}
            vendorOrdersOrderId={vendorOrdersOrderId}
            vendorOrdersLoading={vendorOrdersLoading}
            onToggleSelectAll={toggleSelectAllCurrentPage}
            onToggleOrderSelection={toggleOrderSelection}
            onStatusDraftChange={(orderId, nextStatus) => setStatusDrafts((prev) => ({ ...prev, [orderId]: nextStatus }))}
            onToggleVendorOrders={loadVendorOrders}
            onToggleOrderHistory={loadOrderHistory}
            onSaveOrderStatus={updateOrderStatus}
            emptyFilterLabel={customerEmailFilter ? "Try a different customer email filter" : "No orders exist yet"}
          />

          <div style={{ marginTop: "16px" }}>
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={(p) => { void goToPage(p); }}
              disabled={ordersLoading}
            />
          </div>
          <p style={{ marginTop: "10px", fontSize: "0.72rem", color: "var(--muted-2)" }}>{status}</p>
        </section>
      </main>

      <Footer />
    </div>
  );
}
