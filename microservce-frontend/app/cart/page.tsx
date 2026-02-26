"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { getErrorMessage } from "../../lib/error";
import { PayHereFormData, submitToPayHere } from "../../lib/payhere";
import { trackPurchase, fetchSimilarProducts, type PersonalizationProduct } from "../../lib/personalization";
import { money, calcDiscount } from "../../lib/format";
import { resolveImageUrl } from "../../lib/image";
import type { CartItem, CartResponse, CheckoutPreviewResponse, CheckoutResponse, AppliedPromotion, RejectedPromotion } from "../../lib/types/cart";
import { emptyCart } from "../../lib/types/cart";
import type { CustomerAddress } from "../../lib/types/customer";
import Image from "next/image";
import CheckoutSidebar from "../components/cart/CheckoutSidebar";


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
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  const [movingItemId, setMovingItemId] = useState<string | null>(null);
  const [clearingCart, setClearingCart] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);
  const [couponCode, setCouponCode] = useState("");
  const [preview, setPreview] = useState<CheckoutPreviewResponse | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [couponError, setCouponError] = useState("");
  const [suggestions, setSuggestions] = useState<PersonalizationProduct[]>([]);

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

  // Fetch "Complete Your Purchase" suggestions
  useEffect(() => {
    const firstProduct = cart.items[0];
    if (!firstProduct) { setSuggestions([]); return; }
    fetchSimilarProducts(firstProduct.productId, 4).then(setSuggestions).catch(() => {});
  }, [cart.items.length > 0 ? cart.items[0]?.productId : null]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setBillingAddressId(shippingAddressId);
  }, [billingSameAsShipping, shippingAddressId]);

  const busy = updatingItemId !== null || removingItemId !== null || savingItemId !== null || movingItemId !== null || clearingCart || checkingOut || previewing;

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

  const saveForLater = async (itemId: string) => {
    if (!apiClient || busy) return;
    setSavingItemId(itemId);
    try {
      const res = await apiClient.post(`/cart/me/items/${itemId}/save-for-later`);
      setCart((res.data as CartResponse) || emptyCart);
      toast.success("Item saved for later");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save item");
    } finally { setSavingItemId(null); }
  };

  const moveToCart = async (itemId: string) => {
    if (!apiClient || busy) return;
    setMovingItemId(itemId);
    try {
      const res = await apiClient.post(`/cart/me/items/${itemId}/move-to-cart`);
      setCart((res.data as CartResponse) || emptyCart);
      toast.success("Item moved to cart");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item to cart");
    } finally { setMovingItemId(null); }
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

  const loadPreview = async () => {
    if (!apiClient || cart.items.length === 0) return;
    setPreviewing(true);
    setCouponError("");
    try {
      const res = await apiClient.post("/cart/me/checkout/preview", {
        couponCode: couponCode.trim() || null,
        shippingAmount: 0,
      });
      setPreview(res.data as CheckoutPreviewResponse);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Preview failed";
      if (couponCode.trim() && msg.toLowerCase().includes("coupon")) {
        setCouponError(msg);
      } else {
        toast.error(msg);
      }
      setPreview(null);
    } finally { setPreviewing(false); }
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
        shippingAddressId: resolvedShipping,
        billingAddressId: resolvedBilling,
        couponCode: couponCode.trim() || null,
        shippingAmount: 0,
      });
      const data = res.data as CheckoutResponse;
      await loadCart();
      setPreview(null);

      setStatus("Redirecting to payment...");
      toast.success(`Order placed! Total: ${money(data.grandTotal)}`);
      trackPurchase(cart.items.map((item) => ({ id: item.productId, price: item.unitPrice })), session.token);
      const payRes = await apiClient.post("/payments/me/initiate", { orderId: data.orderId });
      submitToPayHere(payRes.data as PayHereFormData);
    } catch (err) {
      const message = getErrorMessage(err);
      setStatus(message);
      toast.error(message);
      setCheckingOut(false);
    }
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
        canManageAdminCategories={session.canManageAdminCategories}
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
            style={{ border: "1px solid var(--warning-border)", background: "var(--warning-soft)", color: "var(--warning-text)" }}
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
                color: "var(--warning-text)",
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
                color: "var(--brand)",
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
                color: "var(--danger)",
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
              <article key={item.id} className="animate-rise glass-card">
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                  <div style={{ flex: 1, minWidth: "200px" }}>
                    <Link
                      href={`/products/${encodeURIComponent(item.productSlug)}`}
                      className="no-underline"
                      style={{ fontWeight: 700, color: "#fff", fontSize: "0.95rem", lineHeight: 1.4 }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "var(--brand)"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "#fff"; }}
                    >
                      {item.productName}
                    </Link>
                    <p style={{ margin: "4px 0 0", fontSize: "0.7rem", color: "var(--muted-2)", fontFamily: "monospace" }}>
                      SKU: {item.productSku}
                    </p>
                    <p style={{ margin: "6px 0 0", fontSize: "0.9rem", fontWeight: 700, color: "var(--brand)" }}>
                      {money(item.unitPrice)} each
                    </p>
                    <p style={{ margin: "2px 0 0", fontSize: "0.75rem", color: "var(--muted)" }}>
                      Line total: <strong style={{ color: "var(--ink-light)" }}>{money(item.lineTotal)}</strong>
                    </p>
                  </div>

                  <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "10px" }}>
                    <div className="qty-stepper">
                      <button disabled={busy || item.quantity <= 1} onClick={() => { void updateQuantity(item.id, item.quantity - 1); }}>−</button>
                      <span>{updatingItemId === item.id ? "…" : item.quantity}</span>
                      <button disabled={busy} onClick={() => { void updateQuantity(item.id, item.quantity + 1); }}>+</button>
                    </div>
                    <div style={{ display: "flex", gap: "6px" }}>
                      <button
                        onClick={() => { void saveForLater(item.id); }}
                        disabled={busy}
                        style={{
                          padding: "5px 14px",
                          borderRadius: "8px",
                          border: "1px solid rgba(124,58,237,0.25)",
                          background: "rgba(124,58,237,0.06)",
                          color: "#a78bfa",
                          fontSize: "0.72rem",
                          fontWeight: 700,
                          cursor: busy ? "not-allowed" : "pointer",
                          opacity: busy ? 0.5 : 1,
                        }}
                      >
                        {savingItemId === item.id ? "Saving..." : "Save for Later"}
                      </button>
                      <button
                        onClick={() => { void removeItem(item.id); }}
                        disabled={busy}
                        style={{
                          padding: "5px 14px",
                          borderRadius: "8px",
                          border: "1px solid rgba(239,68,68,0.25)",
                          background: "rgba(239,68,68,0.06)",
                          color: "var(--danger)",
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
                </div>
              </article>
            ))}
          </section>

          {/* Checkout Panel */}
          <CheckoutSidebar
            cart={cart}
            preview={preview}
            couponCode={couponCode}
            onCouponChange={(code) => { setCouponCode(code); setCouponError(""); }}
            couponError={couponError}
            onApplyCoupon={() => { void loadPreview(); }}
            previewing={previewing}
            addresses={addresses}
            addressLoading={addressLoading}
            shippingAddressId={shippingAddressId}
            onShippingChange={(v) => {
              setShippingAddressId(v);
              if (billingSameAsShipping) setBillingAddressId(v);
            }}
            billingAddressId={billingAddressId}
            onBillingChange={setBillingAddressId}
            billingSameAsShipping={billingSameAsShipping}
            onBillingSameChange={setBillingSameAsShipping}
            hasRequiredAddresses={hasRequiredAddresses}
            emailVerified={emailVerified}
            busy={busy}
            checkingOut={checkingOut}
            onCheckout={() => { void checkout(); }}
          />
        </div>

        {/* Saved for Later */}
        {cart.savedForLaterItems.length > 0 && (
          <section style={{ marginTop: "24px" }}>
            <h2 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.2rem", fontWeight: 800, color: "#fff", margin: "0 0 12px" }}>
              Saved for Later ({cart.savedForLaterItems.length})
            </h2>
            <div style={{ display: "grid", gap: "10px", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))" }}>
              {cart.savedForLaterItems.map((item) => (
                <article key={item.id} className="glass-card" style={{ padding: "14px" }}>
                  <Link
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="no-underline"
                    style={{ fontWeight: 700, color: "#fff", fontSize: "0.85rem", lineHeight: 1.4 }}
                  >
                    {item.productName}
                  </Link>
                  <p style={{ margin: "4px 0 0", fontSize: "0.7rem", color: "var(--muted-2)", fontFamily: "monospace" }}>
                    SKU: {item.productSku}
                  </p>
                  <p style={{ margin: "6px 0 10px", fontSize: "0.85rem", fontWeight: 700, color: "var(--brand)" }}>
                    {money(item.unitPrice)}
                  </p>
                  <div style={{ display: "flex", gap: "6px" }}>
                    <button
                      onClick={() => { void moveToCart(item.id); }}
                      disabled={busy}
                      style={{
                        padding: "5px 14px",
                        borderRadius: "8px",
                        border: "1px solid rgba(0,212,255,0.25)",
                        background: "rgba(0,212,255,0.06)",
                        color: "var(--brand)",
                        fontSize: "0.72rem",
                        fontWeight: 700,
                        cursor: busy ? "not-allowed" : "pointer",
                        opacity: busy ? 0.5 : 1,
                      }}
                    >
                      {movingItemId === item.id ? "Moving..." : "Move to Cart"}
                    </button>
                    <button
                      onClick={() => { void removeItem(item.id); }}
                      disabled={busy}
                      style={{
                        padding: "5px 14px",
                        borderRadius: "8px",
                        border: "1px solid rgba(239,68,68,0.25)",
                        background: "rgba(239,68,68,0.06)",
                        color: "var(--danger)",
                        fontSize: "0.72rem",
                        fontWeight: 700,
                        cursor: busy ? "not-allowed" : "pointer",
                        opacity: busy ? 0.5 : 1,
                      }}
                    >
                      {removingItemId === item.id ? "Removing..." : "Remove"}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}
      </main>

      {/* Complete Your Purchase */}
      {suggestions.length > 0 && cart.items.length > 0 && (
        <section className="mx-auto max-w-7xl px-4 pb-8">
          <h2 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.3rem", fontWeight: 800, color: "#fff", marginBottom: "16px" }}>Complete Your Purchase</h2>
          <div style={{ display: "flex", gap: "16px", overflowX: "auto", paddingBottom: "8px" }}>
            {suggestions.map((p) => {
              const discount = calcDiscount(p.regularPrice, p.sellingPrice);
              const imgUrl = resolveImageUrl(p.mainImage);
              return (
                <Link href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`} key={p.id} className="product-card no-underline" style={{ minWidth: "200px", maxWidth: "220px", flexShrink: 0 }}>
                  {discount && <span className="badge-sale">-{discount}%</span>}
                  <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)" }}>
                    {imgUrl ? (<Image src={imgUrl} alt={p.name} width={300} height={300} className="product-card-img" unoptimized />) : (<div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, var(--surface), #1c1c38)", color: "var(--muted-2)", fontSize: "0.75rem" }}>No Image</div>)}
                  </div>
                  <div className="product-card-body">
                    <p style={{ margin: "0 0 4px", fontSize: "0.8rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 1, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{p.name}</p>
                    <span className="price-current" style={{ fontSize: "0.85rem" }}>{money(p.sellingPrice)}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>
      )}

      <Footer />
    </div>
  );
}
