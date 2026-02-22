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
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);

  return (
    <header
      className="sticky top-0 z-50 transition-all duration-300"
      style={{
        background: scrolled
          ? "rgba(8,8,18,0.92)"
          : "rgba(8,8,18,0.75)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
        borderBottom: "1px solid rgba(0,212,255,0.1)",
        boxShadow: scrolled ? "0 4px 32px rgba(0,0,0,0.5)" : "none",
      }}
    >
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-3 no-underline group" style={{ flexShrink: 0 }}>
          <div
            className="flex h-9 w-9 items-center justify-center rounded-xl text-xs font-black"
            style={{
              background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
              boxShadow: "0 0 16px rgba(0,212,255,0.35)",
              color: "#fff",
              letterSpacing: "0.02em",
            }}
          >
            RS
          </div>
          <div>
            <p
              className="text-base font-black leading-tight"
              style={{ fontFamily: "'Syne', sans-serif", color: "#fff", letterSpacing: "-0.01em" }}
            >
              Rumal Store
            </p>
            <p className="text-[9px] font-semibold tracking-[0.2em]" style={{ color: "rgba(0,212,255,0.6)" }}>
              ONLINE MARKETPLACE
            </p>
          </div>
        </Link>

        {/* Search */}
        <div className="mx-4 hidden flex-1 md:block">
          <div
            className="flex max-w-lg items-center overflow-hidden rounded-xl"
            style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(0,212,255,0.15)" }}
          >
            <span className="pl-3" style={{ color: "rgba(0,212,255,0.5)" }}>
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
              </svg>
            </span>
            <input
              type="text"
              placeholder="Search products, brands..."
              className="flex-1 border-none bg-transparent px-3 py-2.5 text-sm outline-none"
              style={{ color: "#fff" }}
              readOnly
              onFocus={(e) => { e.target.blur(); router.push("/products"); }}
            />
            <button
              type="button"
              onClick={() => router.push("/products")}
              className="px-4 py-2.5 text-sm font-bold transition"
              style={{
                background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                color: "#fff",
                border: "none",
                cursor: "pointer",
              }}
              aria-label="Search"
            >
              Search
            </button>
          </div>
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-2 sm:gap-3">
          <WishlistNavWidget apiClient={apiClient} />
          <CartNavWidget apiClient={apiClient} emailVerified={emailVerified} />
          <span
            className="hidden rounded-full px-3 py-1.5 text-xs font-medium md:inline-block"
            style={{
              background: "rgba(0,212,255,0.08)",
              border: "1px solid rgba(0,212,255,0.15)",
              color: "rgba(0,212,255,0.8)",
            }}
          >
            {email || "User"}
          </span>
          <button
            disabled={logoutPending}
            onClick={async () => {
              if (logoutPending) return;
              setLogoutPending(true);
              try { await onLogout(); } finally { setLogoutPending(false); }
            }}
            className="hidden md:inline-flex rounded-xl px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
            style={{
              background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
              color: "#fff",
              border: "none",
              cursor: "pointer",
              boxShadow: "0 0 14px rgba(0,212,255,0.2)",
            }}
          >
            {logoutPending ? "Logging out..." : "Logout"}
          </button>
          {/* Mobile toggle */}
          <button
            type="button"
            onClick={() => setMobileOpen((o) => !o)}
            className="md:hidden rounded-lg p-2 transition"
            style={{ color: "#fff", background: "rgba(255,255,255,0.05)", border: "1px solid rgba(0,212,255,0.15)" }}
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              {mobileOpen
                ? <><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></>
                : <><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" /></>
              }
            </svg>
          </button>
        </div>
      </div>

      {/* Bottom nav strip */}
      <nav
        className={mobileOpen ? "block" : "hidden md:block"}
        style={{ borderTop: "1px solid rgba(0,212,255,0.08)", background: "rgba(8,8,18,0.6)" }}
      >
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-1 px-4 py-2">
          {[
            { href: "/", label: "Home" },
            { href: "/products", label: "Shop" },
            { href: "/wishlist", label: "Wishlist" },
            { href: "/cart", label: "Cart" },
            { href: "/orders", label: "My Orders" },
            { href: "/profile", label: "Profile" },
          ].map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline relative"
              style={{
                color: isActive(href) ? "#00d4ff" : "rgba(255,255,255,0.6)",
                background: isActive(href) ? "rgba(0,212,255,0.1)" : "transparent",
                border: isActive(href) ? "1px solid rgba(0,212,255,0.25)" : "1px solid transparent",
                textShadow: isActive(href) ? "0 0 12px rgba(0,212,255,0.5)" : "none",
              }}
            >
              {label}
            </Link>
          ))}
          {canViewAdmin && (
            <>
              <span className="mx-1 hidden h-4 w-px bg-white/10 md:inline-block" />
              <Link href="/admin/orders"
                className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                style={{
                  color: isActive("/admin/orders") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                  background: isActive("/admin/orders") ? "rgba(124,58,237,0.12)" : "transparent",
                  border: isActive("/admin/orders") ? "1px solid rgba(124,58,237,0.3)" : "1px solid transparent",
                }}
              >
                Admin Orders
              </Link>
              <Link href="/admin/products"
                className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                style={{
                  color: isActive("/admin/products") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                  background: isActive("/admin/products") ? "rgba(124,58,237,0.12)" : "transparent",
                  border: isActive("/admin/products") ? "1px solid rgba(124,58,237,0.3)" : "1px solid transparent",
                }}
              >
                Admin Products
              </Link>
              <Link href="/admin/posters"
                className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                style={{
                  color: isActive("/admin/posters") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                  background: isActive("/admin/posters") ? "rgba(124,58,237,0.12)" : "transparent",
                  border: isActive("/admin/posters") ? "1px solid rgba(124,58,237,0.3)" : "1px solid transparent",
                }}
              >
                Admin Posters
              </Link>
            </>
          )}
          {/* Mobile-only: user info + logout */}
          {mobileOpen && (
            <>
              <div className="w-full mt-1 pt-2" style={{ borderTop: "1px solid rgba(0,212,255,0.08)" }}>
                <div className="flex items-center justify-between gap-3">
                  {email && (
                    <span className="text-xs font-medium" style={{ color: "rgba(0,212,255,0.7)" }}>
                      {email}
                    </span>
                  )}
                  <button
                    disabled={logoutPending}
                    onClick={async () => {
                      if (logoutPending) return;
                      setLogoutPending(true);
                      try { await onLogout(); } finally { setLogoutPending(false); }
                    }}
                    className="rounded-lg px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                    style={{
                      background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                      color: "#fff",
                      border: "none",
                      cursor: "pointer",
                    }}
                  >
                    {logoutPending ? "Logging out..." : "Logout"}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
