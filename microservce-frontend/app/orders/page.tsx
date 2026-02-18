"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
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
  totalElements: number;
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
  const [status, setStatus] = useState("Loading session...");
  const [form, setForm] = useState({ item: "", quantity: 1 });
  const [selectedId, setSelectedId] = useState("");
  const [selectedDetail, setSelectedDetail] = useState<OrderDetail | null>(null);

  const loadOrders = useCallback(async () => {
    if (!apiClient) return;
    const res = await apiClient.get("/orders/me");
    const data = res.data as PagedOrder;
    setOrders(data.content || []);
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) {
      router.replace("/");
      return;
    }
    if (canViewAdmin) {
      router.replace("/admin/orders");
      return;
    }

    const run = async () => {
      try {
        await ensureCustomer();
        await loadOrders();
        setStatus("Loaded your orders.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load orders.");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, canViewAdmin, ensureCustomer, loadOrders]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiClient) return;
    setStatus("Creating order...");
    try {
      await apiClient.post("/orders/me", {
        item: form.item.trim(),
        quantity: Number(form.quantity),
      });
      setForm({ item: "", quantity: 1 });
      await loadOrders();
      setStatus("Order created.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Order creation failed.");
    }
  };

  const loadDetail = async (orderId?: string) => {
    const targetId = (orderId || selectedId).trim();
    if (!apiClient || !targetId) return;
    setStatus("Loading order detail...");
    try {
      const res = await apiClient.get(`/orders/me/${targetId}`);
      setSelectedDetail(res.data as OrderDetail);
      setSelectedId(targetId);
      setStatus("Order detail loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load detail.");
    }
  };

  const resendVerification = async () => {
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <main className="mx-auto min-h-screen max-w-6xl px-6 py-10 text-zinc-700">Loading...</main>;
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-10">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        onLogout={() => {
          void logout();
        }}
      />
      {emailVerified === false && (
        <div className="mb-4 rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <p>Your email is not verified. Customer and order endpoints are blocked until verification.</p>
          <button
            onClick={() => {
              void resendVerification();
            }}
            className="mt-2 rounded-lg bg-amber-600 px-3 py-1 text-xs font-semibold text-white hover:bg-amber-500"
          >
            Resend Verification Email
          </button>
        </div>
      )}

      <section className="grid gap-6 lg:grid-cols-[1.15fr,0.85fr]">
        <div className="rounded-3xl border border-zinc-200 bg-white/85 p-6 shadow-xl backdrop-blur">
          <div className="mb-4 flex items-end justify-between">
            <div>
              <p className="text-xs tracking-widest text-zinc-500">MY ORDERS</p>
              <h2 className="text-2xl font-semibold text-zinc-900">Order Timeline</h2>
            </div>
            <span className="rounded-full bg-zinc-900 px-3 py-1 text-xs text-white">
              {orders.length} orders
            </span>
          </div>

          <div className="grid gap-3">
            {orders.length === 0 && (
              <p className="rounded-xl border border-dashed border-zinc-300 p-4 text-sm text-zinc-500">
                No orders yet. Create your first order from the panel.
              </p>
            )}
            {orders.map((order) => (
              <article
                key={order.id}
                className="rounded-2xl border border-zinc-200 bg-zinc-50 p-4 transition hover:border-zinc-400"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-base font-semibold text-zinc-900">{order.item}</h3>
                  <span className="rounded-full bg-zinc-900 px-2 py-1 text-xs text-white">
                    Qty {order.quantity}
                  </span>
                </div>
                <p className="mt-2 break-all font-mono text-[11px] text-zinc-500">{order.id}</p>
                <p className="mt-1 text-xs text-zinc-500">
                  {new Date(order.createdAt).toLocaleString()}
                </p>
                <button
                  onClick={() => {
                    void loadDetail(order.id);
                  }}
                  className="mt-3 rounded-lg border border-zinc-300 bg-white px-3 py-1 text-xs font-medium text-zinc-700 hover:bg-zinc-100"
                >
                  Open Details
                </button>
              </article>
            ))}
          </div>
        </div>

        <div className="grid gap-4">
          <section className="rounded-3xl border border-zinc-200 bg-white/85 p-5 shadow-xl backdrop-blur">
            <p className="text-xs tracking-widest text-zinc-500">CREATE</p>
            <h3 className="mb-3 text-lg font-semibold text-zinc-900">New Order</h3>
            <form className="grid gap-3" onSubmit={createOrder}>
              <input
                value={form.item}
                onChange={(e) => setForm((old) => ({ ...old, item: e.target.value }))}
                placeholder="Item name"
                required
                className="rounded-xl border border-zinc-300 px-3 py-2 text-sm"
              />
              <input
                type="number"
                min={1}
                value={form.quantity}
                onChange={(e) => setForm((old) => ({ ...old, quantity: Number(e.target.value) }))}
                className="rounded-xl border border-zinc-300 px-3 py-2 text-sm"
              />
              <button
                type="submit"
                className="rounded-xl bg-orange-500 px-3 py-2 text-sm font-semibold text-white hover:bg-orange-400"
              >
                Create My Order
              </button>
            </form>
          </section>

          <section className="rounded-3xl border border-zinc-200 bg-white/85 p-5 shadow-xl backdrop-blur">
            <p className="text-xs tracking-widest text-zinc-500">DETAIL</p>
            <h3 className="mb-3 text-lg font-semibold text-zinc-900">Load One Order</h3>
            <div className="grid gap-3">
              <input
                value={selectedId}
                onChange={(e) => setSelectedId(e.target.value)}
                placeholder="Order ID"
                className="rounded-xl border border-zinc-300 px-3 py-2 text-sm"
              />
              <button
                onClick={() => {
                  void loadDetail();
                }}
                className="rounded-xl bg-zinc-900 px-3 py-2 text-sm font-semibold text-white hover:bg-zinc-700"
              >
                GET /orders/me/{"{id}"}
              </button>
            </div>
            {!selectedDetail && (
              <p className="mt-3 rounded-xl border border-dashed border-zinc-300 p-3 text-xs text-zinc-500">
                No order detail loaded.
              </p>
            )}
            {selectedDetail && (
              <div className="mt-3 grid gap-3 rounded-xl bg-zinc-900 p-3 text-xs text-zinc-200">
                <p className="font-mono text-[11px] text-zinc-400">{selectedDetail.id}</p>
                <p>
                  <span className="text-zinc-400">Placed:</span>{" "}
                  {new Date(selectedDetail.createdAt).toLocaleString()}
                </p>
                <div className="overflow-hidden rounded-lg border border-zinc-700">
                  <table className="w-full text-left">
                    <thead className="bg-zinc-800">
                      <tr>
                        <th className="px-2 py-1 font-medium">Item</th>
                        <th className="px-2 py-1 font-medium">Qty</th>
                        <th className="px-2 py-1 font-medium">Row ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedDetail.items?.map((row, idx) => (
                        <tr key={row.id || `${row.item}-${idx}`} className="border-t border-zinc-800">
                          <td className="px-2 py-1">{row.item}</td>
                          <td className="px-2 py-1">{row.quantity}</td>
                          <td className="px-2 py-1 font-mono text-[10px] text-zinc-400">
                            {row.id || "legacy-row"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>
        </div>
      </section>
      <p className="mt-5 text-xs text-zinc-500">{status}</p>
    </main>
  );
}
