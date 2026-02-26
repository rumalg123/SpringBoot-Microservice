"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { PayHereFormData, submitToPayHere } from "../../lib/payhere";
import EmptyState from "../components/ui/EmptyState";
import { money } from "../../lib/format";
import type { Order, OrderDetail } from "../../lib/types/order";
import type { CustomerAddress } from "../../lib/types/customer";
import type { ProductSummary } from "../../lib/types/product";
import type { PagedResponse } from "../../lib/types/pagination";
import { API_BASE, ORDER_STATUS_COLORS as STATUS_COLORS } from "../../lib/constants";

const CREATING_STATUS = "Creating purchase...";
const CANCELLABLE = new Set(["PENDING", "PAYMENT_PENDING", "PAYMENT_FAILED", "CONFIRMED"]);

type PagedOrder = { content: Order[] };
type ProductPageResponse = { content: ProductSummary[] };
type PaymentInfo = { status: string; paymentMethod: string; cardNoMasked: string | null; paidAt: string | null };

export default function OrdersPage() {
  const router = useRouter();
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, canViewAdmin, ensureCustomer, apiClient,
    resendVerificationEmail, profile, logout, emailVerified } = session;

  const [orders, setOrders] = useState<Order[]>([]);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [status, setStatus] = useState("Loading your purchases...");
  const [form, setForm] = useState({ productId: "", quantity: 1, shippingAddressId: "", billingAddressId: "" });
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true);
  const [selectedId, setSelectedId] = useState("");
  const [selectedDetail, setSelectedDetail] = useState<OrderDetail | null>(null);
  const [resendingVerification, setResendingVerification] = useState(false);
  const [detailLoadingTarget, setDetailLoadingTarget] = useState<string | null>(null);
  const [payingOrderId, setPayingOrderId] = useState<string | null>(null);
  const [cancellingOrderId, setCancellingOrderId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo | null>(null);

  const loadOrders = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get<PagedOrder>("/orders/me");
    setOrders(res.data.content ?? []);
  }, [apiClient]);

  const loadProducts = useCallback(async () => {
    const apiBase = API_BASE;
    const res = await fetch(`${apiBase}/products?page=0&size=100`, { cache: "no-store" });
    if (!res.ok) return;
    const data = (await res.json()) as ProductPageResponse;
    setProducts((data.content || []).filter((p) => p.productType !== "PARENT"));
  }, []);

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get<CustomerAddress[]>("/customers/me/addresses");
    const loaded = res.data ?? [];
    setAddresses(loaded);
    const defaultShipping = loaded.find((a) => a.defaultShipping)?.id || loaded[0]?.id || "";
    const defaultBilling = loaded.find((a) => a.defaultBilling)?.id || defaultShipping;
    setForm((old) => ({ ...old, shippingAddressId: old.shippingAddressId || defaultShipping, billingAddressId: old.billingAddressId || defaultBilling }));
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    const run = async () => {
      try {
        await ensureCustomer();
        await loadOrders(); await loadProducts(); await loadAddresses();
        setStatus("Purchase history loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load purchases.");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, ensureCustomer, loadOrders, loadProducts, loadAddresses]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setForm((old) => ({ ...old, billingAddressId: old.shippingAddressId }));
  }, [billingSameAsShipping, form.shippingAddressId]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiClient || status === CREATING_STATUS) return;
    const shippingAddressId = form.shippingAddressId.trim();
    const billingAddressId = (billingSameAsShipping ? form.shippingAddressId : form.billingAddressId).trim();
    if (!shippingAddressId || !billingAddressId) { toast.error("Select shipping and billing addresses"); return; }
    setStatus(CREATING_STATUS);
    try {
      await apiClient.post("/orders/me", { productId: form.productId, quantity: Number(form.quantity), shippingAddressId, billingAddressId });
      setForm((old) => ({ ...old, productId: "", quantity: 1 }));
      await loadOrders();
      setStatus("Purchase created.");
      toast.success("Order placed successfully!");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Purchase creation failed.");
      toast.error(err instanceof Error ? err.message : "Purchase creation failed");
    }
  };

  const loadDetail = async (orderId?: string) => {
    const targetId = (orderId || selectedId).trim();
    if (!apiClient || !targetId || detailLoadingTarget) return;
    setDetailLoadingTarget(targetId);
    setPaymentInfo(null);
    try {
      const [orderRes, paymentRes] = await Promise.all([
        apiClient.get<OrderDetail>(`/orders/me/${targetId}`),
        apiClient.get<PaymentInfo>(`/payments/me/order/${targetId}`).catch(() => null),
      ]);
      setSelectedDetail(orderRes.data);
      setSelectedId(targetId);
      if (paymentRes?.data) {
        setPaymentInfo(paymentRes.data);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to load detail");
    } finally { setDetailLoadingTarget(null); }
  };

  const resendVerification = async () => {
    if (resendingVerification) return;
    setResendingVerification(true);
    try {
      await resendVerificationEmail();
      toast.success("Verification email sent");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    } finally { setResendingVerification(false); }
  };

  const payNow = async (orderId: string) => {
    if (!apiClient || payingOrderId) return;
    setPayingOrderId(orderId);
    try {
      const res = await apiClient.post<PayHereFormData>("/payments/me/initiate", { orderId });
      submitToPayHere(res.data);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to initiate payment");
      setPayingOrderId(null);
    }
  };

  const cancelOrder = async (orderId: string) => {
    if (!apiClient || cancellingOrderId) return;
    setCancellingOrderId(orderId);
    try {
      await apiClient.post(`/orders/me/${orderId}/cancel`, {
        reason: cancelReason.trim() || undefined,
      });
      setCancelReason("");
      setCancellingOrderId(null);
      await loadOrders();
      if (selectedDetail?.id === orderId) {
        void loadDetail(orderId);
      }
      toast.success("Order cancelled");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to cancel order");
      setCancellingOrderId(null);
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }
  if (!isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Email verification */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl px-4 py-3 text-sm"
            style={{ border: "1px solid var(--warning-border)", background: "var(--warning-soft)", color: "var(--warning-text)" }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p style={{ fontWeight: 700, margin: 0 }}>Email Not Verified</p>
              <p style={{ fontSize: "0.75rem", opacity: 0.8, margin: 0 }}>Orders are blocked until you verify your email.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              style={{
                background: "rgba(245,158,11,0.15)", border: "1px solid rgba(245,158,11,0.35)",
                color: "var(--warning-text)", padding: "6px 14px", borderRadius: "8px",
                fontSize: "0.75rem", fontWeight: 700, cursor: resendingVerification ? "not-allowed" : "pointer",
              }}
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">My Orders</span>
        </nav>

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>
              My Orders
            </h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>Track your orders and manage purchases</p>
          </div>
          <Link
            href="/products"
            className="no-underline"
            style={{
              padding: "9px 18px", borderRadius: "10px",
              background: "var(--gradient-brand)",
              color: "#fff", fontSize: "0.8rem", fontWeight: 700,
              boxShadow: "0 0 14px var(--line-bright)",
            }}
          >
            Continue Shopping
          </Link>
        </div>

        <div style={{ display: "grid", gap: "20px", gridTemplateColumns: "1.2fr 0.8fr" }}>
          {/* Order History */}
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {/* Header */}
            <div
              className="glass-card"
              style={{
                padding: "14px 18px",
                display: "flex", alignItems: "center", justifyContent: "space-between",
              }}
            >
              <h2 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1rem", color: "#fff", margin: 0 }}>
                Order History
              </h2>
              <span
                style={{
                  background: "var(--gradient-brand)",
                  color: "#fff", padding: "3px 12px", borderRadius: "20px",
                  fontSize: "0.75rem", fontWeight: 800,
                }}
              >
                {orders.length} orders
              </span>
            </div>

            {orders.length === 0 && (
              <EmptyState
                icon={
                  <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v11m0 0H3m6 0h6m0 0V5m0 11v3a2 2 0 0 1-2 2H9m6-5h3a2 2 0 0 1 2 2v3" />
                  </svg>
                }
                title="No orders yet"
                description="Place your first order to get started!"
                actionLabel="Browse Products"
                onAction={() => router.push("/products")}
              />
            )}

            {orders.map((order, idx) => {
              const sc = STATUS_COLORS[order.status] || STATUS_COLORS.CONFIRMED;
              return (
                <article
                  key={order.id}
                  className="animate-rise glass-card"
                  style={{ padding: "16px 20px", animationDelay: `${idx * 50}ms` }}
                >
                  <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "10px" }}>
                    <div style={{ flex: 1 }}>
                      <p style={{ fontWeight: 700, color: "#fff", fontSize: "0.9rem", margin: 0 }}>{order.item}</p>
                      <p style={{ margin: "4px 0 0", fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)" }}>
                        ID: {order.id}
                      </p>
                      <div style={{ marginTop: "8px", display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                        <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: sc.bg, border: `1px solid ${sc.border}`, color: sc.color }}>
                          {order.status || "PLACED"}
                        </span>
                        <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: "var(--brand-soft)", border: "1px solid var(--line-bright)", color: "var(--brand)" }}>
                          {money(order.orderTotal || order.subtotal)}
                        </span>
                        {order.couponCode && (
                          <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: "var(--success-soft)", border: "1px solid var(--success-glow)", color: "var(--success)" }}>
                            🏷 {order.couponCode}
                          </span>
                        )}
                        <span style={{ fontSize: "0.72rem", color: "var(--muted)" }}>
                          {new Date(order.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" })}
                        </span>
                      </div>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: "6px", alignItems: "flex-end" }}>
                      {(order.status === "PAYMENT_PENDING" || order.status === "PAYMENT_FAILED") && (
                        <button
                          onClick={() => { void payNow(order.id); }}
                          disabled={Boolean(payingOrderId)}
                          style={{
                            padding: "7px 14px", borderRadius: "9px", border: "none",
                            background: "var(--gradient-brand)", color: "#fff",
                            fontSize: "0.75rem", fontWeight: 700,
                            cursor: payingOrderId ? "not-allowed" : "pointer",
                            opacity: payingOrderId && payingOrderId !== order.id ? 0.5 : 1,
                            whiteSpace: "nowrap",
                            display: "inline-flex", alignItems: "center", gap: "6px",
                          }}
                        >
                          {payingOrderId === order.id && <span className="spinner-sm" />}
                          {payingOrderId === order.id ? "Redirecting..." : "Pay Now"}
                        </button>
                      )}
                      <button
                        onClick={() => { void loadDetail(order.id); }}
                        disabled={Boolean(detailLoadingTarget)}
                        style={{
                          padding: "7px 14px", borderRadius: "9px",
                          border: "1px solid var(--line-bright)", background: "var(--brand-soft)",
                          color: "var(--brand)", fontSize: "0.75rem", fontWeight: 700,
                          cursor: detailLoadingTarget ? "not-allowed" : "pointer",
                          opacity: detailLoadingTarget && detailLoadingTarget !== order.id ? 0.5 : 1,
                          whiteSpace: "nowrap",
                        }}
                      >
                        {detailLoadingTarget === order.id ? "Loading..." : "View Details"}
                      </button>
                      {CANCELLABLE.has(order.status) && (
                        <>
                          {cancellingOrderId === order.id ? (
                            <div style={{ display: "flex", gap: "4px" }}>
                              <input
                                value={cancelReason}
                                onChange={(e) => setCancelReason(e.target.value)}
                                placeholder="Reason (optional)"
                                className="form-input"
                                style={{ fontSize: "0.7rem", padding: "5px 8px", width: "130px" }}
                                maxLength={240}
                              />
                              <button
                                onClick={() => { void cancelOrder(order.id); }}
                                style={{
                                  padding: "5px 10px", borderRadius: "8px", border: "none",
                                  background: "var(--danger)", color: "#fff",
                                  fontSize: "0.7rem", fontWeight: 700, cursor: "pointer",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                Confirm
                              </button>
                              <button
                                onClick={() => { setCancellingOrderId(null); setCancelReason(""); }}
                                style={{
                                  padding: "5px 8px", borderRadius: "8px",
                                  border: "1px solid var(--line-bright)", background: "transparent",
                                  color: "var(--muted)", fontSize: "0.7rem", fontWeight: 700,
                                  cursor: "pointer",
                                }}
                              >
                                No
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setCancellingOrderId(order.id)}
                              disabled={Boolean(cancellingOrderId)}
                              style={{
                                padding: "7px 14px", borderRadius: "9px",
                                border: "1px solid rgba(239,68,68,0.25)", background: "rgba(239,68,68,0.06)",
                                color: "var(--danger)", fontSize: "0.75rem", fontWeight: 700,
                                cursor: cancellingOrderId ? "not-allowed" : "pointer",
                                whiteSpace: "nowrap",
                              }}
                            >
                              Cancel Order
                            </button>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>

          {/* Right Sidebar */}
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            {/* Quick Purchase */}
            <section className="glass-card" style={{ padding: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                </svg>
                <h3 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1rem", color: "#fff", margin: 0 }}>
                  Quick Purchase
                </h3>
              </div>

              {addresses.length === 0 && (
                <div style={{ borderRadius: "10px", border: "1px solid var(--warning-border)", background: "var(--warning-soft)", padding: "10px 12px", fontSize: "0.78rem", color: "var(--warning-text)", marginBottom: "12px" }}>
                  Add at least one address in your profile before placing an order.{" "}
                  <Link href="/profile" style={{ color: "var(--brand)", fontWeight: 700 }}>Open Profile</Link>
                </div>
              )}

              <form style={{ display: "flex", flexDirection: "column", gap: "10px" }} onSubmit={createOrder}>
                <select
                  value={form.productId}
                  onChange={(e) => setForm((old) => ({ ...old, productId: e.target.value }))}
                  disabled={status === CREATING_STATUS || addresses.length === 0}
                  className="form-select"
                  required
                >
                  <option value="">Select product...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
                  ))}
                </select>
                <select
                  value={form.shippingAddressId}
                  onChange={(e) => {
                    const v = e.target.value;
                    setForm((old) => ({ ...old, shippingAddressId: v, billingAddressId: billingSameAsShipping ? v : old.billingAddressId }));
                  }}
                  disabled={status === CREATING_STATUS || addresses.length === 0}
                  className="form-select"
                  required
                >
                  <option value="">Select shipping address...</option>
                  {addresses.map((a) => (
                    <option key={`shipping-${a.id}`} value={a.id}>{a.label || "Address"} — {a.line1}, {a.city}</option>
                  ))}
                </select>
                <label style={{ display: "inline-flex", alignItems: "center", gap: "8px", fontSize: "0.8rem", color: "var(--muted)", cursor: "pointer" }}>
                  <input
                    type="checkbox"
                    checked={billingSameAsShipping}
                    onChange={(e) => setBillingSameAsShipping(e.target.checked)}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    style={{ accentColor: "var(--brand)", width: "14px", height: "14px" }}
                  />
                  Billing same as shipping
                </label>
                {!billingSameAsShipping && (
                  <select
                    value={form.billingAddressId}
                    onChange={(e) => setForm((old) => ({ ...old, billingAddressId: e.target.value }))}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    className="form-select"
                    required
                  >
                    <option value="">Select billing address...</option>
                    {addresses.map((a) => (
                      <option key={`billing-${a.id}`} value={a.id}>{a.label || "Address"} — {a.line1}, {a.city}</option>
                    ))}
                  </select>
                )}
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number" min={1} value={form.quantity}
                    onChange={(e) => setForm((old) => ({ ...old, quantity: Number(e.target.value) }))}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    className="form-input"
                    style={{ width: "80px" }}
                    placeholder="Qty"
                  />
                  <button
                    type="submit"
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    style={{
                      flex: 1, padding: "10px", borderRadius: "10px", border: "none",
                      background: status === CREATING_STATUS || addresses.length === 0
                        ? "var(--line-bright)" : "var(--gradient-brand)",
                      color: "#fff", fontSize: "0.875rem", fontWeight: 700,
                      cursor: status === CREATING_STATUS || addresses.length === 0 ? "not-allowed" : "pointer",
                      display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
                    }}
                  >
                    {status === CREATING_STATUS && <span className="spinner-sm" />}
                    {status === CREATING_STATUS ? "Placing..." : "Place Order"}
                  </button>
                </div>
              </form>
            </section>

            {/* Order Lookup */}
            <section className="glass-card" style={{ padding: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                </svg>
                <h3 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1rem", color: "#fff", margin: 0 }}>
                  Order Lookup
                </h3>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                <input
                  value={selectedId}
                  onChange={(e) => setSelectedId(e.target.value)}
                  placeholder="Paste Order ID..."
                  className="form-input"
                />
                <button
                  onClick={() => { void loadDetail(); }}
                  disabled={Boolean(detailLoadingTarget) || !selectedId.trim()}
                  style={{
                    padding: "10px", borderRadius: "10px",
                    border: "1px solid var(--line-bright)", background: "var(--brand-soft)",
                    color: "var(--brand)", fontSize: "0.875rem", fontWeight: 700,
                    cursor: detailLoadingTarget || !selectedId.trim() ? "not-allowed" : "pointer",
                    opacity: !selectedId.trim() ? 0.4 : 1,
                  }}
                >
                  {detailLoadingTarget ? "Loading..." : "Load Detail"}
                </button>
              </div>

              {!selectedDetail && (
                <div style={{ marginTop: "12px", borderRadius: "10px", border: "1px dashed var(--line-bright)", padding: "16px", textAlign: "center", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Select or search for an order to view details
                </div>
              )}

              {selectedDetail && (() => {
                const dsc = STATUS_COLORS[selectedDetail.status] || STATUS_COLORS.CONFIRMED;
                return (
                  <div style={{ marginTop: "12px", borderRadius: "12px", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", overflow: "hidden" }}>
                    <div style={{ padding: "12px 14px", borderBottom: "1px solid var(--brand-soft)" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px" }}>
                        <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: dsc.bg, border: `1px solid ${dsc.border}`, color: dsc.color }}>
                          {selectedDetail.status || "PLACED"}
                        </span>
                        {selectedDetail.couponCode && (
                          <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: "var(--success-soft)", border: "1px solid var(--success-glow)", color: "var(--success)" }}>
                            Coupon: {selectedDetail.couponCode}
                          </span>
                        )}
                      </div>
                      <p style={{ fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)", margin: "0 0 4px" }}>{selectedDetail.id}</p>
                      <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                        Placed:{" "}
                        <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>
                          {new Date(selectedDetail.createdAt).toLocaleString()}
                        </span>
                      </p>
                      {(selectedDetail.status === "PAYMENT_PENDING" || selectedDetail.status === "PAYMENT_FAILED") && (
                        <button
                          onClick={() => { void payNow(selectedDetail.id); }}
                          disabled={Boolean(payingOrderId)}
                          style={{
                            marginTop: "10px", padding: "8px 16px", borderRadius: "9px", border: "none",
                            background: "var(--gradient-brand)", color: "#fff",
                            fontSize: "0.78rem", fontWeight: 700, cursor: payingOrderId ? "not-allowed" : "pointer",
                            display: "inline-flex", alignItems: "center", gap: "6px",
                          }}
                        >
                          {payingOrderId === selectedDetail.id && <span className="spinner-sm" />}
                          {payingOrderId === selectedDetail.id ? "Redirecting..." : "Pay Now"}
                        </button>
                      )}
                      {CANCELLABLE.has(selectedDetail.status) && (
                        <div style={{ marginTop: "10px", display: "flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
                          <input
                            value={cancelReason}
                            onChange={(e) => setCancelReason(e.target.value)}
                            placeholder="Cancel reason (optional)"
                            className="form-input"
                            style={{ fontSize: "0.72rem", padding: "6px 10px", flex: 1, minWidth: "120px" }}
                            maxLength={240}
                          />
                          <button
                            onClick={() => { void cancelOrder(selectedDetail.id); }}
                            disabled={Boolean(cancellingOrderId)}
                            style={{
                              padding: "7px 14px", borderRadius: "9px",
                              border: "1px solid rgba(239,68,68,0.25)", background: "rgba(239,68,68,0.06)",
                              color: "var(--danger)", fontSize: "0.75rem", fontWeight: 700,
                              cursor: cancellingOrderId ? "not-allowed" : "pointer",
                              display: "inline-flex", alignItems: "center", gap: "6px",
                            }}
                          >
                            {cancellingOrderId === selectedDetail.id && <span className="spinner-sm" />}
                            {cancellingOrderId === selectedDetail.id ? "Cancelling..." : "Cancel Order"}
                          </button>
                        </div>
                      )}
                    </div>

                    {/* Price Breakdown */}
                    <div style={{ padding: "12px 14px", borderBottom: "1px solid var(--brand-soft)", fontSize: "0.78rem" }}>
                      {[
                        { label: "Subtotal", value: selectedDetail.subtotal, show: true },
                        { label: "Line Discounts", value: -(selectedDetail.lineDiscountTotal || 0), show: (selectedDetail.lineDiscountTotal || 0) > 0, isDiscount: true },
                        { label: "Cart Discounts", value: -(selectedDetail.cartDiscountTotal || 0), show: (selectedDetail.cartDiscountTotal || 0) > 0, isDiscount: true },
                        { label: "Shipping", value: selectedDetail.shippingAmount || 0, show: true },
                        { label: "Shipping Discount", value: -(selectedDetail.shippingDiscountTotal || 0), show: (selectedDetail.shippingDiscountTotal || 0) > 0, isDiscount: true },
                      ].filter((r) => r.show).map(({ label, value, isDiscount }) => (
                        <div key={label} style={{ display: "flex", justifyContent: "space-between", marginBottom: "4px" }}>
                          <span style={{ color: isDiscount ? "var(--success)" : "var(--muted)" }}>{label}</span>
                          <span style={{ color: isDiscount ? "var(--success)" : "var(--ink-light)", fontWeight: 600 }}>
                            {isDiscount ? `−${money(Math.abs(value))}` : money(value)}
                          </span>
                        </div>
                      ))}
                      {(selectedDetail.totalDiscount || 0) > 0 && (
                        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "4px", fontWeight: 700 }}>
                          <span style={{ color: "var(--success)" }}>Total Savings</span>
                          <span style={{ color: "var(--success)" }}>−{money(selectedDetail.totalDiscount)}</span>
                        </div>
                      )}
                      <div style={{ display: "flex", justifyContent: "space-between", borderTop: "1px solid var(--line-bright)", paddingTop: "8px", marginTop: "4px", fontWeight: 800 }}>
                        <span style={{ color: "#fff" }}>Grand Total</span>
                        <span style={{ color: "var(--brand)", fontSize: "1rem" }}>{money(selectedDetail.orderTotal || selectedDetail.subtotal)}</span>
                      </div>
                    </div>

                    {/* Payment Info */}
                    {paymentInfo && (
                      <div style={{ padding: "12px 14px", borderBottom: "1px solid var(--brand-soft)", fontSize: "0.78rem" }}>
                        <p style={{ fontSize: "0.6rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--brand)", margin: "0 0 8px" }}>Payment</p>
                        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
                          <div>
                            <span style={{ color: "var(--muted)" }}>Status: </span>
                            <span style={{
                              fontWeight: 700,
                              color: paymentInfo.status === "COMPLETED" ? "var(--success)" : paymentInfo.status === "FAILED" ? "var(--danger)" : "var(--warning-text)",
                            }}>
                              {paymentInfo.status}
                            </span>
                          </div>
                          <div>
                            <span style={{ color: "var(--muted)" }}>Method: </span>
                            <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>{paymentInfo.paymentMethod || "—"}</span>
                          </div>
                          {paymentInfo.cardNoMasked && (
                            <div>
                              <span style={{ color: "var(--muted)" }}>Card: </span>
                              <span style={{ color: "var(--ink-light)", fontFamily: "monospace" }}>{paymentInfo.cardNoMasked}</span>
                            </div>
                          )}
                          {paymentInfo.paidAt && (
                            <div>
                              <span style={{ color: "var(--muted)" }}>Paid: </span>
                              <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>{new Date(paymentInfo.paidAt).toLocaleString()}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {(selectedDetail.shippingAddress || selectedDetail.billingAddress) && (
                      <div style={{ padding: "12px 14px", display: "grid", gap: "8px", gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid var(--brand-soft)" }}>
                        {[
                          { label: "Shipping", addr: selectedDetail.shippingAddress },
                          { label: "Billing", addr: selectedDetail.billingAddress },
                        ].filter(({ addr }) => addr).map(({ label, addr }) => (
                          <div key={label} style={{ borderRadius: "8px", border: "1px solid var(--line-bright)", padding: "8px 10px" }}>
                            <p style={{ fontSize: "0.6rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--brand)", margin: "0 0 4px" }}>{label}</p>
                            <p style={{ fontSize: "0.75rem", fontWeight: 700, color: "#fff", margin: 0 }}>{addr!.recipientName}</p>
                            <p style={{ fontSize: "0.7rem", color: "var(--muted)", margin: "2px 0 0" }}>{addr!.line1}{addr!.line2 ? `, ${addr!.line2}` : ""}, {addr!.city}, {addr!.state}</p>
                          </div>
                        ))}
                      </div>
                    )}

                    {selectedDetail.warnings && selectedDetail.warnings.length > 0 && (
                      <div className="alert alert-warning" style={{ margin: "12px 14px", fontSize: "0.75rem" }}>
                        {selectedDetail.warnings.map((w, i) => <p key={i} style={{ margin: i > 0 ? "4px 0 0" : 0 }}>{w}</p>)}
                      </div>
                    )}

                    <div style={{ overflow: "auto" }}>
                      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.78rem" }}>
                        <thead>
                          <tr style={{ background: "var(--brand-soft)" }}>
                            {["Item", "Qty", "Row ID"].map((h) => (
                              <th key={h} style={{ padding: "8px 12px", textAlign: "left", fontWeight: 700, color: "var(--muted)", fontSize: "0.65rem", textTransform: "uppercase", letterSpacing: "0.08em" }}>{h}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {selectedDetail.items?.map((row, idx) => (
                            <tr key={row.id || `${row.item}-${idx}`} style={{ borderTop: "1px solid var(--brand-soft)" }}>
                              <td style={{ padding: "8px 12px", color: "var(--ink-light)" }}>{row.item}</td>
                              <td style={{ padding: "8px 12px", color: "var(--brand)", fontWeight: 700 }}>{row.quantity}</td>
                              <td style={{ padding: "8px 12px", fontFamily: "monospace", fontSize: "0.6rem", color: "var(--muted-2)" }}>{row.id || "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                );
              })()}
            </section>
          </div>
        </div>

        <p style={{ marginTop: "16px", fontSize: "0.75rem", color: "var(--muted-2)" }}>{status}</p>
      </main>

      <Footer />
    </div>
  );
}
