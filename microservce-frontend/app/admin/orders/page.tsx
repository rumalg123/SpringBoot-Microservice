"use client";

import Pagination from "../../components/Pagination";
import ExportButton from "../../components/ui/ExportButton";
import AdminPageShell from "../../components/ui/AdminPageShell";
import OrderBulkActionsBar from "../../components/admin/orders/OrderBulkActionsBar";
import OrderFiltersBar from "../../components/admin/orders/OrderFiltersBar";
import OrdersTable from "../../components/admin/orders/OrdersTable";
import StatusHistoryPanel from "../../components/admin/orders/StatusHistoryPanel";
import VendorOrdersPanel from "../../components/admin/orders/VendorOrdersPanel";
import useAdminOrders from "../../components/admin/orders/useAdminOrders";

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
      <div className="min-h-screen bg-bg grid place-items-center">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-muted text-base">Loading...</p>
        </div>
      </div>
    );
  }
  return (
    <AdminPageShell
      title="All Orders"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Orders" }]}
      actions={
        <div className="flex items-center gap-2.5 flex-wrap">
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
          <span className="bg-[linear-gradient(135deg,#00d4ff,#7c3aed)] text-white py-[3px] px-3.5 rounded-xl text-[0.75rem] font-extrabold">
            {totalElements} total
          </span>
        </div>
      }
    >

        <section className="animate-rise bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-[rgba(0,212,255,0.1)] rounded-lg p-5">
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

          <div className="mt-4">
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={(p) => { void goToPage(p); }}
              disabled={ordersLoading}
            />
          </div>
          <p className="mt-2.5 text-[0.72rem] text-muted-2">{status}</p>
        </section>
    </AdminPageShell>
  );
}
