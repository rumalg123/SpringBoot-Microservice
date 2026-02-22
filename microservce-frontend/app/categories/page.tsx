"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

type Category = {
  id: string;
  name: string;
  slug?: string | null;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

/* ── Per-category accent colours & SVG icons ── */
const CATEGORY_META: Record<string, { accent: string; icon: React.ReactNode }> = {
  electronics: {
    accent: "#00d4ff",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="3" width="20" height="14" rx="2" /><path d="M8 21h8m-4-4v4" />
      </svg>
    ),
  },
  fashion: {
    accent: "#f472b6",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z" />
      </svg>
    ),
  },
  home: {
    accent: "#f59e0b",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
      </svg>
    ),
  },
  beauty: {
    accent: "#d946ef",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      </svg>
    ),
  },
  sports: {
    accent: "#10b981",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" /><path d="M4.93 4.93l14.14 14.14M19.07 4.93 4.93 19.07" />
      </svg>
    ),
  },
  toys: {
    accent: "#fbbf24",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2a5 5 0 0 1 5 5v1h1a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2h1V7a5 5 0 0 1 5-5z" />
      </svg>
    ),
  },
  books: {
    accent: "#818cf8",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
      </svg>
    ),
  },
  food: {
    accent: "#ef4444",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 8h1a4 4 0 0 1 0 8h-1" /><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z" /><line x1="6" y1="1" x2="6" y2="4" /><line x1="10" y1="1" x2="10" y2="4" /><line x1="14" y1="1" x2="14" y2="4" />
      </svg>
    ),
  },
  automotive: {
    accent: "#94a3b8",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v9a2 2 0 0 1-2 2h-3" /><circle cx="7.5" cy="17.5" r="2.5" /><circle cx="17.5" cy="17.5" r="2.5" />
      </svg>
    ),
  },
  health: {
    accent: "#2dd4bf",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
      </svg>
    ),
  },
};

function getCategoryMeta(name: string) {
  const lower = name.toLowerCase();
  for (const [key, meta] of Object.entries(CATEGORY_META)) {
    if (lower.includes(key)) return meta;
  }
  return {
    accent: "#7c3aed",
    icon: (
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      </svg>
    ),
  };
}

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
  const [status, setStatus] = useState("loading");

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to load categories");
        setCategories(((await res.json()) as Category[]) || []);
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
    for (const [, list] of map) list.sort((a, b) => a.name.localeCompare(b.name));
    return map;
  }, [categories]);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      {session.isAuthenticated ? (
        <AppNav
          email={(session.profile?.email as string) || ""}
          canViewAdmin={session.canViewAdmin}
          canManageAdminOrders={session.canManageAdminOrders}
          canManageAdminProducts={session.canManageAdminProducts}
          canManageAdminPosters={session.canManageAdminPosters}
          apiClient={session.apiClient}
          emailVerified={session.emailVerified}
          onLogout={() => { void session.logout(); }}
        />
      ) : (
        <header style={{ background: "var(--header-bg)", backdropFilter: "blur(12px)", borderBottom: "1px solid rgba(0,212,255,0.1)", position: "sticky", top: 0, zIndex: 50 }}>
          <div style={{ maxWidth: "1280px", margin: "0 auto", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px" }}>
            <Link href="/" style={{ display: "flex", alignItems: "center", gap: "10px", textDecoration: "none" }}>
              <span style={{ width: "32px", height: "32px", borderRadius: "8px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", display: "grid", placeItems: "center" }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" /><line x1="3" y1="6" x2="21" y2="6" /><path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
              </span>
              <span style={{ fontSize: "1.1rem", fontWeight: 800, color: "#fff", fontFamily: "'Syne', sans-serif" }}>Rumal Store</span>
            </Link>
            <Link href="/" style={{ padding: "8px 20px", borderRadius: "8px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", fontWeight: 700, fontSize: "0.82rem", textDecoration: "none" }}>
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main style={{ maxWidth: "1280px", margin: "0 auto", padding: "16px 16px 48px" }}>
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">All Categories</span>
        </nav>

        {/* Page Hero */}
        <section
          className="animate-rise"
          style={{
            marginBottom: "28px",
            borderRadius: "20px",
            padding: "48px 32px",
            textAlign: "center",
            background: "linear-gradient(135deg, rgba(0,212,255,0.06) 0%, rgba(124,58,237,0.08) 100%)",
            border: "1px solid rgba(0,212,255,0.12)",
            position: "relative",
            overflow: "hidden",
          }}
        >
          <div style={{ position: "absolute", top: "-40px", right: "-40px", width: "160px", height: "160px", borderRadius: "50%", background: "rgba(0,212,255,0.06)", filter: "blur(40px)", pointerEvents: "none" }} />
          <div style={{ position: "absolute", bottom: "-40px", left: "-40px", width: "160px", height: "160px", borderRadius: "50%", background: "rgba(124,58,237,0.08)", filter: "blur(40px)", pointerEvents: "none" }} />
          <p style={{ fontSize: "0.65rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.15em", color: "#00d4ff", margin: "0 0 8px" }}>EXPLORE</p>
          <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "clamp(1.8rem, 4vw, 2.6rem)", fontWeight: 800, color: "#fff", margin: "0 0 10px" }}>
            Browse Categories
          </h1>
          <p style={{ fontSize: "0.9rem", color: "var(--muted)", margin: 0, maxWidth: "480px", marginLeft: "auto", marginRight: "auto" }}>
            Explore our wide range of product categories. Find exactly what you&apos;re looking for.
          </p>
        </section>

        {/* Loading Skeletons */}
        {status === "loading" && (
          <div style={{ display: "grid", gap: "20px", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} style={{ borderRadius: "16px", overflow: "hidden" }}>
                <div className="skeleton" style={{ height: "120px", width: "100%" }} />
                <div style={{ padding: "16px", display: "grid", gap: "8px" }}>
                  <div className="skeleton" style={{ height: "14px", width: "50%" }} />
                  <div className="skeleton" style={{ height: "12px", width: "75%" }} />
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
            <Link href="/products" className="btn-primary" style={{ marginTop: "8px", padding: "10px 24px", fontSize: "0.85rem", textDecoration: "none" }}>
              Browse All Products
            </Link>
          </div>
        )}

        {/* Category Grid */}
        {status === "ready" && parents.length > 0 && (
          <>
            <div style={{ display: "grid", gap: "20px", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))" }}>
              {parents.map((parent, idx) => {
                const meta = getCategoryMeta(parent.name);
                const children = subsByParent.get(parent.id) || [];
                return (
                  <div
                    key={parent.id}
                    className="animate-rise"
                    style={{
                      animationDelay: `${idx * 55}ms`,
                      borderRadius: "16px",
                      overflow: "hidden",
                      background: "rgba(17,17,40,0.7)",
                      backdropFilter: "blur(16px)",
                      border: `1px solid ${meta.accent}22`,
                      transition: "box-shadow 0.25s, border-color 0.25s",
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
                    <Link href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`} style={{ textDecoration: "none", display: "block" }}>
                      <div
                        style={{
                          padding: "24px 20px",
                          background: `linear-gradient(135deg, ${meta.accent}18 0%, ${meta.accent}08 100%)`,
                          borderBottom: `1px solid ${meta.accent}20`,
                          display: "flex",
                          alignItems: "center",
                          gap: "16px",
                          position: "relative",
                          overflow: "hidden",
                        }}
                      >
                        <div style={{ position: "absolute", top: "-20px", right: "-20px", width: "80px", height: "80px", borderRadius: "50%", background: `${meta.accent}12`, filter: "blur(20px)", pointerEvents: "none" }} />
                        <span style={{ color: meta.accent, flexShrink: 0, filter: `drop-shadow(0 0 8px ${meta.accent}60)` }}>
                          {meta.icon}
                        </span>
                        <div>
                          <h2 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.1rem", fontWeight: 800, color: "#fff", margin: "0 0 3px" }}>
                            {parent.name}
                          </h2>
                          <p style={{ fontSize: "0.72rem", color: "var(--muted)", margin: 0 }}>
                            {children.length} {children.length === 1 ? "subcategory" : "subcategories"}
                          </p>
                        </div>
                      </div>
                    </Link>

                    {/* Subcategory Pills */}
                    {children.length > 0 && (
                      <div style={{ padding: "14px 16px", borderBottom: `1px solid rgba(255,255,255,0.04)` }}>
                        <p style={{ fontSize: "0.6rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.14em", color: "var(--muted-2)", marginBottom: "10px" }}>
                          Subcategories
                        </p>
                        <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                          {children.map((sub) => (
                            <Link
                              key={sub.id}
                              href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                              style={{
                                padding: "4px 12px",
                                borderRadius: "20px",
                                border: "1px solid rgba(0,212,255,0.12)",
                                background: "rgba(0,212,255,0.04)",
                                color: "var(--ink-light)",
                                fontSize: "0.75rem",
                                fontWeight: 500,
                                textDecoration: "none",
                                transition: "all 0.18s",
                              }}
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
                    <div style={{ padding: "12px 16px" }}>
                      <Link
                        href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", color: meta.accent, fontSize: "0.82rem", fontWeight: 700, textDecoration: "none", transition: "opacity 0.15s" }}
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

            <div style={{ marginTop: "40px", textAlign: "center" }}>
              <Link href="/products" className="btn-outline" style={{ padding: "12px 32px", fontSize: "0.875rem", textDecoration: "none" }}>
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
