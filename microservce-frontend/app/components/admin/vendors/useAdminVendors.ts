"use client";

import { useEffect, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { Vendor, VendorDeletionEligibility, VendorLifecycleAudit, VendorUser } from "./types";
import { getApiErrorMessage } from "./vendorUtils";
import { useVendorForm } from "./useVendorForm";
import { useVendorList } from "./useVendorList";
import { useVendorOnboarding } from "./useVendorOnboarding";

export type VendorConfirmState =
  | { kind: "stopOrders"; vendor: Vendor }
  | { kind: "resumeOrders"; vendor: Vendor }
  | { kind: "requestDeleteVendor"; vendor: Vendor }
  | { kind: "confirmDeleteVendor"; vendor: Vendor }
  | { kind: "restoreVendor"; vendor: Vendor }
  | { kind: "removeVendorUser"; vendorId: string; user: VendorUser }
  | { kind: "verifyVendor"; vendor: Vendor }
  | { kind: "rejectVerification"; vendor: Vendor }
  | null;

export function useAdminVendors(apiClient: AxiosInstance | null) {
  const queryClient = useQueryClient();
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
  const [confirmReason, setConfirmReason] = useState("");
  const [vendorDeletionEligibilityById, setVendorDeletionEligibilityById] = useState<Record<string, VendorDeletionEligibility>>({});
  const [orderToggleVendorId, setOrderToggleVendorId] = useState<string | null>(null);
  const [verifyingVendorId, setVerifyingVendorId] = useState<string | null>(null);
  const [rejectingVerificationId, setRejectingVerificationId] = useState<string | null>(null);
  const [eligibilityLoadingVendorId, setEligibilityLoadingVendorId] = useState<string | null>(null);

  useEffect(() => {
    if (!confirmState) {
      setConfirmReason("");
    }
  }, [confirmState]);

  // ---- React Query: Lifecycle audit for selected vendor ----
  const selectedVendorId = vendorList.selectedVendorId;

  const lifecycleAuditQuery = useQuery<VendorLifecycleAudit[]>({
    queryKey: ["admin-vendor-lifecycle-audit", selectedVendorId],
    queryFn: async () => {
      const res = await apiClient!.get(`/admin/vendors/${selectedVendorId}/lifecycle-audit`);
      return ((res.data as VendorLifecycleAudit[]) || []).slice();
    },
    enabled: Boolean(apiClient) && Boolean(selectedVendorId) && !vendorList.showDeleted,
  });

  const lifecycleAuditLoadingVendorId = lifecycleAuditQuery.isFetching ? selectedVendorId : null;

  // Imperative eligibility loading (needed for confirm flow checks)
  const loadVendorDeletionEligibility = async (vendorId: string) => {
    if (!apiClient || !vendorId) return null;
    setEligibilityLoadingVendorId(vendorId);
    try {
      const res = await apiClient.get(`/admin/vendors/${vendorId}/deletion-eligibility`);
      const data = res.data as VendorDeletionEligibility;
      setVendorDeletionEligibilityById((prev) => ({ ...prev, [vendorId]: data }));
      return data;
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to load vendor deletion eligibility."));
      return null;
    } finally {
      setEligibilityLoadingVendorId((current) => (current === vendorId ? null : current));
    }
  };

  const loadVendorLifecycleAudit = async (vendorId: string) => {
    if (!apiClient || !vendorId) return [] as VendorLifecycleAudit[];
    await queryClient.invalidateQueries({ queryKey: ["admin-vendor-lifecycle-audit", vendorId] });
    return lifecycleAuditQuery.data ?? [];
  };

  // Background eligibility fetch for visible vendors
  useEffect(() => {
    if (!apiClient || vendorList.showDeleted || vendorList.vendors.length === 0) return;
    const missing = vendorList.vendors
      .map((v) => v.id)
      .filter((id) => !vendorDeletionEligibilityById[id]);
    if (missing.length === 0) return;
    let cancelled = false;
    void (async () => {
      for (const id of missing.slice(0, 8)) {
        if (cancelled) return;
        try {
          const res = await apiClient.get(`/admin/vendors/${id}/deletion-eligibility`);
          const data = res.data as VendorDeletionEligibility;
          if (!cancelled) {
            setVendorDeletionEligibilityById((prev) => ({ ...prev, [id]: data }));
          }
        } catch {
          // Row-level eligibility fetch is best-effort; backend still enforces on delete.
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [apiClient, vendorList.showDeleted, vendorList.vendors, vendorDeletionEligibilityById]);

  // Load eligibility for selected vendor if not already loaded
  useEffect(() => {
    if (!apiClient || !vendorList.selectedVendorId || vendorList.showDeleted) return;
    if (vendorDeletionEligibilityById[vendorList.selectedVendorId]) return;
    void loadVendorDeletionEligibility(vendorList.selectedVendorId);
  }, [apiClient, vendorList.selectedVendorId, vendorList.showDeleted, vendorDeletionEligibilityById]); // eslint-disable-line react-hooks/exhaustive-deps

  const openDeleteVendorConfirm = async (vendor: Vendor) => {
    const existing = vendorDeletionEligibilityById[vendor.id];
    const eligibility = existing ?? (await loadVendorDeletionEligibility(vendor.id));
    if (eligibility && !eligibility.eligible) {
      const reasons = (eligibility.blockingReasons || []).join(", ") || "Delete is blocked";
      vendorList.setStatus(`Vendor deletion blocked: ${reasons}`);
      toast.error(`Cannot delete vendor. ${reasons}`);
      return;
    }
    setConfirmState({ kind: "requestDeleteVendor", vendor });
  };
  const openConfirmDeleteVendorConfirm = async (vendor: Vendor) => {
    const existing = vendorDeletionEligibilityById[vendor.id];
    const eligibility = existing ?? (await loadVendorDeletionEligibility(vendor.id));
    if (eligibility && !eligibility.eligible) {
      const reasons = (eligibility.blockingReasons || []).join(", ") || "Delete is blocked";
      vendorList.setStatus(`Vendor deletion blocked: ${reasons}`);
      toast.error(`Cannot confirm delete. ${reasons}`);
      return;
    }
    setConfirmState({ kind: "confirmDeleteVendor", vendor });
  };
  const openRestoreVendorConfirm = (vendor: Vendor) => setConfirmState({ kind: "restoreVendor", vendor });
  const openStopOrdersConfirm = (vendor: Vendor) => setConfirmState({ kind: "stopOrders", vendor });
  const openResumeOrdersConfirm = (vendor: Vendor) => setConfirmState({ kind: "resumeOrders", vendor });
  const openRemoveVendorUserConfirm = (vendorId: string, user: VendorUser) =>
    setConfirmState({ kind: "removeVendorUser", vendorId, user });

  const executeStopVendorOrders = async (vendor: Vendor, reason?: string | null) => {
    if (!apiClient || orderToggleVendorId) return;
    setOrderToggleVendorId(vendor.id);
    try {
      await apiClient.post(`/admin/vendors/${vendor.id}/stop-orders`, reason ? { reason } : {});
      await vendorList.loadVendors();
      if (vendorList.deletedLoaded || vendorList.showDeleted) {
        await vendorList.loadDeletedVendors();
      }
      await loadVendorDeletionEligibility(vendor.id);
      await queryClient.invalidateQueries({ queryKey: ["admin-vendor-lifecycle-audit", vendor.id] });
      vendorList.setStatus(`Stopped new orders for ${vendor.name}.`);
      toast.success("Vendor stopped receiving orders");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to stop vendor orders."));
    } finally {
      setOrderToggleVendorId(null);
    }
  };

  const executeResumeVendorOrders = async (vendor: Vendor, reason?: string | null) => {
    if (!apiClient || orderToggleVendorId) return;
    setOrderToggleVendorId(vendor.id);
    try {
      await apiClient.post(`/admin/vendors/${vendor.id}/resume-orders`, reason ? { reason } : {});
      await vendorList.loadVendors();
      if (vendorList.deletedLoaded || vendorList.showDeleted) {
        await vendorList.loadDeletedVendors();
      }
      await loadVendorDeletionEligibility(vendor.id);
      await queryClient.invalidateQueries({ queryKey: ["admin-vendor-lifecycle-audit", vendor.id] });
      vendorList.setStatus(`Resumed orders for ${vendor.name}.`);
      toast.success("Vendor resumed receiving orders");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to resume vendor orders."));
    } finally {
      setOrderToggleVendorId(null);
    }
  };

  const stopVendorOrders = async (vendor: Vendor) => {
    openStopOrdersConfirm(vendor);
  };

  const resumeVendorOrders = async (vendor: Vendor) => {
    openResumeOrdersConfirm(vendor);
  };

  const openVerifyVendorConfirm = (vendor: Vendor) => setConfirmState({ kind: "verifyVendor", vendor });
  const openRejectVerificationConfirm = (vendor: Vendor) => setConfirmState({ kind: "rejectVerification", vendor });

  const executeVerifyVendor = async (vendor: Vendor) => {
    if (!apiClient || verifyingVendorId) return;
    setVerifyingVendorId(vendor.id);
    try {
      await apiClient.post(`/admin/vendors/${vendor.id}/verify`);
      await vendorList.loadVendors();
      if (vendorList.deletedLoaded || vendorList.showDeleted) {
        await vendorList.loadDeletedVendors();
      }
      vendorList.setStatus(`Vendor "${vendor.name}" verified.`);
      toast.success("Vendor verified successfully");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to verify vendor."));
    } finally {
      setVerifyingVendorId(null);
    }
  };

  const executeRejectVerification = async (vendor: Vendor, reason: string) => {
    if (!apiClient || rejectingVerificationId) return;
    setRejectingVerificationId(vendor.id);
    try {
      await apiClient.post(`/admin/vendors/${vendor.id}/reject-verification`, { reason });
      await vendorList.loadVendors();
      if (vendorList.deletedLoaded || vendorList.showDeleted) {
        await vendorList.loadDeletedVendors();
      }
      vendorList.setStatus(`Vendor "${vendor.name}" verification rejected.`);
      toast.success("Vendor verification rejected");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to reject vendor verification."));
    } finally {
      setRejectingVerificationId(null);
    }
  };

  const handleConfirmAction = async () => {
    if (!apiClient || !confirmState || confirmLoading) return;
    const reason = confirmReason.trim() || null;
    setConfirmLoading(true);
    try {
      if (confirmState.kind === "stopOrders") {
        await executeStopVendorOrders(confirmState.vendor, reason);
      } else if (confirmState.kind === "resumeOrders") {
        await executeResumeVendorOrders(confirmState.vendor, reason);
      } else if (confirmState.kind === "requestDeleteVendor") {
        const vendor = confirmState.vendor;
        const eligibility = await loadVendorDeletionEligibility(vendor.id);
        if (eligibility && !eligibility.eligible) {
          throw new Error(`Vendor deletion blocked: ${(eligibility.blockingReasons || []).join(", ")}`);
        }
        await apiClient.post(`/admin/vendors/${vendor.id}/delete-request`, reason ? { reason } : {});
        await vendorList.loadVendors();
        if (vendorList.deletedLoaded || vendorList.showDeleted) {
          await vendorList.loadDeletedVendors();
        }
        await loadVendorDeletionEligibility(vendor.id);
        await queryClient.invalidateQueries({ queryKey: ["admin-vendor-lifecycle-audit", vendor.id] });
        vendorList.setStatus("Vendor delete request created.");
        toast.success("Delete request created");
      } else if (confirmState.kind === "confirmDeleteVendor") {
        const vendor = confirmState.vendor;
        const eligibility = await loadVendorDeletionEligibility(vendor.id);
        if (eligibility && !eligibility.eligible) {
          throw new Error(`Vendor deletion blocked: ${(eligibility.blockingReasons || []).join(", ")}`);
        }
        await apiClient.post(`/admin/vendors/${vendor.id}/confirm-delete`, reason ? { reason } : {});

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
        setVendorDeletionEligibilityById((prev) => {
          const next = { ...prev };
          delete next[vendor.id];
          return next;
        });
        vendorList.setStatus("Vendor deleted.");
        toast.success("Vendor soft deleted");
      } else if (confirmState.kind === "restoreVendor") {
        const vendor = confirmState.vendor;
        await apiClient.post(`/admin/vendors/${vendor.id}/restore`, reason ? { reason } : {});
        await vendorList.loadVendors();
        if (vendorList.deletedLoaded || vendorList.showDeleted) {
          await vendorList.loadDeletedVendors();
        }
        await loadVendorDeletionEligibility(vendor.id);
        await queryClient.invalidateQueries({ queryKey: ["admin-vendor-lifecycle-audit", vendor.id] });
        vendorList.setStatus("Vendor restored.");
        toast.success("Vendor restored");
      } else if (confirmState.kind === "removeVendorUser") {
        const { vendorId, user } = confirmState;
        await vendorOnboarding.removeVendorUser(vendorId, user);
        toast.success("Vendor user removed");
      } else if (confirmState.kind === "verifyVendor") {
        await executeVerifyVendor(confirmState.vendor);
      } else if (confirmState.kind === "rejectVerification") {
        if (!reason) {
          toast.error("A reason is required to reject verification.");
          setConfirmLoading(false);
          return;
        }
        await executeRejectVerification(confirmState.vendor, reason);
      }
      setConfirmState(null);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Action failed."));
    } finally {
      setConfirmLoading(false);
    }
  };

  const confirmUi = (() => {
    if (!confirmState) {
      return { title: "", message: "", confirmLabel: "Confirm", danger: false, reasonEnabled: false };
    }

    if (confirmState.kind === "stopOrders") {
      return {
        title: "Stop Vendor Orders",
        message: `Stop receiving new orders for "${confirmState.vendor.name}" and hide its products from the storefront?`,
        confirmLabel: "Stop Orders",
        danger: true,
        reasonEnabled: true,
      };
    }

    if (confirmState.kind === "resumeOrders") {
      return {
        title: "Resume Vendor Orders",
        message: `Resume receiving new orders for "${confirmState.vendor.name}" (requires ACTIVE status and active vendor)?`,
        confirmLabel: "Resume Orders",
        danger: false,
        reasonEnabled: true,
      };
    }

    if (confirmState.kind === "requestDeleteVendor") {
      const eligibility = vendorDeletionEligibilityById[confirmState.vendor.id];
      const base = `Create delete request for vendor "${confirmState.vendor.name}"? This is step 1 before final delete.`;
      const message = !eligibility
        ? base
        : eligibility.eligible
          ? `${base} Eligibility check passed. You can confirm delete in step 2.`
          : `${base} Currently blocked: ${(eligibility.blockingReasons || []).join(", ") || "Blocked"}`;
      return {
        title: "Request Vendor Delete",
        message,
        confirmLabel: "Request Delete",
        danger: true,
        reasonEnabled: true,
      };
    }

    if (confirmState.kind === "confirmDeleteVendor") {
      const eligibility = vendorDeletionEligibilityById[confirmState.vendor.id];
      const base = `Confirm delete vendor "${confirmState.vendor.name}"? This is irreversible in active lists (soft delete).`;
      const message = !eligibility
        ? base
        : eligibility.eligible
          ? `${base} Deletion eligibility check passed.`
          : `${base} Currently blocked: ${(eligibility.blockingReasons || []).join(", ") || "Blocked"}`;
      return {
        title: "Confirm Vendor Delete",
        message,
        confirmLabel: "Confirm Delete",
        danger: true,
        reasonEnabled: true,
      };
    }

    if (confirmState.kind === "restoreVendor") {
      return {
        title: "Restore Vendor",
        message: `Restore vendor "${confirmState.vendor.name}" to active admin management lists?`,
        confirmLabel: "Restore Vendor",
        danger: false,
        reasonEnabled: true,
      };
    }

    if (confirmState.kind === "verifyVendor") {
      return {
        title: "Verify Vendor",
        message: `Verify vendor "${confirmState.vendor.name}"? This will mark the vendor as trusted and verified.`,
        confirmLabel: "Verify",
        danger: false,
        reasonEnabled: false,
      };
    }

    if (confirmState.kind === "rejectVerification") {
      return {
        title: "Reject Vendor Verification",
        message: `Reject verification for vendor "${confirmState.vendor.name}"? A reason is required.`,
        confirmLabel: "Reject Verification",
        danger: true,
        reasonEnabled: true,
      };
    }

    return {
      title: "Remove Vendor User",
      message: `Remove ${confirmState.user.displayName || confirmState.user.email} from this vendor?`,
      confirmLabel: "Remove User",
      danger: true,
      reasonEnabled: false,
    };
  })();

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
    selectedVendorDeletionEligibility: vendorOnboarding.selectedVendor
      ? (vendorDeletionEligibilityById[vendorOnboarding.selectedVendor.id] ?? null)
      : null,
    selectedVendorLifecycleAudit: vendorOnboarding.selectedVendor
      ? (lifecycleAuditQuery.data ?? [])
      : [],
    vendorDeletionEligibilityById,
    eligibilityLoadingVendorId,
    lifecycleAuditLoadingVendorId,
    orderToggleVendorId,
    verifyingVendorId,
    rejectingVerificationId,

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
    confirmReason,
    confirmUi,

    setShowDeleted: vendorList.setShowDeleted,
    setForm: vendorForm.setForm,
    setSlugEdited: vendorForm.setSlugEdited,
    setOnboardForm: vendorOnboarding.setOnboardForm,
    setConfirmState,
    setConfirmReason,

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
    openConfirmDeleteVendorConfirm,
    openRestoreVendorConfirm,
    openRemoveVendorUserConfirm,
    loadVendorDeletionEligibility,
    loadVendorLifecycleAudit,
    stopVendorOrders,
    resumeVendorOrders,
    openVerifyVendorConfirm,
    openRejectVerificationConfirm,
    handleConfirmAction,
  };
}
