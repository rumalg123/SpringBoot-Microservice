"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

type Category = {
  id: string;
  name: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

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
    <nav className="mb-5 rounded-2xl border border-[var(--line)] bg-white px-3 py-2">
      <div className="flex items-center gap-3">
        <div className="group relative pb-2">
          <button className="inline-flex rounded-full border border-[var(--line)] bg-[var(--surface)] px-4 py-1.5 text-sm font-semibold text-[var(--ink)] hover:bg-[var(--brand-soft)]">
            All Categories
          </button>
          <div className="absolute left-0 top-full z-30 hidden min-w-64 group-hover:block">
            <div className="rounded-xl border border-[var(--line)] bg-white p-2 shadow-2xl">
              <Link
                href="/products"
                className="mb-1 block rounded-lg px-3 py-2 text-sm font-semibold text-[var(--ink)] hover:bg-[var(--brand-soft)]"
              >
                All Products
              </Link>
              {parents.map((parent) => {
                const children = subsByParent.get(parent.id) || [];
                return (
                  <div key={parent.id} className="group/parent relative">
                    <Link
                      href={`/products?mainCategory=${encodeURIComponent(parent.name)}`}
                      className="flex items-center justify-between rounded-lg px-3 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
                    >
                      <span>{parent.name}</span>
                      {children.length > 0 && <span className="text-xs text-[var(--muted)]">›</span>}
                    </Link>
                    {children.length > 0 && (
                      <div className="absolute left-[calc(100%-8px)] top-0 z-40 hidden min-w-56 group-hover/parent:block">
                        <div className="rounded-xl border border-[var(--line)] bg-white p-2 shadow-2xl">
                          {children.map((sub) => (
                            <Link
                              key={sub.id}
                              href={`/products?mainCategory=${encodeURIComponent(parent.name)}&subCategory=${encodeURIComponent(sub.name)}`}
                              className="block rounded-lg px-3 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
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
      </div>
    </nav>
  );
}
