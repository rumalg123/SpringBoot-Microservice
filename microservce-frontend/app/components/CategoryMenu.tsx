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

const CATEGORY_ICONS: Record<string, string> = {
  electronics: "📱",
  fashion: "👗",
  home: "🏠",
  beauty: "💄",
  sports: "⚽",
  toys: "🧸",
  books: "📚",
  food: "🍕",
  automotive: "🚗",
  garden: "🌿",
  health: "💊",
  office: "💼",
};

function getCategoryIcon(name: string): string {
  const lower = name.toLowerCase();
  for (const [key, icon] of Object.entries(CATEGORY_ICONS)) {
    if (lower.includes(key)) return icon;
  }
  return "📂";
}

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

  const parents = useMemo(() => categories.filter((c) => c.type === "PARENT"), [categories]);
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

  if (parents.length === 0) return null;

  return (
    <nav className="mb-4 overflow-hidden rounded-xl bg-white shadow-sm">
      {/* Horizontal Category Scroll */}
      <div className="flex items-center gap-1 overflow-x-auto px-3 py-2.5 hide-scrollbar">
        {/* All Categories Mega Menu */}
        <div className="group relative shrink-0 pb-1">
          <button className="flex items-center gap-2 rounded-lg bg-[var(--brand)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--brand-hover)]">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" /></svg>
            All Categories
          </button>
          <div className="absolute left-0 top-full z-40 hidden min-w-72 pt-1 group-hover:block">
            <div className="rounded-xl border border-[var(--line)] bg-white p-2 shadow-2xl">
              <Link
                href="/products"
                className="mb-1 flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-semibold text-[var(--ink)] no-underline transition hover:bg-[var(--brand-soft)]"
              >
                🏪 All Products
              </Link>
              <div className="my-1 h-px bg-[var(--line)]" />
              {parents.map((parent) => {
                const children = subsByParent.get(parent.id) || [];
                return (
                  <div key={parent.id} className="group/parent relative">
                    <Link
                      href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                      className="flex items-center justify-between rounded-lg px-3 py-2.5 text-sm text-[var(--ink)] no-underline transition hover:bg-[var(--brand-soft)]"
                    >
                      <span className="flex items-center gap-2">
                        <span>{getCategoryIcon(parent.name)}</span>
                        {parent.name}
                      </span>
                      {children.length > 0 && <span className="text-xs text-[var(--muted)]">›</span>}
                    </Link>
                    {children.length > 0 && (
                      <div className="absolute left-full top-0 z-50 hidden min-w-56 pl-1 group-hover/parent:block">
                        <div className="rounded-xl border border-[var(--line)] bg-white p-2 shadow-2xl">
                          {children.map((sub) => (
                            <Link
                              key={sub.id}
                              href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                              className="block rounded-lg px-3 py-2 text-sm text-[var(--ink)] no-underline transition hover:bg-[var(--brand-soft)]"
                            >
                              {sub.name}
                            </Link>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Category Pills */}
        {parents.map((parent) => (
          <Link
            key={parent.id}
            href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
            className="category-pill shrink-0 no-underline"
          >
            <span>{getCategoryIcon(parent.name)}</span>
            {parent.name}
          </Link>
        ))}
      </div>
    </nav>
  );
}
