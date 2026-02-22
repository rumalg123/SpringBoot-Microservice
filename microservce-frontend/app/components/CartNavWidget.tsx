"use client";

import type { AxiosInstance } from "axios";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

type CartItem = {
  id: string;
  productSlug: string;
  productName: string;
  quantity: number;
  lineTotal: number;
};
type CartResponse = { items: CartItem[]; itemCount: number; subtotal: number };
type CustomerAddress = { id: string };
type Props = { apiClient?: AxiosInstance | null; emailVerified?: boolean | null };

const emptyCart: CartResponse = { items: [], itemCount: 0, subtotal: 0 };

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value || 0);
}

const popupStyle: React.CSSProperties = {
  position: "absolute",
  right: 0,
  top: "calc(100% + 8px)",
  zIndex: 50,
  width: "300px",
  borderRadius: "16px",
  border: "1px solid rgba(0,212,255,0.15)",
  background: "rgba(13,13,31,0.97)",
  backdropFilter: "blur(20px)",
  boxShadow: "0 20px 60px rgba(0,0,0,0.7), 0 0 0 1px rgba(0,212,255,0.06)",
  padding: "14px",
};

export default function CartNavWidget({ apiClient, emailVerified }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const [cart, setCart] = useState<CartResponse>(emptyCart);
  const [open, setOpen] = useState(false);
  const [desktop, setDesktop] = useState(false);
  const [loadingCart, setLoadingCart] = useState(false);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [hasAddresses, setHasAddresses] = useState<boolean | null>(null);
  const [addressesFetchedAt, setAddressesFetchedAt] = useState<number>(0);

  const loadCart = useCallback(async () => {
    if (!apiClient) return;
    setLoadingCart(true);
    try {
      const res = await apiClient.get("/cart/me");
      const data = (res.data as CartResponse) || emptyCart;
      setCart({ items: data.items || [], itemCount: Number(data.itemCount || 0), subtotal: Number(data.subtotal || 0) });
    } catch { setCart(emptyCart); }
    finally { setLoadingCart(false); }
  }, [apiClient]);

  const loadAddressPresence = useCallback(async () => {
    if (!apiClient) return;
    setLoadingAddresses(true);
    try {
      const res = await apiClient.get("/customers/me/addresses");
      const data = (res.data as CustomerAddress[]) || [];
      setHasAddresses(data.length > 0);
      setAddressesFetchedAt(Date.now());
    } catch { setHasAddresses(false); setAddressesFetchedAt(Date.now()); }
    finally { setLoadingAddresses(false); }
  }, [apiClient]);

  useEffect(() => {
    const media = window.matchMedia("(min-width: 768px)");
    const sync = () => setDesktop(media.matches);
    sync();
    media.addEventListener("change", sync);
    return () => { media.removeEventListener("change", sync); };
  }, []);

  useEffect(() => { if (!desktop && open) setOpen(false); }, [desktop, open]);
  useEffect(() => { void loadCart(); }, [loadCart, pathname]);
  useEffect(() => { if (!open) return; void loadCart(); }, [open, loadCart]);

  // Re-fetch whenever any page emits 'cart-updated' (e.g. after add-to-cart)
  useEffect(() => {
    const handler = () => { void loadCart(); };
    window.addEventListener("cart-updated", handler);
    return () => window.removeEventListener("cart-updated", handler);
  }, [loadCart]);

  useEffect(() => {
    if (!open || !apiClient || cart.itemCount <= 0 || emailVerified === false) return;
    const stale = Date.now() - addressesFetchedAt > 30_000;
    if (hasAddresses === null || stale) void loadAddressPresence();
  }, [open, apiClient, cart.itemCount, emailVerified, hasAddresses, addressesFetchedAt, loadAddressPresence]);

  const canCheckout = useMemo(() => cart.itemCount > 0 && emailVerified !== false && hasAddresses === true, [cart.itemCount, emailVerified, hasAddresses]);
  const checkoutHint = useMemo(() => {
    if (cart.itemCount <= 0) return "Cart is empty";
    if (emailVerified === false) return "Verify email before checkout";
    if (loadingAddresses) return "Checking addresses...";
    if (hasAddresses === false) return "Add an address in profile";
    return "";
  }, [cart.itemCount, emailVerified, loadingAddresses, hasAddresses]);

  const previewItems = cart.items.slice(0, 3);

  return (
    <div
      style={{ position: "relative" }}
      onMouseEnter={() => { if (desktop) setOpen(true); }}
      onMouseLeave={() => { if (desktop) setOpen(false); }}
    >
      <Link
        href="/cart"
        style={{ position: "relative", display: "inline-flex", alignItems: "center", justifyContent: "center", width: "40px", height: "40px", borderRadius: "50%", background: "rgba(255,255,255,0.08)", color: "#fff", textDecoration: "none", transition: "background 0.2s" }}
        aria-label="Open cart"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        <span style={{ position: "absolute", top: "-6px", right: "-6px", minWidth: "18px", minHeight: "18px", borderRadius: "20px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", fontSize: "10px", fontWeight: 800, color: "#fff", display: "grid", placeItems: "center", padding: "0 4px" }}>
          {cart.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div style={popupStyle}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
            <p style={{ fontSize: "0.875rem", fontWeight: 800, color: "#fff", margin: 0, fontFamily: "'Syne', sans-serif" }}>Cart</p>
            <Link href="/cart" style={{ fontSize: "0.72rem", fontWeight: 700, color: "#00d4ff", textDecoration: "none" }}>Open Cart →</Link>
          </div>

          {loadingCart && (
            <div style={{ padding: "10px 12px", borderRadius: "8px", background: "rgba(0,212,255,0.04)", fontSize: "0.78rem", color: "var(--muted)", display: "flex", alignItems: "center", gap: "8px" }}>
              <span className="spinner-sm" /> Loading cart...
            </div>
          )}

          {!loadingCart && cart.itemCount === 0 && (
            <p style={{ padding: "10px 12px", borderRadius: "8px", background: "rgba(0,212,255,0.03)", fontSize: "0.78rem", color: "var(--muted)", margin: 0 }}>Your cart is empty.</p>
          )}

          {!loadingCart && cart.itemCount > 0 && (
            <>
              <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    style={{
                      display: "block", padding: "8px 10px", borderRadius: "8px",
                      border: "1px solid rgba(0,212,255,0.08)", background: "rgba(0,212,255,0.02)",
                      textDecoration: "none", transition: "all 0.15s",
                    }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "rgba(0,212,255,0.06)";
                      (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,212,255,0.2)";
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.background = "rgba(0,212,255,0.02)";
                      (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,212,255,0.08)";
                    }}
                  >
                    <p style={{ fontSize: "0.78rem", fontWeight: 600, color: "#c8c8e8", margin: "0 0 2px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.productName}</p>
                    <p style={{ fontSize: "0.68rem", color: "var(--muted)", margin: 0 }}>Qty {item.quantity} · {money(item.lineTotal)}</p>
                  </Link>
                ))}
              </div>

              {cart.itemCount > previewItems.length && (
                <p style={{ marginTop: "6px", fontSize: "0.68rem", color: "var(--muted)" }}>+{cart.itemCount - previewItems.length} more item(s)</p>
              )}

              <div style={{ marginTop: "12px", paddingTop: "10px", borderTop: "1px solid rgba(0,212,255,0.08)", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>Subtotal</span>
                <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "#00d4ff" }}>{money(cart.subtotal)}</span>
              </div>

              <button
                type="button"
                onClick={() => { if (canCheckout) router.push("/cart"); }}
                disabled={!canCheckout}
                style={{
                  marginTop: "10px", width: "100%", padding: "10px", borderRadius: "10px", border: "none",
                  background: canCheckout ? "linear-gradient(135deg, #00d4ff, #7c3aed)" : "rgba(255,255,255,0.08)",
                  color: "#fff", fontSize: "0.8rem", fontWeight: 700,
                  cursor: canCheckout ? "pointer" : "not-allowed",
                  opacity: canCheckout ? 1 : 0.6,
                  transition: "all 0.2s",
                }}
              >
                Go to Checkout
              </button>
              {!canCheckout && checkoutHint && (
                <p style={{ marginTop: "6px", fontSize: "0.68rem", color: "var(--muted)", textAlign: "center" }}>{checkoutHint}</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
