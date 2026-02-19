"use client";

import Link from "next/link";

type Props = {
  email?: string;
  canViewAdmin?: boolean;
  onLogout: () => void;
};

export default function AppNav({ email, canViewAdmin = false, onLogout }: Props) {
  return (
    <header className="card-surface mb-8 flex flex-wrap items-center justify-between gap-3 rounded-2xl px-4 py-3">
      <div className="space-y-1">
        <p className="text-xs tracking-[0.22em] text-[var(--muted)]">RUMAL STORE</p>
        <h1 className="text-2xl font-semibold text-[var(--ink)]">Online Shop</h1>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Link
          href="/products"
          className="rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
        >
          Shop
        </Link>
        <Link
          href="/orders"
          className="rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
        >
          Purchases
        </Link>
        {canViewAdmin && (
          <Link
            href="/admin/orders"
            className="rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
          >
            Admin Orders
          </Link>
        )}
        {canViewAdmin && (
          <Link
            href="/admin/products"
            className="rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
          >
            Admin Products
          </Link>
        )}
        <Link
          href="/profile"
          className="rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
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
