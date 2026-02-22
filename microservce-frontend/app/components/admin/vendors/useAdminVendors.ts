"use client";

import { useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import type { Vendor, VendorUser } from "./types";
import { getApiErrorMessage } from "./vendorUtils";
import { useVendorForm } from "./useVendorForm";
import { useVendorList } from "./useVendorList";
import { useVendorOnboarding } from "./useVendorOnboarding";

export type VendorConfirmState =
  | { kind: "deleteVendor"; vendor: Vendor }
  | { kind: "restoreVendor"; vendor: Vendor }
  | { kind: "removeVendorUser"; vendorId: string; user: VendorUser }
  | null;

export function useAdminVendors(apiClient: AxiosInstance | null) {
  const vendorList = useVendorList(apiClient);
  const vendorOnboarding = useVendorOnboarding({
    apiClient,
    vendors: vendorList.vendors,
    selectedVendorId: vendorList.selectedVendorId,
    setSelectedVendorId: vendorList.setSelectedVendorId,
  });
  const vendorForm = useVendorForm({
    apiClient,
    deletedLoaded: vendorList.deletedLoaded,
    setPageStatus: vendorList.setStatus,
    onRefreshVendors: vendorList.loadVendors,
    onRefreshDeletedVendors: vendorList.loadDeletedVendors,
    onSelectVendor: vendorOnboarding.handleSelectVendor,
    onEditStart: () => vendorList.setShowDeleted(false),
  });

  const [confirmState, setConfirmState] = useState<VendorConfirmState>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const openDeleteVendorConfirm = (vendor: Vendor) => setConfirmState({ kind: "deleteVendor", vendor });
  const openRestoreVendorConfirm = (vendor: Vendor) => setConfirmState({ kind: "restoreVendor", vendor });
  const openRemoveVendorUserConfirm = (vendorId: string, user: VendorUser) =>
    setConfirmState({ kind: "removeVendorUser", vendorId, user });

  const handleConfirmAction = async () => {
    if (!apiClient || !confirmState || confirmLoading) return;
    setConfirmLoading(true);
    try {
      if (confirmState.kind === "deleteVendor") {
        const vendor = confirmState.vendor;
        await apiClient.delete(`/admin/vendors/${vendor.id}`);

        if (vendorList.selectedVendorId === vendor.id) {
          vendorOnboarding.clearSelectedVendorContext();
        }
        if (vendorForm.form.id === vendor.id) {
          vendorForm.resetVendorForm();
        }

        await vendorList.loadVendors();
        if (vendorList.deletedLoaded || vendorList.showDeleted) {
          await vendorList.loadDeletedVendors();
        }
        vendorList.setStatus("Vendor deleted.");
        toast.success("Vendor soft deleted");
      } else if (confirmState.kind === "restoreVendor") {
        const vendor = confirmState.vendor;
        await apiClient.post(`/admin/vendors/${vendor.id}/restore`);
        await vendorList.loadVendors();
        if (vendorList.deletedLoaded || vendorList.showDeleted) {
          await vendorList.loadDeletedVendors();
        }
        vendorList.setStatus("Vendor restored.");
        toast.success("Vendor restored");
      } else if (confirmState.kind === "removeVendorUser") {
        const { vendorId, user } = confirmState;
        await vendorOnboarding.removeVendorUser(vendorId, user);
        toast.success("Vendor user removed");
      }
      setConfirmState(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Action failed."));
    } finally {
      setConfirmLoading(false);
    }
  };

  const confirmUi = {
    title:
      confirmState?.kind === "deleteVendor"
        ? "Delete Vendor"
        : confirmState?.kind === "restoreVendor"
          ? "Restore Vendor"
          : confirmState?.kind === "removeVendorUser"
            ? "Remove Vendor User"
            : "",
    message:
      confirmState?.kind === "deleteVendor"
        ? `Soft delete vendor "${confirmState.vendor.name}"? This hides it from active vendor lists.`
        : confirmState?.kind === "restoreVendor"
          ? `Restore vendor "${confirmState.vendor.name}" to active admin management lists?`
          : confirmState?.kind === "removeVendorUser"
            ? `Remove ${confirmState.user.displayName || confirmState.user.email} from this vendor?`
            : "",
    confirmLabel:
      confirmState?.kind === "deleteVendor"
        ? "Delete Vendor"
        : confirmState?.kind === "restoreVendor"
          ? "Restore Vendor"
          : confirmState?.kind === "removeVendorUser"
            ? "Remove User"
            : "Confirm",
    danger: confirmState?.kind === "deleteVendor" || confirmState?.kind === "removeVendorUser",
  };

  return {
    vendors: vendorList.vendors,
    deletedVendors: vendorList.deletedVendors,
    loading: vendorList.loading,
    loadingDeleted: vendorList.loadingDeleted,
    deletedLoaded: vendorList.deletedLoaded,
    showDeleted: vendorList.showDeleted,
    status: vendorList.status,
    selectedVendorId: vendorList.selectedVendorId,
    selectedVendor: vendorOnboarding.selectedVendor,

    form: vendorForm.form,
    slugStatus: vendorForm.slugStatus,
    savingVendor: vendorForm.savingVendor,
    lastVendorSavedAt: vendorForm.lastVendorSavedAt,

    vendorUsers: vendorOnboarding.vendorUsers,
    loadingUsers: vendorOnboarding.loadingUsers,
    onboardForm: vendorOnboarding.onboardForm,
    onboarding: vendorOnboarding.onboarding,
    onboardStatus: vendorOnboarding.onboardStatus,
    removingMembershipId: vendorOnboarding.removingMembershipId,
    lastVendorSelectedAt: vendorOnboarding.lastVendorSelectedAt,
    lastOnboardedAt: vendorOnboarding.lastOnboardedAt,
    lastOnboardResult: vendorOnboarding.lastOnboardResult,

    confirmState,
    confirmLoading,
    confirmUi,

    setShowDeleted: vendorList.setShowDeleted,
    setForm: vendorForm.setForm,
    setSlugEdited: vendorForm.setSlugEdited,
    setOnboardForm: vendorOnboarding.setOnboardForm,
    setConfirmState,

    loadVendors: vendorList.loadVendors,
    loadDeletedVendors: vendorList.loadDeletedVendors,
    loadVendorUsers: vendorOnboarding.loadVendorUsers,
    refreshCurrentVendorList: vendorList.refreshCurrentVendorList,

    fillOnboardFromVendor: vendorOnboarding.fillOnboardFromVendor,
    handleSelectVendor: vendorOnboarding.handleSelectVendor,
    handleSelectVendorId: vendorOnboarding.handleSelectVendorId,

    handleEditVendor: vendorForm.handleEditVendor,
    resetVendorForm: vendorForm.resetVendorForm,
    saveVendor: vendorForm.saveVendor,
    onboardVendorAdmin: vendorOnboarding.onboardVendorAdmin,

    openDeleteVendorConfirm,
    openRestoreVendorConfirm,
    openRemoveVendorUserConfirm,
    handleConfirmAction,
  };
}
