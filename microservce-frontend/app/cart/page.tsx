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
  id: null,
  keycloakId: "",
  items: [],
  itemCount: 0,
  totalQuantity: 0,
  subtotal: 0,
  createdAt: null,
  updatedAt: null,
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value || 0);
}

export default function CartPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus,
    isAuthenticated,
    canViewAdmin,
    ensureCustomer,
    apiClient,
    profile,
    logout,
    emailVerified,
    resendVerificationEmail,
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
    setCart({
      ...emptyCart,
      ...data,
      items: data.items || [],
    });
  }, [apiClient]);

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    setAddressLoading(true);
    try {
      const res = await apiClient.get("/customers/me/addresses");
      const loaded = (res.data as CustomerAddress[]) || [];
      setAddresses(loaded);

      const defaultShipping = loaded.find((address) => address.defaultShipping)?.id || loaded[0]?.id || "";
      const defaultBilling = loaded.find((address) => address.defaultBilling)?.id || defaultShipping;
      setShippingAddressId((old) => old || defaultShipping);
      setBillingAddressId((old) => old || defaultBilling);
    } finally {
      setAddressLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) {
      router.replace("/");
      return;
    }

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
    if (!apiClient || busy) return;
    if (quantity < 1) return;
    setUpdatingItemId(itemId);
    try {
      const res = await apiClient.put(`/cart/me/items/${itemId}`, { quantity });
      setCart((res.data as CartResponse) || emptyCart);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update quantity");
    } finally {
      setUpdatingItemId(null);
    }
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
    } finally {
      setRemovingItemId(null);
    }
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
    } finally {
      setClearingCart(false);
    }
  };

  const checkout = async () => {
    if (!apiClient || busy || cart.items.length === 0) return;
    const resolvedShippingAddressId = shippingAddressId.trim();
    const resolvedBillingAddressId = (billingSameAsShipping ? shippingAddressId : billingAddressId).trim();

    if (!resolvedShippingAddressId || !resolvedBillingAddressId) {
      toast.error("Select shipping and billing addresses");
      return;
    }

    setCheckingOut(true);
    setStatus("Placing order...");
    try {
      const res = await apiClient.post("/cart/me/checkout", {
        shippingAddressId: resolvedShippingAddressId,
        billingAddressId: resolvedBillingAddressId,
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
    } finally {
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
    } finally {
      setResendingVerification(false);
    }
  };

  const hasRequiredAddresses = useMemo(() => {
    return Boolean(shippingAddressId.trim() && (billingSameAsShipping || billingAddressId.trim()));
  }, [shippingAddressId, billingSameAsShipping, billingAddressId]);

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
          <p className="mt-4">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {emailVerified === false && (
          <section className="mb-4 flex items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <div className="flex-1">
              <p className="font-semibold">Email not verified</p>
              <p className="text-xs">Cart checkout is blocked until your email is verified.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-500 disabled:cursor-not-allowed disabled:opacity-60"
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

        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--ink)]">Your Cart</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">{status}</p>
          </div>
          <div className="flex gap-2">
            <Link href="/products" className="btn-outline no-underline px-4 py-2 text-sm">
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearCart(); }}
              disabled={busy || cart.items.length === 0}
              className="btn-danger px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {clearingCart ? "Clearing..." : "Clear Cart"}
            </button>
          </div>
        </div>

        <div className="grid gap-5 lg:grid-cols-[1.4fr,0.8fr]">
          <section className="space-y-3">
            {cart.items.length === 0 && (
              <div className="empty-state">
                <div className="empty-state-icon">🛒</div>
                <p className="empty-state-title">Cart is empty</p>
                <p className="empty-state-desc">Add products to your cart before checkout.</p>
                <Link href="/products" className="btn-primary no-underline px-6 py-2.5 text-sm">
                  Browse Products
                </Link>
              </div>
            )}

            {cart.items.map((item) => (
              <article key={item.id} className="rounded-xl bg-white p-4 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex-1">
                    <Link href={`/products/${encodeURIComponent(item.productSlug)}`} className="text-base font-semibold text-[var(--ink)] no-underline hover:text-[var(--brand)]">
                      {item.productName}
                    </Link>
                    <p className="mt-1 text-xs text-[var(--muted)]">SKU: {item.productSku}</p>
                    <p className="mt-1 text-sm font-semibold text-[var(--brand)]">
                      {money(item.unitPrice)} each
                    </p>
                    <p className="text-xs text-[var(--muted)]">Line total: {money(item.lineTotal)}</p>
                  </div>

                  <div className="flex flex-col items-end gap-2">
                    <div className="qty-stepper">
                      <button
                        disabled={busy || item.quantity <= 1}
                        onClick={() => { void updateQuantity(item.id, item.quantity - 1); }}
                      >
                        −
                      </button>
                      <span>{item.quantity}</span>
                      <button
                        disabled={busy}
                        onClick={() => { void updateQuantity(item.id, item.quantity + 1); }}
                      >
                        +
                      </button>
                    </div>
                    <button
                      onClick={() => { void removeItem(item.id); }}
                      disabled={busy}
                      className="btn-outline px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {removingItemId === item.id ? "Removing..." : "Remove"}
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </section>

          <aside className="rounded-xl bg-white p-5 shadow-sm">
            <h2 className="text-lg font-bold text-[var(--ink)]">Checkout</h2>
            <div className="mt-3 grid gap-2 rounded-lg bg-[#fafafa] p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-[var(--muted)]">Distinct items</span>
                <span>{cart.itemCount}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[var(--muted)]">Total quantity</span>
                <span>{cart.totalQuantity}</span>
              </div>
              <div className="flex items-center justify-between border-t border-[var(--line)] pt-2 font-semibold">
                <span>Subtotal</span>
                <span>{money(cart.subtotal)}</span>
              </div>
            </div>

            <div className="mt-4 space-y-3">
              {addresses.length === 0 && !addressLoading && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  Add at least one address in your profile before checkout.
                  {" "}
                  <Link href="/profile" className="font-semibold underline">Open Profile</Link>
                </div>
              )}
              {addressLoading && (
                <p className="text-xs text-[var(--muted)]">Loading addresses...</p>
              )}

              <select
                value={shippingAddressId}
                onChange={(e) => {
                  const selected = e.target.value;
                  setShippingAddressId(selected);
                  if (billingSameAsShipping) {
                    setBillingAddressId(selected);
                  }
                }}
                disabled={busy || addressLoading || addresses.length === 0}
                className="w-full rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
              >
                <option value="">Select shipping address...</option>
                {addresses.map((address) => (
                  <option key={`shipping-${address.id}`} value={address.id}>
                    {(address.label || "Address")} - {address.line1}, {address.city}
                  </option>
                ))}
              </select>

              <label className="inline-flex items-center gap-2 text-xs text-[var(--muted)]">
                <input
                  type="checkbox"
                  checked={billingSameAsShipping}
                  onChange={(e) => setBillingSameAsShipping(e.target.checked)}
                  disabled={busy || addressLoading || addresses.length === 0}
                />
                Billing address same as shipping
              </label>

              {!billingSameAsShipping && (
                <select
                  value={billingAddressId}
                  onChange={(e) => setBillingAddressId(e.target.value)}
                  disabled={busy || addressLoading || addresses.length === 0}
                  className="w-full rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                >
                  <option value="">Select billing address...</option>
                  {addresses.map((address) => (
                    <option key={`billing-${address.id}`} value={address.id}>
                      {(address.label || "Address")} - {address.line1}, {address.city}
                    </option>
                  ))}
                </select>
              )}

              <button
                onClick={() => { void checkout(); }}
                disabled={busy || cart.items.length === 0 || !hasRequiredAddresses || emailVerified === false}
                className="btn-primary w-full py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
              >
                {checkingOut ? "Placing order..." : "Checkout"}
              </button>
            </div>
          </aside>
        </div>
      </main>

      <Footer />
    </div>
  );
}
