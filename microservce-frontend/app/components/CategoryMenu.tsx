"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

type Category = {
  id: string;
  name: string;
  slug?: string | null;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

function toCategorySlug(category: Category): string {
  const fromSlug = (category.slug || "").trim();
  if (fromSlug) return slugify(fromSlug);
  return slugify(category.name || "");
}

export default function CategoryMenu() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [open, setOpen] = useState(false);
  const [activeParentId, setActiveParentId] = useState<string>("");

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/categories`, { cache: "no-store" });
        if (!res.ok) return;
        const data = (await res.json()) as Category[];
        setCategories(data || []);
      } catch {
        setCategories([]);
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

  useEffect(() => {
    if (parents.length === 0) {
      setActiveParentId("");
      return;
    }
    if (!activeParentId || !parents.some((parent) => parent.id === activeParentId)) {
      setActiveParentId(parents[0].id);
    }
  }, [parents, activeParentId]);

  if (parents.length === 0) return null;

  const activeParent = parents.find((parent) => parent.id === activeParentId) || parents[0];
  const activeSubs = activeParent ? (subsByParent.get(activeParent.id) || []) : [];

  return (
    <nav className="mb-4 rounded-xl bg-white p-2 shadow-sm">
      <div
        className="relative inline-block"
        onMouseEnter={() => {
          setOpen(true);
          if (!activeParentId && parents.length > 0) {
            setActiveParentId(parents[0].id);
          }
        }}
        onMouseLeave={() => setOpen(false)}
      >
        <button
          type="button"
          onClick={() => setOpen((current) => !current)}
          className="flex items-center gap-2 rounded-lg bg-[var(--brand)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--brand-hover)]"
          aria-expanded={open}
          aria-label="Browse all categories"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
          All Categories
        </button>

        {open && (
          <div className="absolute left-0 top-full z-40 mt-1 w-[min(92vw,760px)] rounded-xl border border-[var(--line)] bg-white shadow-2xl">
            <div className="border-b border-[var(--line)] p-3">
              <Link
                href="/products"
                className="inline-flex items-center rounded-lg bg-[var(--brand-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--brand)] no-underline"
              >
                View All Products
              </Link>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-[250px_1fr]">
              <div className="border-b border-[var(--line)] p-2 md:border-b-0 md:border-r">
                {parents.map((parent) => {
                  const isActive = activeParent?.id === parent.id;
                  const childCount = (subsByParent.get(parent.id) || []).length;
                  return (
                    <Link
                      key={parent.id}
                      href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                      onMouseEnter={() => setActiveParentId(parent.id)}
                      className={`mb-1 flex items-center justify-between rounded-lg px-3 py-2 text-sm no-underline transition ${
                        isActive
                          ? "bg-[var(--brand-soft)] text-[var(--brand)]"
                          : "text-[var(--ink)] hover:bg-[#fafafa]"
                      }`}
                    >
                      <span className="line-clamp-1">{parent.name}</span>
                      <span className="text-xs text-[var(--muted)]">{childCount}</span>
                    </Link>
                  );
                })}
              </div>

              <div className="p-3">
                <div className="mb-2 flex items-center justify-between">
                  <p className="text-xs font-bold uppercase tracking-[0.1em] text-[var(--muted)]">
                    {activeParent?.name || "Subcategories"}
                  </p>
                  {activeParent && (
                    <Link
                      href={`/categories/${encodeURIComponent(toCategorySlug(activeParent))}`}
                      className="text-xs font-semibold text-[var(--brand)] no-underline hover:underline"
                    >
                      Open
                    </Link>
                  )}
                </div>

                {activeSubs.length === 0 && (
                  <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">
                    No subcategories.
                  </p>
                )}

                {activeSubs.length > 0 && (
                  <div className="grid gap-2 sm:grid-cols-2">
                    {activeSubs.map((sub) => (
                      <Link
                        key={sub.id}
                        href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                        className="rounded-lg border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink)] no-underline transition hover:border-[var(--brand)] hover:bg-[var(--brand-soft)] hover:text-[var(--brand)]"
                      >
                        {sub.name}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
