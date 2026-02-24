"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import StatusBadge, { ORDER_STATUS_COLORS } from "../../components/ui/StatusBadge";

/* ── Types ── */

type VendorOrder = {
  id: string;
  orderId: string;
  vendorId: string;
  status: string;
  itemCount: number;
  quantity: number;
  orderTotal: number;
  currency: string;
  discountAmount: number | null;
  shippingAmount: number | null;
  platformFee: number | null;
  payoutAmount: number | null;
  trackingNumber: string | null;
  carrierCode: string | null;
  estimatedDeliveryDate: string | null;
  refundAmount: number | null;
  createdAt: string;
  updatedAt: string;
};

type PageResponse = {
  content: VendorOrder[];
  totalPages?: number;
  totalElements?: number;
  number?: number;
  size?: number;
  page?: { number?: number; size?: number; totalElements?: number; totalPages?: number };
};

type VendorOrderItem = {
  id: string;
  productId: string;
  productSku: string;
  item: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  discountAmount: number | null;
  fulfilledQuantity: number | null;
  cancelledQuantity: number | null;
};

type ShippingAddress = {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
};

type VendorOrderDetail = VendorOrder & {
  items: VendorOrderItem[];
  shippingAddress: ShippingAddress | null;
};

/* ── Helpers ── */

function extractErrorMessage(error: unknown): string {
  if (error && typeof error === "object") {
    const err = error as Record<string, unknown>;
    const response = err.response as Record<string, unknown> | undefined;
    if (response) {
      const data = response.data as Record<string, unknown> | undefined;
      if (data) {
        if (typeof data.error === "string" && data.error.trim()) return data.error;
        if (typeof data.message === "string" && data.message.trim()) return data.message;
      }
    }
    if (typeof err.message === "string" && err.message.trim()) return err.message;
  }
  if (error instanceof Error) return error.message;
  return "An unexpected error occurred";
}

function formatCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined) return "--";
  return `$${value.toFixed(2)}`;
}

function truncateId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) + "..." : id;
}

/* ── Inline styles ── */

const glassCard: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)",
  backdropFilter: "blur(16px)",
  border: "1px solid rgba(0,212,255,0.1)",
  borderRadius: "16px",
};


const tableHeaderStyle: React.CSSProperties = {
  background: "var(--surface-2)",
  padding: "10px 12px",
  fontSize: "0.7rem",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.08em",
  color: "var(--muted)",
  textAlign: "left",
  whiteSpace: "nowrap",
  borderBottom: "1px solid var(--line)",
};

const tableCellStyle: React.CSSProperties = {
  padding: "10px 12px",
  fontSize: "0.8rem",
  color: "var(--ink)",
  borderBottom: "1px solid var(--line)",
  whiteSpace: "nowrap",
};

const buttonBase: React.CSSProperties = {
  padding: "5px 14px",
  borderRadius: 8,
  fontSize: "0.75rem",
  fontWeight: 700,
  cursor: "pointer",
  border: "1px solid var(--line-bright)",
  background: "var(--brand-soft)",
  color: "var(--brand)",
  transition: "opacity 0.15s",
};

const paginationButton: React.CSSProperties = {
  padding: "6px 16px",
  borderRadius: 8,
  fontSize: "0.78rem",
  fontWeight: 700,
  cursor: "pointer",
  border: "1px solid var(--line)",
  background: "var(--surface-2)",
  color: "var(--ink)",
  transition: "opacity 0.15s",
};

const detailPanelStyle: React.CSSProperties = {
  background: "rgba(17,17,40,0.5)",
  border: "1px solid var(--line)",
  borderRadius: 12,
  padding: "16px 20px",
  marginTop: 4,
  marginBottom: 8,
};

const detailLabelStyle: React.CSSProperties = {
  fontSize: "0.68rem",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.08em",
  color: "var(--muted)",
  marginBottom: 6,
};

const detailValueStyle: React.CSSProperties = {
  fontSize: "0.82rem",
  color: "var(--ink)",
};

/* ── Status filter options ── */

const STATUS_OPTIONS = [
  "ALL",
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "CANCELLED",
  "REFUNDED",
  "FAILED",
  "PARTIALLY_FULFILLED",
  "ON_HOLD",
  "RETURNED",
];

/* ── Component ── */

export default function VendorOrdersPage() {
  const session = useAuthSession();

  /* ── State ── */
  const [orders, setOrders] = useState<VendorOrder[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [orderDetail, setOrderDetail] = useState<VendorOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);

  /* ── Fetch orders ── */
  const fetchOrders = useCallback(
    async (targetPage: number, status: string) => {
      if (!session.apiClient) return;
      setLoading(true);
      try {
        const params = new URLSearchParams();
        if (status !== "ALL") params.set("status", status);
        params.set("page", String(targetPage));
        params.set("size", "20");
        const res = await session.apiClient.get(`/orders/vendor/me?${params.toString()}`);
        const data = res.data as PageResponse;
        setOrders(data.content || []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
        setPage(data.number ?? data.page?.number ?? 0);
      } catch (err) {
        toast.error(extractErrorMessage(err));
        setOrders([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    },
    [session.apiClient],
  );

  /* ── Fetch order detail ── */
  const fetchOrderDetail = useCallback(
    async (vendorOrderId: string) => {
      if (!session.apiClient) return;
      setDetailLoading(true);
      try {
        const res = await session.apiClient.get(`/orders/vendor/me/${vendorOrderId}`);
        setOrderDetail(res.data as VendorOrderDetail);
      } catch (err) {
        toast.error(extractErrorMessage(err));
        setOrderDetail(null);
      } finally {
        setDetailLoading(false);
      }
    },
    [session.apiClient],
  );

  /* ── Initial load and filter/page changes ── */
  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) return;
    if (!session.isVendorAdmin && !session.isVendorStaff) return;
    void fetchOrders(page, statusFilter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.status, session.isAuthenticated, session.isVendorAdmin, session.isVendorStaff, session.apiClient]);

  /* ── Handlers ── */
  const handleStatusFilterChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setSelectedOrderId(null);
    setOrderDetail(null);
    void fetchOrders(0, newStatus);
  };

  const handlePageChange = (newPage: number) => {
    setSelectedOrderId(null);
    setOrderDetail(null);
    void fetchOrders(newPage, statusFilter);
  };

  const handleViewClick = (vendorOrderId: string) => {
    if (selectedOrderId === vendorOrderId) {
      setSelectedOrderId(null);
      setOrderDetail(null);
    } else {
      setSelectedOrderId(vendorOrderId);
      void fetchOrderDetail(vendorOrderId);
    }
  };

  /* ── Guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell
        title="Vendor Orders"
        breadcrumbs={[
          { label: "Vendor Portal", href: "/vendor" },
          { label: "Orders" },
        ]}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading session...</p>
        </div>
      </AdminPageShell>
    );
  }

  if (!session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <AdminPageShell
        title="Vendor Orders"
        breadcrumbs={[
          { label: "Vendor Portal", href: "/vendor" },
          { label: "Orders" },
        ]}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: 300,
            flexDirection: "column",
            gap: 12,
          }}
        >
          <p
            style={{
              color: "var(--danger, #ef4444)",
              fontSize: "1.1rem",
              fontWeight: 700,
              fontFamily: "var(--font-display, Syne, sans-serif)",
            }}
          >
            Unauthorized
          </p>
          <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>
            You do not have permission to view vendor orders.
          </p>
        </div>
      </AdminPageShell>
    );
  }

  /* ── Render ── */
  return (
    <AdminPageShell
      title="Vendor Orders"
      breadcrumbs={[
        { label: "Vendor Portal", href: "/vendor" },
        { label: "Orders" },
      ]}
      actions={
        <span
          style={{
            background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
            color: "#fff",
            padding: "3px 14px",
            borderRadius: 20,
            fontSize: "0.75rem",
            fontWeight: 800,
          }}
        >
          {totalElements} total
        </span>
      }
    >
      {/* ── Filter bar ── */}
      <div style={{ marginBottom: 16, display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <label
          style={{
            fontSize: "0.72rem",
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.08em",
            color: "var(--muted)",
          }}
        >
          Status
        </label>
        <select
          value={statusFilter}
          onChange={(e) => handleStatusFilterChange(e.target.value)}
          className="filter-select"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === "ALL" ? "All Statuses" : s.replace(/_/g, " ")}
            </option>
          ))}
        </select>
      </div>

      {/* ── Main card ── */}
      <section style={{ ...glassCard, padding: "20px", overflow: "hidden" }}>
        {loading ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200 }}>
            <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading orders...</p>
          </div>
        ) : orders.length === 0 ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 200, flexDirection: "column", gap: 8 }}>
            <p style={{ color: "var(--muted)", fontSize: "0.95rem", fontWeight: 600 }}>No orders found</p>
            <p style={{ color: "var(--muted)", fontSize: "0.78rem" }}>
              {statusFilter !== "ALL"
                ? "Try selecting a different status filter."
                : "Orders placed to your store will appear here."}
            </p>
          </div>
        ) : (
          <>
            {/* ── Orders table ── */}
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr>
                    <th style={tableHeaderStyle}>Order ID</th>
                    <th style={tableHeaderStyle}>Items</th>
                    <th style={tableHeaderStyle}>Qty</th>
                    <th style={tableHeaderStyle}>Total</th>
                    <th style={tableHeaderStyle}>Payout</th>
                    <th style={tableHeaderStyle}>Status</th>
                    <th style={tableHeaderStyle}>Date</th>
                    <th style={{ ...tableHeaderStyle, textAlign: "center" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order) => {
                    const isExpanded = selectedOrderId === order.id;
                    return (
                      <tr key={order.id} style={{ cursor: "default" }}>
                        <td style={tableCellStyle} colSpan={8}>
                          {/* Row content + optional detail panel */}
                          <div>
                            {/* Order row */}
                            <div
                              style={{
                                display: "grid",
                                gridTemplateColumns: "1.4fr 0.6fr 0.5fr 1fr 1fr 1.1fr 1fr 0.8fr",
                                alignItems: "center",
                                gap: 0,
                                margin: "-10px -12px",
                                padding: "10px 12px",
                              }}
                            >
                              <span
                                style={{
                                  fontFamily: "monospace",
                                  fontSize: "0.78rem",
                                  color: "var(--brand)",
                                }}
                                title={order.orderId}
                              >
                                {truncateId(order.orderId)}
                              </span>
                              <span style={{ fontSize: "0.8rem" }}>{order.itemCount}</span>
                              <span style={{ fontSize: "0.8rem" }}>{order.quantity}</span>
                              <span style={{ fontSize: "0.8rem", fontWeight: 600 }}>
                                {formatCurrency(order.orderTotal)}
                              </span>
                              <span style={{ fontSize: "0.8rem", fontWeight: 600, color: "var(--success, #22c55e)" }}>
                                {formatCurrency(order.payoutAmount)}
                              </span>
                              <span>
                                <StatusBadge value={order.status} colorMap={ORDER_STATUS_COLORS} />
                              </span>
                              <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>
                                {new Date(order.createdAt).toLocaleDateString()}
                              </span>
                              <span style={{ textAlign: "center" }}>
                                <button
                                  onClick={() => handleViewClick(order.id)}
                                  style={{
                                    ...buttonBase,
                                    background: isExpanded ? "var(--brand)" : "var(--brand-soft)",
                                    color: isExpanded ? "#fff" : "var(--brand)",
                                  }}
                                >
                                  {isExpanded ? "Close" : "View"}
                                </button>
                              </span>
                            </div>

                            {/* ── Detail panel ── */}
                            {isExpanded && (
                              <div style={detailPanelStyle}>
                                {detailLoading ? (
                                  <p style={{ color: "var(--muted)", fontSize: "0.82rem", textAlign: "center", padding: "16px 0" }}>
                                    Loading order details...
                                  </p>
                                ) : orderDetail ? (
                                  <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
                                    {/* ── Summary row ── */}
                                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 16 }}>
                                      <div>
                                        <p style={detailLabelStyle}>Vendor Order ID</p>
                                        <p style={{ ...detailValueStyle, fontFamily: "monospace", fontSize: "0.74rem" }}>{orderDetail.id}</p>
                                      </div>
                                      <div>
                                        <p style={detailLabelStyle}>Order Total</p>
                                        <p style={detailValueStyle}>{formatCurrency(orderDetail.orderTotal)} {orderDetail.currency}</p>
                                      </div>
                                      <div>
                                        <p style={detailLabelStyle}>Discount</p>
                                        <p style={detailValueStyle}>{formatCurrency(orderDetail.discountAmount)}</p>
                                      </div>
                                      <div>
                                        <p style={detailLabelStyle}>Shipping</p>
                                        <p style={detailValueStyle}>{formatCurrency(orderDetail.shippingAmount)}</p>
                                      </div>
                                      <div>
                                        <p style={detailLabelStyle}>Platform Fee</p>
                                        <p style={detailValueStyle}>{formatCurrency(orderDetail.platformFee)}</p>
                                      </div>
                                      <div>
                                        <p style={detailLabelStyle}>Payout</p>
                                        <p style={{ ...detailValueStyle, color: "var(--success, #22c55e)", fontWeight: 700 }}>
                                          {formatCurrency(orderDetail.payoutAmount)}
                                        </p>
                                      </div>
                                    </div>

                                    {/* ── Tracking info ── */}
                                    {(orderDetail.trackingNumber || orderDetail.carrierCode || orderDetail.estimatedDeliveryDate) && (
                                      <div>
                                        <p style={{ ...detailLabelStyle, marginBottom: 10 }}>Tracking Information</p>
                                        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 12 }}>
                                          {orderDetail.trackingNumber && (
                                            <div>
                                              <p style={{ ...detailLabelStyle, fontSize: "0.64rem" }}>Tracking Number</p>
                                              <p style={{ ...detailValueStyle, fontFamily: "monospace", fontSize: "0.78rem" }}>
                                                {orderDetail.trackingNumber}
                                              </p>
                                            </div>
                                          )}
                                          {orderDetail.carrierCode && (
                                            <div>
                                              <p style={{ ...detailLabelStyle, fontSize: "0.64rem" }}>Carrier</p>
                                              <p style={detailValueStyle}>{orderDetail.carrierCode}</p>
                                            </div>
                                          )}
                                          {orderDetail.estimatedDeliveryDate && (
                                            <div>
                                              <p style={{ ...detailLabelStyle, fontSize: "0.64rem" }}>Est. Delivery</p>
                                              <p style={detailValueStyle}>
                                                {new Date(orderDetail.estimatedDeliveryDate).toLocaleDateString()}
                                              </p>
                                            </div>
                                          )}
                                        </div>
                                      </div>
                                    )}

                                    {/* ── Refund info ── */}
                                    {orderDetail.refundAmount !== null && orderDetail.refundAmount > 0 && (
                                      <div>
                                        <p style={detailLabelStyle}>Refund Amount</p>
                                        <p style={{ ...detailValueStyle, color: "#f87171", fontWeight: 700 }}>
                                          {formatCurrency(orderDetail.refundAmount)}
                                        </p>
                                      </div>
                                    )}

                                    {/* ── Items table ── */}
                                    <div>
                                      <p style={{ ...detailLabelStyle, marginBottom: 10 }}>Order Items</p>
                                      {orderDetail.items.length === 0 ? (
                                        <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>No items.</p>
                                      ) : (
                                        <div style={{ overflowX: "auto" }}>
                                          <table style={{ width: "100%", borderCollapse: "collapse" }}>
                                            <thead>
                                              <tr>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Product</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>SKU</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Qty</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Unit Price</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Line Total</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Discount</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Fulfilled</th>
                                                <th style={{ ...tableHeaderStyle, fontSize: "0.65rem" }}>Cancelled</th>
                                              </tr>
                                            </thead>
                                            <tbody>
                                              {orderDetail.items.map((item) => (
                                                <tr key={item.id}>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem", maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis" }}>
                                                    {item.item}
                                                  </td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.74rem", fontFamily: "monospace", color: "var(--muted)" }}>
                                                    {item.productSku}
                                                  </td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem" }}>{item.quantity}</td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem" }}>{formatCurrency(item.unitPrice)}</td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem", fontWeight: 600 }}>
                                                    {formatCurrency(item.lineTotal)}
                                                  </td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem" }}>
                                                    {formatCurrency(item.discountAmount)}
                                                  </td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem" }}>
                                                    {item.fulfilledQuantity ?? "--"}
                                                  </td>
                                                  <td style={{ ...tableCellStyle, fontSize: "0.78rem" }}>
                                                    {item.cancelledQuantity ?? "--"}
                                                  </td>
                                                </tr>
                                              ))}
                                            </tbody>
                                          </table>
                                        </div>
                                      )}
                                    </div>

                                    {/* ── Shipping address ── */}
                                    {orderDetail.shippingAddress && (
                                      <div>
                                        <p style={{ ...detailLabelStyle, marginBottom: 10 }}>Shipping Address</p>
                                        <div
                                          style={{
                                            background: "rgba(0,212,255,0.04)",
                                            border: "1px solid var(--line)",
                                            borderRadius: 8,
                                            padding: "12px 16px",
                                          }}
                                        >
                                          <p style={{ fontSize: "0.82rem", fontWeight: 700, color: "var(--ink)", marginBottom: 4 }}>
                                            {orderDetail.shippingAddress.recipientName}
                                            {orderDetail.shippingAddress.label && (
                                              <span style={{ fontSize: "0.68rem", color: "var(--muted)", fontWeight: 500, marginLeft: 8 }}>
                                                ({orderDetail.shippingAddress.label})
                                              </span>
                                            )}
                                          </p>
                                          <p style={{ fontSize: "0.78rem", color: "var(--muted)", lineHeight: 1.5 }}>
                                            {orderDetail.shippingAddress.line1}
                                            {orderDetail.shippingAddress.line2 && (
                                              <>, {orderDetail.shippingAddress.line2}</>
                                            )}
                                            <br />
                                            {orderDetail.shippingAddress.city}, {orderDetail.shippingAddress.state}{" "}
                                            {orderDetail.shippingAddress.postalCode}
                                            <br />
                                            {orderDetail.shippingAddress.countryCode}
                                          </p>
                                          {orderDetail.shippingAddress.phone && (
                                            <p style={{ fontSize: "0.75rem", color: "var(--muted)", marginTop: 4 }}>
                                              Phone: {orderDetail.shippingAddress.phone}
                                            </p>
                                          )}
                                        </div>
                                      </div>
                                    )}
                                  </div>
                                ) : (
                                  <p style={{ color: "var(--muted)", fontSize: "0.82rem", textAlign: "center", padding: "16px 0" }}>
                                    Failed to load order details.
                                  </p>
                                )}
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* ── Pagination ── */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                marginTop: 16,
                flexWrap: "wrap",
                gap: 12,
              }}
            >
              <p style={{ fontSize: "0.75rem", color: "var(--muted)" }}>
                Page {page + 1} of {totalPages} ({totalElements} orders)
              </p>
              <div style={{ display: "flex", gap: 8 }}>
                <button
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page <= 0}
                  style={{
                    ...paginationButton,
                    opacity: page <= 0 ? 0.4 : 1,
                    cursor: page <= 0 ? "not-allowed" : "pointer",
                  }}
                >
                  Prev
                </button>
                <button
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page + 1 >= totalPages}
                  style={{
                    ...paginationButton,
                    opacity: page + 1 >= totalPages ? 0.4 : 1,
                    cursor: page + 1 >= totalPages ? "not-allowed" : "pointer",
                  }}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
      </section>
    </AdminPageShell>
  );
}
