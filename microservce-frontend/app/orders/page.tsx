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

type PagedOrder = {
  content: Order[];
  totalElements: number;
};

export default function OrdersPage() {
  const router = useRouter();
  const session = useAuthSession();

  const [orders, setOrders] = useState<Order[]>([]);
  const [status, setStatus] = useState("Loading session...");
  const [form, setForm] = useState({ item: "", quantity: 1 });
  const [selectedId, setSelectedId] = useState("");
  const [selectedDetail, setSelectedDetail] = useState<Record<string, unknown> | null>(null);

  const loadOrders = useCallback(async () => {
    if (!session.apiClient) return;
    const res = await session.apiClient.get("/orders/me");
    const data = res.data as PagedOrder;
    setOrders(data.content || []);
  }, [session.apiClient]);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }

    const run = async () => {
      try {
        await session.ensureCustomer();
        await loadOrders();
        setStatus("Loaded your orders.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load orders.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.ensureCustomer, loadOrders]);

  const createOrder = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient) return;
    setStatus("Creating order...");
    try {
      await session.apiClient.post("/orders/me", {
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

  const loadDetail = async () => {
    if (!session.apiClient || !selectedId.trim()) return;
    setStatus("Loading order detail...");
    try {
      const res = await session.apiClient.get(`/orders/me/${selectedId.trim()}`);
      setSelectedDetail(res.data as Record<string, unknown>);
      setStatus("Order detail loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load detail.");
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return <main className="mx-auto min-h-screen max-w-6xl px-6 py-10 text-zinc-700">Loading...</main>;
  }

  if (!session.isAuthenticated) {
    return null;
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-10">
      <AppNav
        email={(session.profile?.email as string) || ""}
        onLogout={() => {
          void session.logout();
        }}
      />

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
                onClick={loadDetail}
                className="rounded-xl bg-zinc-900 px-3 py-2 text-sm font-semibold text-white hover:bg-zinc-700"
              >
                GET /orders/me/{"{id}"}
              </button>
            </div>
            <pre className="mt-3 max-h-52 overflow-auto rounded-xl bg-zinc-900 p-3 text-xs text-zinc-200">
              {selectedDetail ? JSON.stringify(selectedDetail, null, 2) : "No order detail loaded."}
            </pre>
          </section>
        </div>
      </section>
      <p className="mt-5 text-xs text-zinc-500">{status}</p>
    </main>
  );
}
