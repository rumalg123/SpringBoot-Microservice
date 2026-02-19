"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
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
  const [resendingVerification, setResendingVerification] = useState(false);

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
    if (resendingVerification) return;
    setResendingVerification(true);
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
      toast.success("Verification email sent");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    } finally {
      setResendingVerification(false);
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto w-12 h-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
          <p className="mt-4">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">My Profile</span>
        </nav>

        {emailVerified === false && (
          <section className="mb-4 flex items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <span className="text-xl">⚠️</span>
            <div className="flex-1">
              <p className="font-semibold">Email Not Verified</p>
              <p className="text-xs">Profile and order actions are blocked until verification.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--ink)]">👤 My Profile</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">Manage your account and view your details</p>
          </div>
          <div className="flex gap-2">
            <Link href="/products" className="btn-primary no-underline px-4 py-2.5 text-sm">
              🛍️ Shop
            </Link>
            <Link href="/orders" className="btn-outline no-underline px-4 py-2.5 text-sm">
              📦 Orders
            </Link>
          </div>
        </div>

        {/* Profile Cards */}
        <div className="grid gap-5 md:grid-cols-2">
          {/* Customer Profile Card */}
          <article className="animate-rise rounded-xl bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--brand-soft)] text-xl">
                👤
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-[var(--muted)]">Customer Profile</p>
                <p className="text-sm font-semibold text-[var(--ink)]">
                  {canViewAdmin ? "Admin Account" : customer?.name || "Loading..."}
                </p>
              </div>
            </div>

            {!canViewAdmin && (
              <div className="space-y-4">
                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Full Name</p>
                  <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{customer?.name || "—"}</p>
                </div>
                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Email</p>
                  <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{customer?.email || "—"}</p>
                </div>
                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Customer ID</p>
                  <p className="mt-0.5 break-all font-mono text-xs text-[var(--ink)]">{customer?.id || "—"}</p>
                </div>
                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Member Since</p>
                  <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">
                    {customer?.createdAt
                      ? new Date(customer.createdAt).toLocaleDateString("en-US", {
                        year: "numeric", month: "long", day: "numeric",
                      })
                      : "—"}
                  </p>
                </div>
              </div>
            )}

            {canViewAdmin && (
              <div className="rounded-lg bg-blue-50 px-4 py-3 text-sm text-blue-700">
                <p className="font-semibold">Admin Account Detected</p>
                <p className="mt-1 text-xs">Customer profile bootstrap is not required for admin operations.</p>
              </div>
            )}
          </article>

          {/* Session Info Card */}
          <article className="animate-rise rounded-xl bg-white p-6 shadow-sm" style={{ animationDelay: "100ms" }}>
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--accent-soft)] text-xl">
                🔐
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-[var(--muted)]">Session Info</p>
                <p className="text-sm font-semibold text-[var(--ink)]">Authentication Details</p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Auth Email</p>
                <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{(profile?.email as string) || "—"}</p>
              </div>
              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Auth Name</p>
                <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{(profile?.name as string) || "—"}</p>
              </div>
              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Role</p>
                <p className="mt-0.5">
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${canViewAdmin
                    ? "bg-purple-100 text-purple-700"
                    : "bg-green-100 text-green-700"
                    }`}>
                    {canViewAdmin ? "👑 Admin" : "🛒 Customer"}
                  </span>
                </p>
              </div>
              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Email Verified</p>
                <p className="mt-0.5">
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${emailVerified
                    ? "bg-green-100 text-green-700"
                    : "bg-amber-100 text-amber-700"
                    }`}>
                    {emailVerified ? "✓ Verified" : "⚠ Not Verified"}
                  </span>
                </p>
              </div>
            </div>
          </article>
        </div>

        <p className="mt-5 text-xs text-[var(--muted)]">{canViewAdmin ? "Admin account detected." : status}</p>
      </main>

      <Footer />
    </div>
  );
}
