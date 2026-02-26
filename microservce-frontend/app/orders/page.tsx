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
import type { ProductSummary } from "../../lib/types/product";
import { API_BASE, ORDER_STATUS_COLORS as STATUS_COLORS } from "../../lib/constants";
import OrderHistoryCard from "../components/orders/OrderHistoryCard";
import QuickPurchaseForm from "../components/orders/QuickPurchaseForm";
import OrderDetailPanel from "../components/orders/OrderDetailPanel";
import { useOrders, useOrderDetail, useOrderPayment, usePlaceOrder, useCancelOrder } from "../../lib/hooks/queries/useOrders";
import { useAddresses } from "../../lib/hooks/queries/useAddresses";

type ProductPageResponse = { content: ProductSummary[] };

export default function OrdersPage() {
  const router = useRouter();
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, canViewAdmin, ensureCustomer, apiClient,
    resendVerificationEmail, profile, logout, emailVerified } = session;

  /* ── React Query hooks ── */
  const { data: orders = [], isLoading: ordersLoading } = useOrders(apiClient);
  const { data: addresses = [] } = useAddresses(apiClient);

  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [status, setStatus] = useState("Loading your purchases...");
  const [form, setForm] = useState({ productId: "", quantity: 1, shippingAddressId: "", billingAddressId: "" });
  const [billingSameAsShipping, setBillingSameAsShipping] = useState(true);
  const [selectedId, setSelectedId] = useState("");
  const [resendingVerification, setResendingVerification] = useState(false);
  const [payingOrderId, setPayingOrderId] = useState<string | null>(null);
  const [cancellingOrderId, setCancellingOrderId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");

  const { data: selectedDetail, isLoading: detailLoading } = useOrderDetail(apiClient, selectedId || null);
  const { data: paymentInfo } = useOrderPayment(apiClient, selectedId || null);

  const placeOrderMutation = usePlaceOrder(apiClient);
  const cancelOrderMutation = useCancelOrder(apiClient);

  const loadProducts = useCallback(async () => {
    const apiBase = API_BASE;
    const res = await fetch(`${apiBase}/products?page=0&size=100`, { cache: "no-store" });
    if (!res.ok) return;
    const data = (await res.json()) as ProductPageResponse;
    setProducts((data.content || []).filter((p) => p.productType !== "PARENT"));
  }, []);

  /* Initialise default address selections when addresses arrive */
  useEffect(() => {
    if (addresses.length === 0) return;
    setForm((old) => {
      if (old.shippingAddressId) return old;
      const defaultShipping = addresses.find((a) => a.defaultShipping)?.id || addresses[0]?.id || "";
      const defaultBilling = addresses.find((a) => a.defaultBilling)?.id || defaultShipping;
      return { ...old, shippingAddressId: defaultShipping, billingAddressId: defaultBilling };
    });
  }, [addresses]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    const run = async () => {
      try {
        await ensureCustomer();
        await loadProducts();
        setStatus("Purchase history loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load purchases.");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, ensureCustomer, loadProducts]);

  useEffect(() => {
    if (!billingSameAsShipping) return;
    setForm((old) => ({ ...old, billingAddressId: old.shippingAddressId }));
  }, [billingSameAsShipping, form.shippingAddressId]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiClient || placeOrderMutation.isPending) return;
    const shippingAddressId = form.shippingAddressId.trim();
    const billingAddressId = (billingSameAsShipping ? form.shippingAddressId : form.billingAddressId).trim();
    if (!shippingAddressId || !billingAddressId) { toast.error("Select shipping and billing addresses"); return; }
    placeOrderMutation.mutate(
      { productId: form.productId, quantity: Number(form.quantity), shippingAddressId, billingAddressId },
      {
        onSuccess: () => {
          setForm((old) => ({ ...old, productId: "", quantity: 1 }));
          setStatus("Purchase created.");
          toast.success("Order placed successfully!");
        },
        onError: (err) => {
          setStatus(err instanceof Error ? err.message : "Purchase creation failed.");
          toast.error(err instanceof Error ? err.message : "Purchase creation failed");
        },
      },
    );
  };

  const handleViewClick = (orderId: string) => {
    setSelectedId(orderId);
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

  const cancelOrder = (orderId: string) => {
    if (!apiClient || cancelOrderMutation.isPending) return;
    setCancellingOrderId(orderId);
    cancelOrderMutation.mutate(
      { orderId, reason: cancelReason.trim() || undefined },
      {
        onSuccess: () => {
          setCancelReason("");
          setCancellingOrderId(null);
          toast.success("Order cancelled");
        },
        onError: (err) => {
          toast.error(err instanceof Error ? err.message : "Failed to cancel order");
          setCancellingOrderId(null);
        },
      },
    );
  };

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
        {/* Email verification */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl border border-warning-border bg-warning-soft px-4 py-3 text-sm text-warning-text"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p className="m-0 font-bold">Email Not Verified</p>
              <p className="m-0 text-xs opacity-80">Orders are blocked until you verify your email.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              className="rounded-[8px] border border-[rgba(245,158,11,0.35)] bg-[rgba(245,158,11,0.15)] px-3.5 py-1.5 text-xs font-bold text-warning-text disabled:cursor-not-allowed"
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
            <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
              My Orders
            </h1>
            <p className="mt-1 text-sm text-muted">Track your orders and manage purchases</p>
          </div>
          <Link
            href="/products"
            className="rounded-md bg-[var(--gradient-brand)] px-[18px] py-[9px] text-sm font-bold text-white shadow-[0_0_14px_var(--line-bright)] no-underline"
          >
            Continue Shopping
          </Link>
        </div>

        <div className="grid gap-5" style={{ gridTemplateColumns: "1.2fr 0.8fr" }}>
          {/* Order History */}
          <div className="flex flex-col gap-3">
            {/* Header */}
            <div className="glass-card flex items-center justify-between px-[18px] py-3.5">
              <h2 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold text-white">
                Order History
              </h2>
              <span className="rounded-xl bg-[var(--gradient-brand)] px-3 py-[3px] text-xs font-extrabold text-white">

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

            {orders.map((order, idx) => (
              <OrderHistoryCard
                key={order.id}
                order={order}
                statusColors={STATUS_COLORS}
                onViewDetails={handleViewClick}
                onPayNow={(id) => { void payNow(id); }}
                onCancelOrder={(id) => setCancellingOrderId(id)}
                payingOrderId={payingOrderId}
                detailLoadingTarget={detailLoading ? selectedId : null}
                cancellingOrderId={cancellingOrderId}
                cancelReason={cancelReason}
                onCancelReasonChange={setCancelReason}
                onConfirmCancel={(id) => { void cancelOrder(id); }}
                onDismissCancel={() => { setCancellingOrderId(null); setCancelReason(""); }}
                index={idx}
              />
            ))}
          </div>

          {/* Right Sidebar */}
          <div className="flex flex-col gap-4">
            <QuickPurchaseForm
              products={products}
              addresses={addresses}
              form={form}
              onFormChange={setForm}
              billingSameAsShipping={billingSameAsShipping}
              onBillingSameChange={setBillingSameAsShipping}
              isCreating={placeOrderMutation.isPending}
              onSubmit={createOrder}
            />

            {/* Order Lookup */}
            <section className="glass-card p-5">
              <div className="mb-3.5 flex items-center gap-2.5">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                </svg>
                <h3 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold text-white">
                  Order Lookup
                </h3>
              </div>
              <div className="flex flex-col gap-2.5">
                <input
                  value={selectedId}
                  onChange={(e) => setSelectedId(e.target.value)}
                  placeholder="Paste Order ID..."
                  className="form-input"
                />
                <button
                  onClick={() => { setSelectedId((id) => id.trim()); }}
                  disabled={detailLoading || !selectedId.trim()}
                  className="rounded-md border border-line-bright bg-brand-soft p-2.5 text-base font-bold text-brand disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {detailLoading ? "Loading..." : "Load Detail"}
                </button>
              </div>

              {!selectedDetail && (
                <div className="mt-3 rounded-md border border-dashed border-line-bright p-4 text-center text-sm text-muted">
                  Select or search for an order to view details
                </div>
              )}

              {selectedDetail && (
                <OrderDetailPanel
                  detail={selectedDetail}
                  statusColors={STATUS_COLORS}
                  onPayNow={(id) => { void payNow(id); }}
                  payingOrderId={payingOrderId}
                  onCancelOrder={(id) => { void cancelOrder(id); }}
                  cancellingOrderId={cancellingOrderId}
                  cancelReason={cancelReason}
                  onCancelReasonChange={setCancelReason}
                  paymentInfo={paymentInfo ?? null}
                />
              )}
            </section>
          </div>
        </div>

        <p className="mt-4 text-xs text-muted-2">{status}</p>
      </main>

      <Footer />
    </div>
  );
}
