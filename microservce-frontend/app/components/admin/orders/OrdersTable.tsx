"use client";

import { canTransitionOrderStatus, getAllowedNextStatuses, getStatusChip, ORDER_STATUSES } from "./orderStatus";
import { AdminOrder } from "./types";

type Props = {
  orders: AdminOrder[];
  isVendorScopedActor: boolean;
  ordersLoading: boolean;
  bulkSaving: boolean;
  statusSavingId: string | null;
  vendorOrderStatusSavingId: string | null;
  selectedOrderIds: string[];
  allCurrentSelected: boolean;
  someCurrentSelected: boolean;
  statusDrafts: Record<string, string>;
  historyOrderId: string | null;
  historyLoading: boolean;
  vendorOrdersOrderId: string | null;
  vendorOrdersLoading: boolean;
  onToggleSelectAll: (checked: boolean) => void;
  onToggleOrderSelection: (orderId: string, checked: boolean) => void;
  onStatusDraftChange: (orderId: string, status: string) => void;
  onToggleVendorOrders: (orderId: string) => void | Promise<void>;
  onToggleOrderHistory: (orderId: string) => void | Promise<void>;
  onSaveOrderStatus: (orderId: string) => void | Promise<void>;
  emptyFilterLabel: string;
};

export default function OrdersTable({
  orders,
  isVendorScopedActor,
  ordersLoading,
  bulkSaving,
  statusSavingId,
  vendorOrderStatusSavingId,
  selectedOrderIds,
  allCurrentSelected,
  someCurrentSelected,
  statusDrafts,
  historyOrderId,
  historyLoading,
  vendorOrdersOrderId,
  vendorOrdersLoading,
  onToggleSelectAll,
  onToggleOrderSelection,
  onStatusDraftChange,
  onToggleVendorOrders,
  onToggleOrderHistory,
  onSaveOrderStatus,
  emptyFilterLabel,
}: Props) {
  return (
    <div className="overflow-x-auto rounded-xl border border-[rgba(0,212,255,0.08)]">
      <table className="admin-table min-w-[980px]">
        <thead>
          <tr>
            <th className="w-[40px]">
              <input
                type="checkbox"
                checked={allCurrentSelected}
                ref={(el) => {
                  if (el) el.indeterminate = someCurrentSelected;
                }}
                onChange={(e) => onToggleSelectAll(e.target.checked)}
                disabled={isVendorScopedActor || orders.length === 0 || ordersLoading || bulkSaving || statusSavingId !== null}
              />
            </th>
            <th>Order ID</th>
            <th>Status</th>
            <th>Item</th>
            <th>Qty / Items</th>
            <th>Total</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {orders.length === 0 && (
            <tr>
              <td colSpan={8}>
                <div className="empty-state">
                  <div className="empty-state-icon">
                    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v11m0 0H3m6 0h6m0 0V5m0 11v3a2 2 0 0 1-2 2H9m6-5h3a2 2 0 0 1 2 2v3" />
                    </svg>
                  </div>
                  <p className="empty-state-title">No orders found</p>
                  <p className="empty-state-desc">{emptyFilterLabel}</p>
                </div>
              </td>
            </tr>
          )}
          {orders.map((order) => (
            <tr key={order.id}>
              <td>
                <input
                  type="checkbox"
                  checked={selectedOrderIds.includes(order.id)}
                  onChange={(e) => onToggleOrderSelection(order.id, e.target.checked)}
                  disabled={isVendorScopedActor || ordersLoading || bulkSaving || statusSavingId !== null}
                />
              </td>
              <td className="font-mono text-[0.65rem] text-muted-2">{order.id}</td>
              <td>
                {(() => {
                  const chip = getStatusChip(order.status || "PENDING");
                  return (
                    <span
                      className="rounded-full px-2.5 py-0.5 text-[0.7rem] font-extrabold"
                      style={{
                        background: chip.bg,
                        border: `1px solid ${chip.border}`,
                        color: chip.color,
                      }}
                    >
                      {(order.status || "PENDING").replaceAll("_", " ")}
                    </span>
                  );
                })()}
              </td>
              <td className="font-semibold text-[#c8c8e8]">{order.item}</td>
              <td>
                <span className="rounded-full border border-[rgba(0,212,255,0.2)] bg-brand-soft px-2.5 py-0.5 text-xs font-extrabold text-[#00d4ff]">
                  {order.quantity} / {order.itemCount}
                </span>
              </td>
              <td className="font-bold text-[#c8c8e8]">${Number(order.orderTotal ?? 0).toFixed(2)}</td>
              <td className="text-xs text-muted">{new Date(order.createdAt).toLocaleString()}</td>
              <td>
                <div className={`flex flex-wrap items-center gap-2 ${isVendorScopedActor ? "min-w-[220px]" : "min-w-[360px]"}`}>
                  {!isVendorScopedActor && (
                    <select
                      value={statusDrafts[order.id] || order.status || "PENDING"}
                      onChange={(e) => onStatusDraftChange(order.id, e.target.value)}
                      disabled={ordersLoading || statusSavingId === order.id}
                      className="min-w-[150px] flex-1 rounded-md border border-[rgba(0,212,255,0.15)] bg-[rgba(0,212,255,0.04)] px-2.5 py-2 text-xs text-[#c8c8e8]"
                    >
                      {ORDER_STATUSES.map((s) => (
                        <option key={s} value={s} disabled={!getAllowedNextStatuses(order.status).includes(s)}>
                          {s.replaceAll("_", " ")}
                        </option>
                      ))}
                    </select>
                  )}
                  <button
                    type="button"
                    onClick={() => { void onToggleVendorOrders(order.id); }}
                    disabled={ordersLoading || bulkSaving || statusSavingId !== null || vendorOrderStatusSavingId !== null}
                    className={`rounded-md border border-[rgba(124,58,237,0.18)] px-2.5 py-2 text-xs font-bold text-[#c4b5fd] ${
                      vendorOrdersOrderId === order.id ? "bg-[rgba(124,58,237,0.12)]" : "bg-[rgba(124,58,237,0.08)]"
                    }`}
                  >
                    {vendorOrdersLoading && vendorOrdersOrderId === order.id ? "Loading..." : vendorOrdersOrderId === order.id ? "Hide Vendor Orders" : "Vendor Orders"}
                  </button>
                  <button
                    type="button"
                    onClick={() => { void onToggleOrderHistory(order.id); }}
                    disabled={ordersLoading || bulkSaving || statusSavingId !== null || vendorOrderStatusSavingId !== null}
                    className={`rounded-md border border-white/[0.08] px-2.5 py-2 text-xs font-bold ${
                      historyOrderId === order.id ? "bg-white/5 text-[#c8c8e8]" : "bg-white/[0.02] text-muted"
                    }`}
                  >
                    {historyLoading && historyOrderId === order.id ? "Loading..." : historyOrderId === order.id ? "Hide" : "History"}
                  </button>
                  {!isVendorScopedActor && (
                    <button
                      type="button"
                      onClick={() => { void onSaveOrderStatus(order.id); }}
                      disabled={
                        ordersLoading
                        || statusSavingId !== null
                        || (statusDrafts[order.id] || order.status) === order.status
                        || !canTransitionOrderStatus(order.status, statusDrafts[order.id] || order.status)
                      }
                      className={`rounded-md border border-[rgba(0,212,255,0.18)] px-2.5 py-2 text-xs font-bold text-[#67e8f9] ${
                        statusSavingId === order.id ? "bg-[rgba(0,212,255,0.12)]" : "bg-[rgba(0,212,255,0.08)]"
                      } ${statusSavingId !== null ? "cursor-not-allowed" : "cursor-pointer"} ${
                        (statusDrafts[order.id] || order.status) === order.status ? "opacity-55" : "opacity-100"
                      }`}
                      title={
                        canTransitionOrderStatus(order.status, statusDrafts[order.id] || order.status)
                          ? "Save order status"
                          : `Invalid transition: ${order.status} -> ${statusDrafts[order.id] || order.status}`
                      }
                    >
                      {statusSavingId === order.id ? "Saving..." : "Save"}
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
