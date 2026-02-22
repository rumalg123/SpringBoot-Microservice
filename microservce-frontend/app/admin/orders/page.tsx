"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
import { useAuthSession } from "../../../lib/authSession";

type AdminOrder = {
  id: string; customerId: string; item: string; quantity: number; createdAt: string;
};
type AdminOrdersPage = {
  content: AdminOrder[]; number: number; size: number;
  totalElements: number; totalPages: number; first: boolean; last: boolean; empty: boolean;
};

const DEFAULT_PAGE_SIZE = 20;

const glassCard: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)",
  border: "1px solid rgba(0,212,255,0.1)", borderRadius: "16px",
};
const darkInput: React.CSSProperties = {
  flex: 1, padding: "10px 14px", borderRadius: "10px",
  border: "1px solid rgba(0,212,255,0.15)", background: "rgba(0,212,255,0.04)",
  color: "#c8c8e8", fontSize: "0.85rem", outline: "none",
};

export default function AdminOrdersPage() {
  const router = useRouter();
  const session = useAuthSession();

  const [ordersPage, setOrdersPage] = useState<AdminOrdersPage | null>(null);
  const [status, setStatus] = useState("Loading session...");
  const [page, setPage] = useState(0);
  const [customerEmailInput, setCustomerEmailInput] = useState("");
  const [customerEmailFilter, setCustomerEmailFilter] = useState("");
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [filterSubmitting, setFilterSubmitting] = useState(false);

  const loadAdminOrders = useCallback(
    async (targetPage: number, targetCustomerEmail: string) => {
      if (!session.apiClient) return;
      setOrdersLoading(true);
      try {
        const params = new URLSearchParams();
        params.set("page", String(targetPage));
        params.set("size", String(DEFAULT_PAGE_SIZE));
        params.set("sort", "createdAt,DESC");
        if (targetCustomerEmail.trim()) params.set("customerEmail", targetCustomerEmail.trim());
        const res = await session.apiClient.get(`/admin/orders?${params.toString()}`);
        setOrdersPage(res.data as AdminOrdersPage);
        setPage(targetPage);
      } finally { setOrdersLoading(false); }
    },
    [session.apiClient]
  );

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) { router.replace("/"); return; }
    if (!session.canManageAdminOrders) { router.replace("/orders"); return; }
    const run = async () => {
      try {
        await loadAdminOrders(0, "");
        setStatus("Admin orders loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.canManageAdminOrders, loadAdminOrders]);

  const applyFilter = async (e: FormEvent) => {
    e.preventDefault();
    if (ordersLoading || filterSubmitting) return;
    setFilterSubmitting(true);
    const nextFilter = customerEmailInput.trim();
    try {
      setCustomerEmailFilter(nextFilter);
      await loadAdminOrders(0, nextFilter);
      setStatus("Admin orders loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
    } finally { setFilterSubmitting(false); }
  };

  const clearFilter = async () => {
    if (ordersLoading || filterSubmitting) return;
    setFilterSubmitting(true);
    try {
      setCustomerEmailInput(""); setCustomerEmailFilter("");
      await loadAdminOrders(0, "");
      setStatus("Admin orders loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
    } finally { setFilterSubmitting(false); }
  };

  const goToPage = async (nextPage: number) => {
    if (nextPage < 0 || ordersLoading) return;
    try {
      await loadAdminOrders(nextPage, customerEmailFilter);
      setStatus("Admin orders loaded.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load admin orders.");
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }
  if (!session.isAuthenticated) return null;

  const orders = ordersPage?.content || [];
  const currentPage = ordersPage?.number ?? page;
  const totalPages = ordersPage?.totalPages ?? 0;
  const totalElements = ordersPage?.totalElements ?? 0;
  const filterBusy = ordersLoading || filterSubmitting;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <Link href="/admin/products">Admin</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Orders</span>
        </nav>

        {/* Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.12em", color: "#00d4ff", margin: "0 0 4px" }}>ADMIN</p>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>All Orders</h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>Manage and inspect all customer orders</p>
          </div>
          <span style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", padding: "3px 14px", borderRadius: "20px", fontSize: "0.75rem", fontWeight: 800 }}>
            {totalElements} total
          </span>
        </div>

        <section className="animate-rise" style={{ ...glassCard, padding: "20px" }}>
          {/* Filter */}
          <form onSubmit={(e) => { void applyFilter(e); }} style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "10px", marginBottom: "12px" }}>
            <div style={{ position: "relative", display: "flex", alignItems: "center", flex: 1, minWidth: "260px", ...darkInput, padding: 0, overflow: "hidden" }}>
              <span style={{ padding: "0 12px", color: "var(--muted)", flexShrink: 0 }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                </svg>
              </span>
              <input
                value={customerEmailInput}
                onChange={(e) => setCustomerEmailInput(e.target.value)}
                placeholder="Filter by customer email..."
                disabled={filterBusy}
                style={{ flex: 1, border: "none", background: "transparent", color: "#c8c8e8", fontSize: "0.85rem", outline: "none", padding: "10px 0" }}
              />
              {customerEmailInput && (
                <button
                  type="button"
                  onClick={() => { void clearFilter(); }}
                  disabled={filterBusy}
                  style={{ marginRight: "10px", width: "20px", height: "20px", borderRadius: "50%", background: "rgba(0,212,255,0.15)", border: "none", color: "var(--muted)", fontSize: "0.75rem", cursor: "pointer", display: "grid", placeItems: "center", flexShrink: 0 }}
                >
                  ×
                </button>
              )}
            </div>
            <button
              type="submit"
              disabled={filterBusy}
              style={{
                padding: "10px 20px", borderRadius: "10px", border: "none",
                background: filterBusy ? "rgba(0,212,255,0.2)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
                color: "#fff", fontSize: "0.82rem", fontWeight: 700,
                cursor: filterBusy ? "not-allowed" : "pointer",
              }}
            >
              {filterBusy ? "Applying..." : "Apply Filter"}
            </button>
          </form>
          <p style={{ fontSize: "0.68rem", color: "var(--muted-2)", marginBottom: "16px" }}>Use full customer email, e.g. user@example.com</p>

          {/* Table */}
          <div style={{ overflowX: "auto", borderRadius: "12px", border: "1px solid rgba(0,212,255,0.08)" }}>
            <table className="admin-table" style={{ minWidth: "640px" }}>
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
                        <div className="empty-state-icon">
                          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v11m0 0H3m6 0h6m0 0V5m0 11v3a2 2 0 0 1-2 2H9m6-5h3a2 2 0 0 1 2 2v3" />
                          </svg>
                        </div>
                        <p className="empty-state-title">No orders found</p>
                        <p className="empty-state-desc">{customerEmailFilter ? "Try a different customer email filter" : "No orders exist yet"}</p>
                      </div>
                    </td>
                  </tr>
                )}
                {orders.map((order) => (
                  <tr key={order.id}>
                    <td style={{ fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)" }}>{order.id}</td>
                    <td style={{ fontFamily: "monospace", fontSize: "0.65rem", color: "var(--muted-2)" }}>{order.customerId}</td>
                    <td style={{ fontWeight: 600, color: "#c8c8e8" }}>{order.item}</td>
                    <td>
                      <span style={{ borderRadius: "20px", background: "rgba(0,212,255,0.08)", border: "1px solid rgba(0,212,255,0.2)", color: "#00d4ff", padding: "2px 10px", fontSize: "0.72rem", fontWeight: 800 }}>
                        {order.quantity}
                      </span>
                    </td>
                    <td style={{ fontSize: "0.72rem", color: "var(--muted)" }}>{new Date(order.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ marginTop: "16px" }}>
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={(p) => { void goToPage(p); }}
              disabled={ordersLoading}
            />
          </div>
          <p style={{ marginTop: "10px", fontSize: "0.72rem", color: "var(--muted-2)" }}>{status}</p>
        </section>
      </main>

      <Footer />
    </div>
  );
}
