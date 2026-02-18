"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
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
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [status, setStatus] = useState("Loading session...");

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }

    const run = async () => {
      if (!session.apiClient) return;
      try {
        await session.ensureCustomer();
        const response = await session.apiClient.get("/customers/me");
        setCustomer(response.data as Customer);
        setStatus("Profile loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load profile");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.apiClient, session.ensureCustomer]);

  const resendVerification = async () => {
    setStatus("Requesting verification email...");
    try {
      await session.resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to resend verification email.");
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return <main className="mx-auto min-h-screen max-w-5xl px-6 py-10 text-zinc-700">Loading...</main>;
  }

  if (!session.isAuthenticated) {
    return null;
  }

  return (
    <main className="mx-auto min-h-screen max-w-5xl px-6 py-10">
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        onLogout={() => {
          void session.logout();
        }}
      />
      {!session.emailVerified && (
        <section className="mb-4 rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900">
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

      <section className="grid gap-4 rounded-3xl border border-zinc-200 bg-white/85 p-6 shadow-xl backdrop-blur">
        <p className="text-xs tracking-widest text-zinc-500">MY ACCOUNT</p>
        <h2 className="text-2xl font-semibold text-zinc-900">Personal Profile</h2>
        <p className="text-sm text-zinc-600">
          This page reads only `GET /customers/me`. Other customer records are blocked at the gateway.
        </p>
        <div className="grid gap-3 rounded-2xl bg-zinc-900 p-5 text-sm text-zinc-100">
          <div>
            <span className="text-zinc-400">Name</span>
            <p className="font-medium">{customer?.name || "-"}</p>
          </div>
          <div>
            <span className="text-zinc-400">Email</span>
            <p className="font-medium">{customer?.email || "-"}</p>
          </div>
          <div>
            <span className="text-zinc-400">Customer ID</span>
            <p className="break-all font-mono text-xs">{customer?.id || "-"}</p>
          </div>
          <div>
            <span className="text-zinc-400">Created At</span>
            <p className="font-medium">
              {customer?.createdAt ? new Date(customer.createdAt).toLocaleString() : "-"}
            </p>
          </div>
        </div>
        <p className="text-xs text-zinc-500">{status}</p>
      </section>
    </main>
  );
}
