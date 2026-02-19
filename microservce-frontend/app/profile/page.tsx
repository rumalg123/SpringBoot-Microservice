"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import { useAuthSession } from "../../lib/authSession";

type Customer = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
};

export default function ProfilePage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus,
    isAuthenticated,
    canViewAdmin,
    apiClient,
    ensureCustomer,
    resendVerificationEmail,
    profile,
    logout,
    emailVerified,
  } = session;
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [status, setStatus] = useState("Loading account...");

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) {
      router.replace("/");
      return;
    }
    if (canViewAdmin) {
      return;
    }

    const run = async () => {
      if (!apiClient) return;
      try {
        await ensureCustomer();
        const response = await apiClient.get("/customers/me");
        setCustomer(response.data as Customer);
        setStatus("Account loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load account");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, canViewAdmin, apiClient, ensureCustomer]);

  const resendVerification = async () => {
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
      toast.success("Verification email sent");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return <main className="mx-auto min-h-screen max-w-5xl px-6 py-10 text-[var(--muted)]">Loading...</main>;
  }

  if (!isAuthenticated) {
    return null;
  }

  const displayStatus = canViewAdmin ? "Admin account detected." : status;

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-8">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        onLogout={() => {
          void logout();
        }}
      />

      {emailVerified === false && (
        <section className="mb-5 rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <p>Your email is not verified. Profile and order actions are blocked until verification.</p>
          <button
            onClick={() => {
              void resendVerification();
            }}
            className="mt-2 rounded-lg bg-amber-600 px-3 py-1 text-xs font-semibold text-white hover:bg-amber-500"
          >
            Resend Verification Email
          </button>
        </section>
      )}

      <section className="card-surface animate-rise rounded-3xl p-6 md:p-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs tracking-[0.22em] text-[var(--muted)]">ACCOUNT</p>
            <h1 className="text-4xl text-[var(--ink)]">My Profile</h1>
            <p className="mt-1 text-sm text-[var(--muted)]">Manage your customer identity and view account metadata.</p>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/products"
              className="rounded-full border border-[var(--line)] bg-white px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
            >
              Shop
            </Link>
            <Link
              href="/orders"
              className="rounded-full border border-[var(--line)] bg-white px-4 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
            >
              Purchases
            </Link>
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <article className="card-surface rounded-2xl p-5">
            <p className="text-xs tracking-[0.2em] text-[var(--muted)]">CUSTOMER PROFILE</p>
            {!canViewAdmin && (
              <div className="mt-3 space-y-3 text-sm">
                <div>
                  <p className="text-[var(--muted)]">Name</p>
                  <p className="font-semibold text-[var(--ink)]">{customer?.name || "-"}</p>
                </div>
                <div>
                  <p className="text-[var(--muted)]">Email</p>
                  <p className="font-semibold text-[var(--ink)]">{customer?.email || "-"}</p>
                </div>
                <div>
                  <p className="text-[var(--muted)]">Customer ID</p>
                  <p className="break-all font-mono text-xs text-[var(--ink)]">{customer?.id || "-"}</p>
                </div>
                <div>
                  <p className="text-[var(--muted)]">Created</p>
                  <p className="font-semibold text-[var(--ink)]">
                    {customer?.createdAt ? new Date(customer.createdAt).toLocaleString() : "-"}
                  </p>
                </div>
              </div>
            )}
            {canViewAdmin && (
              <p className="mt-3 text-sm text-[var(--muted)]">
                Admin account detected. Customer profile bootstrap is not required for admin operations.
              </p>
            )}
          </article>

          <article className="card-surface rounded-2xl p-5">
            <p className="text-xs tracking-[0.2em] text-[var(--muted)]">SESSION INFO</p>
            <div className="mt-3 space-y-3 text-sm">
              <div>
                <p className="text-[var(--muted)]">Auth Email</p>
                <p className="font-semibold text-[var(--ink)]">{(profile?.email as string) || "-"}</p>
              </div>
              <div>
                <p className="text-[var(--muted)]">Auth Name</p>
                <p className="font-semibold text-[var(--ink)]">{(profile?.name as string) || "-"}</p>
              </div>
              <div>
                <p className="text-[var(--muted)]">Role</p>
                <p className="font-semibold text-[var(--ink)]">{canViewAdmin ? "Admin" : "Customer"}</p>
              </div>
            </div>
          </article>
        </div>

        <p className="mt-5 text-xs text-[var(--muted)]">{displayStatus}</p>
      </section>
    </main>
  );
}
