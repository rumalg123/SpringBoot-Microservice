"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { getErrorMessage } from "../../lib/error";
import { PayHereFormData, submitToPayHere } from "../../lib/payhere";
import { trackPurchase, fetchSimilarProducts, type PersonalizationProduct } from "../../lib/personalization";
import { money } from "../../lib/format";
import type { CheckoutPreviewResponse } from "../../lib/types/cart";
import { emptyCart } from "../../lib/types/cart";
import { useCart, useUpdateCartItem, useRemoveCartItem, useSaveForLater, useMoveToCart, useClearCart, useCheckoutPreview, useCheckout } from "../../lib/hooks/queries/useCart";
import { useAddresses } from "../../lib/hooks/queries/useAddresses";
import CheckoutSidebar from "../components/cart/CheckoutSidebar";
import CartItemCard from "../components/cart/CartItemCard";
import SavedForLaterSection from "../components/cart/SavedForLaterSection";
import CartSuggestions from "../components/cart/CartSuggestions";
import EmptyState from "../components/ui/EmptyState";


export default function CartPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin,
    ensureCustomer, apiClient, profile, logout, emailVerified, resendVerificationEmail,
  } = session;

  // --- UI form state ---
  const [shippingAddressId, setShippingAddressId] = useState("");
  const [billingAddressId, setBillingAddressId] = useState("");
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true);
  const [status, setStatus] = useState("Loading cart...");
  const [resendingVerification, setResendingVerification] = useState(false);
  const [couponCode, setCouponCode] = useState("");
  const [preview, setPreview] = useState<CheckoutPreviewResponse | null>(null);
  const [couponError, setCouponError] = useState("");
  const [suggestions, setSuggestions] = useState<PersonalizationProduct[]>([]);

  // --- Item-level busy tracking ---
  const [updatingItemId, setUpdatingItemId] = useState<string | null>(null);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [savingItemId, setSavingItemId] = useState<string | null>(null);
  const [movingItemId, setMovingItemId] = useState<string | null>(null);

  // --- React Query: data fetching ---
  const { data: cart = emptyCart, isLoading: cartLoading } = useCart(apiClient);
  const { data: addresses = [], isLoading: addressLoading } = useAddresses(apiClient);

  // --- React Query: mutations ---
  const updateCartItemMut = useUpdateCartItem(apiClient);
  const removeCartItemMut = useRemoveCartItem(apiClient);
  const saveForLaterMut = useSaveForLater(apiClient);
  const moveToCartMut = useMoveToCart(apiClient);
  const clearCartMut = useClearCart(apiClient);
  const checkoutPreviewMut = useCheckoutPreview(apiClient);
  const checkoutMut = useCheckout(apiClient);

  // --- Auth guard & customer bootstrap ---
  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    void ensureCustomer();
  }, [sessionStatus, isAuthenticated, router, ensureCustomer]);

  // --- Update status based on query state ---
  useEffect(() => {
    if (cartLoading) setStatus("Loading cart...");
    else setStatus("Cart ready.");
  }, [cartLoading]);

  // --- Default address selection when addresses load ---
  useEffect(() => {
    if (addresses.length === 0) return;
    const defaultShipping = addresses.find((a) => a.defaultShipping)?.id || addresses[0]?.id || "";
    const defaultBilling = addresses.find((a) => a.defaultBilling)?.id || defaultShipping;
    setShippingAddressId((old) => old || defaultShipping);
    setBillingAddressId((old) => old || defaultBilling);
  }, [addresses]);

  // Fetch "Complete Your Purchase" suggestions
  useEffect(() => {
    const firstProduct = cart.items[0];
    if (!firstProduct) { setSuggestions([]); return; }
    fetchSimilarProducts(firstProduct.productId, 4).then(setSuggestions).catch((e) => console.error("Failed to load suggestions:", e));
  }, [cart.items.length > 0 ? cart.items[0]?.productId : null]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setBillingAddressId(shippingAddressId);
  }, [billingSameAsShipping, shippingAddressId]);

  const busy =
    updatingItemId !== null ||
    removingItemId !== null ||
    savingItemId !== null ||
    movingItemId !== null ||
    clearCartMut.isPending ||
    checkoutMut.isPending ||
    checkoutPreviewMut.isPending;

  const clearingCart = clearCartMut.isPending;
  const previewing = checkoutPreviewMut.isPending;
  const checkingOut = checkoutMut.isPending;

  const updateQuantity = (itemId: string, quantity: number) => {
    if (!apiClient || busy || quantity < 1) return;
    setUpdatingItemId(itemId);
    updateCartItemMut.mutate(
      { itemId, quantity },
      {
        onError: (err) => { toast.error(err instanceof Error ? err.message : "Failed to update quantity"); },
        onSettled: () => { setUpdatingItemId(null); },
      },
    );
  };

  const removeItem = (itemId: string) => {
    if (!apiClient || busy) return;
    setRemovingItemId(itemId);
    removeCartItemMut.mutate(itemId, {
      onSuccess: () => { toast.success("Item removed from cart"); },
      onError: (err) => { toast.error(err instanceof Error ? err.message : "Failed to remove item"); },
      onSettled: () => { setRemovingItemId(null); },
    });
  };

  const saveForLater = (itemId: string) => {
    if (!apiClient || busy) return;
    setSavingItemId(itemId);
    saveForLaterMut.mutate(itemId, {
      onSuccess: () => { toast.success("Item saved for later"); },
      onError: (err) => { toast.error(err instanceof Error ? err.message : "Failed to save item"); },
      onSettled: () => { setSavingItemId(null); },
    });
  };

  const moveToCart = (itemId: string) => {
    if (!apiClient || busy) return;
    setMovingItemId(itemId);
    moveToCartMut.mutate(itemId, {
      onSuccess: () => { toast.success("Item moved to cart"); },
      onError: (err) => { toast.error(err instanceof Error ? err.message : "Failed to move item to cart"); },
      onSettled: () => { setMovingItemId(null); },
    });
  };

  const clearCart = () => {
    if (!apiClient || busy || cart.items.length === 0) return;
    clearCartMut.mutate(undefined, {
      onSuccess: () => { toast.success("Cart cleared"); },
      onError: (err) => { toast.error(err instanceof Error ? err.message : "Failed to clear cart"); },
    });
  };

  const loadPreview = () => {
    if (!apiClient || cart.items.length === 0) return;
    setCouponError("");
    checkoutPreviewMut.mutate(
      { couponCode: couponCode.trim() || null, shippingAmount: 0 },
      {
        onSuccess: (data) => { setPreview(data); },
        onError: (err) => {
          const msg = err instanceof Error ? err.message : "Preview failed";
          if (couponCode.trim() && msg.toLowerCase().includes("coupon")) {
            setCouponError(msg);
          } else {
            toast.error(msg);
          }
          setPreview(null);
        },
      },
    );
  };

  const checkout = () => {
    if (!apiClient || busy || cart.items.length === 0) return;
    const resolvedShipping = shippingAddressId.trim();
    const resolvedBilling = (billingSameAsShipping ? shippingAddressId : billingAddressId).trim();
    if (!resolvedShipping || !resolvedBilling) { toast.error("Select shipping and billing addresses"); return; }
    setStatus("Placing order...");
    checkoutMut.mutate(
      {
        shippingAddressId: resolvedShipping,
        billingAddressId: resolvedBilling,
        couponCode: couponCode.trim() || null,
        shippingAmount: 0,
      },
      {
        onSuccess: async (data) => {
          setPreview(null);
          setStatus("Redirecting to payment...");
          toast.success(`Order placed! Total: ${money(data.grandTotal)}`);
          trackPurchase(cart.items.map((item) => ({ id: item.productId, price: item.unitPrice })), session.token);
          const payRes = await apiClient.post<PayHereFormData>("/payments/me/initiate", { orderId: data.orderId });
          submitToPayHere(payRes.data);
        },
        onError: (err) => {
          const message = getErrorMessage(err);
          setStatus(message);
          toast.error(message);
        },
      },
    );
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
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-base text-muted">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <div className="min-h-screen bg-bg">
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
            className="mb-4 flex items-center gap-3 rounded-xl border border-warning-border bg-warning-soft px-4 py-3 text-sm text-warning-text"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p className="font-semibold">Email not verified</p>
              <p className="text-xs opacity-80">Cart checkout is blocked until your email is verified.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              className="rounded-[8px] border border-[rgba(245,158,11,0.4)] bg-[rgba(245,158,11,0.2)] px-3.5 py-1.5 text-xs font-bold text-warning-text disabled:cursor-not-allowed disabled:opacity-50"
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
            <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
              Your Cart
            </h1>
            <p className="mt-1 text-sm text-muted">{status}</p>
          </div>
          <div className="flex gap-2.5">
            <Link
              href="/products"
              className="rounded-md border border-[rgba(0,212,255,0.25)] bg-[rgba(0,212,255,0.06)] px-[18px] py-[9px] text-sm font-bold text-brand no-underline"
            >
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearCart(); }}
              disabled={busy || cart.items.length === 0}
              className="rounded-md border border-[rgba(239,68,68,0.25)] bg-[rgba(239,68,68,0.06)] px-[18px] py-[9px] text-sm font-bold text-danger disabled:cursor-not-allowed disabled:opacity-40"
            >
              {clearingCart ? "Clearing..." : "Clear Cart"}
            </button>
          </div>
        </div>

        <div className="cart-grid grid gap-5" style={{ gridTemplateColumns: "1.4fr 0.8fr" }}>
          {/* Cart Items */}
          <section className="flex flex-col gap-3">
            {cart.items.length === 0 && (
              <EmptyState
                icon={
                  <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                    <path d="M16 10a4 4 0 0 1-8 0" />
                  </svg>
                }
                title="Cart is empty"
                description="Add products to your cart before checkout."
                actionLabel="Browse Products"
                onAction={() => router.push("/products")}
              />
            )}

            {cart.items.map((item) => (
              <CartItemCard
                key={item.id}
                item={item}
                busy={busy}
                updatingItemId={updatingItemId}
                savingItemId={savingItemId}
                removingItemId={removingItemId}
                onUpdateQuantity={(id, qty) => { void updateQuantity(id, qty); }}
                onSaveForLater={(id) => { void saveForLater(id); }}
                onRemove={(id) => { void removeItem(id); }}
              />
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
        <SavedForLaterSection
          items={cart.savedForLaterItems}
          busy={busy}
          movingItemId={movingItemId}
          removingItemId={removingItemId}
          onMoveToCart={(id) => { void moveToCart(id); }}
          onRemove={(id) => { void removeItem(id); }}
        />
      </main>

      {/* Complete Your Purchase */}
      <CartSuggestions
        suggestions={suggestions}
        visible={cart.items.length > 0}
      />

      <Footer />
    </div>
  );
}
