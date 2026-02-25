"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import type { AxiosInstance } from "axios";
import CartNavWidget from "./CartNavWidget";
import WishlistNavWidget from "./WishlistNavWidget";
import ProductSearchBar from "./search/ProductSearchBar";

type Props = {
  email?: string;
  isSuperAdmin?: boolean;
  isVendorAdmin?: boolean;
  canViewAdmin?: boolean;
  canManageAdminOrders?: boolean;
  canManageAdminProducts?: boolean;
  canManageAdminCategories?: boolean;
  canManageAdminVendors?: boolean;
  canManageAdminPosters?: boolean;
  apiClient?: AxiosInstance | null;
  emailVerified?: boolean | null;
  onLogout: () => void | Promise<void>;
};

export default function AppNav({
  email,
  isSuperAdmin = false,
  isVendorAdmin = false,
  canViewAdmin = false,
  canManageAdminOrders,
  canManageAdminProducts,
  canManageAdminCategories,
  canManageAdminVendors,
  canManageAdminPosters,
  apiClient = null,
  emailVerified = null,
  onLogout,
}: Props) {
  const pathname = usePathname();
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

  const showAdminOrders = canManageAdminOrders ?? canViewAdmin;
  const showAdminProducts = canManageAdminProducts ?? canViewAdmin;
  const showAdminVendors = canManageAdminVendors ?? isSuperAdmin;
  const showAdminPosters = canManageAdminPosters ?? canViewAdmin;
  const showAdminCategories = canManageAdminCategories ?? isSuperAdmin;
  const showAdminPromotions = canViewAdmin;
  const showAdminInventory = canViewAdmin;
  const showVendorStaffAdmin = isSuperAdmin || isVendorAdmin;
  const showAdminDashboard = isSuperAdmin;
  const showAdminPermissionGroups = isSuperAdmin;
  const showAdminAccessAudit = isSuperAdmin;
  const showAnyAdminLinks = showAdminOrders || showAdminProducts || showAdminVendors || showVendorStaffAdmin || showAdminPosters || showAdminPromotions || showAdminDashboard || showAdminCategories || showAdminInventory;
  const showVendorPortal = isVendorAdmin;

  return (
    <header
      className="sticky top-0 z-50 transition-all duration-300"
      style={{
        background: "var(--header-bg)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
        borderBottom: "1px solid var(--brand-soft)",
        boxShadow: scrolled ? "0 4px 32px rgba(0,0,0,0.5)" : "none",
      }}
    >
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-3 no-underline group" style={{ flexShrink: 0 }}>
          <div
            className="flex h-9 w-9 items-center justify-center rounded-xl text-xs font-black"
            style={{
              background: "var(--gradient-brand)",
              boxShadow: "0 0 16px var(--brand-glow)",
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
          <ProductSearchBar maxWidth={560} placeholder="Search products, brands and more..." />
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-2 sm:gap-3">
          <WishlistNavWidget apiClient={apiClient} />
          <CartNavWidget apiClient={apiClient} emailVerified={emailVerified} />
          <span
            className="hidden rounded-full px-3 py-1.5 text-xs font-medium md:inline-block"
            style={{
              background: "var(--brand-soft)",
              border: "1px solid var(--line-bright)",
              color: "rgba(0,212,255,0.8)",
            }}
          >
            {email || "User"}
          </span>
          <button type="button"
            disabled={logoutPending}
            onClick={async () => {
              if (logoutPending) return;
              setLogoutPending(true);
              try { await onLogout(); } finally { setLogoutPending(false); }
            }}
            className="hidden md:inline-flex rounded-xl px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
            style={{
              background: "var(--gradient-brand)",
              color: "#fff",
              border: "none",
              cursor: "pointer",
              boxShadow: "0 0 14px var(--line-bright)",
            }}
          >
            {logoutPending ? "Logging out..." : "Logout"}
          </button>
          {/* Mobile toggle */}
          <button
            type="button"
            onClick={() => setMobileOpen((o) => !o)}
            className="md:hidden rounded-lg p-2 transition"
            style={{ color: "#fff", background: "rgba(255,255,255,0.05)", border: "1px solid var(--line-bright)" }}
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
        style={{ borderTop: "1px solid var(--brand-soft)", background: "rgba(8,8,18,0.6)" }}
      >
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-1 px-4 py-2">
          {[
            { href: "/", label: "Home" },
            { href: "/products", label: "Shop" },
            { href: "/wishlist", label: "Wishlist" },
            { href: "/cart", label: "Cart" },
            { href: "/orders", label: "My Orders" },
            { href: "/promotions", label: "Promotions" },
            { href: "/profile", label: "Profile" },
          ].map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline relative"
              style={{
                color: isActive(href) ? "var(--brand)" : "rgba(255,255,255,0.6)",
                background: isActive(href) ? "rgba(0,212,255,0.1)" : "transparent",
                border: isActive(href) ? "1px solid rgba(0,212,255,0.25)" : "1px solid transparent",
                textShadow: isActive(href) ? "0 0 12px rgba(0,212,255,0.5)" : "none",
              }}
            >
              {label}
            </Link>
          ))}
          {showVendorPortal && (
            <>
              <span className="mx-1 hidden h-4 w-px bg-white/10 md:inline-block" />
              <Link href="/vendor"
                className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                style={{
                  color: isActive("/vendor") ? "#34d399" : "rgba(52,211,153,0.6)",
                  background: isActive("/vendor") ? "rgba(52,211,153,0.1)" : "transparent",
                  border: isActive("/vendor") ? "1px solid rgba(52,211,153,0.25)" : "1px solid transparent",
                }}
              >
                Vendor Portal
              </Link>
              <Link href="/vendor/inventory"
                className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                style={{
                  color: isActive("/vendor/inventory") ? "#34d399" : "rgba(52,211,153,0.6)",
                  background: isActive("/vendor/inventory") ? "rgba(52,211,153,0.1)" : "transparent",
                  border: isActive("/vendor/inventory") ? "1px solid rgba(52,211,153,0.25)" : "1px solid transparent",
                }}
              >
                Vendor Inventory
              </Link>
            </>
          )}
          {showAnyAdminLinks && (
            <>
              <span className="mx-1 hidden h-4 w-px bg-white/10 md:inline-block" />
              {showAdminDashboard && (
                <Link href="/admin/dashboard"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/dashboard") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/dashboard") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/dashboard") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Dashboard
                </Link>
              )}
              {showAdminOrders && (
                <Link href="/admin/orders"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/orders") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/orders") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/orders") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Admin Orders
                </Link>
              )}
              {showAdminProducts && (
                <Link href="/admin/products"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/products") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/products") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/products") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Admin Products
                </Link>
              )}
              {showAdminCategories && (
                <Link href="/admin/categories"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/categories") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/categories") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/categories") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Categories
                </Link>
              )}
              {showAdminVendors && (
                <Link href="/admin/vendors"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/vendors") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/vendors") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/vendors") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Admin Vendors
                </Link>
              )}
              {showVendorStaffAdmin && (
                <Link href="/admin/vendor-staff"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/vendor-staff") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/vendor-staff") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/vendor-staff") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Vendor Staff
                </Link>
              )}
              {showAdminVendors && (
                <Link href="/admin/platform-staff"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/platform-staff") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/platform-staff") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/platform-staff") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Platform Staff
                </Link>
              )}
              {showAdminPosters && (
                <Link href="/admin/posters"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/posters") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/posters") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/posters") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Admin Posters
                </Link>
              )}
              {showAdminPromotions && (
                <Link href="/admin/promotions"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/promotions") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/promotions") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/promotions") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Promotions
                </Link>
              )}
              {showAdminInventory && (
                <Link href="/admin/inventory"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/inventory") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/inventory") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/inventory") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Inventory
                </Link>
              )}
              {showAdminPermissionGroups && (
                <Link href="/admin/permission-groups"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/permission-groups") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/permission-groups") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/permission-groups") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Permissions
                </Link>
              )}
              {showAdminAccessAudit && (
                <Link href="/admin/access-audit"
                  className="px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
                  style={{
                    color: isActive("/admin/access-audit") ? "#a78bfa" : "rgba(167,139,250,0.6)",
                    background: isActive("/admin/access-audit") ? "var(--accent-soft)" : "transparent",
                    border: isActive("/admin/access-audit") ? "1px solid var(--accent-glow)" : "1px solid transparent",
                  }}
                >
                  Access Audit
                </Link>
              )}
            </>
          )}
          {/* Mobile-only: user info + logout */}
          {mobileOpen && (
            <>
              <div className="w-full mt-1 pt-2" style={{ borderTop: "1px solid var(--brand-soft)" }}>
                <div className="flex items-center justify-between gap-3">
                  {email && (
                    <span className="text-xs font-medium" style={{ color: "rgba(0,212,255,0.7)" }}>
                      {email}
                    </span>
                  )}
                  <button type="button"
                    disabled={logoutPending}
                    onClick={async () => {
                      if (logoutPending) return;
                      setLogoutPending(true);
                      try { await onLogout(); } finally { setLogoutPending(false); }
                    }}
                    className="rounded-lg px-4 py-2 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                    style={{
                      background: "var(--gradient-brand)",
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

