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
    <div style={{ overflowX: "auto", borderRadius: "12px", border: "1px solid rgba(0,212,255,0.08)" }}>
      <table className="admin-table" style={{ minWidth: "980px" }}>
        <thead>
          <tr>
            <th style={{ width: "40px" }}>
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
              <td style={{ fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)" }}>{order.id}</td>
              <td>
                {(() => {
                  const chip = getStatusChip(order.status || "PENDING");
                  return (
                    <span
                      style={{
                        borderRadius: "20px",
                        background: chip.bg,
                        border: `1px solid ${chip.border}`,
                        color: chip.color,
                        padding: "2px 10px",
                        fontSize: "0.7rem",
                        fontWeight: 800,
                      }}
                    >
                      {(order.status || "PENDING").replaceAll("_", " ")}
                    </span>
                  );
                })()}
              </td>
              <td style={{ fontWeight: 600, color: "#c8c8e8" }}>{order.item}</td>
              <td>
                <span style={{ borderRadius: "20px", background: "rgba(0,212,255,0.08)", border: "1px solid rgba(0,212,255,0.2)", color: "#00d4ff", padding: "2px 10px", fontSize: "0.72rem", fontWeight: 800 }}>
                  {order.quantity} / {order.itemCount}
                </span>
              </td>
              <td style={{ color: "#c8c8e8", fontWeight: 700 }}>${Number(order.orderTotal ?? 0).toFixed(2)}</td>
              <td style={{ fontSize: "0.72rem", color: "var(--muted)" }}>{new Date(order.createdAt).toLocaleString()}</td>
              <td>
                <div style={{ display: "flex", gap: "8px", alignItems: "center", minWidth: isVendorScopedActor ? "220px" : "360px", flexWrap: "wrap" }}>
                  {!isVendorScopedActor && (
                    <select
                      value={statusDrafts[order.id] || order.status || "PENDING"}
                      onChange={(e) => onStatusDraftChange(order.id, e.target.value)}
                      disabled={ordersLoading || statusSavingId === order.id}
                      style={{
                        flex: 1,
                        minWidth: "150px",
                        borderRadius: "8px",
                        border: "1px solid rgba(0,212,255,0.15)",
                        background: "rgba(0,212,255,0.04)",
                        color: "#c8c8e8",
                        padding: "8px 10px",
                        fontSize: "0.75rem",
                      }}
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
                    style={{
                      padding: "8px 10px",
                      borderRadius: "8px",
                      border: "1px solid rgba(124,58,237,0.18)",
                      background: vendorOrdersOrderId === order.id ? "rgba(124,58,237,0.12)" : "rgba(124,58,237,0.08)",
                      color: "#c4b5fd",
                      fontSize: "0.72rem",
                      fontWeight: 700,
                    }}
                  >
                    {vendorOrdersLoading && vendorOrdersOrderId === order.id ? "Loading..." : vendorOrdersOrderId === order.id ? "Hide Vendor Orders" : "Vendor Orders"}
                  </button>
                  <button
                    type="button"
                    onClick={() => { void onToggleOrderHistory(order.id); }}
                    disabled={ordersLoading || bulkSaving || statusSavingId !== null || vendorOrderStatusSavingId !== null}
                    style={{
                      padding: "8px 10px",
                      borderRadius: "8px",
                      border: "1px solid rgba(255,255,255,0.08)",
                      background: historyOrderId === order.id ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.02)",
                      color: historyOrderId === order.id ? "#c8c8e8" : "var(--muted)",
                      fontSize: "0.72rem",
                      fontWeight: 700,
                    }}
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
                      style={{
                        padding: "8px 10px",
                        borderRadius: "8px",
                        border: "1px solid rgba(0,212,255,0.18)",
                        background: statusSavingId === order.id ? "rgba(0,212,255,0.12)" : "rgba(0,212,255,0.08)",
                        color: "#67e8f9",
                        fontSize: "0.72rem",
                        fontWeight: 700,
                        cursor: statusSavingId !== null ? "not-allowed" : "pointer",
                        opacity: (statusDrafts[order.id] || order.status) === order.status ? 0.55 : 1,
                      }}
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

