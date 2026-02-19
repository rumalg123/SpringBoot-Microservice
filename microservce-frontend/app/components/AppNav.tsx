"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

type Props = {
  email?: string;
  canViewAdmin?: boolean;
  onLogout: () => void;
};

export default function AppNav({ email, canViewAdmin = false, onLogout }: Props) {
  const pathname = usePathname();
  const navClass = (href: string) =>
    `rounded-full border px-4 py-2 text-sm ${pathname === href || pathname.startsWith(`${href}/`) ? "border-[var(--brand)] bg-[var(--brand-soft)] text-[var(--ink)]" : "border-[var(--line)] bg-[var(--surface)] text-[var(--ink)] hover:bg-[var(--brand-soft)]"}`;

  return (
    <header className="card-surface mb-8 flex flex-wrap items-center justify-between gap-3 rounded-2xl px-4 py-3">
      <div className="space-y-1">
        <p className="text-xs tracking-[0.22em] text-[var(--muted)]">RUMAL STORE</p>
        <h1 className="text-2xl font-semibold text-[var(--ink)]">Online Shop</h1>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Link
          href="/products"
          className={navClass("/products")}
        >
          Shop
        </Link>
        <Link
          href="/orders"
          className={navClass("/orders")}
        >
          Purchases
        </Link>
        {canViewAdmin && (
          <Link
            href="/admin/orders"
            className={navClass("/admin/orders")}
          >
            Admin Orders
          </Link>
        )}
        {canViewAdmin && (
          <Link
            href="/admin/products"
            className={navClass("/admin/products")}
          >
            Admin Products
          </Link>
        )}
        <Link
          href="/profile"
          className={navClass("/profile")}
        >
          My Profile
        </Link>
        <button
          onClick={onLogout}
          className="btn-brand rounded-full px-4 py-2 text-sm font-medium"
        >
          Logout
        </button>
        <span className="rounded-full bg-[var(--brand-soft)] px-3 py-1 text-xs text-[var(--ink)]">
          {email || "signed-in user"}
        </span>
      </div>
    </header>
  );
}
