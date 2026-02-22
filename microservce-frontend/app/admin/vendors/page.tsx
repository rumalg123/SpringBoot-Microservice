"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import ConfirmModal from "../../components/ConfirmModal";
import Footer from "../../components/Footer";
import VendorFormPanel from "../../components/admin/vendors/VendorFormPanel";
import VendorListPanel from "../../components/admin/vendors/VendorListPanel";
import VendorOnboardingPanel from "../../components/admin/vendors/VendorOnboardingPanel";
import { useAdminVendors } from "../../components/admin/vendors/useAdminVendors";
import { useAuthSession } from "../../../lib/authSession";

export default function AdminVendorsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const vendors = useAdminVendors(session.apiClient);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.isSuperAdmin) {
      router.replace("/products");
      return;
    }
    void vendors.loadVendors();
  }, [router, session.status, session.isAuthenticated, session.isSuperAdmin]); // eslint-disable-line react-hooks/exhaustive-deps

  if (session.status === "loading" || session.status === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)" }} />;
  }
  if (!session.isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => {
          void session.logout();
        }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <Link href="/admin/orders">Admin</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <span className="breadcrumb-current">Vendors</span>
        </nav>

        <section
          className="animate-rise space-y-4 rounded-xl p-5"
          style={{
            background: "rgba(17,17,40,0.7)",
            border: "1px solid rgba(0,212,255,0.1)",
            backdropFilter: "blur(16px)",
          }}
        >
          <div className="flex items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN VENDORS</p>
              <h1 className="text-2xl font-bold text-[var(--ink)]">Vendor Setup & Onboarding</h1>
              <p className="mt-1 text-xs text-[var(--muted)]">
                Create vendors, restore deleted vendors, and onboard vendor admins without calling APIs manually.
              </p>
            </div>
            <button
              type="button"
              onClick={vendors.refreshCurrentVendorList}
              disabled={vendors.showDeleted ? vendors.loadingDeleted : vendors.loading}
              className="rounded-md border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
              style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
            >
              {(vendors.showDeleted ? vendors.loadingDeleted : vendors.loading) ? "Refreshing..." : "Refresh Vendors"}
            </button>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.05fr,0.95fr]">
            <div className="space-y-4">
              <VendorFormPanel
                form={vendors.form}
                slugStatus={vendors.slugStatus}
                saving={vendors.savingVendor}
                onChange={vendors.setForm}
                onSlugEdited={() => vendors.setSlugEdited(true)}
                onReset={vendors.resetVendorForm}
                onSubmit={() => {
                  void vendors.saveVendor();
                }}
              />

              <VendorListPanel
                vendors={vendors.vendors}
                deletedVendors={vendors.deletedVendors}
                showDeleted={vendors.showDeleted}
                loading={vendors.loading}
                loadingDeleted={vendors.loadingDeleted}
                deletedLoaded={vendors.deletedLoaded}
                selectedVendorId={vendors.selectedVendorId}
                onRefresh={vendors.refreshCurrentVendorList}
                onToggleShowDeleted={vendors.setShowDeleted}
                onEditVendor={vendors.handleEditVendor}
                onSelectVendor={vendors.handleSelectVendor}
                onDeleteVendor={vendors.openDeleteVendorConfirm}
                onRestoreVendor={vendors.openRestoreVendorConfirm}
              />
            </div>

            <VendorOnboardingPanel
              vendors={vendors.vendors}
              selectedVendorId={vendors.selectedVendorId}
              selectedVendor={vendors.selectedVendor}
              onboardForm={vendors.onboardForm}
              onboarding={vendors.onboarding}
              onboardStatus={vendors.onboardStatus}
              loadingUsers={vendors.loadingUsers}
              vendorUsers={vendors.vendorUsers}
              removingMembershipId={vendors.removingMembershipId}
              onSelectVendorId={vendors.handleSelectVendorId}
              onFillFromVendor={() => {
                if (vendors.selectedVendor) vendors.fillOnboardFromVendor(vendors.selectedVendor);
              }}
              onChangeOnboardForm={vendors.setOnboardForm}
              onSubmit={() => {
                void vendors.onboardVendorAdmin();
              }}
              onRefreshUsers={() => {
                if (vendors.selectedVendorId) void vendors.loadVendorUsers(vendors.selectedVendorId);
              }}
              onRemoveUser={(user) => vendors.openRemoveVendorUserConfirm(vendors.selectedVendorId, user)}
            />
          </div>

          <p className="text-xs text-[var(--muted)]">{vendors.status}</p>
        </section>
      </main>

      <Footer />

      <ConfirmModal
        open={Boolean(vendors.confirmState)}
        title={vendors.confirmUi.title}
        message={vendors.confirmUi.message}
        confirmLabel={vendors.confirmUi.confirmLabel}
        danger={vendors.confirmUi.danger}
        loading={vendors.confirmLoading}
        onCancel={() => {
          if (!vendors.confirmLoading) vendors.setConfirmState(null);
        }}
        onConfirm={() => {
          void vendors.handleConfirmAction();
        }}
      />
    </div>
  );
}

