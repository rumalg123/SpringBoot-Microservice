"use client";

import AppNav from "./AppNav";
import { useAuthSession } from "../../lib/authSession";

/**
 * Self-contained AppNav wrapper that reads auth state internally,
 * eliminating the need for every page to drill 11+ props.
 */
export default function ConnectedAppNav() {
  const session = useAuthSession();

  return (
    <AppNav
      email={(session.profile?.email as string) || ""}
      isSuperAdmin={session.isSuperAdmin}
      isPlatformStaff={session.isPlatformStaff}
      isVendorAdmin={session.isVendorAdmin}
      isVendorStaff={session.isVendorStaff}
      canViewAdmin={session.canViewAdmin}
      canManageAdminOrders={session.canManageAdminOrders}
      canManageAdminPayments={session.canManageAdminPayments}
      canManageAdminProducts={session.canManageAdminProducts}
      canManageAdminCategories={session.canManageAdminCategories}
      canManageAdminVendors={session.canManageAdminVendors}
      canManageAdminPosters={session.canManageAdminPosters}
      canManageAdminPromotions={session.canManageAdminPromotions}
      canManageAdminReviews={session.canManageAdminReviews}
      canManageAdminInventory={session.canManageAdminInventory}
      canViewVendorAnalytics={session.canViewVendorAnalytics}
      canViewVendorFinance={session.canViewVendorFinance}
      canManageVendorSettings={session.canManageVendorSettings}
      apiClient={session.apiClient}
      emailVerified={session.emailVerified}
      onLogout={() => {
        void session.logout();
      }}
    />
  );
}
