"use client";
import { ReactNode } from "react";
import Link from "next/link";
import AppNav from "../AppNav";
import Footer from "../Footer";
import { useAuthSession } from "../../../lib/authSession";

type Crumb = { label: string; href?: string };
type Props = { title: string; breadcrumbs?: Crumb[]; actions?: ReactNode; children: ReactNode };

export default function AdminPageShell({ title, breadcrumbs = [], actions, children }: Props) {
  const session = useAuthSession();

  return (
    <>
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
      <main style={{ minHeight: "100vh", background: "var(--bg)", color: "var(--ink)", padding: "100px 24px 48px" }}>
        <div style={{ maxWidth: 1280, margin: "0 auto" }}>
          {breadcrumbs.length > 0 && (
            <nav style={{ display: "flex", gap: 6, fontSize: "0.75rem", color: "var(--muted)", marginBottom: 12 }}>
              {breadcrumbs.map((c, i) => (
                <span key={i}>
                  {i > 0 && <span style={{ margin: "0 4px" }}>/</span>}
                  {c.href ? <Link href={c.href} style={{ color: "var(--brand)", textDecoration: "none" }}>{c.label}</Link> : <span>{c.label}</span>}
                </span>
              ))}
            </nav>
          )}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 24, flexWrap: "wrap", gap: 12 }}>
            <h1 style={{ fontFamily: "var(--font-display, Syne, sans-serif)", fontSize: "clamp(1.4rem,3vw,1.8rem)", fontWeight: 800, background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>{title}</h1>
            {actions && <div style={{ display: "flex", gap: 8 }}>{actions}</div>}
          </div>
          {children}
        </div>
      </main>
      <Footer />
    </>
  );
}
