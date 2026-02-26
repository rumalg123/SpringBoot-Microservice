"use client";

import { useEffect, useMemo, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { Vendor } from "./types";
import { getApiErrorMessage } from "./vendorUtils";

export function useVendorList(apiClient: AxiosInstance | null) {
  const queryClient = useQueryClient();
  const [showDeleted, setShowDeleted] = useState(false);
  const [status, setStatus] = useState("Loading vendors...");
  const [selectedVendorId, setSelectedVendorId] = useState("");

  const canFetch = Boolean(apiClient);

  const vendorsQuery = useQuery<Vendor[]>({
    queryKey: ["admin-vendors"],
    queryFn: async () => {
      const res = await apiClient!.get("/admin/vendors");
      return (((res.data as Vendor[]) || []).filter((v) => !v.deleted)).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
    },
    enabled: canFetch,
  });

  const vendors = vendorsQuery.data ?? [];
  const loading = vendorsQuery.isLoading || vendorsQuery.isFetching;

  useEffect(() => {
    if (vendorsQuery.data) {
      setStatus("Vendors loaded.");
    }
    if (vendorsQuery.error) {
      setStatus(getApiErrorMessage(vendorsQuery.error, "Failed to load vendors."));
    }
  }, [vendorsQuery.data, vendorsQuery.error]);

  const deletedVendorsQuery = useQuery<Vendor[]>({
    queryKey: ["admin-vendors-deleted"],
    queryFn: async () => {
      const res = await apiClient!.get("/admin/vendors/deleted");
      return (((res.data as Vendor[]) || []).filter((v) => v.deleted)).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
    },
    enabled: canFetch && showDeleted,
  });

  const deletedVendors = deletedVendorsQuery.data ?? [];
  const loadingDeleted = deletedVendorsQuery.isLoading || deletedVendorsQuery.isFetching;
  const deletedLoaded = deletedVendorsQuery.isSuccess;

  useEffect(() => {
    if (deletedVendorsQuery.error) {
      toast.error(getApiErrorMessage(deletedVendorsQuery.error, "Failed to load deleted vendors."));
    }
  }, [deletedVendorsQuery.error]);

  const selectedVendor = useMemo(
    () => vendors.find((vendor) => vendor.id === selectedVendorId) || null,
    [vendors, selectedVendorId]
  );

  const loadVendors = async () => {
    await queryClient.invalidateQueries({ queryKey: ["admin-vendors"] });
  };

  const loadDeletedVendors = async () => {
    await queryClient.invalidateQueries({ queryKey: ["admin-vendors-deleted"] });
  };

  const refreshCurrentVendorList = () => {
    if (showDeleted) {
      void loadDeletedVendors();
      return;
    }
    void loadVendors();
  };

  return {
    vendors,
    deletedVendors,
    loading,
    loadingDeleted,
    deletedLoaded,
    showDeleted,
    status,
    selectedVendorId,
    selectedVendor,

    setShowDeleted,
    setStatus,
    setSelectedVendorId,

    loadVendors,
    loadDeletedVendors,
    refreshCurrentVendorList,
  };
}
