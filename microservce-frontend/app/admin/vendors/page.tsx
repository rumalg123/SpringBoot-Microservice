"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import AppNav from "../../components/AppNav";
import ConfirmModal from "../../components/ConfirmModal";
import Footer from "../../components/Footer";
import VendorFormPanel from "../../components/admin/vendors/VendorFormPanel";
import VendorListPanel from "../../components/admin/vendors/VendorListPanel";
import VendorDeletionEligibilityPanel from "../../components/admin/vendors/VendorDeletionEligibilityPanel";
import VendorLifecycleAuditPanel from "../../components/admin/vendors/VendorLifecycleAuditPanel";
import VendorOnboardingPanel from "../../components/admin/vendors/VendorOnboardingPanel";
import VendorSetupStepper from "../../components/admin/vendors/VendorSetupStepper";
import { useAdminVendors } from "../../components/admin/vendors/useAdminVendors";
import { useAuthSession } from "../../../lib/authSession";

export default function AdminVendorsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const vendors = useAdminVendors(session.apiClient);
  const [highlightTarget, setHighlightTarget] = useState<"vendor-list" | "onboarding" | "users" | null>(null);
  const highlightTimerRef = useRef<number | null>(null);

  const ONBOARDING_PANEL_ID = "vendor-onboarding-panel";
  const ONBOARDING_EMAIL_ID = "vendor-onboarding-email";
  const VENDOR_USERS_SECTION_ID = "vendor-users-section";
  const VENDOR_LIST_SECTION_ID = "vendor-list-section";

  const runGuidance = (
    target: "vendor-list" | "onboarding" | "users",
    options?: { focusEmail?: boolean }
  ) => {
    setHighlightTarget(target);
    if (highlightTimerRef.current) {
      window.clearTimeout(highlightTimerRef.current);
    }
    highlightTimerRef.current = window.setTimeout(() => setHighlightTarget(null), 1800);

    const elementId =
      target === "vendor-list"
        ? VENDOR_LIST_SECTION_ID
        : target === "onboarding"
          ? ONBOARDING_PANEL_ID
          : VENDOR_USERS_SECTION_ID;

    window.requestAnimationFrame(() => {
      const node = document.getElementById(elementId);
      node?.scrollIntoView({ behavior: "smooth", block: "start" });
      if (options?.focusEmail) {
        window.setTimeout(() => {
          const emailInput = document.getElementById(ONBOARDING_EMAIL_ID) as HTMLInputElement | null;
          emailInput?.focus();
          emailInput?.select?.();
        }, 250);
      }
    });
  };

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

  useEffect(() => {
    if (!vendors.lastVendorSavedAt) return;
    runGuidance("onboarding", { focusEmail: true });
  }, [vendors.lastVendorSavedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!vendors.lastVendorSelectedAt) return;
    runGuidance("onboarding", { focusEmail: !vendors.onboardForm.email.trim() });
  }, [vendors.lastVendorSelectedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!vendors.lastOnboardedAt) return;
    runGuidance("users");
  }, [vendors.lastOnboardedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    return () => {
      if (highlightTimerRef.current) {
        window.clearTimeout(highlightTimerRef.current);
      }
    };
  }, []);

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

          <VendorSetupStepper
            hasAnyVendors={vendors.vendors.length > 0}
            selectedVendorName={vendors.selectedVendor?.name || null}
            vendorUserCount={vendors.vendorUsers.length}
          />

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

              <div
                id={VENDOR_LIST_SECTION_ID}
                className="rounded-2xl transition-all duration-300"
                style={
                  highlightTarget === "vendor-list"
                    ? { boxShadow: "0 0 0 2px rgba(0,212,255,0.35), 0 0 30px rgba(0,212,255,0.10)" }
                    : undefined
                }
              >
                <VendorListPanel
                  vendors={vendors.vendors}
                  deletedVendors={vendors.deletedVendors}
                  showDeleted={vendors.showDeleted}
                  loading={vendors.loading}
                  loadingDeleted={vendors.loadingDeleted}
                  deletedLoaded={vendors.deletedLoaded}
                  selectedVendorId={vendors.selectedVendorId}
                  vendorDeletionEligibilityById={vendors.vendorDeletionEligibilityById}
                  eligibilityLoadingVendorId={vendors.eligibilityLoadingVendorId}
                  orderToggleVendorId={vendors.orderToggleVendorId}
                  onRefresh={vendors.refreshCurrentVendorList}
                  onToggleShowDeleted={vendors.setShowDeleted}
                  onEditVendor={vendors.handleEditVendor}
                  onSelectVendor={vendors.handleSelectVendor}
                  onDeleteVendor={vendors.openDeleteVendorConfirm}
                  onConfirmDeleteVendor={vendors.openConfirmDeleteVendorConfirm}
                  onRestoreVendor={vendors.openRestoreVendorConfirm}
                  onStopOrders={(vendor) => { void vendors.stopVendorOrders(vendor); }}
                  onResumeOrders={(vendor) => { void vendors.resumeVendorOrders(vendor); }}
                />
              </div>
            </div>

            <div
              className="rounded-2xl transition-all duration-300"
              style={
                highlightTarget === "onboarding"
                  ? { boxShadow: "0 0 0 2px rgba(0,212,255,0.35), 0 0 30px rgba(0,212,255,0.10)" }
                  : highlightTarget === "users"
                    ? { boxShadow: "0 0 0 2px rgba(16,185,129,0.28), 0 0 24px rgba(16,185,129,0.08)" }
                    : undefined
              }
            >
              <div className="space-y-4">
                <VendorDeletionEligibilityPanel
                  vendor={vendors.selectedVendor}
                  eligibility={vendors.selectedVendorDeletionEligibility}
                  loading={
                    Boolean(vendors.selectedVendorId) &&
                    vendors.eligibilityLoadingVendorId === vendors.selectedVendorId
                  }
                  onRefresh={() => {
                    if (vendors.selectedVendorId) void vendors.loadVendorDeletionEligibility(vendors.selectedVendorId);
                  }}
                />

                <VendorLifecycleAuditPanel
                  vendor={vendors.selectedVendor}
                  audits={vendors.selectedVendorLifecycleAudit}
                  loading={
                    Boolean(vendors.selectedVendorId) &&
                    vendors.lifecycleAuditLoadingVendorId === vendors.selectedVendorId
                  }
                  onRefresh={() => {
                    if (vendors.selectedVendorId) void vendors.loadVendorLifecycleAudit(vendors.selectedVendorId);
                  }}
                />

                <VendorOnboardingPanel
                  apiClient={session.apiClient}
                  panelId={ONBOARDING_PANEL_ID}
                  emailInputId={ONBOARDING_EMAIL_ID}
                  vendorUsersSectionId={VENDOR_USERS_SECTION_ID}
                  vendors={vendors.vendors}
                  selectedVendorId={vendors.selectedVendorId}
                  selectedVendor={vendors.selectedVendor}
                  onboardForm={vendors.onboardForm}
                  onboarding={vendors.onboarding}
                  onboardStatus={vendors.onboardStatus}
                  lastOnboardResult={vendors.lastOnboardResult}
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
            </div>
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
        reasonEnabled={vendors.confirmUi.reasonEnabled}
        reasonLabel="Reason for audit (optional)"
        reasonPlaceholder="Why are you performing this lifecycle action?"
        reasonValue={vendors.confirmReason}
        onReasonChange={vendors.setConfirmReason}
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
