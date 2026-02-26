"use client";

import type { AxiosInstance } from "axios";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { money } from "../../lib/format";
import type { WishlistItem, WishlistResponse } from "../../lib/types/wishlist";
import { emptyWishlist } from "../../lib/types/wishlist";

type Props = { apiClient?: AxiosInstance | null };

const popupStyle: React.CSSProperties = {
  position: "absolute",
  right: 0,
  top: "calc(100% + 8px)",
  zIndex: 50,
  width: "290px",
  padding: "14px",
  "--popup-accent": "rgba(124,58,237,0.2)",
} as React.CSSProperties;

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
      const raw = res.data as Record<string, unknown>;
      const items = (Array.isArray(raw.content) ? raw.content : raw.items || []) as WishlistItem[];
      const itemCount = Number((raw.page as Record<string, unknown> | undefined)?.totalElements ?? raw.itemCount ?? items.length);
      setWishlist({ keycloakId: (raw.keycloakId as string) || "", items, itemCount });
    } catch { setWishlist(emptyWishlist); }
    finally { setLoading(false); }
  }, [apiClient]);

  useEffect(() => {
    const media = window.matchMedia("(min-width: 768px)");
    const sync = () => setDesktop(media.matches);
    sync();
    media.addEventListener("change", sync);
    return () => { media.removeEventListener("change", sync); };
  }, []);

  useEffect(() => { if (!desktop && open) setOpen(false); }, [desktop, open]);
  useEffect(() => { void loadWishlist(); }, [loadWishlist, pathname]);
  useEffect(() => { if (!open) return; void loadWishlist(); }, [open, loadWishlist]);

  // Re-fetch whenever any page emits 'wishlist-updated'
  useEffect(() => {
    const handler = () => { void loadWishlist(); };
    window.addEventListener("wishlist-updated", handler);
    return () => window.removeEventListener("wishlist-updated", handler);
  }, [loadWishlist]);

  const previewItems = wishlist.items.slice(0, 3);

  return (
    <div
      className="relative"
      onMouseEnter={() => { if (desktop) setOpen(true); }}
      onMouseLeave={() => { if (desktop) setOpen(false); }}
    >
      <Link
        href="/wishlist"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full bg-white/[0.08] text-white no-underline transition-[background] duration-200"
        aria-label="Open wishlist"
        aria-haspopup="true"
        aria-expanded={open}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
        </svg>
        <span
          className="absolute -right-1.5 -top-1.5 grid min-h-[18px] min-w-[18px] place-items-center rounded-[20px] bg-[linear-gradient(135deg,#7c3aed,#00d4ff)] px-1 text-[10px] font-extrabold text-white"
          aria-live="polite"
          aria-atomic="true"
        >
          {wishlist.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div className="popup-dropdown" style={popupStyle} role="dialog" aria-label="Wishlist preview" aria-hidden={!open}>
          <div className="mb-3 flex items-center justify-between">
            <p className="m-0 font-[Syne,sans-serif] text-base font-extrabold text-white">Wishlist</p>
            <Link href="/wishlist" className="text-[0.72rem] font-bold text-accent-light no-underline">Open Wishlist →</Link>
          </div>

          {loading && (
            <div className="flex items-center gap-2 rounded-[8px] bg-[rgba(124,58,237,0.04)] px-3 py-2.5 text-[0.78rem] text-muted">
              <span className="spinner-sm" /> Loading wishlist...
            </div>
          )}

          {!loading && wishlist.itemCount === 0 && (
            <p className="m-0 rounded-[8px] bg-[rgba(124,58,237,0.03)] px-3 py-2.5 text-[0.78rem] text-muted">Your wishlist is empty.</p>
          )}

          {!loading && wishlist.itemCount > 0 && (
            <>
              <div className="flex flex-col gap-1.5">
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="block rounded-[8px] border border-[rgba(124,58,237,0.1)] bg-[rgba(124,58,237,0.03)] px-2.5 py-2 no-underline transition-all duration-150 hover:border-[rgba(124,58,237,0.25)] hover:bg-[rgba(124,58,237,0.08)]"
                  >
                    <p className="m-0 mb-0.5 overflow-hidden text-ellipsis whitespace-nowrap text-[0.78rem] font-semibold text-[#c8c8e8]">{item.productName}</p>
                    <p className="m-0 text-[0.68rem] text-muted">
                      {item.productType}{item.sellingPriceSnapshot !== null ? ` · ${money(item.sellingPriceSnapshot)}` : ""}
                    </p>
                  </Link>
                ))}
              </div>
              {wishlist.itemCount > previewItems.length && (
                <p className="mt-1.5 text-[0.68rem] text-muted">+{wishlist.itemCount - previewItems.length} more item(s)</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
