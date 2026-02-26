"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import type { Category } from "../../lib/types/category";
import { API_BASE } from "../../lib/constants";

function slugify(value: string): string {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}

function toCategorySlug(category: Category): string {
  const fromSlug = (category.slug || "").trim();
  if (fromSlug) return slugify(fromSlug);
  return slugify(category.name || "");
}

const dropdownStyle: React.CSSProperties = {
  position: "absolute",
  left: 0,
  top: "calc(100% + 6px)",
  zIndex: 50,
  width: "min(92vw, 760px)",
  borderRadius: "16px",
  border: "1px solid rgba(0,212,255,0.15)",
  background: "rgba(13,13,31,0.97)",
  backdropFilter: "blur(20px)",
  boxShadow: "0 24px 80px rgba(0,0,0,0.7), 0 0 0 1px rgba(0,212,255,0.06)",
  overflow: "hidden",
};

export default function CategoryMenu() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [open, setOpen] = useState(false);
  const [activeParentId, setActiveParentId] = useState<string>("");
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const openMenu = () => {
    if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null; }
    setOpen(true);
    if (!activeParentId && parents.length > 0) setActiveParentId(parents[0].id);
  };

  const scheduleClose = () => {
    closeTimer.current = setTimeout(() => setOpen(false), 150);
  };

  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/categories`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) return;
        if (controller.signal.aborted) return;
        setCategories(((await res.json()) as Category[]) || []);
      } catch {
        if (!controller.signal.aborted) setCategories([]);
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

  useEffect(() => {
    if (parents.length === 0) { setActiveParentId(""); return; }
    if (!activeParentId || !parents.some((p) => p.id === activeParentId)) {
      setActiveParentId(parents[0].id);
    }
  }, [parents, activeParentId]);

  if (parents.length === 0) return null;

  const activeParent = parents.find((p) => p.id === activeParentId) || parents[0];
  const activeSubs = activeParent ? (subsByParent.get(activeParent.id) || []) : [];

  return (
    <nav className="mb-4">
      <div
        className="relative inline-block"
        onMouseEnter={openMenu}
        onMouseLeave={scheduleClose}
      >
        {/* Trigger Button */}
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="true"
          aria-expanded={open}
          aria-label="Browse all categories"
          className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-[rgba(0,212,255,0.2)] px-4 py-[9px] text-[0.82rem] font-bold text-white shadow-[0_0_16px_rgba(0,212,255,0.2)] transition-all duration-200"
          style={{ background: "linear-gradient(135deg, #00d4ff, #7c3aed)" }}
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
          </svg>
          All Categories
        </button>

        {/* Transparent bridge — fills the gap between button and dropdown so onMouseLeave doesn't fire */}
        {open && (
          <div
            onMouseEnter={openMenu}
            onMouseLeave={scheduleClose}
            className="absolute left-0 right-0 top-full z-[49] h-[10px]"
          />
        )}

        {/* Dropdown Panel */}
        {open && (
          <div role="menu" style={dropdownStyle} onMouseEnter={openMenu} onMouseLeave={scheduleClose}>
            {/* Header */}
            <div className="flex items-center justify-between border-b border-[rgba(0,212,255,0.08)] px-3.5 py-3">
              <p className="m-0 text-[0.65rem] font-extrabold uppercase tracking-[0.12em] text-muted">Browse Categories</p>
              <Link
                href="/products"
                className="inline-flex items-center gap-1 text-[0.72rem] font-bold text-[#00d4ff] no-underline"
              >
                View All Products
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </Link>
            </div>

            {/* Body: Parent List | Sub Grid */}
            <div className="grid grid-cols-[220px_1fr]">
              {/* Parent list */}
              <div className="max-h-[360px] overflow-y-auto border-r border-[rgba(0,212,255,0.08)] p-2">
                {parents.map((parent) => {
                  const isActive = activeParent?.id === parent.id;
                  const childCount = (subsByParent.get(parent.id) || []).length;
                  return (
                    <Link
                      key={parent.id}
                      role="menuitem"
                      href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                      onMouseEnter={() => setActiveParentId(parent.id)}
                      className="mb-0.5 flex items-center justify-between rounded-[8px] px-3 py-2 text-[0.82rem] no-underline transition-all duration-150"
                      style={{
                        background: isActive ? "rgba(0,212,255,0.08)" : "transparent",
                        color: isActive ? "#00d4ff" : "#c8c8e8",
                        border: isActive ? "1px solid rgba(0,212,255,0.15)" : "1px solid transparent",
                      }}
                    >
                      <span className="overflow-hidden text-ellipsis whitespace-nowrap">{parent.name}</span>
                      <span className="ml-2 shrink-0 text-[0.65rem] text-muted">{childCount}</span>
                    </Link>
                  );
                })}
              </div>

              {/* Sub grid */}
              <div className="max-h-[360px] overflow-y-auto p-3">
                <div className="mb-2.5 flex items-center justify-between">
                  <p className="m-0 text-[0.65rem] font-extrabold uppercase tracking-[0.1em] text-muted">
                    {activeParent?.name || "Subcategories"}
                  </p>
                  {activeParent && (
                    <Link
                      href={`/categories/${encodeURIComponent(toCategorySlug(activeParent))}`}
                      className="text-[0.72rem] font-bold text-[#00d4ff] no-underline"
                    >
                      Open →
                    </Link>
                  )}
                </div>

                {activeSubs.length === 0 && (
                  <p className="m-0 rounded-[8px] bg-[rgba(0,212,255,0.03)] px-3 py-2.5 text-sm text-muted">
                    No subcategories
                  </p>
                )}

                {activeSubs.length > 0 && (
                  <div className="grid grid-cols-2 gap-1.5">
                    {activeSubs.map((sub) => (
                      <Link
                        key={sub.id}
                        role="menuitem"
                        href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                        className="block rounded-[8px] border border-[rgba(0,212,255,0.08)] bg-[rgba(0,212,255,0.02)] px-3 py-2 text-sm text-[#c8c8e8] no-underline transition-all duration-150 hover:border-[rgba(0,212,255,0.3)] hover:bg-[rgba(0,212,255,0.06)] hover:text-[#00d4ff]"
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
