"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

const CATEGORY_COLORS: Record<string, { bg: string; accent: string; icon: string }> = {
  electronics: { bg: "from-blue-500 to-blue-700", accent: "#3b82f6", icon: "📱" },
  fashion: { bg: "from-pink-500 to-rose-600", accent: "#ec4899", icon: "👗" },
  home: { bg: "from-amber-500 to-orange-600", accent: "#f59e0b", icon: "🏠" },
  beauty: { bg: "from-fuchsia-500 to-purple-600", accent: "#d946ef", icon: "💄" },
  sports: { bg: "from-emerald-500 to-green-700", accent: "#10b981", icon: "⚽" },
  toys: { bg: "from-yellow-400 to-orange-500", accent: "#eab308", icon: "🧸" },
  books: { bg: "from-indigo-500 to-violet-700", accent: "#6366f1", icon: "📚" },
  food: { bg: "from-red-500 to-rose-600", accent: "#ef4444", icon: "🍕" },
  automotive: { bg: "from-slate-600 to-gray-800", accent: "#475569", icon: "🚗" },
  garden: { bg: "from-lime-500 to-green-600", accent: "#84cc16", icon: "🌿" },
  health: { bg: "from-teal-500 to-cyan-600", accent: "#14b8a6", icon: "💊" },
  office: { bg: "from-sky-500 to-blue-600", accent: "#0ea5e9", icon: "💼" },
};

function getCategoryStyle(name: string) {
  const lower = name.toLowerCase();
  for (const [key, style] of Object.entries(CATEGORY_COLORS)) {
    if (lower.includes(key)) return style;
  }
  return { bg: "from-gray-500 to-gray-700", accent: "#6b7280", icon: "📂" };
}

export default function CategoriesPage() {
  const session = useAuthSession();
  const [categories, setCategories] = useState<Category[]>([]);
  const [status, setStatus] = useState("loading");

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to load categories");
        const data = (await res.json()) as Category[];
        setCategories(data || []);
        setStatus("ready");
      } catch {
        setStatus("error");
      }
    };
    void run();
  }, []);

  const parents = useMemo(
    () => categories.filter((c) => c.type === "PARENT").sort((a, b) => a.name.localeCompare(b.name)),
    [categories]
  );

  const subsByParent = useMemo(() => {
    const map = new Map<string, Category[]>();
    for (const sub of categories.filter((c) => c.type === "SUB" && c.parentCategoryId)) {
      const key = sub.parentCategoryId as string;
      const existing = map.get(key) || [];
      existing.push(sub);
      map.set(key, existing);
    }
    for (const [, list] of map) {
      list.sort((a, b) => a.name.localeCompare(b.name));
    }
    return map;
  }, [categories]);

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      {session.isAuthenticated ? (
        <AppNav
          email={(session.profile?.email as string) || ""}
          canViewAdmin={session.canViewAdmin}
          onLogout={() => { void session.logout(); }}
        />
      ) : (
        <header className="bg-[var(--header-bg)] shadow-lg">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2 text-white no-underline">
              <span className="text-2xl">🛒</span>
              <p className="text-lg font-bold text-white">Rumal Store</p>
            </Link>
            <Link href="/" className="rounded-lg bg-[var(--brand)] px-5 py-2 text-sm font-semibold text-white no-underline transition hover:bg-[var(--brand-hover)]">
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">All Categories</span>
        </nav>

        {/* Page Header */}
        <section className="animate-rise mb-6 rounded-2xl bg-gradient-to-r from-[var(--header-bg)] to-[#2d2d5e] p-8 text-white shadow-xl">
          <div className="text-center">
            <h1 className="text-3xl font-extrabold text-white md:text-4xl">Browse Categories</h1>
            <p className="mx-auto mt-2 max-w-lg text-sm text-gray-300">
              Explore our wide range of product categories. Find exactly what you&apos;re looking for.
            </p>
          </div>
        </section>

        {/* Loading State */}
        {status === "loading" && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="overflow-hidden rounded-2xl">
                <div className="skeleton h-40 w-full" />
                <div className="space-y-2 p-4">
                  <div className="skeleton h-4 w-1/2" />
                  <div className="skeleton h-3 w-3/4" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error State */}
        {status === "error" && (
          <div className="empty-state">
            <div className="empty-state-icon">⚠️</div>
            <p className="empty-state-title">Couldn&apos;t load categories</p>
            <p className="empty-state-desc">Please try again later</p>
          </div>
        )}

        {/* Category Grid */}
        {status === "ready" && parents.length === 0 && (
          <div className="empty-state">
            <div className="empty-state-icon">📂</div>
            <p className="empty-state-title">No categories yet</p>
            <p className="empty-state-desc">Categories will appear here once they are created</p>
            <Link href="/products" className="btn-primary mt-2 inline-block px-6 py-2.5 text-sm no-underline">
              Browse All Products
            </Link>
          </div>
        )}

        {status === "ready" && parents.length > 0 && (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {parents.map((parent, idx) => {
              const style = getCategoryStyle(parent.name);
              const children = subsByParent.get(parent.id) || [];
              return (
                <div
                  key={parent.id}
                  className="animate-rise group overflow-hidden rounded-2xl bg-white shadow-sm transition-all hover:shadow-xl"
                  style={{ animationDelay: `${idx * 60}ms` }}
                >
                  {/* Category Header Card */}
                  <Link
                    href={`/categories/${encodeURIComponent(parent.slug)}`}
                    className="no-underline"
                  >
                    <div className={`relative flex items-center gap-4 bg-gradient-to-r ${style.bg} p-6 text-white transition-transform group-hover:scale-[1.01]`}>
                      <div className="absolute -right-6 -top-6 h-24 w-24 rounded-full bg-white/10 blur-2xl" />
                      <span className="relative text-4xl">{style.icon}</span>
                      <div className="relative">
                        <h2 className="text-xl font-bold text-white">{parent.name}</h2>
                        <p className="mt-0.5 text-xs text-white/70">
                          {children.length} {children.length === 1 ? "subcategory" : "subcategories"}
                        </p>
                      </div>
                    </div>
                  </Link>

                  {/* Subcategories */}
                  {children.length > 0 && (
                    <div className="border-t border-[var(--line)] p-4">
                      <p className="mb-2.5 text-[10px] font-bold uppercase tracking-[0.15em] text-[var(--muted)]">Subcategories</p>
                      <div className="flex flex-wrap gap-2">
                        {children.map((sub) => (
                          <Link
                            key={sub.id}
                            href={`/categories/${encodeURIComponent(sub.slug)}`}
                            className="rounded-full border border-[var(--line)] bg-[var(--bg)] px-3 py-1.5 text-xs font-medium text-[var(--ink)] no-underline transition hover:border-[var(--brand)] hover:bg-[var(--brand-soft)] hover:text-[var(--brand)]"
                          >
                            {sub.name}
                          </Link>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* View All Link */}
                  <div className="border-t border-[var(--line)] px-4 py-3">
                    <Link
                      href={`/categories/${encodeURIComponent(parent.slug)}`}
                      className="flex items-center justify-between text-sm font-semibold text-[var(--brand)] no-underline hover:underline"
                    >
                      View All Products
                      <span>→</span>
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Browse All CTA */}
        {status === "ready" && parents.length > 0 && (
          <div className="mt-8 text-center">
            <Link href="/products" className="btn-outline px-8 py-3 text-sm no-underline">
              🏪 Browse All Products
            </Link>
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
}
