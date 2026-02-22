"use client";

import { useEffect, useMemo, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import type { Vendor } from "./types";
import { getApiErrorMessage } from "./vendorUtils";

export function useVendorList(apiClient: AxiosInstance | null) {
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [deletedVendors, setDeletedVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingDeleted, setLoadingDeleted] = useState(false);
  const [deletedLoaded, setDeletedLoaded] = useState(false);
  const [showDeleted, setShowDeleted] = useState(false);
  const [status, setStatus] = useState("Loading vendors...");
  const [selectedVendorId, setSelectedVendorId] = useState("");

  const selectedVendor = useMemo(
    () => vendors.find((vendor) => vendor.id === selectedVendorId) || null,
    [vendors, selectedVendorId]
  );

  const loadVendors = async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const res = await apiClient.get("/admin/vendors");
      const rows = (((res.data as Vendor[]) || []).filter((v) => !v.deleted)).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
      setVendors(rows);
      setStatus("Vendors loaded.");
    } catch (err) {
      setStatus(getApiErrorMessage(err, "Failed to load vendors."));
    } finally {
      setLoading(false);
    }
  };

  const loadDeletedVendors = async () => {
    if (!apiClient) return;
    setLoadingDeleted(true);
    try {
      const res = await apiClient.get("/admin/vendors/deleted");
      const rows = (((res.data as Vendor[]) || []).filter((v) => v.deleted)).sort((a, b) =>
        a.name.localeCompare(b.name)
      );
      setDeletedVendors(rows);
      setDeletedLoaded(true);
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Failed to load deleted vendors."));
    } finally {
      setLoadingDeleted(false);
    }
  };

  useEffect(() => {
    if (!showDeleted || deletedLoaded) return;
    void loadDeletedVendors();
  }, [showDeleted, deletedLoaded]); // eslint-disable-line react-hooks/exhaustive-deps

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

