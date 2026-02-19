"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
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
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto w-12 h-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
          <p className="mt-4">Loading...</p>
        </div>
      </div>
    );
  }

  if (!session.isAuthenticated) {
    return null;
  }

  const orders = ordersPage?.content || [];
  const currentPage = ordersPage?.number ?? page;
  const totalPages = ordersPage?.totalPages ?? 0;
  const totalElements = ordersPage?.totalElements ?? 0;

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        onLogout={() => { void session.logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <Link href="/admin/products">Admin</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Orders</span>
        </nav>

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN</p>
            <h1 className="text-2xl font-bold text-[var(--ink)]">📋 All Orders</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">Manage and inspect all customer orders</p>
          </div>
          <span className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs font-bold text-white">{totalElements} total</span>
        </div>

        <section className="animate-rise space-y-4 rounded-xl bg-white p-5 shadow-sm">
          {/* Filter Form */}
          <form onSubmit={applyFilter} className="flex flex-wrap items-center gap-2">
            <div className="relative flex min-w-[280px] flex-1 items-center rounded-lg border border-[var(--line)] bg-white">
              <span className="pl-3 text-[var(--muted)]">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
              </span>
              <input
                value={customerIdInput}
                onChange={(e) => setCustomerIdInput(e.target.value)}
                placeholder="Filter by Customer ID (UUID)..."
                className="flex-1 border-none bg-transparent px-3 py-2.5 text-sm outline-none"
              />
              {customerIdInput && (
                <button
                  type="button"
                  onClick={() => { setCustomerIdInput(""); setCustomerIdFilter(""); }}
                  className="mr-2 flex h-6 w-6 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-600 hover:bg-gray-300"
                  title="Clear filter"
                >
                  ×
                </button>
              )}
            </div>
            <button type="submit" className="btn-primary px-5 py-2.5 text-sm">
              Apply Filter
            </button>
          </form>

          <div className="overflow-hidden rounded-xl border border-[var(--line)]">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Customer ID</th>
                  <th>Item</th>
                  <th>Qty</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {orders.length === 0 && (
                  <tr>
                    <td colSpan={5}>
                      <div className="empty-state">
                        <div className="empty-state-icon">📦</div>
                        <p className="empty-state-title">No orders found</p>
                        <p className="empty-state-desc">{customerIdFilter ? "Try a different customer ID filter" : "No orders exist yet"}</p>
                      </div>
                    </td>
                  </tr>
                )}
                {orders.map((order) => (
                  <tr key={order.id}>
                    <td className="font-mono text-xs text-[var(--ink)]">{order.id}</td>
                    <td className="font-mono text-xs text-[var(--ink)]">{order.customerId}</td>
                    <td className="font-medium text-[var(--ink)]">{order.item}</td>
                    <td>
                      <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700">{order.quantity}</span>
                    </td>
                    <td className="text-xs text-[var(--muted)]">{new Date(order.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(p) => { void goToPage(p); }}
          />
          <p className="text-xs text-[var(--muted)]">{status}</p>
        </section>
      </main>

      <Footer />
    </div>
  );
}
