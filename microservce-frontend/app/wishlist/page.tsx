"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { emitCartUpdate, emitWishlistUpdate } from "../../lib/navEvents";

type WishlistItem = {
  id: string;
  productId: string;
  productSlug: string;
  productName: string;
  productType: string;
  mainImage: string | null;
  sellingPriceSnapshot: number | null;
};

type WishlistResponse = {
  items: WishlistItem[];
  itemCount: number;
};

const emptyWishlist: WishlistResponse = { items: [], itemCount: 0 };

function money(value: number | null): string {
  if (value === null || Number.isNaN(value)) return "—";
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function WishlistPage() {
  const router = useRouter();
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = session;

  const [wishlist, setWishlist] = useState<WishlistResponse>(emptyWishlist);
  const [status, setStatus] = useState("Loading wishlist...");
  const [loading, setLoading] = useState(false);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [movingItemId, setMovingItemId] = useState<string | null>(null);
  const [clearing, setClearing] = useState(false);

  const loadWishlist = useCallback(async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const res = await apiClient.get("/wishlist/me");
      const data = (res.data as WishlistResponse) || emptyWishlist;
      setWishlist({ items: data.items || [], itemCount: Number(data.itemCount || 0) });
      setStatus("Wishlist ready.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load wishlist");
      setWishlist(emptyWishlist);
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    void loadWishlist();
  }, [sessionStatus, isAuthenticated, router, loadWishlist]);

  const busy = removingItemId !== null || movingItemId !== null || clearing || loading;

  const removeItem = async (itemId: string) => {
    if (!apiClient || busy) return;
    setRemovingItemId(itemId);
    try {
      await apiClient.delete(`/wishlist/me/items/${itemId}`);
      setWishlist((old) => {
        const nextItems = old.items.filter((item) => item.id !== itemId);
        return { items: nextItems, itemCount: nextItems.length };
      });
      toast.success("Removed from wishlist");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to remove wishlist item");
    } finally { setRemovingItemId(null); }
  };

  const clearWishlist = async () => {
    if (!apiClient || busy || wishlist.itemCount === 0) return;
    setClearing(true);
    try {
      await apiClient.delete("/wishlist/me");
      setWishlist(emptyWishlist);
      toast.success("Wishlist cleared");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to clear wishlist");
    } finally { setClearing(false); }
  };

  const moveToCart = async (item: WishlistItem) => {
    if (!apiClient || busy) return;
    if ((item.productType || "").toUpperCase() === "PARENT") {
      toast.error("Parent products cannot be bought directly. Select a variation.");
      return;
    }
    setMovingItemId(item.id);
    try {
      await apiClient.post("/cart/me/items", { productId: item.productId, quantity: 1 });
      await apiClient.delete(`/wishlist/me/items/${item.id}`);
      setWishlist((old) => {
        const nextItems = old.items.filter((e) => e.id !== item.id);
        return { items: nextItems, itemCount: nextItems.length };
      });
      toast.success("Moved to cart");
      emitCartUpdate();
      emitWishlistUpdate();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item to cart");
    } finally { setMovingItemId(null); }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Wishlist</span>
        </nav>

        {/* Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>
              My Wishlist
            </h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>
              {status} {wishlist.itemCount > 0 && `· ${wishlist.itemCount} item${wishlist.itemCount !== 1 ? "s" : ""}`}
            </p>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <Link
              href="/products"
              className="no-underline"
              style={{ padding: "9px 18px", borderRadius: "10px", border: "1px solid rgba(0,212,255,0.25)", color: "#00d4ff", background: "rgba(0,212,255,0.06)", fontSize: "0.8rem", fontWeight: 700 }}
            >
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearWishlist(); }}
              disabled={busy || wishlist.itemCount === 0}
              style={{
                padding: "9px 18px", borderRadius: "10px",
                border: "1px solid rgba(239,68,68,0.25)", background: "rgba(239,68,68,0.06)",
                color: "#ef4444", fontSize: "0.8rem", fontWeight: 700,
                cursor: busy || wishlist.itemCount === 0 ? "not-allowed" : "pointer",
                opacity: wishlist.itemCount === 0 ? 0.4 : 1,
              }}
            >
              {clearing ? "Clearing..." : "Clear Wishlist"}
            </button>
          </div>
        </div>

        {/* Empty state */}
        {wishlist.itemCount === 0 && !loading && (
          <div className="empty-state">
            <div className="empty-state-icon">
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
              </svg>
            </div>
            <p className="empty-state-title">Wishlist is empty</p>
            <p className="empty-state-desc">Save products here and revisit them anytime.</p>
            <Link href="/products" className="btn-primary no-underline px-6 py-2.5 text-sm">Browse Products</Link>
          </div>
        )}

        {/* Wishlist Items */}
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          {wishlist.items.map((item) => {
            const isParent = (item.productType || "").toUpperCase() === "PARENT";
            return (
              <article
                key={item.id}
                className="animate-rise"
                style={{
                  background: "rgba(17,17,40,0.7)",
                  backdropFilter: "blur(16px)",
                  border: "1px solid rgba(0,212,255,0.1)",
                  borderRadius: "16px",
                  padding: "18px 20px",
                }}
              >
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: "12px" }}>
                  <div style={{ flex: 1, minWidth: "200px" }}>
                    <Link
                      href={`/products/${encodeURIComponent(item.productSlug)}`}
                      className="no-underline"
                      style={{ fontWeight: 700, color: "#fff", fontSize: "0.95rem" }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "#00d4ff"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "#fff"; }}
                    >
                      {item.productName}
                    </Link>
                    <div style={{ marginTop: "6px", display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                      <span
                        style={{
                          fontSize: "0.65rem", fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase",
                          padding: "2px 10px", borderRadius: "20px",
                          background: "rgba(0,212,255,0.08)", border: "1px solid rgba(0,212,255,0.2)", color: "#00d4ff",
                        }}
                      >
                        {item.productType}
                      </span>
                      <span style={{ fontSize: "0.875rem", fontWeight: 700, color: "#00d4ff" }}>
                        {money(item.sellingPriceSnapshot)}
                      </span>
                    </div>
                  </div>

                  <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                    <button
                      onClick={() => { void moveToCart(item); }}
                      disabled={busy || isParent}
                      style={{
                        padding: "8px 16px", borderRadius: "10px", border: "none",
                        background: busy || isParent ? "rgba(0,212,255,0.2)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
                        color: "#fff", fontSize: "0.78rem", fontWeight: 700,
                        cursor: busy || isParent ? "not-allowed" : "pointer",
                        display: "inline-flex", alignItems: "center", gap: "6px",
                      }}
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                        <path d="M16 10a4 4 0 0 1-8 0" />
                      </svg>
                      {movingItemId === item.id ? "Moving..." : isParent ? "Select Variation" : "Move to Cart"}
                    </button>
                    <button
                      onClick={() => { void removeItem(item.id); }}
                      disabled={busy}
                      style={{
                        padding: "8px 14px", borderRadius: "10px",
                        border: "1px solid rgba(239,68,68,0.25)", background: "rgba(239,68,68,0.06)",
                        color: "#ef4444", fontSize: "0.72rem", fontWeight: 700,
                        cursor: busy ? "not-allowed" : "pointer", opacity: busy ? 0.5 : 1,
                      }}
                    >
                      {removingItemId === item.id ? "Removing..." : "Remove"}
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </main>

      <Footer />
    </div>
  );
}
