"use client";

import { useEffect, useMemo, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
  const queryClient = useQueryClient();
  const [onboardForm, setOnboardForm] = useState<OnboardForm>(emptyOnboardForm);
  const [onboarding, setOnboarding] = useState(false);
  const [onboardStatus, setOnboardStatus] = useState(DEFAULT_ONBOARD_STATUS);
  const [removingMembershipId, setRemovingMembershipId] = useState<string | null>(null);
  const [lastVendorSelectedAt, setLastVendorSelectedAt] = useState<number>(0);
  const [lastOnboardedAt, setLastOnboardedAt] = useState<number>(0);
  const [lastOnboardResult, setLastOnboardResult] = useState<VendorOnboardResponse | null>(null);

  const selectedVendor = useMemo(
    () => vendors.find((vendor) => vendor.id === selectedVendorId) || null,
    [vendors, selectedVendorId]
  );

  const vendorUsersQuery = useQuery<VendorUser[]>({
    queryKey: ["admin-vendor-users", selectedVendorId],
    queryFn: async () => {
      const res = await apiClient!.get(`/admin/vendors/${selectedVendorId}/users`);
      return (res.data as VendorUser[]) || [];
    },
    enabled: Boolean(apiClient) && Boolean(selectedVendorId),
  });

  const vendorUsers = vendorUsersQuery.data ?? [];
  const loadingUsers = vendorUsersQuery.isLoading || vendorUsersQuery.isFetching;

  useEffect(() => {
    if (vendorUsersQuery.data && selectedVendorId) {
      setOnboardStatus("Vendor users loaded.");
    }
    if (vendorUsersQuery.error && selectedVendorId) {
      setOnboardStatus(getApiErrorMessage(vendorUsersQuery.error, "Failed to load vendor users."));
    }
  }, [vendorUsersQuery.data, vendorUsersQuery.error, selectedVendorId]);

  const loadVendorUsers = async (vendorId: string) => {
    if (!apiClient || !vendorId) return;
    await queryClient.invalidateQueries({ queryKey: ["admin-vendor-users", vendorId] });
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
    setLastVendorSelectedAt(Date.now());
    // Users will be loaded automatically by the query since selectedVendorId changed
  };

  const handleSelectVendorId = (vendorId: string) => {
    setSelectedVendorId(vendorId);
    const vendor = vendors.find((v) => v.id === vendorId);
    if (!vendor) {
      setOnboardStatus(DEFAULT_ONBOARD_STATUS);
      setLastOnboardResult(null);
      return;
    }
    fillOnboardFromVendor(vendor);
    setLastVendorSelectedAt(Date.now());
    // Users will be loaded automatically by the query since selectedVendorId changed
  };

  const clearSelectedVendorContext = () => {
    setSelectedVendorId("");
    setOnboardStatus(DEFAULT_ONBOARD_STATUS);
    setLastOnboardResult(null);
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
      await queryClient.invalidateQueries({ queryKey: ["admin-vendor-users", selectedVendorId] });
      setLastOnboardResult(data ?? null);
      const emailStatus = data?.keycloakUserCreated
        ? data.keycloakActionEmailSent
          ? " Keycloak action email sent."
          : " Keycloak user created."
        : " Existing Keycloak user linked.";
      setOnboardStatus(`Vendor admin onboarded.${emailStatus}`);
      setLastOnboardedAt(Date.now());
      setOnboardForm((old) => ({
        ...emptyOnboardForm,
        vendorUserRole: old.vendorUserRole,
        createIfMissing: old.createIfMissing,
      }));
      toast.success(`Vendor admin onboarded.${emailStatus}`);
    } catch (err) {
      const message = getApiErrorMessage(err, "Failed to onboard vendor admin.");
      setOnboardStatus(message);
      setLastOnboardResult(null);
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
        await queryClient.invalidateQueries({ queryKey: ["admin-vendor-users", vendorId] });
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
    lastVendorSelectedAt,
    lastOnboardedAt,
    lastOnboardResult,
    selectedVendor,

    setOnboardForm,
    setOnboardStatus,
    setVendorUsers: (_users: VendorUser[]) => {
      // Compatibility stub - data managed by React Query
    },

    loadVendorUsers,
    fillOnboardFromVendor,
    handleSelectVendor,
    handleSelectVendorId,
    clearSelectedVendorContext,
    onboardVendorAdmin,
    removeVendorUser,
  };
}
