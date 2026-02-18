"use client";

import Link from "next/link";

type Props = {
  email?: string;
  canViewAdmin?: boolean;
  onLogout: () => void;
};

export default function AppNav({ email, canViewAdmin = false, onLogout }: Props) {
  return (
    <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div className="space-y-1">
        <p className="text-xs tracking-widest text-zinc-500">MICROSERVICE FRONTEND</p>
        <h1 className="text-2xl font-semibold text-zinc-900">Customer Portal</h1>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Link href="/orders" className="rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm">
          My Orders
        </Link>
        {canViewAdmin && (
          <Link href="/admin/orders" className="rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm">
            Admin Orders
          </Link>
        )}
        <Link href="/profile" className="rounded-full border border-zinc-300 bg-white px-4 py-2 text-sm">
          My Profile
        </Link>
        <button
          onClick={onLogout}
          className="rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
        >
          Logout
        </button>
        <span className="rounded-full bg-zinc-200 px-3 py-1 text-xs text-zinc-700">
          {email || "signed-in user"}
        </span>
      </div>
    </header>
  );
}
