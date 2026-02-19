"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import { useAuthSession } from "../../lib/authSession";

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
  const [status, setStatus] = useState("Loading your purchases...");
  const [form, setForm] = useState({ productId: "", quantity: 1 });
  const [selectedId, setSelectedId] = useState("");
  const [selectedDetail, setSelectedDetail] = useState<OrderDetail | null>(null);

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
        setStatus("Purchase history loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load purchases.");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, ensureCustomer, loadOrders, loadProducts]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiClient) return;
    setStatus("Creating purchase...");
    try {
      await apiClient.post("/orders/me", {
        productId: form.productId,
        quantity: Number(form.quantity),
      });
      setForm({ productId: "", quantity: 1 });
      await loadOrders();
      setStatus("Purchase created.");
      toast.success("Purchase created");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Purchase creation failed.");
      toast.error(err instanceof Error ? err.message : "Purchase creation failed");
    }
  };

  const loadDetail = async (orderId?: string) => {
    const targetId = (orderId || selectedId).trim();
    if (!apiClient || !targetId) return;
    setStatus("Loading purchase detail...");
    try {
      const res = await apiClient.get(`/orders/me/${targetId}`);
      setSelectedDetail(res.data as OrderDetail);
      setSelectedId(targetId);
      setStatus("Purchase detail loaded.");
      toast.success("Purchase detail loaded");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load detail.");
      toast.error(err instanceof Error ? err.message : "Failed to load detail");
    }
  };

  const resendVerification = async () => {
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
      toast.success("Verification email sent");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <main className="mx-auto min-h-screen max-w-6xl px-6 py-10 text-[var(--muted)]">Loading...</main>;
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <main className="mx-auto min-h-screen max-w-7xl px-6 py-8">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        onLogout={() => {
          void logout();
        }}
      />

      {emailVerified === false && (
        <section className="mb-5 rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <p>Your email is not verified. Orders are blocked until verification.</p>
          <button
            onClick={() => {
              void resendVerification();
            }}
            className="mt-2 rounded-lg bg-amber-600 px-3 py-1 text-xs font-semibold text-white hover:bg-amber-500"
          >
            Resend Verification Email
          </button>
        </section>
      )}

      <section className="card-surface animate-rise rounded-3xl p-6 md:p-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs tracking-[0.22em] text-[var(--muted)]">MY ACCOUNT</p>
            <h1 className="text-4xl text-[var(--ink)]">My Purchases</h1>
            <p className="mt-1 text-sm text-[var(--muted)]">Track your order timeline and inspect order details.</p>
          </div>
          <Link
            href="/products"
            className="rounded-full border border-[var(--line)] bg-white px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
          >
            Continue Shopping
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.2fr,0.8fr]">
          <div className="space-y-3">
            <div className="mb-2 flex items-center justify-between">
              <h2 className="text-2xl text-[var(--ink)]">Purchase History</h2>
              <span className="rounded-full bg-[var(--brand-soft)] px-3 py-1 text-xs text-[var(--ink)]">
                {orders.length} orders
              </span>
            </div>

            {orders.length === 0 && (
              <p className="rounded-xl border border-dashed border-[var(--line)] p-4 text-sm text-[var(--muted)]">
                No purchases yet. Place your first order.
              </p>
            )}

            {orders.map((order) => (
              <article key={order.id} className="card-surface rounded-2xl p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-lg font-semibold text-[var(--ink)]">{order.item}</h3>
                  <span className="rounded-full border border-[var(--line)] bg-white px-3 py-1 text-xs text-[var(--ink)]">
                    Qty {order.quantity}
                  </span>
                </div>
                <p className="mt-2 break-all font-mono text-[11px] text-[var(--muted)]">{order.id}</p>
                <p className="mt-1 text-xs text-[var(--muted)]">{new Date(order.createdAt).toLocaleString()}</p>
                <button
                  onClick={() => {
                    void loadDetail(order.id);
                  }}
                  className="mt-3 rounded-lg border border-[var(--line)] bg-white px-3 py-1 text-xs text-[var(--ink)] hover:bg-[var(--brand-soft)]"
                >
                  View Details
                </button>
              </article>
            ))}
          </div>

          <div className="space-y-4">
            <section className="card-surface rounded-2xl p-5">
              <p className="text-xs tracking-[0.2em] text-[var(--muted)]">CREATE ORDER</p>
              <h3 className="mt-1 text-xl text-[var(--ink)]">Quick Purchase</h3>
              <form className="mt-3 grid gap-3" onSubmit={createOrder}>
                <input
                  value={form.productId}
                  onChange={(e) => setForm((old) => ({ ...old, productId: e.target.value }))}
                  placeholder="Product ID"
                  className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
                />
                <select
                  value={form.productId}
                  onChange={(e) => setForm((old) => ({ ...old, productId: e.target.value }))}
                  className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
                  required
                >
                  <option value="">Select product</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.sku})
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min={1}
                  value={form.quantity}
                  onChange={(e) => setForm((old) => ({ ...old, quantity: Number(e.target.value) }))}
                  className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
                />
                <button type="submit" className="btn-brand rounded-xl px-3 py-2 text-sm font-semibold">
                  Create Purchase
                </button>
              </form>
            </section>

            <section className="card-surface rounded-2xl p-5">
              <p className="text-xs tracking-[0.2em] text-[var(--muted)]">ORDER DETAIL</p>
              <h3 className="mt-1 text-xl text-[var(--ink)]">Lookup by ID</h3>
              <div className="mt-3 grid gap-3">
                <input
                  value={selectedId}
                  onChange={(e) => setSelectedId(e.target.value)}
                  placeholder="Order ID"
                  className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
                />
                <button
                  onClick={() => {
                    void loadDetail();
                  }}
                  className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm font-semibold text-[var(--ink)] hover:bg-[var(--brand-soft)]"
                >
                  Load Detail
                </button>
              </div>

              {!selectedDetail && (
                <p className="mt-3 rounded-xl border border-dashed border-[var(--line)] p-3 text-xs text-[var(--muted)]">
                  No order detail selected.
                </p>
              )}

              {selectedDetail && (
                <div className="mt-3 grid gap-3 rounded-xl border border-[var(--line)] bg-[var(--surface)] p-3 text-xs">
                  <p className="font-mono text-[11px] text-[var(--muted)]">{selectedDetail.id}</p>
                  <p className="text-[var(--muted)]">
                    Placed: <span className="text-[var(--ink)]">{new Date(selectedDetail.createdAt).toLocaleString()}</span>
                  </p>
                  <div className="overflow-hidden rounded-lg border border-[var(--line)]">
                    <table className="w-full text-left">
                      <thead className="bg-[#f0e8dd] text-[var(--ink)]">
                        <tr>
                          <th className="px-2 py-1 font-medium">Item</th>
                          <th className="px-2 py-1 font-medium">Qty</th>
                          <th className="px-2 py-1 font-medium">Row ID</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedDetail.items?.map((row, idx) => (
                          <tr key={row.id || `${row.item}-${idx}`} className="border-t border-[var(--line)]">
                            <td className="px-2 py-1">{row.item}</td>
                            <td className="px-2 py-1">{row.quantity}</td>
                            <td className="px-2 py-1 font-mono text-[10px] text-[var(--muted)]">{row.id || "legacy-row"}</td>
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

        <p className="mt-5 text-xs text-[var(--muted)]">{status}</p>
      </section>
    </main>
  );
}
