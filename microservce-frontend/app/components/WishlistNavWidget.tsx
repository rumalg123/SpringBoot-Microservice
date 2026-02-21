"use client";

import type { AxiosInstance } from "axios";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

type WishlistItem = {
  id: string;
  productId: string;
  productSlug: string;
  productName: string;
  productType: string;
  sellingPriceSnapshot: number | null;
};

type WishlistResponse = {
  items: WishlistItem[];
  itemCount: number;
};

type Props = {
  apiClient?: AxiosInstance | null;
};

const emptyWishlist: WishlistResponse = {
  items: [],
  itemCount: 0,
};

function money(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return "";
  }
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function WishlistNavWidget({ apiClient }: Props) {
  const pathname = usePathname();
  const [wishlist, setWishlist] = useState<WishlistResponse>(emptyWishlist);
  const [open, setOpen] = useState(false);
  const [desktop, setDesktop] = useState(false);
  const [loading, setLoading] = useState(false);

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
    } catch {
      setWishlist(emptyWishlist);
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    const media = window.matchMedia("(min-width: 768px)");
    const sync = () => setDesktop(media.matches);
    sync();
    media.addEventListener("change", sync);
    return () => {
      media.removeEventListener("change", sync);
    };
  }, []);

  useEffect(() => {
    if (!desktop && open) {
      setOpen(false);
    }
  }, [desktop, open]);

  useEffect(() => {
    void loadWishlist();
  }, [loadWishlist, pathname]);

  useEffect(() => {
    if (!open) return;
    void loadWishlist();
  }, [open, loadWishlist]);

  const previewItems = wishlist.items.slice(0, 3);

  return (
    <div
      className="relative"
      onMouseEnter={() => {
        if (desktop) setOpen(true);
      }}
      onMouseLeave={() => {
        if (desktop) setOpen(false);
      }}
    >
      <Link
        href="/wishlist"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white transition hover:bg-white/20"
        aria-label="Open wishlist"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
        </svg>
        <span className="absolute -right-1.5 -top-1.5 grid min-h-5 min-w-5 place-items-center rounded-full bg-[var(--brand)] px-1 text-[10px] font-bold text-white">
          {wishlist.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div className="absolute right-0 top-full z-50 hidden w-80 rounded-xl border border-[var(--line)] bg-white p-3 text-[var(--ink)] shadow-2xl md:block">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-sm font-bold">Wishlist</p>
            <Link href="/wishlist" className="text-xs font-semibold text-[var(--brand)] no-underline hover:underline">
              Open Wishlist
            </Link>
          </div>

          {loading && (
            <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">Loading wishlist...</p>
          )}

          {!loading && wishlist.itemCount === 0 && (
            <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">Your wishlist is empty.</p>
          )}

          {!loading && wishlist.itemCount > 0 && (
            <>
              <div className="space-y-2">
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="block rounded-lg border border-[var(--line)] px-3 py-2 no-underline hover:bg-[#fafafa]"
                  >
                    <p className="line-clamp-1 text-xs font-semibold text-[var(--ink)]">{item.productName}</p>
                    <p className="text-[11px] text-[var(--muted)]">
                      {item.productType}
                      {item.sellingPriceSnapshot !== null ? ` | ${money(item.sellingPriceSnapshot)}` : ""}
                    </p>
                  </Link>
                ))}
              </div>
              {wishlist.itemCount > previewItems.length && (
                <p className="mt-2 text-[11px] text-[var(--muted)]">
                  +{wishlist.itemCount - previewItems.length} more item(s)
                </p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
