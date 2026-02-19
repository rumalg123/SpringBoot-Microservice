"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthSession } from "../lib/authSession";

export default function LandingPage() {
  const router = useRouter();
  const session = useAuthSession();

  useEffect(() => {
    if (session.status === "ready" && session.isAuthenticated) {
      router.replace(session.canViewAdmin ? "/admin/orders" : "/orders");
    }
  }, [router, session.isAuthenticated, session.status, session.canViewAdmin]);

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-6 py-10">
      <section className="relative overflow-hidden rounded-3xl border border-white/30 bg-[linear-gradient(145deg,#1f252f,#2e1f23)] p-8 text-white shadow-2xl">
        <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-orange-500/25 blur-3xl" />
        <div className="absolute -bottom-24 -left-20 h-64 w-64 rounded-full bg-cyan-500/20 blur-3xl" />
        <div className="relative grid gap-8 md:grid-cols-2 md:items-center">
          <div className="space-y-5">
            <p className="inline-flex rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs tracking-widest text-zinc-200">
              AUTH0 + SPRING MICROSERVICES
            </p>
            <h1 className="text-4xl font-semibold leading-tight md:text-5xl">
              One login.
              <br />
              Your profile.
              <br />
              Your orders.
            </h1>
            <p className="max-w-xl text-sm text-zinc-200">
              This app is user-scoped: after login you can only access your own customer data and
              your own orders.
            </p>
            <div className="flex flex-wrap gap-3">
              <button
                onClick={() => void session.login("/orders")}
                disabled={session.status === "loading" || session.status === "idle"}
                className="rounded-full bg-orange-500 px-5 py-2 text-sm font-semibold text-white transition hover:bg-orange-400 disabled:opacity-50"
              >
                Login
              </button>
              <button
                onClick={() => void session.signup("/orders")}
                disabled={session.status === "loading" || session.status === "idle"}
                className="rounded-full border border-white/40 bg-white/5 px-5 py-2 text-sm font-semibold text-white transition hover:bg-white/10 disabled:opacity-50"
              >
                Create Account
              </button>
              <Link
                href="/orders"
                className="rounded-full border border-white/30 px-5 py-2 text-sm font-semibold text-zinc-200 transition hover:bg-white/10"
              >
                Explore Dashboard
              </Link>
            </div>
            {session.status === "error" && (
              <p className="rounded-xl border border-red-300/40 bg-red-500/15 px-3 py-2 text-xs text-red-100">
                {session.error}
              </p>
            )}
          </div>
          <div className="grid gap-4 rounded-2xl border border-white/20 bg-white/5 p-5 text-sm">
            <p className="text-zinc-300">Connected API</p>
            <p className="break-all rounded-lg bg-black/30 px-3 py-2 font-mono text-xs text-cyan-100">
              {session.env.apiBase}
            </p>
            <p className="text-zinc-300">Auth0 Domain</p>
            <p className="break-all rounded-lg bg-black/30 px-3 py-2 font-mono text-xs text-amber-100">
              {session.env.domain || "missing-domain"}
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
