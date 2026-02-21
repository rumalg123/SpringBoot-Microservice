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

type CartResponse = {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
};

type CustomerAddress = {
  id: string;
};

type Props = {
  apiClient?: AxiosInstance | null;
  emailVerified?: boolean | null;
};

const emptyCart: CartResponse = {
  items: [],
  itemCount: 0,
  subtotal: 0,
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value || 0);
}

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
      setCart({
        items: data.items || [],
        itemCount: Number(data.itemCount || 0),
        subtotal: Number(data.subtotal || 0),
      });
    } catch {
      setCart(emptyCart);
    } finally {
      setLoadingCart(false);
    }
  }, [apiClient]);

  const loadAddressPresence = useCallback(async () => {
    if (!apiClient) return;
    setLoadingAddresses(true);
    try {
      const res = await apiClient.get("/customers/me/addresses");
      const data = (res.data as CustomerAddress[]) || [];
      setHasAddresses(data.length > 0);
      setAddressesFetchedAt(Date.now());
    } catch {
      setHasAddresses(false);
      setAddressesFetchedAt(Date.now());
    } finally {
      setLoadingAddresses(false);
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
    void loadCart();
  }, [loadCart, pathname]);

  useEffect(() => {
    if (!open) return;
    void loadCart();
  }, [open, loadCart]);

  useEffect(() => {
    if (!open || !apiClient || cart.itemCount <= 0 || emailVerified === false) return;
    const stale = Date.now() - addressesFetchedAt > 30_000;
    if (hasAddresses === null || stale) {
      void loadAddressPresence();
    }
  }, [open, apiClient, cart.itemCount, emailVerified, hasAddresses, addressesFetchedAt, loadAddressPresence]);

  const canCheckout = useMemo(() => {
    return cart.itemCount > 0 && emailVerified !== false && hasAddresses === true;
  }, [cart.itemCount, emailVerified, hasAddresses]);

  const checkoutHint = useMemo(() => {
    if (cart.itemCount <= 0) return "Cart is empty";
    if (emailVerified === false) return "Verify email before checkout";
    if (loadingAddresses) return "Checking addresses...";
    if (hasAddresses === false) return "Add an address in profile before checkout";
    return "";
  }, [cart.itemCount, emailVerified, loadingAddresses, hasAddresses]);

  const previewItems = cart.items.slice(0, 3);

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
        href="/cart"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white transition hover:bg-white/20"
        aria-label="Open cart"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="9" cy="21" r="1" />
          <circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        <span className="absolute -right-1.5 -top-1.5 grid min-h-5 min-w-5 place-items-center rounded-full bg-[var(--brand)] px-1 text-[10px] font-bold text-white">
          {cart.itemCount}
        </span>
      </Link>

      {desktop && open && (
        <div className="absolute right-0 top-full z-50 mt-2 hidden w-80 rounded-xl border border-[var(--line)] bg-white p-3 text-[var(--ink)] shadow-2xl md:block">
          <div className="mb-2 flex items-center justify-between">
            <p className="text-sm font-bold">Cart</p>
            <Link href="/cart" className="text-xs font-semibold text-[var(--brand)] no-underline hover:underline">
              Open Cart
            </Link>
          </div>

          {loadingCart && (
            <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">Loading cart...</p>
          )}

          {!loadingCart && cart.itemCount === 0 && (
            <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">Your cart is empty.</p>
          )}

          {!loadingCart && cart.itemCount > 0 && (
            <>
              <div className="space-y-2">
                {previewItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug)}`}
                    className="block rounded-lg border border-[var(--line)] px-3 py-2 no-underline hover:bg-[#fafafa]"
                  >
                    <p className="line-clamp-1 text-xs font-semibold text-[var(--ink)]">{item.productName}</p>
                    <p className="text-[11px] text-[var(--muted)]">Qty {item.quantity} • {money(item.lineTotal)}</p>
                  </Link>
                ))}
              </div>

              {cart.itemCount > previewItems.length && (
                <p className="mt-2 text-[11px] text-[var(--muted)]">
                  +{cart.itemCount - previewItems.length} more item(s)
                </p>
              )}

              <div className="mt-3 flex items-center justify-between border-t border-[var(--line)] pt-2">
                <span className="text-xs text-[var(--muted)]">Subtotal</span>
                <span className="text-sm font-semibold">{money(cart.subtotal)}</span>
              </div>

              <button
                type="button"
                onClick={() => {
                  if (!canCheckout) return;
                  router.push("/cart");
                }}
                disabled={!canCheckout}
                className="mt-2 w-full rounded-lg bg-[var(--brand)] px-3 py-2 text-xs font-semibold text-white transition hover:bg-[var(--brand-hover)] disabled:cursor-not-allowed disabled:opacity-60"
              >
                Go to Checkout
              </button>
              {!canCheckout && (
                <p className="mt-1 text-[11px] text-[var(--muted)]">{checkoutHint}</p>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
