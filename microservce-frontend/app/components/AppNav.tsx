"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
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

type NavItem = { href: string; label: string; show: boolean; color: "brand" | "vendor" | "admin" };

const colorMap = {
  brand: { active: "var(--brand)", inactive: "rgba(255,255,255,0.6)", bg: "rgba(0,212,255,0.1)", border: "rgba(0,212,255,0.25)" },
  vendor: { active: "#34d399", inactive: "rgba(52,211,153,0.6)", bg: "rgba(52,211,153,0.1)", border: "rgba(52,211,153,0.25)" },
  admin: { active: "#a78bfa", inactive: "rgba(167,139,250,0.6)", bg: "var(--accent-soft)", border: "var(--accent-glow)" },
};

function NavLink({ href, label, color, isActive }: { href: string; label: string; color: "brand" | "vendor" | "admin"; isActive: boolean }) {
  const c = colorMap[color];
  return (
    <Link
      href={href}
      aria-current={isActive ? "page" : undefined}
      className="relative px-4 py-2 text-sm font-semibold rounded-lg transition no-underline"
      style={{
        color: isActive ? c.active : c.inactive,
        background: isActive ? c.bg : "transparent",
        border: isActive ? `1px solid ${c.border}` : "1px solid transparent",
        textShadow: isActive && color === "brand" ? "0 0 12px rgba(0,212,255,0.5)" : "none",
      }}
    >
      {label}
    </Link>
  );
}

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
    let ticking = false;
    const onScroll = () => {
      if (!ticking) {
        requestAnimationFrame(() => {
          setScrolled(window.scrollY > 10);
          ticking = false;
        });
        ticking = true;
      }
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => { setMobileOpen(false); }, [pathname]);

  const isActive = (href: string) => {
    if (href === "/") return pathname === "/";
    if (pathname === href) return true;
    if (!pathname.startsWith(`${href}/`)) return false;
    // Don't highlight parent when a more specific child nav item matches
    return !navItems.some(
      (n) => n.href !== href && n.href.length > href.length && n.href.startsWith(`${href}/`)
        && (pathname === n.href || pathname.startsWith(`${n.href}/`))
    );
  };

  const showAdminOrders = canManageAdminOrders ?? canViewAdmin;
  const showAdminProducts = canManageAdminProducts ?? canViewAdmin;
  const showAdminVendors = canManageAdminVendors ?? isSuperAdmin;
  const showAdminPosters = canManageAdminPosters ?? canViewAdmin;
  const showAdminCategories = canManageAdminCategories ?? isSuperAdmin;
  const showVendorStaffAdmin = isSuperAdmin || isVendorAdmin;

  const navItems: NavItem[] = useMemo(() => [
    // User links
    { href: "/", label: "Home", show: true, color: "brand" },
    { href: "/products", label: "Shop", show: true, color: "brand" },
    { href: "/wishlist", label: "Wishlist", show: true, color: "brand" },
    { href: "/cart", label: "Cart", show: true, color: "brand" },
    { href: "/orders", label: "My Orders", show: true, color: "brand" },
    { href: "/promotions", label: "Promotions", show: true, color: "brand" },
    { href: "/profile", label: "Profile", show: true, color: "brand" },
    { href: "/profile/insights", label: "Insights", show: true, color: "brand" },
    // Vendor links
    { href: "/vendor", label: "Vendor Portal", show: isVendorAdmin, color: "vendor" },
    { href: "/vendor/inventory", label: "Vendor Inventory", show: isVendorAdmin, color: "vendor" },
    { href: "/vendor/analytics", label: "Analytics", show: isVendorAdmin, color: "vendor" },
    { href: "/vendor/reviews", label: "Reviews", show: isVendorAdmin, color: "vendor" },
    { href: "/vendor/bank-accounts", label: "Bank Accounts", show: isVendorAdmin, color: "vendor" },
    { href: "/vendor/payouts", label: "Payouts", show: isVendorAdmin, color: "vendor" },
    // Admin links
    { href: "/admin/dashboard", label: "Dashboard", show: isSuperAdmin, color: "admin" },
    { href: "/admin/orders", label: "Admin Orders", show: showAdminOrders, color: "admin" },
    { href: "/admin/products", label: "Admin Products", show: showAdminProducts, color: "admin" },
    { href: "/admin/categories", label: "Categories", show: showAdminCategories, color: "admin" },
    { href: "/admin/vendors", label: "Admin Vendors", show: showAdminVendors, color: "admin" },
    { href: "/admin/vendor-staff", label: "Vendor Staff", show: showVendorStaffAdmin, color: "admin" },
    { href: "/admin/platform-staff", label: "Platform Staff", show: showAdminVendors, color: "admin" },
    { href: "/admin/posters", label: "Admin Posters", show: showAdminPosters, color: "admin" },
    { href: "/admin/reviews", label: "Reviews", show: canViewAdmin, color: "admin" },
    { href: "/admin/payments", label: "Payments", show: canViewAdmin, color: "admin" },
    { href: "/admin/api-keys", label: "API Keys", show: isSuperAdmin, color: "admin" },
    { href: "/admin/sessions", label: "Sessions", show: isSuperAdmin, color: "admin" },
    { href: "/admin/settings", label: "Settings", show: isSuperAdmin, color: "admin" },
    { href: "/admin/promotions", label: "Promotions", show: canViewAdmin, color: "admin" },
    { href: "/admin/inventory", label: "Inventory", show: canViewAdmin, color: "admin" },
    { href: "/admin/permission-groups", label: "Permissions", show: isSuperAdmin, color: "admin" },
    { href: "/admin/access-audit", label: "Access Audit", show: isSuperAdmin, color: "admin" },
  ], [isSuperAdmin, isVendorAdmin, canViewAdmin, showAdminOrders, showAdminProducts, showAdminVendors, showAdminPosters, showAdminCategories, showVendorStaffAdmin]);

  const userLinks = navItems.filter((i) => i.color === "brand" && i.show);
  const vendorLinks = navItems.filter((i) => i.color === "vendor" && i.show);
  const adminLinks = navItems.filter((i) => i.color === "admin" && i.show);

  const handleLogout = async () => {
    if (logoutPending) return;
    setLogoutPending(true);
    try { await onLogout(); } finally { setLogoutPending(false); }
  };

  return (
    <header
      className="sticky top-0 z-50 border-b border-[var(--brand-soft)] bg-header-bg backdrop-blur-[20px] transition-all duration-300"
      style={{
        WebkitBackdropFilter: "blur(20px)",
        boxShadow: scrolled ? "0 4px 32px rgba(0,0,0,0.5)" : "none",
      }}
    >
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
        {/* Logo */}
        <Link href="/" className="flex shrink-0 items-center gap-3 no-underline group">
          <div
            className="flex h-9 w-9 items-center justify-center rounded-xl text-xs font-black tracking-[0.02em] text-white shadow-[0_0_16px_var(--brand-glow)]"
            style={{ background: "var(--gradient-brand)" }}
          >
            RS
          </div>
          <div>
            <p className="text-base font-black leading-tight tracking-[-0.01em] text-white" style={{ fontFamily: "'Syne', sans-serif" }}>
              Rumal Store
            </p>
            <p className="text-[9px] font-semibold tracking-[0.2em] text-[rgba(0,212,255,0.6)]">
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
          <span className="hidden rounded-full border border-line-bright bg-brand-soft px-3 py-1.5 text-xs font-medium text-[rgba(0,212,255,0.8)] md:inline-block">
            {email || "User"}
          </span>
          <button type="button"
            disabled={logoutPending}
            onClick={() => { void handleLogout(); }}
            className="hidden cursor-pointer rounded-xl border-none px-4 py-2 text-xs font-bold text-white shadow-[0_0_14px_var(--line-bright)] transition disabled:cursor-not-allowed disabled:opacity-50 md:inline-flex"
            style={{ background: "var(--gradient-brand)" }}
          >
            {logoutPending ? "Logging out..." : "Logout"}
          </button>
          {/* Mobile toggle */}
          <button
            type="button"
            onClick={() => setMobileOpen((o) => !o)}
            className="rounded-lg border border-line-bright bg-white/5 p-2 text-white transition md:hidden"
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
            aria-expanded={mobileOpen}
            aria-controls="main-nav"
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
        id="main-nav"
        aria-label="Main navigation"
        className={`border-t border-[var(--brand-soft)] bg-[rgba(8,8,18,0.6)] ${mobileOpen ? "block" : "hidden md:block"}`}
      >
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-1 px-4 py-2">
          {userLinks.map((item) => (
            <NavLink key={item.href} href={item.href} label={item.label} color={item.color} isActive={isActive(item.href)} />
          ))}

          {vendorLinks.length > 0 && (
            <>
              <span className="mx-1 hidden h-4 w-px bg-white/10 md:inline-block" />
              {vendorLinks.map((item) => (
                <NavLink key={item.href} href={item.href} label={item.label} color={item.color} isActive={isActive(item.href)} />
              ))}
            </>
          )}

          {adminLinks.length > 0 && (
            <>
              <span className="mx-1 hidden h-4 w-px bg-white/10 md:inline-block" />
              {adminLinks.map((item) => (
                <NavLink key={item.href} href={item.href} label={item.label} color={item.color} isActive={isActive(item.href)} />
              ))}
            </>
          )}

          {/* Mobile-only: user info + logout */}
          {mobileOpen && (
            <div className="mt-1 w-full border-t border-[var(--brand-soft)] pt-2">
              <div className="flex items-center justify-between gap-3">
                {email && (
                  <span className="text-xs font-medium text-[rgba(0,212,255,0.7)]">
                    {email}
                  </span>
                )}
                <button type="button"
                  disabled={logoutPending}
                  onClick={() => { void handleLogout(); }}
                  className="cursor-pointer rounded-lg border-none px-4 py-2 text-xs font-bold text-white transition disabled:cursor-not-allowed disabled:opacity-50"
                  style={{ background: "var(--gradient-brand)" }}
                >
                  {logoutPending ? "Logging out..." : "Logout"}
                </button>
              </div>
            </div>
          )}
        </div>
      </nav>
    </header>
  );
}
