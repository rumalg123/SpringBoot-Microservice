"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import type { Category } from "../../lib/types/category";
import { API_BASE } from "../../lib/constants";
import { getCategoryMeta } from "../components/icons/CategoryIcons";
import type { LoadingStatus } from "../../lib/types/status";

function slugify(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}

function toCategorySlug(category: Category): string {
  const fromSlug = (category.slug || "").trim();
  if (fromSlug) return slugify(fromSlug);
  return slugify(category.name || "");
}

export default function CategoriesPage() {
  const session = useAuthSession();
  const [categories, setCategories] = useState<Category[]>([]);
  const [status, setStatus] = useState<LoadingStatus>("loading");

  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/categories`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) throw new Error("Failed to load categories");
        if (controller.signal.aborted) return;
        setCategories(((await res.json()) as Category[]) || []);
        setStatus("ready");
      } catch {
        if (!controller.signal.aborted) setStatus("error");
      }
    };
    void run();
    return () => controller.abort();
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
    for (const [, list] of map) list.sort((a, b) => a.name.localeCompare(b.name));
    return map;
  }, [categories]);

  return (
    <div className="min-h-screen bg-bg">
      {session.isAuthenticated ? (
        <AppNav
          email={(session.profile?.email as string) || ""}
          isSuperAdmin={session.isSuperAdmin}
          isVendorAdmin={session.isVendorAdmin}
          canViewAdmin={session.canViewAdmin}
          canManageAdminOrders={session.canManageAdminOrders}
          canManageAdminProducts={session.canManageAdminProducts}
          canManageAdminCategories={session.canManageAdminCategories}
          canManageAdminVendors={session.canManageAdminVendors}
          canManageAdminPosters={session.canManageAdminPosters}
          apiClient={session.apiClient}
          emailVerified={session.emailVerified}
          onLogout={() => { void session.logout(); }}
        />
      ) : (
        <header className="sticky top-0 z-50 border-b border-[rgba(0,212,255,0.1)] bg-header-bg backdrop-blur-[12px]">
          <div className="mx-auto flex max-w-[1280px] items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2.5 no-underline">
              <span className="grid h-8 w-8 place-items-center rounded-[8px] bg-[linear-gradient(135deg,#00d4ff,#7c3aed)]">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" /><line x1="3" y1="6" x2="21" y2="6" /><path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
              </span>
              <span className="font-[Syne,sans-serif] text-[1.1rem] font-extrabold text-white">Rumal Store</span>
            </Link>
            <Link href="/" className="rounded-[8px] bg-[linear-gradient(135deg,#00d4ff,#7c3aed)] px-5 py-2 text-sm font-bold text-white no-underline">
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-[1280px] px-4 pb-12 pt-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">All Categories</span>
        </nav>

        {/* Page Hero */}
        <section
          className="animate-rise relative mb-7 overflow-hidden rounded-xl border border-[rgba(0,212,255,0.12)] bg-[linear-gradient(135deg,rgba(0,212,255,0.06)_0%,rgba(124,58,237,0.08)_100%)] px-8 py-12 text-center"
        >
          <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-[rgba(0,212,255,0.06)] blur-[40px]" />
          <div className="pointer-events-none absolute -bottom-10 -left-10 h-40 w-40 rounded-full bg-[rgba(124,58,237,0.08)] blur-[40px]" />
          <p className="mb-2 mt-0 text-[0.65rem] font-extrabold uppercase tracking-[0.15em] text-[#00d4ff]">EXPLORE</p>
          <h1 className="mb-2.5 mt-0 font-[Syne,sans-serif] text-[clamp(1.8rem,4vw,2.6rem)] font-extrabold text-white">
            Browse Categories
          </h1>
          <p className="mx-auto m-0 max-w-[480px] text-[0.9rem] text-muted">
            Explore our wide range of product categories. Find exactly what you&apos;re looking for.
          </p>
        </section>

        {/* Loading Skeletons */}
        {status === "loading" && (
          <div className="grid gap-5" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="overflow-hidden rounded-lg">
                <div className="skeleton h-[120px] w-full" />
                <div className="grid gap-2 p-4">
                  <div className="skeleton h-3.5 w-1/2" />
                  <div className="skeleton h-3 w-3/4" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error */}
        {status === "error" && (
          <div className="empty-state">
            <div className="empty-state-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
            </div>
            <p className="empty-state-title">Couldn&apos;t load categories</p>
            <p className="empty-state-desc">Please try again later</p>
          </div>
        )}

        {/* Empty */}
        {status === "ready" && parents.length === 0 && (
          <div className="empty-state">
            <div className="empty-state-icon">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
              </svg>
            </div>
            <p className="empty-state-title">No categories yet</p>
            <p className="empty-state-desc">Categories will appear here once they are created</p>
            <Link href="/products" className="btn-primary mt-2 px-6 py-2.5 text-sm no-underline">
              Browse All Products
            </Link>
          </div>
        )}

        {/* Category Grid */}
        {status === "ready" && parents.length > 0 && (
          <>
            <div className="grid gap-5" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
              {parents.map((parent, idx) => {
                const meta = getCategoryMeta(parent.name);
                const children = subsByParent.get(parent.id) || [];
                return (
                  <div
                    key={parent.id}
                    className="animate-rise overflow-hidden rounded-lg bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] transition-[box-shadow,border-color] duration-[250ms]"
                    style={{
                      animationDelay: `${idx * 55}ms`,
                      border: `1px solid ${meta.accent}22`,
                    }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.boxShadow = `0 0 28px ${meta.accent}22, 0 8px 32px rgba(0,0,0,0.5)`;
                      (e.currentTarget as HTMLElement).style.borderColor = `${meta.accent}55`;
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.boxShadow = "none";
                      (e.currentTarget as HTMLElement).style.borderColor = `${meta.accent}22`;
                    }}
                  >
                    {/* Category Banner */}
                    <Link href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`} className="block no-underline">
                      <div
                        className="relative flex items-center gap-4 overflow-hidden px-5 py-6"
                        style={{
                          background: `linear-gradient(135deg, ${meta.accent}18 0%, ${meta.accent}08 100%)`,
                          borderBottom: `1px solid ${meta.accent}20`,
                        }}
                      >
                        <div className="pointer-events-none absolute -right-5 -top-5 h-20 w-20 rounded-full blur-[20px]" style={{ background: `${meta.accent}12` }} />
                        <span className="shrink-0" style={{ color: meta.accent, filter: `drop-shadow(0 0 8px ${meta.accent}60)` }}>
                          {meta.icon}
                        </span>
                        <div>
                          <h2 className="mb-[3px] mt-0 font-[Syne,sans-serif] text-[1.1rem] font-extrabold text-white">
                            {parent.name}
                          </h2>
                          <p className="m-0 text-xs text-muted">
                            {children.length} {children.length === 1 ? "subcategory" : "subcategories"}
                          </p>
                        </div>
                      </div>
                    </Link>

                    {/* Subcategory Pills */}
                    {children.length > 0 && (
                      <div className="border-b border-[rgba(255,255,255,0.04)] px-4 py-3.5">
                        <p className="mb-2.5 text-[0.6rem] font-extrabold uppercase tracking-[0.14em] text-muted-2">
                          Subcategories
                        </p>
                        <div className="flex flex-wrap gap-1.5">
                          {children.map((sub) => (
                            <Link
                              key={sub.id}
                              href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                              className="rounded-xl border border-[rgba(0,212,255,0.12)] bg-[rgba(0,212,255,0.04)] px-3 py-1 text-xs font-medium text-ink-light no-underline transition-all duration-[180ms]"
                              onMouseEnter={(e) => {
                                (e.currentTarget as HTMLElement).style.borderColor = meta.accent;
                                (e.currentTarget as HTMLElement).style.color = meta.accent;
                                (e.currentTarget as HTMLElement).style.background = `${meta.accent}12`;
                              }}
                              onMouseLeave={(e) => {
                                (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,212,255,0.12)";
                                (e.currentTarget as HTMLElement).style.color = "var(--ink-light)";
                                (e.currentTarget as HTMLElement).style.background = "rgba(0,212,255,0.04)";
                              }}
                            >
                              {sub.name}
                            </Link>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* View All Footer */}
                    <div className="px-4 py-3">
                      <Link
                        href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                        className="flex items-center justify-between text-sm font-bold no-underline transition-opacity duration-150"
                        style={{ color: meta.accent }}
                      >
                        View All Products
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M5 12h14M12 5l7 7-7 7" />
                        </svg>
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="mt-10 text-center">
              <Link href="/products" className="btn-outline px-8 py-3 text-base no-underline">
                Browse All Products
              </Link>
            </div>
          </>
        )}
      </main>

      <Footer />
    </div>
  );
}
