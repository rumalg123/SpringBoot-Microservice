"use client";

import { useEffect, useState } from "react";
import type { AxiosInstance } from "axios";
import toast from "react-hot-toast";
import { emptyVendorForm, type SlugStatus, type Vendor, type VendorForm } from "./types";
import { getApiErrorMessage, slugify } from "./vendorUtils";

type UseVendorFormArgs = {
  apiClient: AxiosInstance | null;
  deletedLoaded: boolean;
  setPageStatus: (status: string) => void;
  onRefreshVendors: () => Promise<void> | void;
  onRefreshDeletedVendors: () => Promise<void> | void;
  onSelectVendor: (vendor: Vendor) => void;
  onEditStart: () => void;
};

export function useVendorForm({
  apiClient,
  deletedLoaded,
  setPageStatus,
  onRefreshVendors,
  onRefreshDeletedVendors,
  onSelectVendor,
  onEditStart,
}: UseVendorFormArgs) {
  const [form, setForm] = useState<VendorForm>(emptyVendorForm);
  const [slugEdited, setSlugEdited] = useState(false);
  const [slugStatus, setSlugStatus] = useState<SlugStatus>("idle");
  const [savingVendor, setSavingVendor] = useState(false);
  const [lastVendorSavedAt, setLastVendorSavedAt] = useState<number>(0);

  useEffect(() => {
    if (slugEdited) return;
    setForm((prev) => ({ ...prev, slug: slugify(prev.name).slice(0, 180) }));
  }, [form.name, slugEdited]);

  useEffect(() => {
    if (!apiClient) return;
    const normalized = slugify(form.slug).slice(0, 180);
    if (!normalized) {
      setSlugStatus("invalid");
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setSlugStatus("checking");
      try {
        const params = new URLSearchParams({ slug: normalized });
        if (form.id) params.set("excludeId", form.id);
        const res = await apiClient.get(`/vendors/slug-available?${params.toString()}`);
        if (cancelled) return;
        setSlugStatus(Boolean((res.data as { available?: boolean })?.available) ? "available" : "taken");
      } catch {
        if (!cancelled) setSlugStatus("idle");
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [form.slug, form.id, apiClient]);

  const resetVendorForm = () => {
    setForm(emptyVendorForm);
    setSlugEdited(false);
    setSlugStatus("idle");
  };

  const handleEditVendor = (vendor: Vendor) => {
    onEditStart();
    setForm({
      id: vendor.id,
      name: vendor.name,
      slug: vendor.slug || "",
      contactEmail: vendor.contactEmail || "",
      contactPersonName: vendor.contactPersonName || "",
      status: vendor.status,
      active: vendor.active,
      acceptingOrders: vendor.acceptingOrders,
    });
    setSlugEdited(true);
    setSlugStatus("available");
    onSelectVendor(vendor);
    toast.success("Vendor loaded into form");
  };

  const saveVendor = async () => {
    if (!apiClient || savingVendor) return;
    const normalizedSlug = slugify(form.slug).slice(0, 180);
    if (!normalizedSlug) {
      toast.error("Vendor slug is required");
      return;
    }
    if (slugStatus === "checking") {
      toast.error("Wait until slug check completes");
      return;
    }
    if (slugStatus === "taken" || slugStatus === "invalid") {
      toast.error("Use a unique valid vendor slug");
      return;
    }

    setSavingVendor(true);
    try {
      const payload = {
        name: form.name.trim(),
        slug: normalizedSlug,
        contactEmail: form.contactEmail.trim(),
        supportEmail: null,
        contactPhone: null,
        contactPersonName: form.contactPersonName.trim() || null,
        logoImage: null,
        bannerImage: null,
        websiteUrl: null,
        description: null,
        status: form.status,
        active: form.active,
        acceptingOrders: form.acceptingOrders,
      };
      const isUpdate = Boolean(form.id);
      const res = isUpdate
        ? await apiClient.put(`/admin/vendors/${form.id}`, payload)
        : await apiClient.post("/admin/vendors", payload);
      const saved = res.data as Vendor;

      await onRefreshVendors();
      if (deletedLoaded) {
        await onRefreshDeletedVendors();
      }

      onSelectVendor(saved);
      resetVendorForm();
      setLastVendorSavedAt(Date.now());
      setPageStatus(isUpdate ? "Vendor updated." : "Vendor created.");
      toast.success(isUpdate ? "Vendor updated" : "Vendor created");
    } catch (err) {
      const message = getApiErrorMessage(err, "Failed to save vendor.");
      setPageStatus(message);
      toast.error(message);
    } finally {
      setSavingVendor(false);
    }
  };

  return {
    form,
    slugStatus,
    savingVendor,
    lastVendorSavedAt,

    setForm,
    setSlugEdited,

    resetVendorForm,
    handleEditVendor,
    saveVendor,
  };
}
