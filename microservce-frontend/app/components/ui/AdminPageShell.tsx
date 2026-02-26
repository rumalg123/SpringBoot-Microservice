"use client";

import { type ReactNode } from "react";
import Link from "next/link";

type Crumb = { label: string; href?: string };
type Props = { title: string; breadcrumbs?: Crumb[]; actions?: ReactNode; children: ReactNode };

/**
 * Content wrapper for admin pages.
 * AppNav + Footer are rendered by app/admin/layout.tsx — this component
 * only provides the inner content structure (breadcrumbs, title, actions).
 */
export default function AdminPageShell({ title, breadcrumbs = [], actions, children }: Props) {
  return (
    <main className="min-h-screen bg-bg text-ink pt-[100px] px-6 pb-12">
      <div className="max-w-[1280px] mx-auto">
        {breadcrumbs.length > 0 && (
          <nav aria-label="Breadcrumbs" className="flex gap-1.5 text-sm text-muted mb-3">
            {breadcrumbs.map((c, i) => (
              <span key={i}>
                {i > 0 && <span className="mx-1">/</span>}
                {c.href ? <Link href={c.href} className="text-brand no-underline">{c.label}</Link> : <span>{c.label}</span>}
              </span>
            ))}
          </nav>
        )}
        <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
          <h1 className="gradient-text" style={{ fontFamily: "var(--font-display, Syne, sans-serif)", fontSize: "clamp(1.4rem,3vw,1.8rem)" }}>{title}</h1>
          {actions && <div className="flex gap-2">{actions}</div>}
        </div>
        {children}
      </div>
    </main>
  );
}
