"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

const CREATING_STATUS = "Creating purchase...";

type Order = {
  id: string;
  customerId: string;
  item: string;
  quantity: number;
  createdAt: string;
};

type OrderItem = {
  id: string | null;
  item: string;
  quantity: number;
};

type OrderDetail = {
  id: string;
  customerId: string;
  item: string;
  quantity: number;
  createdAt: string;
  items: OrderItem[];
  shippingAddress?: OrderAddress | null;
  billingAddress?: OrderAddress | null;
};

type OrderAddress = {
  sourceAddressId: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
};

type PagedOrder = {
  content: Order[];
};

type ProductSummary = {
  id: string;
  name: string;
  sku: string;
  productType: string;
};

type ProductPageResponse = {
  content: ProductSummary[];
};

type CustomerAddress = {
  id: string;
  customerId: string;
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

export default function OrdersPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus,
    isAuthenticated,
    canViewAdmin,
    ensureCustomer,
    apiClient,
    resendVerificationEmail,
    profile,
    logout,
    emailVerified,
  } = session;

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
    const data = res.data as PagedOrder;
    setOrders(data.content || []);
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

    const defaultShipping = loaded.find((address) => address.defaultShipping)?.id || loaded[0]?.id || "";
    const defaultBilling = loaded.find((address) => address.defaultBilling)?.id || defaultShipping;
    setForm((old) => ({
      ...old,
      shippingAddressId: old.shippingAddressId || defaultShipping,
      billingAddressId: old.billingAddressId || defaultBilling,
    }));
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
        await loadOrders();
        await loadProducts();
        await loadAddresses();
        setStatus("Purchase history loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load purchases.");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, ensureCustomer, loadOrders, loadProducts, loadAddresses]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setForm((old) => ({
      ...old,
      billingAddressId: old.shippingAddressId,
    }));
  }, [billingSameAsShipping, form.shippingAddressId]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiClient) return;
    if (status === CREATING_STATUS) return;
    const shippingAddressId = form.shippingAddressId.trim();
    const billingAddressId = (billingSameAsShipping ? form.shippingAddressId : form.billingAddressId).trim();
    if (!shippingAddressId || !billingAddressId) {
      toast.error("Select shipping and billing addresses");
      return;
    }
    setStatus("Creating purchase...");
    try {
      await apiClient.post("/orders/me", {
        productId: form.productId,
        quantity: Number(form.quantity),
        shippingAddressId,
        billingAddressId,
      });
      setForm((old) => ({ ...old, productId: "", quantity: 1 }));
      await loadOrders();
      setStatus("Purchase created.");
      toast.success("Order placed successfully! 🎉");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Purchase creation failed.");
      toast.error(err instanceof Error ? err.message : "Purchase creation failed");
    }
  };

  const loadDetail = async (orderId?: string) => {
    const targetId = (orderId || selectedId).trim();
    if (!apiClient || !targetId) return;
    if (detailLoadingTarget) return;
    setDetailLoadingTarget(targetId);
    setStatus("Loading purchase detail...");
    try {
      const res = await apiClient.get(`/orders/me/${targetId}`);
      setSelectedDetail(res.data as OrderDetail);
      setSelectedId(targetId);
      setStatus("Purchase detail loaded.");
      toast.success("Order detail loaded");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load detail.");
      toast.error(err instanceof Error ? err.message : "Failed to load detail");
    } finally {
      setDetailLoadingTarget(null);
    }
  };

  const resendVerification = async () => {
    if (resendingVerification) return;
    setResendingVerification(true);
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
      toast.success("Verification email sent");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    } finally {
      setResendingVerification(false);
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto w-12 h-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
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
            <span className="text-xl">⚠️</span>
            <div className="flex-1">
              <p className="font-semibold">Email Not Verified</p>
              <p className="text-xs">Orders are blocked until you verify your email.</p>
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

        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">My Orders</span>
        </nav>

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--ink)]">📦 My Orders</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">Track your orders and place new ones</p>
          </div>
          <Link
            href="/products"
            className="btn-primary no-underline px-5 py-2.5 text-sm"
          >
            🛍️ Continue Shopping
          </Link>
        </div>

        <div className="grid gap-5 lg:grid-cols-[1.2fr,0.8fr]">
          {/* Order History */}
          <div className="space-y-3">
            <div className="flex items-center justify-between rounded-xl bg-white px-4 py-3 shadow-sm">
              <h2 className="text-lg font-bold text-[var(--ink)]">Order History</h2>
              <span className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs font-bold text-white">
                {orders.length} orders
              </span>
            </div>

            {orders.length === 0 && (
              <div className="empty-state">
                <div className="empty-state-icon">🛒</div>
                <p className="empty-state-title">No orders yet</p>
                <p className="empty-state-desc">Place your first order to get started!</p>
                <Link href="/products" className="btn-primary no-underline inline-block px-6 py-2.5 text-sm">
                  Browse Products
                </Link>
              </div>
            )}

            {orders.map((order, idx) => (
              <article
                key={order.id}
                className="animate-rise rounded-xl bg-white p-4 shadow-sm transition hover:shadow-md"
                style={{ animationDelay: `${idx * 50}ms` }}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="flex-1">
                    <h3 className="text-base font-semibold text-[var(--ink)]">{order.item}</h3>
                    <p className="mt-1 font-mono text-[10px] text-[var(--muted)]">ID: {order.id}</p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
                        Qty: {order.quantity}
                      </span>
                      <span className="rounded-full bg-green-50 px-2.5 py-1 text-xs font-medium text-green-700">
                        ✓ Placed
                      </span>
                      <span className="text-xs text-[var(--muted)]">
                        {new Date(order.createdAt).toLocaleDateString("en-US", {
                          year: "numeric", month: "short", day: "numeric",
                        })}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => { void loadDetail(order.id); }}
                    disabled={Boolean(detailLoadingTarget)}
                    className="btn-outline px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {detailLoadingTarget === order.id ? "Loading..." : "View Details"}
                  </button>
                </div>
              </article>
            ))}
          </div>

          {/* Right Sidebar */}
          <div className="space-y-4">
            {/* Quick Purchase */}
            <section className="rounded-xl bg-white p-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2">
                <span className="text-lg">🛒</span>
                <h3 className="text-lg font-bold text-[var(--ink)]">Quick Purchase</h3>
              </div>
              {addresses.length === 0 && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  Add at least one address in your profile before placing an order.
                  {" "}
                  <Link href="/profile" className="font-semibold underline">
                    Open Profile
                  </Link>
                </div>
              )}
              <form className="grid gap-3" onSubmit={createOrder}>
                <select
                  value={form.productId}
                  onChange={(e) => setForm((old) => ({ ...old, productId: e.target.value }))}
                  disabled={status === CREATING_STATUS || addresses.length === 0}
                  className="rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                  required
                >
                  <option value="">Select product...</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.sku})
                    </option>
                  ))}
                </select>
                <select
                  value={form.shippingAddressId}
                  onChange={(e) => {
                    const selected = e.target.value;
                    setForm((old) => ({
                      ...old,
                      shippingAddressId: selected,
                      billingAddressId: billingSameAsShipping ? selected : old.billingAddressId,
                    }));
                  }}
                  disabled={status === CREATING_STATUS || addresses.length === 0}
                  className="rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                  required
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
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                  />
                  Billing address same as shipping
                </label>
                {!billingSameAsShipping && (
                  <select
                    value={form.billingAddressId}
                    onChange={(e) => setForm((old) => ({ ...old, billingAddressId: e.target.value }))}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    className="rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                    required
                  >
                    <option value="">Select billing address...</option>
                    {addresses.map((address) => (
                      <option key={`billing-${address.id}`} value={address.id}>
                        {(address.label || "Address")} - {address.line1}, {address.city}
                      </option>
                    ))}
                  </select>
                )}
                <div className="flex gap-3">
                  <input
                    type="number"
                    min={1}
                    value={form.quantity}
                    onChange={(e) => setForm((old) => ({ ...old, quantity: Number(e.target.value) }))}
                    disabled={status === CREATING_STATUS || addresses.length === 0}
                    className="w-24 rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                    placeholder="Qty"
                  />
                  <button type="submit" disabled={status === CREATING_STATUS || addresses.length === 0} className="btn-primary flex-1 py-2.5 text-sm inline-flex items-center justify-center gap-2 disabled:opacity-60">
                    {status === CREATING_STATUS && <span className="spinner-sm" />}
                    {status === CREATING_STATUS ? "Placing..." : "Place Order"}
                  </button>
                </div>
              </form>
            </section>

            {/* Order Detail */}
            <section className="rounded-xl bg-white p-5 shadow-sm">
              <div className="mb-3 flex items-center gap-2">
                <span className="text-lg">🔍</span>
                <h3 className="text-lg font-bold text-[var(--ink)]">Order Lookup</h3>
              </div>
              <div className="grid gap-3">
                <input
                  value={selectedId}
                  onChange={(e) => setSelectedId(e.target.value)}
                  placeholder="Paste Order ID..."
                  className="rounded-lg border border-[var(--line)] bg-white px-3 py-2.5 text-sm"
                />
                <button
                  onClick={() => { void loadDetail(); }}
                  disabled={Boolean(detailLoadingTarget) || !selectedId.trim()}
                  className="btn-outline py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {detailLoadingTarget ? "Loading..." : "Load Detail"}
                </button>
              </div>

              {!selectedDetail && (
                <div className="mt-3 rounded-lg border border-dashed border-[var(--line)] px-3 py-4 text-center text-xs text-[var(--muted)]">
                  Select or search for an order to view details
                </div>
              )}

              {selectedDetail && (
                <div className="mt-3 space-y-2 rounded-lg border border-[var(--line)] bg-[#fafafa] p-3">
                  <p className="font-mono text-[10px] text-[var(--muted)]">{selectedDetail.id}</p>
                  <p className="text-xs text-[var(--muted)]">
                    Placed: <span className="text-[var(--ink)]">{new Date(selectedDetail.createdAt).toLocaleString()}</span>
                  </p>
                  {(selectedDetail.shippingAddress || selectedDetail.billingAddress) && (
                    <div className="grid gap-2 sm:grid-cols-2">
                      {selectedDetail.shippingAddress && (
                        <div className="rounded border border-[var(--line)] bg-white p-2">
                          <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--muted)]">Shipping Address</p>
                          <p className="mt-1 text-xs font-semibold text-[var(--ink)]">
                            {selectedDetail.shippingAddress.label ? `${selectedDetail.shippingAddress.label} - ` : ""}
                            {selectedDetail.shippingAddress.recipientName}
                          </p>
                          <p className="text-[11px] text-[var(--muted)]">{selectedDetail.shippingAddress.phone}</p>
                          <p className="text-[11px] text-[var(--ink)]">
                            {selectedDetail.shippingAddress.line1}
                            {selectedDetail.shippingAddress.line2 ? `, ${selectedDetail.shippingAddress.line2}` : ""}, {selectedDetail.shippingAddress.city}, {selectedDetail.shippingAddress.state} {selectedDetail.shippingAddress.postalCode}, {selectedDetail.shippingAddress.countryCode}
                          </p>
                        </div>
                      )}
                      {selectedDetail.billingAddress && (
                        <div className="rounded border border-[var(--line)] bg-white p-2">
                          <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--muted)]">Billing Address</p>
                          <p className="mt-1 text-xs font-semibold text-[var(--ink)]">
                            {selectedDetail.billingAddress.label ? `${selectedDetail.billingAddress.label} - ` : ""}
                            {selectedDetail.billingAddress.recipientName}
                          </p>
                          <p className="text-[11px] text-[var(--muted)]">{selectedDetail.billingAddress.phone}</p>
                          <p className="text-[11px] text-[var(--ink)]">
                            {selectedDetail.billingAddress.line1}
                            {selectedDetail.billingAddress.line2 ? `, ${selectedDetail.billingAddress.line2}` : ""}, {selectedDetail.billingAddress.city}, {selectedDetail.billingAddress.state} {selectedDetail.billingAddress.postalCode}, {selectedDetail.billingAddress.countryCode}
                          </p>
                        </div>
                      )}
                    </div>
                  )}
                  <div className="overflow-hidden rounded-lg border border-[var(--line)]">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-gray-50 text-[var(--ink)]">
                        <tr>
                          <th className="px-2.5 py-2 font-semibold">Item</th>
                          <th className="px-2.5 py-2 font-semibold">Qty</th>
                          <th className="px-2.5 py-2 font-semibold">Row ID</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedDetail.items?.map((row, idx) => (
                          <tr key={row.id || `${row.item}-${idx}`} className="border-t border-[var(--line)]">
                            <td className="px-2.5 py-2">{row.item}</td>
                            <td className="px-2.5 py-2">{row.quantity}</td>
                            <td className="px-2.5 py-2 font-mono text-[10px] text-[var(--muted)]">{row.id || "—"}</td>
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

        <p className="mt-4 text-xs text-[var(--muted)]">{status}</p>
      </main>

      <Footer />
    </div>
  );
}
