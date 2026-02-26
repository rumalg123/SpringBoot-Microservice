"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import VendorPageShell from "../../components/ui/VendorPageShell";
import VendorProfileTab from "../../components/vendor/settings/VendorProfileTab";
import VendorPayoutTab from "../../components/vendor/settings/VendorPayoutTab";
import VendorActionsTab from "../../components/vendor/settings/VendorActionsTab";
import {
  VendorProfile,
  PayoutConfig,
  Tab,
  EMPTY_VENDOR,
  EMPTY_PAYOUT,
} from "../../components/vendor/settings/types";

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function VendorSettingsPage() {
  const session = useAuthSession();

  /* tabs */
  const [activeTab, setActiveTab] = useState<Tab>("profile");

  /* vendor profile (local form state) */
  const [vendor, setVendor] = useState<VendorProfile>(EMPTY_VENDOR);

  /* payout config (local form state) */
  const [payoutConfig, setPayoutConfig] = useState<PayoutConfig>(EMPTY_PAYOUT);

  /* actions */
  const [verificationDocUrl, setVerificationDocUrl] = useState("");
  const [verificationNotes, setVerificationNotes] = useState("");

  const ready = session.status === "ready" && !!session.apiClient && (session.isVendorAdmin || session.isVendorStaff);

  /* ---------------------------------------------------------------- */
  /*  Queries                                                          */
  /* ---------------------------------------------------------------- */

  const { isLoading: loadingProfile } = useQuery({
    queryKey: ["vendor-profile"],
    queryFn: async () => {
      const res = await session.apiClient!.get("/vendors/me");
      const d = res.data as Record<string, unknown>;
      const profile: VendorProfile = {
        id: (d.id as string) || undefined,
        name: (d.name as string) || "",
        slug: (d.slug as string) || undefined,
        contactEmail: (d.contactEmail as string) || "",
        supportEmail: (d.supportEmail as string) || "",
        contactPhone: (d.contactPhone as string) || "",
        contactPersonName: (d.contactPersonName as string) || "",
        logoImage: (d.logoImage as string) || "",
        bannerImage: (d.bannerImage as string) || "",
        websiteUrl: (d.websiteUrl as string) || "",
        description: (d.description as string) || "",
        returnPolicy: (d.returnPolicy as string) || "",
        shippingPolicy: (d.shippingPolicy as string) || "",
        processingTimeDays: typeof d.processingTimeDays === "number" ? d.processingTimeDays : "",
        acceptsReturns: Boolean(d.acceptsReturns),
        returnWindowDays: typeof d.returnWindowDays === "number" ? d.returnWindowDays : "",
        freeShippingThreshold: typeof d.freeShippingThreshold === "number" ? d.freeShippingThreshold : "",
        primaryCategory: (d.primaryCategory as string) || "",
        specializations: Array.isArray(d.specializations)
          ? (d.specializations as string[]).join(", ")
          : (d.specializations as string) || "",
        verificationStatus: (d.verificationStatus as string) || "",
        acceptingOrders: d.acceptingOrders !== false,
      };
      setVendor(profile);
      return profile;
    },
    enabled: ready,
  });

  const { isLoading: loadingPayout } = useQuery({
    queryKey: ["vendor-payout-config"],
    queryFn: async () => {
      try {
        const res = await session.apiClient!.get("/vendors/me/payout-config");
        const d = res.data as Record<string, unknown>;
        const config: PayoutConfig = {
          payoutCurrency: (d.payoutCurrency as string) || "USD",
          payoutSchedule: (d.payoutSchedule as string) || "MONTHLY",
          payoutMinimum: typeof d.payoutMinimum === "number" ? d.payoutMinimum : "",
          bankAccountHolder: (d.bankAccountHolder as string) || "",
          bankName: (d.bankName as string) || "",
          bankRoutingCode: (d.bankRoutingCode as string) || "",
          bankAccountNumberMasked: (d.bankAccountNumberMasked as string) || "",
          taxId: (d.taxId as string) || "",
        };
        setPayoutConfig(config);
        return config;
      } catch (err) {
        const msg = err instanceof Error ? err.message : "";
        if (!msg.startsWith("404")) {
          throw err;
        }
        // 404 = not configured yet, keep defaults
        return EMPTY_PAYOUT;
      }
    },
    enabled: ready,
  });

  /* ---------------------------------------------------------------- */
  /*  Mutations                                                        */
  /* ---------------------------------------------------------------- */

  const saveProfileMutation = useMutation({
    mutationFn: async () => {
      const specializations = vendor.specializations
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      await session.apiClient!.put("/vendors/me", {
        name: vendor.name,
        slug: vendor.slug,
        contactEmail: vendor.contactEmail,
        supportEmail: vendor.supportEmail,
        contactPhone: vendor.contactPhone,
        contactPersonName: vendor.contactPersonName,
        logoImage: vendor.logoImage,
        bannerImage: vendor.bannerImage,
        websiteUrl: vendor.websiteUrl,
        description: vendor.description,
        returnPolicy: vendor.returnPolicy,
        shippingPolicy: vendor.shippingPolicy,
        processingTimeDays: vendor.processingTimeDays === "" ? null : Number(vendor.processingTimeDays),
        acceptsReturns: vendor.acceptsReturns,
        returnWindowDays: vendor.returnWindowDays === "" ? null : Number(vendor.returnWindowDays),
        freeShippingThreshold: vendor.freeShippingThreshold === "" ? null : Number(vendor.freeShippingThreshold),
        primaryCategory: vendor.primaryCategory,
        specializations,
      });
    },
    onSuccess: () => {
      toast.success("Profile saved successfully");
    },
    onError: (err) => {
      toast.error("Failed to save profile");
      console.error(err);
    },
  });

  const savePayoutMutation = useMutation({
    mutationFn: async () => {
      await session.apiClient!.put("/vendors/me/payout-config", {
        payoutCurrency: payoutConfig.payoutCurrency,
        payoutSchedule: payoutConfig.payoutSchedule,
        payoutMinimum: payoutConfig.payoutMinimum === "" ? null : Number(payoutConfig.payoutMinimum),
        bankAccountHolder: payoutConfig.bankAccountHolder,
        bankName: payoutConfig.bankName,
        bankRoutingCode: payoutConfig.bankRoutingCode,
        bankAccountNumberMasked: payoutConfig.bankAccountNumberMasked,
        taxId: payoutConfig.taxId,
      });
    },
    onSuccess: () => {
      toast.success("Payout config saved successfully");
    },
    onError: (err) => {
      toast.error("Failed to save payout config");
      console.error(err);
    },
  });

  const requestVerificationMutation = useMutation({
    mutationFn: async () => {
      const body: Record<string, string> = {};
      if (verificationDocUrl.trim()) body.verificationDocumentUrl = verificationDocUrl.trim();
      if (verificationNotes.trim()) body.notes = verificationNotes.trim();
      await session.apiClient!.post("/vendors/me/request-verification", body);
    },
    onSuccess: () => {
      toast.success("Verification request submitted");
      setVendor((prev) => ({ ...prev, verificationStatus: "PENDING_VERIFICATION" }));
      setVerificationDocUrl("");
      setVerificationNotes("");
    },
    onError: (err) => {
      toast.error("Failed to request verification");
      console.error(err);
    },
  });

  const toggleOrdersMutation = useMutation({
    mutationFn: async (action: "stop" | "resume") => {
      if (action === "stop") {
        await session.apiClient!.post("/vendors/me/stop-orders");
      } else {
        await session.apiClient!.post("/vendors/me/resume-orders");
      }
      return action;
    },
    onSuccess: (action) => {
      if (action === "stop") {
        setVendor((prev) => ({ ...prev, acceptingOrders: false }));
        toast.success("Orders paused");
      } else {
        setVendor((prev) => ({ ...prev, acceptingOrders: true }));
        toast.success("Orders resumed");
      }
    },
    onError: (err, action) => {
      toast.error(`Failed to ${action} orders`);
      console.error(err);
    },
  });

  const savingProfile = saveProfileMutation.isPending;
  const savingPayout = savePayoutMutation.isPending;
  const requestingVerification = requestVerificationMutation.isPending;
  const togglingOrders = toggleOrdersMutation.isPending;

  /* ---------------------------------------------------------------- */
  /*  Field helpers                                                    */
  /* ---------------------------------------------------------------- */

  const profileField = (key: keyof VendorProfile, value: string | number | boolean | "") => {
    setVendor((prev) => ({ ...prev, [key]: value }));
  };

  const payoutField = (key: keyof PayoutConfig, value: string | number | "") => {
    setPayoutConfig((prev) => ({ ...prev, [key]: value }));
  };

  /* ---------------------------------------------------------------- */
  /*  Auth guard                                                       */
  /* ---------------------------------------------------------------- */

  if (session.status !== "ready") {
    return (
      <VendorPageShell
        title="Vendor Settings"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
      >
        <div className="text-center p-12 text-muted">Loading...</div>
      </VendorPageShell>
    );
  }

  if (!session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <VendorPageShell
        title="Vendor Settings"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
      >
        <div className="text-center p-12 text-muted text-[1.1rem]">
          Unauthorized
        </div>
      </VendorPageShell>
    );
  }

  /* ---------------------------------------------------------------- */
  /*  Render                                                           */
  /* ---------------------------------------------------------------- */

  const tabs: { key: Tab; label: string }[] = [
    { key: "profile", label: "Profile" },
    { key: "payout", label: "Payout" },
    { key: "actions", label: "Actions" },
  ];

  return (
    <VendorPageShell
      title="Vendor Settings"
      breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
    >
      {/* Tab navigation */}
      <div className="flex border-b border-line mb-7">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setActiveTab(t.key)}
            className="px-5 py-2.5 bg-transparent border-none cursor-pointer text-base font-semibold transition-colors duration-200"
            style={{
              color: activeTab === t.key ? "var(--brand)" : "var(--muted)",
              borderBottom: activeTab === t.key ? "2px solid var(--brand)" : "2px solid transparent",
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === "profile" && (
        <VendorProfileTab
          vendor={vendor}
          loadingProfile={loadingProfile}
          savingProfile={savingProfile}
          onFieldChange={profileField}
          onSave={() => saveProfileMutation.mutate()}
        />
      )}
      {activeTab === "payout" && (
        <VendorPayoutTab
          payoutConfig={payoutConfig}
          loadingPayout={loadingPayout}
          savingPayout={savingPayout}
          onFieldChange={payoutField}
          onSave={() => savePayoutMutation.mutate()}
        />
      )}
      {activeTab === "actions" && (
        <VendorActionsTab
          vendor={vendor}
          verificationDocUrl={verificationDocUrl}
          onVerificationDocUrlChange={setVerificationDocUrl}
          verificationNotes={verificationNotes}
          onVerificationNotesChange={setVerificationNotes}
          requestingVerification={requestingVerification}
          onRequestVerification={() => requestVerificationMutation.mutate()}
          togglingOrders={togglingOrders}
          onToggleOrders={(action) => toggleOrdersMutation.mutate(action)}
        />
      )}
    </VendorPageShell>
  );
}
