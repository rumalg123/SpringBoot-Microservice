"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

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

const emptyWishlist: WishlistResponse = {
  items: [],
  itemCount: 0,
};

function money(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return "-";
  }
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function WishlistPage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus,
    isAuthenticated,
    profile,
    logout,
    canViewAdmin,
    apiClient,
    emailVerified,
  } = session;

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
      setWishlist({
        items: data.items || [],
        itemCount: Number(data.itemCount || 0),
      });
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
    if (!isAuthenticated) {
      router.replace("/");
      return;
    }
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
    } finally {
      setRemovingItemId(null);
    }
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
    } finally {
      setClearing(false);
    }
  };

  const moveToCart = async (item: WishlistItem) => {
    if (!apiClient || busy) return;
    if ((item.productType || "").toUpperCase() === "PARENT") {
      toast.error("Parent products cannot be bought directly. Select a variation.");
      return;
    }
    setMovingItemId(item.id);
    try {
      await apiClient.post("/cart/me/items", {
        productId: item.productId,
        quantity: 1,
      });
      await apiClient.delete(`/wishlist/me/items/${item.id}`);
      setWishlist((old) => {
        const nextItems = old.items.filter((entry) => entry.id !== item.id);
        return { items: nextItems, itemCount: nextItems.length };
      });
      toast.success("Moved to cart");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item to cart");
    } finally {
      setMovingItemId(null);
    }
  };

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
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <span className="breadcrumb-current">Wishlist</span>
        </nav>

        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--ink)]">My Wishlist</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">{status}</p>
          </div>
          <div className="flex gap-2">
            <Link href="/products" className="btn-outline no-underline px-4 py-2 text-sm">
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearWishlist(); }}
              disabled={busy || wishlist.itemCount === 0}
              className="btn-danger px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {clearing ? "Clearing..." : "Clear Wishlist"}
            </button>
          </div>
        </div>

        {wishlist.itemCount === 0 && !loading && (
          <div className="empty-state">
            <div className="empty-state-icon">W</div>
            <p className="empty-state-title">Wishlist is empty</p>
            <p className="empty-state-desc">Save products here and revisit them anytime.</p>
            <Link href="/products" className="btn-primary no-underline px-6 py-2.5 text-sm">
              Browse Products
            </Link>
          </div>
        )}

        <div className="space-y-3">
          {wishlist.items.map((item) => (
            <article key={item.id} className="rounded-xl bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex-1">
                  <Link
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="text-base font-semibold text-[var(--ink)] no-underline hover:text-[var(--brand)]"
                  >
                    {item.productName}
                  </Link>
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[var(--muted)]">
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 uppercase tracking-wide">
                      {item.productType}
                    </span>
                    <span>Price: {money(item.sellingPriceSnapshot)}</span>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <button
                    onClick={() => { void moveToCart(item); }}
                    disabled={busy || (item.productType || "").toUpperCase() === "PARENT"}
                    className="btn-primary px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {movingItemId === item.id ? "Moving..." : ((item.productType || "").toUpperCase() === "PARENT" ? "Select Variation" : "Move to Cart")}
                  </button>
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
        </div>
      </main>

      <Footer />
    </div>
  );
}
