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
    <nav style={{ marginBottom: "16px" }}>
      <div
        style={{ position: "relative", display: "inline-block" }}
        onMouseEnter={openMenu}
        onMouseLeave={scheduleClose}
      >
        {/* Trigger Button */}
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-label="Browse all categories"
          style={{
            display: "inline-flex", alignItems: "center", gap: "8px",
            padding: "9px 16px", borderRadius: "10px", border: "1px solid rgba(0,212,255,0.2)",
            background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
            color: "#fff", fontSize: "0.82rem", fontWeight: 700, cursor: "pointer",
            boxShadow: "0 0 16px rgba(0,212,255,0.2)", transition: "all 0.2s",
          }}
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
            style={{ position: "absolute", top: "100%", left: 0, right: 0, height: "10px", zIndex: 49 }}
          />
        )}

        {/* Dropdown Panel */}
        {open && (
          <div role="menu" style={dropdownStyle} onMouseEnter={openMenu} onMouseLeave={scheduleClose}>
            {/* Header */}
            <div style={{ padding: "12px 14px", borderBottom: "1px solid rgba(0,212,255,0.08)", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <p style={{ fontSize: "0.65rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.12em", color: "var(--muted)", margin: 0 }}>Browse Categories</p>
              <Link
                href="/products"
                style={{ fontSize: "0.72rem", fontWeight: 700, color: "#00d4ff", textDecoration: "none", display: "inline-flex", alignItems: "center", gap: "4px" }}
              >
                View All Products
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </Link>
            </div>

            {/* Body: Parent List | Sub Grid */}
            <div style={{ display: "grid", gridTemplateColumns: "220px 1fr" }}>
              {/* Parent list */}
              <div style={{ borderRight: "1px solid rgba(0,212,255,0.08)", padding: "8px", maxHeight: "360px", overflowY: "auto" }}>
                {parents.map((parent) => {
                  const isActive = activeParent?.id === parent.id;
                  const childCount = (subsByParent.get(parent.id) || []).length;
                  return (
                    <Link
                      key={parent.id}
                      role="menuitem"
                      href={`/categories/${encodeURIComponent(toCategorySlug(parent))}`}
                      onMouseEnter={() => setActiveParentId(parent.id)}
                      style={{
                        display: "flex", alignItems: "center", justifyContent: "space-between",
                        padding: "8px 12px", borderRadius: "8px", marginBottom: "2px",
                        textDecoration: "none", fontSize: "0.82rem", transition: "all 0.15s",
                        background: isActive ? "rgba(0,212,255,0.08)" : "transparent",
                        color: isActive ? "#00d4ff" : "#c8c8e8",
                        border: isActive ? "1px solid rgba(0,212,255,0.15)" : "1px solid transparent",
                      }}
                    >
                      <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{parent.name}</span>
                      <span style={{ fontSize: "0.65rem", color: "var(--muted)", flexShrink: 0, marginLeft: "8px" }}>{childCount}</span>
                    </Link>
                  );
                })}
              </div>

              {/* Sub grid */}
              <div style={{ padding: "12px", maxHeight: "360px", overflowY: "auto" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "10px" }}>
                  <p style={{ fontSize: "0.65rem", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: 0 }}>
                    {activeParent?.name || "Subcategories"}
                  </p>
                  {activeParent && (
                    <Link
                      href={`/categories/${encodeURIComponent(toCategorySlug(activeParent))}`}
                      style={{ fontSize: "0.72rem", fontWeight: 700, color: "#00d4ff", textDecoration: "none" }}
                    >
                      Open →
                    </Link>
                  )}
                </div>

                {activeSubs.length === 0 && (
                  <p style={{ padding: "10px 12px", borderRadius: "8px", background: "rgba(0,212,255,0.03)", fontSize: "0.8rem", color: "var(--muted)", margin: 0 }}>
                    No subcategories
                  </p>
                )}

                {activeSubs.length > 0 && (
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px" }}>
                    {activeSubs.map((sub) => (
                      <Link
                        key={sub.id}
                        href={`/categories/${encodeURIComponent(toCategorySlug(sub))}`}
                        style={{
                          padding: "8px 12px", borderRadius: "8px",
                          border: "1px solid rgba(0,212,255,0.08)",
                          background: "rgba(0,212,255,0.02)",
                          fontSize: "0.8rem", color: "#c8c8e8",
                          textDecoration: "none", transition: "all 0.15s",
                          display: "block",
                        }}
                        onMouseEnter={(e) => {
                          (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,212,255,0.3)";
                          (e.currentTarget as HTMLElement).style.color = "#00d4ff";
                          (e.currentTarget as HTMLElement).style.background = "rgba(0,212,255,0.06)";
                        }}
                        onMouseLeave={(e) => {
                          (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,212,255,0.08)";
                          (e.currentTarget as HTMLElement).style.color = "#c8c8e8";
                          (e.currentTarget as HTMLElement).style.background = "rgba(0,212,255,0.02)";
                        }}
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
