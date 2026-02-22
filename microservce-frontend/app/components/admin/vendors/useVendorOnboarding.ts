"use client";

import { useMemo, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import {
  emptyOnboardForm,
  type OnboardForm,
  type Vendor,
  type VendorOnboardResponse,
  type VendorUser,
} from "./types";
import { getApiErrorMessage, splitName } from "./vendorUtils";

type UseVendorOnboardingArgs = {
  apiClient: AxiosInstance | null;
  vendors: Vendor[];
  selectedVendorId: string;
  setSelectedVendorId: (vendorId: string) => void;
};

const DEFAULT_ONBOARD_STATUS = "Select a vendor to onboard a vendor admin.";

export function useVendorOnboarding({
  apiClient,
  vendors,
  selectedVendorId,
  setSelectedVendorId,
}: UseVendorOnboardingArgs) {
  const [vendorUsers, setVendorUsers] = useState<VendorUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [onboardForm, setOnboardForm] = useState<OnboardForm>(emptyOnboardForm);
  const [onboarding, setOnboarding] = useState(false);
  const [onboardStatus, setOnboardStatus] = useState(DEFAULT_ONBOARD_STATUS);
  const [removingMembershipId, setRemovingMembershipId] = useState<string | null>(null);

  const selectedVendor = useMemo(
    () => vendors.find((vendor) => vendor.id === selectedVendorId) || null,
    [vendors, selectedVendorId]
  );

  const loadVendorUsers = async (vendorId: string) => {
    if (!apiClient || !vendorId) return;
    setLoadingUsers(true);
    try {
      const res = await apiClient.get(`/admin/vendors/${vendorId}/users`);
      setVendorUsers((res.data as VendorUser[]) || []);
      setOnboardStatus("Vendor users loaded.");
    } catch (err) {
      setVendorUsers([]);
      setOnboardStatus(getApiErrorMessage(err, "Failed to load vendor users."));
    } finally {
      setLoadingUsers(false);
    }
  };

  const fillOnboardFromVendor = (vendor: Vendor) => {
    const parts = splitName(vendor.contactPersonName || "");
    setOnboardForm((old) => ({
      ...old,
      email: old.email.trim() ? old.email : vendor.contactEmail,
      firstName: old.firstName.trim() ? old.firstName : parts.firstName,
      lastName: old.lastName.trim() ? old.lastName : parts.lastName,
      displayName: old.displayName.trim() ? old.displayName : (vendor.contactPersonName || vendor.name),
    }));
  };

  const handleSelectVendor = (vendor: Vendor) => {
    setSelectedVendorId(vendor.id);
    fillOnboardFromVendor(vendor);
    void loadVendorUsers(vendor.id);
  };

  const handleSelectVendorId = (vendorId: string) => {
    setSelectedVendorId(vendorId);
    const vendor = vendors.find((v) => v.id === vendorId);
    if (!vendor) {
      setVendorUsers([]);
      setOnboardStatus(DEFAULT_ONBOARD_STATUS);
      return;
    }
    fillOnboardFromVendor(vendor);
    void loadVendorUsers(vendorId);
  };

  const clearSelectedVendorContext = () => {
    setSelectedVendorId("");
    setVendorUsers([]);
    setOnboardStatus(DEFAULT_ONBOARD_STATUS);
  };

  const onboardVendorAdmin = async () => {
    if (!apiClient || onboarding) return;
    if (!selectedVendorId) {
      toast.error("Select a vendor first");
      return;
    }
    setOnboarding(true);
    try {
      const res = await apiClient.post(`/admin/vendors/${selectedVendorId}/users/onboard`, {
        keycloakUserId: onboardForm.keycloakUserId.trim() || null,
        email: onboardForm.email.trim(),
        firstName: onboardForm.firstName.trim() || null,
        lastName: onboardForm.lastName.trim() || null,
        displayName: onboardForm.displayName.trim() || null,
        vendorUserRole: onboardForm.vendorUserRole,
        createIfMissing: onboardForm.createIfMissing,
      });
      const data = (res.data as VendorOnboardResponse | undefined) || undefined;
      await loadVendorUsers(selectedVendorId);
      const emailStatus = data?.keycloakUserCreated
        ? data.keycloakActionEmailSent
          ? " Keycloak action email sent."
          : " Keycloak user created."
        : " Existing Keycloak user linked.";
      setOnboardStatus(`Vendor admin onboarded.${emailStatus}`);
      setOnboardForm((old) => ({
        ...emptyOnboardForm,
        vendorUserRole: old.vendorUserRole,
        createIfMissing: old.createIfMissing,
      }));
      toast.success(`Vendor admin onboarded.${emailStatus}`);
    } catch (err) {
      const message = getApiErrorMessage(err, "Failed to onboard vendor admin.");
      setOnboardStatus(message);
      toast.error(message);
    } finally {
      setOnboarding(false);
    }
  };

  const removeVendorUser = async (vendorId: string, user: VendorUser) => {
    if (!apiClient) return;
    setRemovingMembershipId(user.id);
    try {
      await apiClient.delete(`/admin/vendors/${vendorId}/users/${user.id}`);
      if (selectedVendorId === vendorId) {
        await loadVendorUsers(vendorId);
      }
      setOnboardStatus("Vendor user removed.");
    } finally {
      setRemovingMembershipId(null);
    }
  };

  return {
    vendorUsers,
    loadingUsers,
    onboardForm,
    onboarding,
    onboardStatus,
    removingMembershipId,
    selectedVendor,

    setOnboardForm,
    setOnboardStatus,
    setVendorUsers,

    loadVendorUsers,
    fillOnboardFromVendor,
    handleSelectVendor,
    handleSelectVendorId,
    clearSelectedVendorContext,
    onboardVendorAdmin,
    removeVendorUser,
  };
}

