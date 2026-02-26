"use client";

import type { AxiosInstance } from "axios";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { money } from "../../lib/format";

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
      const res = await apiClient.get<CartResponse>("/cart/me");
      const data = res.data ?? emptyCart;
      setCart({ items: data.items || [], itemCount: Number(data.itemCount || 0), subtotal: Number(data.subtotal || 0) });
    } catch { setCart(emptyCart); }
    finally { setLoadingCart(false); }
  }, [apiClient]);

  const loadAddressPresence = useCallback(async () => {
    if (!apiClient) return;
    setLoadingAddresses(true);
    try {
      const res = await apiClient.get<CustomerAddress[]>("/customers/me/addresses");
      const data = res.data ?? [];
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
      className="relative"
      onMouseEnter={() => { if (desktop) setOpen(true); }}
      onMouseLeave={() => { if (desktop) setOpen(false); }}
    >
      <Link
        href="/cart"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full bg-white/[0.08] text-white no-underline transition-[background] duration-200"
        aria-label="Open cart"
        aria-haspopup="true"
        aria-expanded={open}
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        <span
          className="absolute -right-1.5 -top-1.5 grid min-h-[18px] min-w-[18px] place-items-center rounded-[20px] bg-[linear-gradient(135deg,#00d4ff,#7c3aed)] px-1 text-[10px] font-extrabold text-white"
          aria-live="polite"
          aria-atomic="true"
        >
          {cart.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div style={popupStyle} role="dialog" aria-label="Cart preview" aria-hidden={!open}>
          <div className="mb-3 flex items-center justify-between">
            <p className="m-0 font-[Syne,sans-serif] text-base font-extrabold text-white">Cart</p>
            <Link href="/cart" className="text-[0.72rem] font-bold text-[#00d4ff] no-underline">Open Cart →</Link>
          </div>

          {loadingCart && (
            <div className="flex items-center gap-2 rounded-[8px] bg-[rgba(0,212,255,0.04)] px-3 py-2.5 text-[0.78rem] text-muted">
              <span className="spinner-sm" /> Loading cart...
            </div>
          )}

          {!loadingCart && cart.itemCount === 0 && (
            <p className="m-0 rounded-[8px] bg-[rgba(0,212,255,0.03)] px-3 py-2.5 text-[0.78rem] text-muted">Your cart is empty.</p>
          )}

          {!loadingCart && cart.itemCount > 0 && (
            <>
              <div className="flex flex-col gap-1.5">
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="block rounded-[8px] border border-[rgba(0,212,255,0.08)] bg-[rgba(0,212,255,0.02)] px-2.5 py-2 no-underline transition-all duration-150 hover:border-[rgba(0,212,255,0.2)] hover:bg-[rgba(0,212,255,0.06)]"
                  >
                    <p className="m-0 mb-0.5 overflow-hidden text-ellipsis whitespace-nowrap text-[0.78rem] font-semibold text-[#c8c8e8]">{item.productName}</p>
                    <p className="m-0 text-[0.68rem] text-muted">Qty {item.quantity} · {money(item.lineTotal)}</p>
                  </Link>
                ))}
              </div>

              {cart.itemCount > previewItems.length && (
                <p className="mt-1.5 text-[0.68rem] text-muted">+{cart.itemCount - previewItems.length} more item(s)</p>
              )}

              <div className="mt-3 flex items-center justify-between border-t border-[rgba(0,212,255,0.08)] pt-2.5">
                <span className="text-sm text-muted">Subtotal</span>
                <span className="text-base font-extrabold text-[#00d4ff]">{money(cart.subtotal)}</span>
              </div>

              <button
                type="button"
                onClick={() => { if (canCheckout) router.push("/cart"); }}
                disabled={!canCheckout}
                className={`mt-2.5 w-full rounded-md border-none px-2.5 py-2.5 text-sm font-bold text-white transition-all duration-200 ${
                  canCheckout
                    ? "cursor-pointer opacity-100"
                    : "cursor-not-allowed opacity-60"
                }`}
                style={{
                  background: canCheckout ? "linear-gradient(135deg, #00d4ff, #7c3aed)" : "rgba(255,255,255,0.08)",
                }}
              >
                Go to Checkout
              </button>
              {!canCheckout && checkoutHint && (
                <p className="mt-1.5 text-center text-[0.68rem] text-muted">{checkoutHint}</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
