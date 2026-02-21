"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { AxiosInstance } from "axios";
import CartNavWidget from "./CartNavWidget";
import WishlistNavWidget from "./WishlistNavWidget";

type Props = {
  email?: string;
  canViewAdmin?: boolean;
  apiClient?: AxiosInstance | null;
  emailVerified?: boolean | null;
  onLogout: () => void | Promise<void>;
};

export default function AppNav({ email, canViewAdmin = false, apiClient = null, emailVerified = null, onLogout }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [logoutPending, setLogoutPending] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const isActive = (href: string) =>
    href === "/"
      ? pathname === "/"
      : pathname === href || pathname.startsWith(`${href}/`);

  const navLinkClass = (href: string) =>
    `px-4 py-2 text-sm font-medium rounded-lg transition-colors no-underline ${isActive(href)
      ? "bg-[var(--brand)] text-white shadow-md"
      : "text-gray-300 hover:bg-white/10 hover:text-white"
    }`;

  return (
    <header className="sticky top-0 z-50 bg-[var(--header-bg)] shadow-lg">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3">
        <Link href="/" className="flex items-center gap-2 text-white no-underline">
          <span className="inline-grid h-8 w-8 place-items-center rounded-md bg-white/10 text-xs font-bold text-white">RS</span>
          <div>
            <p className="text-lg font-bold leading-tight text-white">Rumal Store</p>
            <p className="text-[10px] tracking-[0.15em] text-gray-400">ONLINE MARKETPLACE</p>
          </div>
        </Link>

        <div className="mx-6 hidden flex-1 md:block">
          <div className="flex max-w-xl items-center overflow-hidden rounded-lg bg-white">
            <input
              type="text"
              placeholder="Search products, brands and more..."
              className="flex-1 border-none px-4 py-2.5 text-sm text-[var(--ink)] outline-none"
              readOnly
              onFocus={(e) => {
                e.target.blur();
                router.push("/products");
              }}
            />
            <button
              type="button"
              onClick={() => {
                router.push("/products");
              }}
              className="bg-[var(--brand)] px-5 py-2.5 text-white transition hover:bg-[var(--brand-hover)]"
              aria-label="Open products search"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          <WishlistNavWidget apiClient={apiClient} />
          <CartNavWidget apiClient={apiClient} emailVerified={emailVerified} />
          <span className="hidden rounded-full bg-white/10 px-3 py-1.5 text-xs text-gray-300 md:inline-block">
            {email || "User"}
          </span>
          <button
            disabled={logoutPending}
            onClick={async () => {
              if (logoutPending) return;
              setLogoutPending(true);
              try {
                await onLogout();
              } finally {
                setLogoutPending(false);
              }
            }}
            className="rounded-lg bg-[var(--brand)] px-3 py-2 text-xs font-semibold text-white transition hover:bg-[var(--brand-hover)] disabled:cursor-not-allowed disabled:opacity-60 sm:px-4"
          >
            {logoutPending ? "Logging out..." : "Logout"}
          </button>
          <button
            type="button"
            onClick={() => setMobileOpen((open) => !open)}
            className="text-white md:hidden"
            aria-label={mobileOpen ? "Close navigation" : "Open navigation"}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              {mobileOpen ? (
                <><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></>
              ) : (
                <><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" /></>
              )}
            </svg>
          </button>
        </div>
      </div>

      <nav className={`border-t border-white/10 bg-[#12122a] ${mobileOpen ? "block" : "hidden md:block"}`}>
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-1 px-4 py-2">
          <Link href="/" className={navLinkClass("/")}>Home</Link>
          <Link href="/products" className={navLinkClass("/products")}>Shop</Link>
          <Link href="/wishlist" className={navLinkClass("/wishlist")}>Wishlist</Link>
          <Link href="/cart" className={navLinkClass("/cart")}>Cart</Link>
          <Link href="/orders" className={navLinkClass("/orders")}>My Orders</Link>
          <Link href="/profile" className={navLinkClass("/profile")}>Profile</Link>
          {canViewAdmin && (
            <>
              <span className="mx-2 hidden h-5 w-px bg-white/20 md:inline-block" />
              <Link href="/admin/orders" className={navLinkClass("/admin/orders")}>Admin Orders</Link>
              <Link href="/admin/products" className={navLinkClass("/admin/products")}>Admin Products</Link>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
