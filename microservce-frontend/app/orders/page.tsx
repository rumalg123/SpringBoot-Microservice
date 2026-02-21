"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

const CREATING_STATUS = "Creating purchase...";

type Order = { id: string; customerId: string; item: string; quantity: number; createdAt: string };
type OrderItem = { id: string | null; item: string; quantity: number };
type OrderAddress = {
  sourceAddressId: string; label: string | null; recipientName: string; phone: string;
  line1: string; line2: string | null; city: string; state: string; postalCode: string; countryCode: string;
};
type OrderDetail = {
  id: string; customerId: string; item: string; quantity: number; createdAt: string;
  items: OrderItem[]; shippingAddress?: OrderAddress | null; billingAddress?: OrderAddress | null;
};
type PagedOrder = { content: Order[] };
type ProductSummary = { id: string; name: string; sku: string; productType: string };
type ProductPageResponse = { content: ProductSummary[] };
type CustomerAddress = {
  id: string; customerId: string; label: string | null; recipientName: string;
  phone: string; line1: string; line2: string | null; city: string; state: string;
  postalCode: string; countryCode: string; defaultShipping: boolean; defaultBilling: boolean;
};

const darkInput: React.CSSProperties = {
  width: "100%", padding: "10px 14px", borderRadius: "10px",
  border: "1px solid rgba(0,212,255,0.15)", background: "rgba(0,212,255,0.04)",
  color: "#c8c8e8", fontSize: "0.85rem", outline: "none",
};
const darkSelect: React.CSSProperties = { ...darkInput, appearance: "none", WebkitAppearance: "none" };
const glassCard: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)",
  border: "1px solid rgba(0,212,255,0.1)", borderRadius: "16px",
};

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

  const loadOrders = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get("/orders/me");
    setOrders(((res.data as PagedOrder).content || []));
  }, [apiClient]);

  const loadProducts = useCallback(async () => {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
    const res = await fetch(`${apiBase}/products?page=0&size=100`, { cache: "no-store" });
    if (!res.ok) return;
    const data = (await res.json()) as ProductPageResponse;
    setProducts((data.content || []).filter((p) => p.productType !== "PARENT"));
  }, []);

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get("/customers/me/addresses");
    const loaded = (res.data as CustomerAddress[]) || [];
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
    try {
      const res = await apiClient.get(`/orders/me/${targetId}`);
      setSelectedDetail(res.data as OrderDetail);
      setSelectedId(targetId);
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
        canViewAdmin={canViewAdmin}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Email verification */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl px-4 py-3 text-sm"
            style={{ border: "1px solid rgba(245,158,11,0.3)", background: "rgba(245,158,11,0.08)", color: "#fbbf24" }}
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
                color: "#fbbf24", padding: "6px 14px", borderRadius: "8px",
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
              background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
              color: "#fff", fontSize: "0.8rem", fontWeight: 700,
              boxShadow: "0 0 14px rgba(0,212,255,0.2)",
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
              style={{
                ...glassCard, padding: "14px 18px",
                display: "flex", alignItems: "center", justifyContent: "space-between",
              }}
            >
              <h2 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1rem", color: "#fff", margin: 0 }}>
                Order History
              </h2>
              <span
                style={{
                  background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                  color: "#fff", padding: "3px 12px", borderRadius: "20px",
                  fontSize: "0.75rem", fontWeight: 800,
                }}
              >
                {orders.length} orders
              </span>
            </div>

            {orders.length === 0 && (
              <div className="empty-state">
                <div className="empty-state-icon">
                  <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v11m0 0H3m6 0h6m0 0V5m0 11v3a2 2 0 0 1-2 2H9m6-5h3a2 2 0 0 1 2 2v3" />
                  </svg>
                </div>
                <p className="empty-state-title">No orders yet</p>
                <p className="empty-state-desc">Place your first order to get started!</p>
                <Link href="/products" className="btn-primary no-underline inline-block px-6 py-2.5 text-sm">Browse Products</Link>
              </div>
            )}

            {orders.map((order, idx) => (
              <article
                key={order.id}
                className="animate-rise"
                style={{ ...glassCard, padding: "16px 20px", animationDelay: `${idx * 50}ms` }}
              >
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "10px" }}>
                  <div style={{ flex: 1 }}>
                    <p style={{ fontWeight: 700, color: "#fff", fontSize: "0.9rem", margin: 0 }}>{order.item}</p>
                    <p style={{ margin: "4px 0 0", fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)" }}>
                      ID: {order.id}
                    </p>
                    <div style={{ marginTop: "8px", display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                      <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: "rgba(0,212,255,0.08)", border: "1px solid rgba(0,212,255,0.2)", color: "#00d4ff" }}>
                        Qty: {order.quantity}
                      </span>
                      <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "3px 10px", borderRadius: "20px", background: "rgba(34,197,94,0.08)", border: "1px solid rgba(34,197,94,0.2)", color: "#4ade80" }}>
                        ✓ Placed
                      </span>
                      <span style={{ fontSize: "0.72rem", color: "var(--muted)" }}>
                        {new Date(order.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" })}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => { void loadDetail(order.id); }}
                    disabled={Boolean(detailLoadingTarget)}
                    style={{
                      padding: "7px 14px", borderRadius: "9px",
                      border: "1px solid rgba(0,212,255,0.25)", background: "rgba(0,212,255,0.06)",
                      color: "#00d4ff", fontSize: "0.75rem", fontWeight: 700,
                      cursor: detailLoadingTarget ? "not-allowed" : "pointer",
                      opacity: detailLoadingTarget && detailLoadingTarget !== order.id ? 0.5 : 1,
                      whiteSpace: "nowrap",
                    }}
                  >
                    {detailLoadingTarget === order.id ? "Loading..." : "View Details"}
                  </button>
                </div>
              </article>
            ))}
          </div>

          {/* Right Sidebar */}
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            {/* Quick Purchase */}
            <section style={{ ...glassCard, padding: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00d4ff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                </svg>
                <h3 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1rem", color: "#fff", margin: 0 }}>
                  Quick Purchase
                </h3>
              </div>

              {addresses.length === 0 && (
                <div style={{ borderRadius: "10px", border: "1px solid rgba(245,158,11,0.25)", background: "rgba(245,158,11,0.06)", padding: "10px 12px", fontSize: "0.78rem", color: "#fbbf24", marginBottom: "12px" }}>
                  Add at least one address in your profile before placing an order.{" "}
                  <Link href="/profile" style={{ color: "#00d4ff", fontWeight: 700 }}>Open Profile</Link>
                </div>
              )}

              <form style={{ display: "flex", flexDirection: "column", gap: "10px" }} onSubmit={createOrder}>
                <select
                  value={form.productId}
                  onChange={(e) => setForm((old) => ({ ...old, productId: e.target.value }))}
                  disabled={status === CREATING_STATUS || addresses.length === 0}
                  style={darkSelect}
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
                  style={darkSelect}
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
                    style={{ accentColor: "#00d4ff", width: "14px", height: "14px" }}
                  />
                  Billing same as shipping
                </label>
                {!billingSameAsShipping && (
                  <select
                    value={form.billingAddressId}
                    onChange={(e) => setForm((old) => ({ ...old, billingAddressId: e.target.value }))}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    style={darkSelect}
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
                    style={{ ...darkInput, width: "80px" }}
                    placeholder="Qty"
                  />
                  <button
                    type="submit"
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    style={{
                      flex: 1, padding: "10px", borderRadius: "10px", border: "none",
                      background: status === CREATING_STATUS || addresses.length === 0
                        ? "rgba(0,212,255,0.2)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
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
            <section style={{ ...glassCard, padding: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "14px" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7c3aed" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
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
                  style={darkInput}
                />
                <button
                  onClick={() => { void loadDetail(); }}
                  disabled={Boolean(detailLoadingTarget) || !selectedId.trim()}
                  style={{
                    padding: "10px", borderRadius: "10px",
                    border: "1px solid rgba(0,212,255,0.25)", background: "rgba(0,212,255,0.06)",
                    color: "#00d4ff", fontSize: "0.875rem", fontWeight: 700,
                    cursor: detailLoadingTarget || !selectedId.trim() ? "not-allowed" : "pointer",
                    opacity: !selectedId.trim() ? 0.4 : 1,
                  }}
                >
                  {detailLoadingTarget ? "Loading..." : "Load Detail"}
                </button>
              </div>

              {!selectedDetail && (
                <div style={{ marginTop: "12px", borderRadius: "10px", border: "1px dashed rgba(0,212,255,0.12)", padding: "16px", textAlign: "center", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Select or search for an order to view details
                </div>
              )}

              {selectedDetail && (
                <div style={{ marginTop: "12px", borderRadius: "12px", border: "1px solid rgba(0,212,255,0.1)", background: "rgba(0,212,255,0.03)", overflow: "hidden" }}>
                  <div style={{ padding: "12px 14px", borderBottom: "1px solid rgba(0,212,255,0.08)" }}>
                    <p style={{ fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)", margin: "0 0 4px" }}>{selectedDetail.id}</p>
                    <p style={{ fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>
                      Placed:{" "}
                      <span style={{ color: "#c8c8e8", fontWeight: 600 }}>
                        {new Date(selectedDetail.createdAt).toLocaleString()}
                      </span>
                    </p>
                  </div>

                  {(selectedDetail.shippingAddress || selectedDetail.billingAddress) && (
                    <div style={{ padding: "12px 14px", display: "grid", gap: "8px", gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid rgba(0,212,255,0.08)" }}>
                      {[
                        { label: "Shipping", addr: selectedDetail.shippingAddress },
                        { label: "Billing", addr: selectedDetail.billingAddress },
                      ].filter(({ addr }) => addr).map(({ label, addr }) => (
                        <div key={label} style={{ borderRadius: "8px", border: "1px solid rgba(0,212,255,0.1)", padding: "8px 10px" }}>
                          <p style={{ fontSize: "0.6rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "#00d4ff", margin: "0 0 4px" }}>{label}</p>
                          <p style={{ fontSize: "0.75rem", fontWeight: 700, color: "#fff", margin: 0 }}>{addr!.recipientName}</p>
                          <p style={{ fontSize: "0.7rem", color: "var(--muted)", margin: "2px 0 0" }}>{addr!.line1}{addr!.line2 ? `, ${addr!.line2}` : ""}, {addr!.city}, {addr!.state}</p>
                        </div>
                      ))}
                    </div>
                  )}

                  <div style={{ overflow: "auto" }}>
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.78rem" }}>
                      <thead>
                        <tr style={{ background: "rgba(0,212,255,0.04)" }}>
                          {["Item", "Qty", "Row ID"].map((h) => (
                            <th key={h} style={{ padding: "8px 12px", textAlign: "left", fontWeight: 700, color: "var(--muted)", fontSize: "0.65rem", textTransform: "uppercase", letterSpacing: "0.08em" }}>{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {selectedDetail.items?.map((row, idx) => (
                          <tr key={row.id || `${row.item}-${idx}`} style={{ borderTop: "1px solid rgba(0,212,255,0.06)" }}>
                            <td style={{ padding: "8px 12px", color: "#c8c8e8" }}>{row.item}</td>
                            <td style={{ padding: "8px 12px", color: "#00d4ff", fontWeight: 700 }}>{row.quantity}</td>
                            <td style={{ padding: "8px 12px", fontFamily: "monospace", fontSize: "0.6rem", color: "var(--muted-2)" }}>{row.id || "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </section>
          </div>
        </div>

        <p style={{ marginTop: "16px", fontSize: "0.75rem", color: "var(--muted-2)" }}>{status}</p>
      </main>

      <Footer />
    </div>
  );
}
