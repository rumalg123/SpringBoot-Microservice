"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import { useAuthSession } from "../../../lib/authSession";

type AdminOrder = {
  id: string;
  customerId: string;
  item: string;
  quantity: number;
  createdAt: string;
};

type AdminOrdersPage = {
  content: AdminOrder[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

const DEFAULT_PAGE_SIZE = 20;

export default function AdminOrdersPage() {
  const router = useRouter();
  const session = useAuthSession();

  const [ordersPage, setOrdersPage] = useState<AdminOrdersPage | null>(null);
  const [status, setStatus] = useState("Loading session...");
  const [page, setPage] = useState(0);
  const [customerIdInput, setCustomerIdInput] = useState("");
  const [customerIdFilter, setCustomerIdFilter] = useState("");

  const loadAdminOrders = useCallback(
    async (targetPage: number, targetCustomerId: string) => {
      if (!session.apiClient) return;
      const params = new URLSearchParams();
      params.set("page", String(targetPage));
      params.set("size", String(DEFAULT_PAGE_SIZE));
      params.set("sort", "createdAt,DESC");
      if (targetCustomerId.trim()) {
        params.set("customerId", targetCustomerId.trim());
      }

      const res = await session.apiClient.get(`/admin/orders?${params.toString()}`);
      setOrdersPage(res.data as AdminOrdersPage);
      setPage(targetPage);
    },
    [session.apiClient]
  );

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.canViewAdmin) {
      router.replace("/orders");
      return;
    }

    const run = async () => {
      try {
        await loadAdminOrders(0, customerIdFilter);
        setStatus("Admin orders loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.canViewAdmin, customerIdFilter, loadAdminOrders]);

  const applyFilter = async (e: FormEvent) => {
    e.preventDefault();
    setStatus("Loading filtered orders...");
    const nextFilter = customerIdInput.trim();
    setCustomerIdFilter(nextFilter);
  };

  const goToPage = async (nextPage: number) => {
    if (nextPage < 0) return;
    setStatus("Loading page...");
    try {
      await loadAdminOrders(nextPage, customerIdFilter);
      setStatus("Admin orders loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return <main className="mx-auto min-h-screen max-w-6xl px-6 py-10 text-zinc-700">Loading...</main>;
  }

  if (!session.isAuthenticated) {
    return null;
  }

  const orders = ordersPage?.content || [];
  const currentPage = ordersPage?.number ?? page;
  const totalPages = ordersPage?.totalPages ?? 0;
  const totalElements = ordersPage?.totalElements ?? 0;

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-10">
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        onLogout={() => {
          void session.logout();
        }}
      />

      <section className="grid gap-4 rounded-3xl border border-zinc-200 bg-white/85 p-6 shadow-xl backdrop-blur">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs tracking-widest text-zinc-500">ADMIN</p>
            <h2 className="text-2xl font-semibold text-zinc-900">All Orders</h2>
            <p className="text-sm text-zinc-600">Gateway requires admin permission for this endpoint.</p>
          </div>
          <span className="rounded-full bg-zinc-900 px-3 py-1 text-xs text-white">{totalElements} total</span>
        </div>

        <form onSubmit={applyFilter} className="flex flex-wrap items-center gap-2">
          <input
            value={customerIdInput}
            onChange={(e) => setCustomerIdInput(e.target.value)}
            placeholder="Filter by customerId (UUID)"
            className="min-w-[280px] flex-1 rounded-xl border border-zinc-300 px-3 py-2 text-sm"
          />
          <button type="submit" className="rounded-xl bg-zinc-900 px-4 py-2 text-sm font-semibold text-white">
            Apply Filter
          </button>
          <button
            type="button"
            onClick={() => {
              setCustomerIdInput("");
              setCustomerIdFilter("");
            }}
            className="rounded-xl border border-zinc-300 bg-white px-4 py-2 text-sm text-zinc-700"
          >
            Clear
          </button>
        </form>

        <div className="overflow-hidden rounded-2xl border border-zinc-200">
          <table className="w-full text-left text-sm">
            <thead className="bg-zinc-100 text-zinc-700">
              <tr>
                <th className="px-3 py-2 font-medium">Order ID</th>
                <th className="px-3 py-2 font-medium">Customer ID</th>
                <th className="px-3 py-2 font-medium">Item</th>
                <th className="px-3 py-2 font-medium">Qty</th>
                <th className="px-3 py-2 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-zinc-500">
                    No orders found for this page/filter.
                  </td>
                </tr>
              )}
              {orders.map((order) => (
                <tr key={order.id} className="border-t border-zinc-200">
                  <td className="px-3 py-2 font-mono text-xs text-zinc-700">{order.id}</td>
                  <td className="px-3 py-2 font-mono text-xs text-zinc-700">{order.customerId}</td>
                  <td className="px-3 py-2 text-zinc-900">{order.item}</td>
                  <td className="px-3 py-2 text-zinc-900">{order.quantity}</td>
                  <td className="px-3 py-2 text-zinc-600">{new Date(order.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-zinc-500">
            Page {totalPages === 0 ? 0 : currentPage + 1} of {totalPages}
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => {
                void goToPage(currentPage - 1);
              }}
              disabled={ordersPage?.first ?? true}
              className="rounded-xl border border-zinc-300 bg-white px-4 py-2 text-sm text-zinc-700 disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              onClick={() => {
                void goToPage(currentPage + 1);
              }}
              disabled={ordersPage?.last ?? true}
              className="rounded-xl border border-zinc-300 bg-white px-4 py-2 text-sm text-zinc-700 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
        <p className="text-xs text-zinc-500">{status}</p>
      </section>
    </main>
  );
}
