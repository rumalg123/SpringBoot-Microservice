"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

type CartItem = {
  id: string;
  productId: string;
  productSlug: string;
  productName: string;
  productSku: string;
  mainImage: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
};

type CartResponse = {
  id: string | null;
  keycloakId: string;
  items: CartItem[];
  itemCount: number;
  totalQuantity: number;
  subtotal: number;
  createdAt: string | null;
  updatedAt: string | null;
};

type CustomerAddress = {
  id: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
};

type CheckoutResponse = {
  orderId: string;
  itemCount: number;
  totalQuantity: number;
  subtotal: number;
};

const emptyCart: CartResponse = {
  id: null, keycloakId: "", items: [], itemCount: 0,
  totalQuantity: 0, subtotal: 0, createdAt: null, updatedAt: null,
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value || 0);
}

/* Shared dark select style */
const darkSelect: React.CSSProperties = {
  width: "100%",
  padding: "10px 14px",
  borderRadius: "10px",
  border: "1px solid rgba(0,212,255,0.15)",
  background: "rgba(0,212,255,0.04)",
  color: "#c8c8e8",
  fontSize: "0.85rem",
  outline: "none",
  appearance: "none",
  WebkitAppearance: "none",
};

/* Glass card style */
const glassCard: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)",
  backdropFilter: "blur(16px)",
  border: "1px solid rgba(0,212,255,0.1)",
  borderRadius: "16px",
  padding: "20px",
};

export default function CartPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin,
    ensureCustomer, apiClient, profile, logout, emailVerified, resendVerificationEmail,
  } = session;

  const [cart, setCart] = useState<CartResponse>(emptyCart);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [shippingAddressId, setShippingAddressId] = useState("");
  const [billingAddressId, setBillingAddressId] = useState("");
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true);
  const [status, setStatus] = useState("Loading cart...");
  const [resendingVerification, setResendingVerification] = useState(false);
  const [addressLoading, setAddressLoading] = useState(false);
  const [updatingItemId, setUpdatingItemId] = useState<string | null>(null);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [clearingCart, setClearingCart] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);

  const loadCart = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get("/cart/me");
    const data = (res.data as CartResponse) || emptyCart;
    setCart({ ...emptyCart, ...data, items: data.items || [] });
  }, [apiClient]);

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    setAddressLoading(true);
    try {
      const res = await apiClient.get("/customers/me/addresses");
      const loaded = (res.data as CustomerAddress[]) || [];
      setAddresses(loaded);
      const defaultShipping = loaded.find((a) => a.defaultShipping)?.id || loaded[0]?.id || "";
      const defaultBilling = loaded.find((a) => a.defaultBilling)?.id || defaultShipping;
      setShippingAddressId((old) => old || defaultShipping);
      setBillingAddressId((old) => old || defaultBilling);
    } finally {
      setAddressLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    const run = async () => {
      try {
        await ensureCustomer();
        await Promise.all([loadCart(), loadAddresses()]);
        setStatus("Cart ready.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load cart");
      }
    };
    void run();
  }, [sessionStatus, isAuthenticated, router, ensureCustomer, loadCart, loadAddresses]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setBillingAddressId(shippingAddressId);
  }, [billingSameAsShipping, shippingAddressId]);

  const busy = updatingItemId !== null || removingItemId !== null || clearingCart || checkingOut;

  const updateQuantity = async (itemId: string, quantity: number) => {
    if (!apiClient || busy || quantity < 1) return;
    setUpdatingItemId(itemId);
    try {
      const res = await apiClient.put(`/cart/me/items/${itemId}`, { quantity });
      setCart((res.data as CartResponse) || emptyCart);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update quantity");
    } finally { setUpdatingItemId(null); }
  };

  const removeItem = async (itemId: string) => {
    if (!apiClient || busy) return;
    setRemovingItemId(itemId);
    try {
      await apiClient.delete(`/cart/me/items/${itemId}`);
      await loadCart();
      toast.success("Item removed from cart");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to remove item");
    } finally { setRemovingItemId(null); }
  };

  const clearCart = async () => {
    if (!apiClient || busy || cart.items.length === 0) return;
    setClearingCart(true);
    try {
      await apiClient.delete("/cart/me");
      await loadCart();
      toast.success("Cart cleared");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to clear cart");
    } finally { setClearingCart(false); }
  };

  const checkout = async () => {
    if (!apiClient || busy || cart.items.length === 0) return;
    const resolvedShipping = shippingAddressId.trim();
    const resolvedBilling = (billingSameAsShipping ? shippingAddressId : billingAddressId).trim();
    if (!resolvedShipping || !resolvedBilling) { toast.error("Select shipping and billing addresses"); return; }
    setCheckingOut(true);
    setStatus("Placing order...");
    try {
      const res = await apiClient.post("/cart/me/checkout", {
        shippingAddressId: resolvedShipping, billingAddressId: resolvedBilling,
      });
      const data = res.data as CheckoutResponse;
      await loadCart();
      setStatus("Order placed successfully.");
      toast.success(`Order placed: ${data.orderId}`);
      router.push("/orders");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Checkout failed";
      setStatus(message);
      toast.error(message);
    } finally { setCheckingOut(false); }
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

  const hasRequiredAddresses = useMemo(
    () => Boolean(shippingAddressId.trim() && (billingSameAsShipping || billingAddressId.trim())),
    [shippingAddressId, billingSameAsShipping, billingAddressId]
  );

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
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Email verification warning */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl px-4 py-3 text-sm"
            style={{ border: "1px solid rgba(245,158,11,0.3)", background: "rgba(245,158,11,0.08)", color: "#fbbf24" }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p className="font-semibold">Email not verified</p>
              <p style={{ fontSize: "0.75rem", opacity: 0.8 }}>Cart checkout is blocked until your email is verified.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              style={{
                background: "rgba(245,158,11,0.2)",
                border: "1px solid rgba(245,158,11,0.4)",
                color: "#fbbf24",
                padding: "6px 14px",
                borderRadius: "8px",
                fontSize: "0.75rem",
                fontWeight: 700,
                cursor: resendingVerification ? "not-allowed" : "pointer",
                opacity: resendingVerification ? 0.5 : 1,
              }}
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Cart</span>
        </nav>

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>
              Your Cart
            </h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>{status}</p>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <Link
              href="/products"
              className="no-underline"
              style={{
                padding: "9px 18px",
                borderRadius: "10px",
                border: "1px solid rgba(0,212,255,0.25)",
                color: "#00d4ff",
                background: "rgba(0,212,255,0.06)",
                fontSize: "0.8rem",
                fontWeight: 700,
              }}
            >
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearCart(); }}
              disabled={busy || cart.items.length === 0}
              style={{
                padding: "9px 18px",
                borderRadius: "10px",
                border: "1px solid rgba(239,68,68,0.25)",
                background: "rgba(239,68,68,0.06)",
                color: "#ef4444",
                fontSize: "0.8rem",
                fontWeight: 700,
                cursor: busy || cart.items.length === 0 ? "not-allowed" : "pointer",
                opacity: cart.items.length === 0 ? 0.4 : 1,
              }}
            >
              {clearingCart ? "Clearing..." : "Clear Cart"}
            </button>
          </div>
        </div>

        <div className="cart-grid" style={{ display: "grid", gap: "20px", gridTemplateColumns: "1.4fr 0.8fr" }}>
          {/* Cart Items */}
          <section style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {cart.items.length === 0 && (
              <div className="empty-state">
                <div className="empty-state-icon">
                  <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                    <path d="M16 10a4 4 0 0 1-8 0" />
                  </svg>
                </div>
                <p className="empty-state-title">Cart is empty</p>
                <p className="empty-state-desc">Add products to your cart before checkout.</p>
                <Link href="/products" className="btn-primary no-underline px-6 py-2.5 text-sm">
                  Browse Products
                </Link>
              </div>
            )}

            {cart.items.map((item) => (
              <article key={item.id} className="animate-rise" style={glassCard}>
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                  <div style={{ flex: 1, minWidth: "200px" }}>
                    <Link
                      href={`/products/${encodeURIComponent(item.productSlug)}`}
                      className="no-underline"
                      style={{ fontWeight: 700, color: "#fff", fontSize: "0.95rem", lineHeight: 1.4 }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "#00d4ff"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "#fff"; }}
                    >
                      {item.productName}
                    </Link>
                    <p style={{ margin: "4px 0 0", fontSize: "0.7rem", color: "var(--muted-2)", fontFamily: "monospace" }}>
                      SKU: {item.productSku}
                    </p>
                    <p style={{ margin: "6px 0 0", fontSize: "0.9rem", fontWeight: 700, color: "#00d4ff" }}>
                      {money(item.unitPrice)} each
                    </p>
                    <p style={{ margin: "2px 0 0", fontSize: "0.75rem", color: "var(--muted)" }}>
                      Line total: <strong style={{ color: "#c8c8e8" }}>{money(item.lineTotal)}</strong>
                    </p>
                  </div>

                  <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "10px" }}>
                    <div className="qty-stepper">
                      <button disabled={busy || item.quantity <= 1} onClick={() => { void updateQuantity(item.id, item.quantity - 1); }}>−</button>
                      <span>{updatingItemId === item.id ? "…" : item.quantity}</span>
                      <button disabled={busy} onClick={() => { void updateQuantity(item.id, item.quantity + 1); }}>+</button>
                    </div>
                    <button
                      onClick={() => { void removeItem(item.id); }}
                      disabled={busy}
                      style={{
                        padding: "5px 14px",
                        borderRadius: "8px",
                        border: "1px solid rgba(239,68,68,0.25)",
                        background: "rgba(239,68,68,0.06)",
                        color: "#ef4444",
                        fontSize: "0.72rem",
                        fontWeight: 700,
                        cursor: busy ? "not-allowed" : "pointer",
                        opacity: busy ? 0.5 : 1,
                      }}
                    >
                      {removingItemId === item.id ? "Removing..." : "Remove"}
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </section>

          {/* Checkout Panel */}
          <aside className="cart-summary-aside" style={{ ...glassCard, alignSelf: "start", position: "sticky", top: "80px" }}>
            <h2 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1.1rem", color: "#fff", margin: "0 0 16px" }}>
              Checkout Summary
            </h2>

            {/* Totals */}
            <div style={{ borderRadius: "10px", background: "rgba(0,212,255,0.04)", border: "1px solid rgba(0,212,255,0.08)", padding: "12px 14px", marginBottom: "16px" }}>
              {[
                { label: "Distinct items", value: String(cart.itemCount) },
                { label: "Total quantity", value: String(cart.totalQuantity) },
              ].map(({ label, value }) => (
                <div key={label} style={{ display: "flex", justifyContent: "space-between", marginBottom: "8px", fontSize: "0.82rem" }}>
                  <span style={{ color: "var(--muted)" }}>{label}</span>
                  <span style={{ color: "#c8c8e8", fontWeight: 600 }}>{value}</span>
                </div>
              ))}
              <div style={{ display: "flex", justifyContent: "space-between", borderTop: "1px solid rgba(0,212,255,0.1)", paddingTop: "10px", fontWeight: 800 }}>
                <span style={{ color: "#fff" }}>Subtotal</span>
                <span style={{ color: "#00d4ff", fontSize: "1rem" }}>{money(cart.subtotal)}</span>
              </div>
            </div>

            {/* Address notices */}
            {addresses.length === 0 && !addressLoading && (
              <div style={{ borderRadius: "10px", border: "1px solid rgba(245,158,11,0.25)", background: "rgba(245,158,11,0.06)", padding: "10px 12px", fontSize: "0.78rem", color: "#fbbf24", marginBottom: "14px" }}>
                Add at least one address in your profile before checkout.{" "}
                <Link href="/profile" style={{ color: "#00d4ff", fontWeight: 700 }}>Open Profile</Link>
              </div>
            )}
            {addressLoading && (
              <p style={{ fontSize: "0.8rem", color: "var(--muted)", marginBottom: "12px" }}>Loading addresses...</p>
            )}

            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              {/* Shipping */}
              <div>
                <label style={{ display: "block", fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
                  Shipping Address
                </label>
                <select
                  value={shippingAddressId}
                  onChange={(e) => {
                    const v = e.target.value;
                    setShippingAddressId(v);
                    if (billingSameAsShipping) setBillingAddressId(v);
                  }}
                  disabled={busy || addressLoading || addresses.length === 0}
                  style={darkSelect}
                >
                  <option value="">Select shipping address...</option>
                  {addresses.map((a) => (
                    <option key={`shipping-${a.id}`} value={a.id}>
                      {a.label || "Address"} — {a.line1}, {a.city}
                    </option>
                  ))}
                </select>
              </div>

              {/* Billing same toggle */}
              <label style={{ display: "inline-flex", alignItems: "center", gap: "8px", fontSize: "0.8rem", color: "var(--muted)", cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={billingSameAsShipping}
                  onChange={(e) => setBillingSameAsShipping(e.target.checked)}
                  disabled={busy || addressLoading || addresses.length === 0}
                  style={{ accentColor: "#00d4ff", width: "14px", height: "14px" }}
                />
                Billing same as shipping
              </label>

              {/* Billing address */}
              {!billingSameAsShipping && (
                <div>
                  <label style={{ display: "block", fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
                    Billing Address
                  </label>
                  <select
                    value={billingAddressId}
                    onChange={(e) => setBillingAddressId(e.target.value)}
                    disabled={busy || addressLoading || addresses.length === 0}
                    style={darkSelect}
                  >
                    <option value="">Select billing address...</option>
                    {addresses.map((a) => (
                      <option key={`billing-${a.id}`} value={a.id}>
                        {a.label || "Address"} — {a.line1}, {a.city}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {/* Checkout button */}
              <button
                onClick={() => { void checkout(); }}
                disabled={busy || cart.items.length === 0 || !hasRequiredAddresses || emailVerified === false}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "10px",
                  border: "none",
                  background: busy || cart.items.length === 0 || !hasRequiredAddresses || emailVerified === false
                    ? "rgba(0,212,255,0.2)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
                  color: "#fff",
                  fontSize: "0.9rem",
                  fontWeight: 800,
                  cursor: busy || cart.items.length === 0 || !hasRequiredAddresses || emailVerified === false
                    ? "not-allowed" : "pointer",
                  boxShadow: "0 0 20px rgba(0,212,255,0.15)",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                  marginTop: "6px",
                }}
              >
                {checkingOut && <span className="spinner-sm" />}
                {checkingOut ? "Placing Order..." : "Checkout"}
              </button>
            </div>
          </aside>
        </div>
      </main>

      <Footer />
    </div>
  );
}
