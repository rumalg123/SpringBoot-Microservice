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
type WishlistResponse = { items: WishlistItem[]; itemCount: number };
type Props = { apiClient?: AxiosInstance | null };

const emptyWishlist: WishlistResponse = { items: [], itemCount: 0 };

function money(value: number | null): string {
  if (value === null || Number.isNaN(value)) return "";
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

const popupStyle: React.CSSProperties = {
  position: "absolute",
  right: 0,
  top: "calc(100% + 8px)",
  zIndex: 50,
  width: "290px",
  borderRadius: "16px",
  border: "1px solid rgba(124,58,237,0.2)",
  background: "rgba(13,13,31,0.97)",
  backdropFilter: "blur(20px)",
  boxShadow: "0 20px 60px rgba(0,0,0,0.7), 0 0 0 1px rgba(124,58,237,0.08)",
  padding: "14px",
};

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
      setWishlist({ items: data.items || [], itemCount: Number(data.itemCount || 0) });
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

  const previewItems = wishlist.items.slice(0, 3);

  return (
    <div
      style={{ position: "relative" }}
      onMouseEnter={() => { if (desktop) setOpen(true); }}
      onMouseLeave={() => { if (desktop) setOpen(false); }}
    >
      <Link
        href="/wishlist"
        style={{ position: "relative", display: "inline-flex", alignItems: "center", justifyContent: "center", width: "40px", height: "40px", borderRadius: "50%", background: "rgba(255,255,255,0.08)", color: "#fff", textDecoration: "none", transition: "background 0.2s" }}
        aria-label="Open wishlist"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
        </svg>
        <span style={{ position: "absolute", top: "-6px", right: "-6px", minWidth: "18px", minHeight: "18px", borderRadius: "20px", background: "linear-gradient(135deg, #7c3aed, #00d4ff)", fontSize: "10px", fontWeight: 800, color: "#fff", display: "grid", placeItems: "center", padding: "0 4px" }}>
          {wishlist.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div style={popupStyle}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
            <p style={{ fontSize: "0.875rem", fontWeight: 800, color: "#fff", margin: 0, fontFamily: "'Syne', sans-serif" }}>Wishlist</p>
            <Link href="/wishlist" style={{ fontSize: "0.72rem", fontWeight: 700, color: "#a78bfa", textDecoration: "none" }}>Open Wishlist →</Link>
          </div>

          {loading && (
            <div style={{ padding: "10px 12px", borderRadius: "8px", background: "rgba(124,58,237,0.04)", fontSize: "0.78rem", color: "var(--muted)", display: "flex", alignItems: "center", gap: "8px" }}>
              <span className="spinner-sm" /> Loading wishlist...
            </div>
          )}

          {!loading && wishlist.itemCount === 0 && (
            <p style={{ padding: "10px 12px", borderRadius: "8px", background: "rgba(124,58,237,0.03)", fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>Your wishlist is empty.</p>
          )}

          {!loading && wishlist.itemCount > 0 && (
            <>
              <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    style={{
                      display: "block", padding: "8px 10px", borderRadius: "8px",
                      border: "1px solid rgba(124,58,237,0.1)", background: "rgba(124,58,237,0.03)",
                      textDecoration: "none", transition: "all 0.15s",
                    }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "rgba(124,58,237,0.08)";
                      (e.currentTarget as HTMLElement).style.borderColor = "rgba(124,58,237,0.25)";
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "rgba(124,58,237,0.03)";
                      (e.currentTarget as HTMLElement).style.borderColor = "rgba(124,58,237,0.1)";
                    }}
                  >
                    <p style={{ fontSize: "0.78rem", fontWeight: 600, color: "#c8c8e8", margin: "0 0 2px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.productName}</p>
                    <p style={{ fontSize: "0.68rem", color: "var(--muted)", margin: 0 }}>
                      {item.productType}{item.sellingPriceSnapshot !== null ? ` · ${money(item.sellingPriceSnapshot)}` : ""}
                    </p>
                  </Link>
                ))}
              </div>
              {wishlist.itemCount > previewItems.length && (
                <p style={{ marginTop: "6px", fontSize: "0.68rem", color: "var(--muted)" }}>+{wishlist.itemCount - previewItems.length} more item(s)</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
