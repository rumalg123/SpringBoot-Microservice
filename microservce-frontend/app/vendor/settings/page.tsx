"use client";

import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import StatusBadge, { VENDOR_STATUS_COLORS } from "../../components/ui/StatusBadge";
import ConfirmModal from "../../components/ConfirmModal";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

type VendorProfile = {
  id?: string;
  name: string;
  slug?: string;
  contactEmail: string;
  supportEmail: string;
  contactPhone: string;
  contactPersonName: string;
  logoImage: string;
  bannerImage: string;
  websiteUrl: string;
  description: string;
  returnPolicy: string;
  shippingPolicy: string;
  processingTimeDays: number | "";
  acceptsReturns: boolean;
  returnWindowDays: number | "";
  freeShippingThreshold: number | "";
  primaryCategory: string;
  specializations: string;
  verificationStatus?: string;
  acceptingOrders?: boolean;
};

type PayoutConfig = {
  payoutCurrency: string;
  payoutSchedule: string;
  payoutMinimum: number | "";
  bankAccountHolder: string;
  bankName: string;
  bankRoutingCode: string;
  bankAccountNumberMasked: string;
  taxId: string;
};

const EMPTY_VENDOR: VendorProfile = {
  name: "",
  contactEmail: "",
  supportEmail: "",
  contactPhone: "",
  contactPersonName: "",
  logoImage: "",
  bannerImage: "",
  websiteUrl: "",
  description: "",
  returnPolicy: "",
  shippingPolicy: "",
  processingTimeDays: "",
  acceptsReturns: false,
  returnWindowDays: "",
  freeShippingThreshold: "",
  primaryCategory: "",
  specializations: "",
};

const EMPTY_PAYOUT: PayoutConfig = {
  payoutCurrency: "USD",
  payoutSchedule: "MONTHLY",
  payoutMinimum: "",
  bankAccountHolder: "",
  bankName: "",
  bankRoutingCode: "",
  bankAccountNumberMasked: "",
  taxId: "",
};

/* ------------------------------------------------------------------ */
/*  Style helpers                                                      */
/* ------------------------------------------------------------------ */

const tabBtnBase: React.CSSProperties = {
  padding: "10px 20px",
  background: "transparent",
  border: "none",
  cursor: "pointer",
  fontSize: "0.85rem",
  fontWeight: 600,
  transition: "color 0.2s",
};

const cardStyle: React.CSSProperties = {
  background: "rgba(255,255,255,0.03)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: 24,
  marginBottom: 24,
};

const labelStyle: React.CSSProperties = {
  display: "block",
  fontSize: "0.78rem",
  fontWeight: 600,
  color: "var(--muted)",
  marginBottom: 4,
};

const inputStyle: React.CSSProperties = {
  padding: "10px 14px",
  borderRadius: 8,
  border: "1px solid var(--line)",
  background: "var(--surface-2)",
  color: "var(--ink)",
  fontSize: "0.85rem",
  width: "100%",
  boxSizing: "border-box",
};

const textareaStyle: React.CSSProperties = {
  ...inputStyle,
  minHeight: 100,
  resize: "vertical" as const,
};

const fieldWrap: React.CSSProperties = {
  marginBottom: 16,
};

const saveBtnStyle: React.CSSProperties = {
  padding: "10px 24px",
  borderRadius: 10,
  fontWeight: 600,
};

/* ------------------------------------------------------------------ */
/*  Tabs                                                               */
/* ------------------------------------------------------------------ */

type Tab = "profile" | "payout" | "actions";

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function VendorSettingsPage() {
  const session = useAuthSession();

  /* tabs */
  const [activeTab, setActiveTab] = useState<Tab>("profile");

  /* vendor profile */
  const [vendor, setVendor] = useState<VendorProfile>(EMPTY_VENDOR);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);

  /* payout config */
  const [payoutConfig, setPayoutConfig] = useState<PayoutConfig>(EMPTY_PAYOUT);
  const [loadingPayout, setLoadingPayout] = useState(true);
  const [savingPayout, setSavingPayout] = useState(false);

  /* actions */
  const [verificationDocUrl, setVerificationDocUrl] = useState("");
  const [verificationNotes, setVerificationNotes] = useState("");
  const [requestingVerification, setRequestingVerification] = useState(false);
  const [togglingOrders, setTogglingOrders] = useState(false);

  /* confirm modal for order toggle */
  const [confirmModal, setConfirmModal] = useState<{
    open: boolean;
    action: "stop" | "resume";
  }>({ open: false, action: "stop" });

  /* ---------------------------------------------------------------- */
  /*  Load data                                                        */
  /* ---------------------------------------------------------------- */

  useEffect(() => {
    if (session.status !== "ready" || !session.apiClient) return;
    if (!session.isVendorAdmin && !session.isVendorStaff) return;

    const loadProfile = async () => {
      try {
        setLoadingProfile(true);
        const res = await session.apiClient!.get("/vendors/me");
        const d = res.data as Record<string, unknown>;
        setVendor({
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
        });
      } catch (err) {
        toast.error("Failed to load vendor profile");
        console.error(err);
      } finally {
        setLoadingProfile(false);
      }
    };

    const loadPayout = async () => {
      try {
        setLoadingPayout(true);
        const res = await session.apiClient!.get("/vendors/me/payout-config");
        const d = res.data as Record<string, unknown>;
        setPayoutConfig({
          payoutCurrency: (d.payoutCurrency as string) || "USD",
          payoutSchedule: (d.payoutSchedule as string) || "MONTHLY",
          payoutMinimum: typeof d.payoutMinimum === "number" ? d.payoutMinimum : "",
          bankAccountHolder: (d.bankAccountHolder as string) || "",
          bankName: (d.bankName as string) || "",
          bankRoutingCode: (d.bankRoutingCode as string) || "",
          bankAccountNumberMasked: (d.bankAccountNumberMasked as string) || "",
          taxId: (d.taxId as string) || "",
        });
      } catch (err) {
        const msg = err instanceof Error ? err.message : "";
        if (!msg.startsWith("404")) {
          toast.error("Failed to load payout config");
          console.error(err);
        }
        // 404 = not configured yet, keep defaults
      } finally {
        setLoadingPayout(false);
      }
    };

    void loadProfile();
    void loadPayout();
  }, [session.status, session.apiClient, session.isVendorAdmin, session.isVendorStaff]);

  /* ---------------------------------------------------------------- */
  /*  Handlers                                                         */
  /* ---------------------------------------------------------------- */

  const saveProfile = async () => {
    if (!session.apiClient) return;
    try {
      setSavingProfile(true);
      const specializations = vendor.specializations
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      await session.apiClient.put("/vendors/me", {
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
      toast.success("Profile saved successfully");
    } catch (err) {
      toast.error("Failed to save profile");
      console.error(err);
    } finally {
      setSavingProfile(false);
    }
  };

  const savePayout = async () => {
    if (!session.apiClient) return;
    try {
      setSavingPayout(true);
      await session.apiClient.put("/vendors/me/payout-config", {
        payoutCurrency: payoutConfig.payoutCurrency,
        payoutSchedule: payoutConfig.payoutSchedule,
        payoutMinimum: payoutConfig.payoutMinimum === "" ? null : Number(payoutConfig.payoutMinimum),
        bankAccountHolder: payoutConfig.bankAccountHolder,
        bankName: payoutConfig.bankName,
        bankRoutingCode: payoutConfig.bankRoutingCode,
        bankAccountNumberMasked: payoutConfig.bankAccountNumberMasked,
        taxId: payoutConfig.taxId,
      });
      toast.success("Payout config saved successfully");
    } catch (err) {
      toast.error("Failed to save payout config");
      console.error(err);
    } finally {
      setSavingPayout(false);
    }
  };

  const requestVerification = async () => {
    if (!session.apiClient) return;
    try {
      setRequestingVerification(true);
      const body: Record<string, string> = {};
      if (verificationDocUrl.trim()) body.verificationDocumentUrl = verificationDocUrl.trim();
      if (verificationNotes.trim()) body.notes = verificationNotes.trim();
      await session.apiClient.post("/vendors/me/request-verification", body);
      toast.success("Verification request submitted");
      setVendor((prev) => ({ ...prev, verificationStatus: "PENDING_VERIFICATION" }));
      setVerificationDocUrl("");
      setVerificationNotes("");
    } catch (err) {
      toast.error("Failed to request verification");
      console.error(err);
    } finally {
      setRequestingVerification(false);
    }
  };

  const toggleOrders = async (action: "stop" | "resume") => {
    if (!session.apiClient) return;
    try {
      setTogglingOrders(true);
      if (action === "stop") {
        await session.apiClient.post("/vendors/me/stop-orders");
        setVendor((prev) => ({ ...prev, acceptingOrders: false }));
        toast.success("Orders paused");
      } else {
        await session.apiClient.post("/vendors/me/resume-orders");
        setVendor((prev) => ({ ...prev, acceptingOrders: true }));
        toast.success("Orders resumed");
      }
    } catch (err) {
      toast.error(`Failed to ${action} orders`);
      console.error(err);
    } finally {
      setTogglingOrders(false);
      setConfirmModal({ open: false, action: "stop" });
    }
  };

  /* ---------------------------------------------------------------- */
  /*  Auth guard                                                       */
  /* ---------------------------------------------------------------- */

  if (session.status !== "ready") {
    return (
      <AdminPageShell
        title="Vendor Settings"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
      >
        <div style={{ textAlign: "center", padding: 48, color: "var(--muted)" }}>Loading...</div>
      </AdminPageShell>
    );
  }

  if (!session.isVendorAdmin && !session.isVendorStaff) {
    return (
      <AdminPageShell
        title="Vendor Settings"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
      >
        <div style={{ textAlign: "center", padding: 48, color: "var(--muted)", fontSize: "1.1rem" }}>
          Unauthorized
        </div>
      </AdminPageShell>
    );
  }

  /* ---------------------------------------------------------------- */
  /*  Field helpers                                                    */
  /* ---------------------------------------------------------------- */

  const profileField = (key: keyof VendorProfile, value: string | number | "") => {
    setVendor((prev) => ({ ...prev, [key]: value }));
  };

  const payoutField = (key: keyof PayoutConfig, value: string | number | "") => {
    setPayoutConfig((prev) => ({ ...prev, [key]: value }));
  };

  /* ---------------------------------------------------------------- */
  /*  Render helpers                                                   */
  /* ---------------------------------------------------------------- */

  const renderInput = (
    label: string,
    value: string | number | "",
    onChange: (v: string) => void,
    opts?: { type?: string; required?: boolean; placeholder?: string; maxLength?: number; disabled?: boolean }
  ) => (
    <div style={fieldWrap}>
      <label style={labelStyle}>
        {label}
        {opts?.required && <span style={{ color: "var(--brand)", marginLeft: 2 }}>*</span>}
      </label>
      <input
        type={opts?.type || "text"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={opts?.placeholder}
        maxLength={opts?.maxLength}
        disabled={opts?.disabled}
        required={opts?.required}
        style={inputStyle}
      />
    </div>
  );

  const renderTextarea = (
    label: string,
    value: string,
    onChange: (v: string) => void,
    opts?: { rows?: number; placeholder?: string }
  ) => (
    <div style={fieldWrap}>
      <label style={labelStyle}>{label}</label>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={opts?.placeholder}
        rows={opts?.rows || 4}
        style={textareaStyle}
      />
    </div>
  );

  /* ---------------------------------------------------------------- */
  /*  Tab content                                                      */
  /* ---------------------------------------------------------------- */

  const renderProfileTab = () => (
    <div>
      {loadingProfile ? (
        <div style={{ textAlign: "center", padding: 48, color: "var(--muted)" }}>Loading profile...</div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void saveProfile();
          }}
        >
          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Basic Information
            </h3>
            {renderInput("Name", vendor.name, (v) => profileField("name", v), { required: true })}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              {renderInput("Contact Email", vendor.contactEmail, (v) => profileField("contactEmail", v), {
                type: "email",
                required: true,
              })}
              {renderInput("Support Email", vendor.supportEmail, (v) => profileField("supportEmail", v), {
                type: "email",
              })}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              {renderInput("Contact Phone", vendor.contactPhone, (v) => profileField("contactPhone", v))}
              {renderInput("Contact Person Name", vendor.contactPersonName, (v) =>
                profileField("contactPersonName", v)
              )}
            </div>
          </div>

          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Branding
            </h3>
            {renderInput("Logo Image URL", vendor.logoImage, (v) => profileField("logoImage", v), {
              placeholder: "https://...",
            })}
            {renderInput("Banner Image URL", vendor.bannerImage, (v) => profileField("bannerImage", v), {
              placeholder: "https://...",
            })}
            {renderInput("Website URL", vendor.websiteUrl, (v) => profileField("websiteUrl", v), {
              placeholder: "https://...",
            })}
          </div>

          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Details
            </h3>
            {renderTextarea("Description", vendor.description, (v) => profileField("description", v), {
              rows: 5,
              placeholder: "Tell customers about your store...",
            })}
            {renderTextarea("Return Policy", vendor.returnPolicy, (v) => profileField("returnPolicy", v), {
              rows: 3,
            })}
            {renderTextarea("Shipping Policy", vendor.shippingPolicy, (v) => profileField("shippingPolicy", v), {
              rows: 3,
            })}
          </div>

          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Fulfillment
            </h3>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              {renderInput(
                "Processing Time (days)",
                vendor.processingTimeDays,
                (v) => profileField("processingTimeDays", v === "" ? "" : Number(v)),
                { type: "number" }
              )}
              {renderInput(
                "Free Shipping Threshold",
                vendor.freeShippingThreshold,
                (v) => profileField("freeShippingThreshold", v === "" ? "" : Number(v)),
                { type: "number", placeholder: "0.00" }
              )}
            </div>

            <div style={{ ...fieldWrap, display: "flex", alignItems: "center", gap: 10 }}>
              <label
                style={{ ...labelStyle, marginBottom: 0, cursor: "pointer", display: "flex", alignItems: "center", gap: 8 }}
              >
                <input
                  type="checkbox"
                  checked={vendor.acceptsReturns}
                  onChange={(e) => setVendor((prev) => ({ ...prev, acceptsReturns: e.target.checked }))}
                  style={{ width: 18, height: 18, accentColor: "var(--brand)" }}
                />
                Accepts Returns
              </label>
            </div>

            {vendor.acceptsReturns &&
              renderInput(
                "Return Window (days)",
                vendor.returnWindowDays,
                (v) => profileField("returnWindowDays", v === "" ? "" : Number(v)),
                { type: "number" }
              )}
          </div>

          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Categories
            </h3>
            {renderInput("Primary Category", vendor.primaryCategory, (v) => profileField("primaryCategory", v))}
            {renderInput("Specializations", vendor.specializations, (v) => profileField("specializations", v), {
              placeholder: "Comma-separated values",
            })}
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}>
            <button
              type="submit"
              className="btn-brand"
              disabled={savingProfile}
              style={saveBtnStyle}
            >
              {savingProfile ? "Saving..." : "Save Profile"}
            </button>
          </div>
        </form>
      )}
    </div>
  );

  const renderPayoutTab = () => (
    <div>
      {loadingPayout ? (
        <div style={{ textAlign: "center", padding: 48, color: "var(--muted)" }}>Loading payout config...</div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void savePayout();
          }}
        >
          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Payout Settings
            </h3>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
              {renderInput("Payout Currency", payoutConfig.payoutCurrency, (v) => payoutField("payoutCurrency", v), {
                maxLength: 3,
                placeholder: "USD",
              })}
              <div style={fieldWrap}>
                <label style={labelStyle}>Payout Schedule</label>
                <select
                  value={payoutConfig.payoutSchedule}
                  onChange={(e) => payoutField("payoutSchedule", e.target.value)}
                  style={inputStyle}
                >
                  <option value="WEEKLY">Weekly</option>
                  <option value="BIWEEKLY">Biweekly</option>
                  <option value="MONTHLY">Monthly</option>
                </select>
              </div>
              {renderInput(
                "Payout Minimum",
                payoutConfig.payoutMinimum,
                (v) => payoutField("payoutMinimum", v === "" ? "" : Number(v)),
                { type: "number", placeholder: "0.00" }
              )}
            </div>
          </div>

          <div style={cardStyle}>
            <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
              Bank Details
            </h3>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              {renderInput("Bank Account Holder", payoutConfig.bankAccountHolder, (v) =>
                payoutField("bankAccountHolder", v)
              )}
              {renderInput("Bank Name", payoutConfig.bankName, (v) => payoutField("bankName", v))}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              {renderInput("Bank Routing Code", payoutConfig.bankRoutingCode, (v) =>
                payoutField("bankRoutingCode", v)
              )}
              {renderInput("Bank Account Number", payoutConfig.bankAccountNumberMasked, (v) =>
                payoutField("bankAccountNumberMasked", v)
              )}
            </div>
            {renderInput("Tax ID", payoutConfig.taxId, (v) => payoutField("taxId", v))}
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}>
            <button
              type="submit"
              className="btn-brand"
              disabled={savingPayout}
              style={saveBtnStyle}
            >
              {savingPayout ? "Saving..." : "Save Payout Config"}
            </button>
          </div>
        </form>
      )}
    </div>
  );

  const isVerificationDisabled =
    vendor.verificationStatus === "PENDING_VERIFICATION" || vendor.verificationStatus === "VERIFIED";

  const renderActionsTab = () => (
    <div>
      {/* Verification section */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
          Verification
        </h3>

        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20 }}>
          <span style={{ fontSize: "0.82rem", color: "var(--muted)", fontWeight: 600 }}>Current Status:</span>
          {vendor.verificationStatus ? (
            <StatusBadge value={vendor.verificationStatus} colorMap={VENDOR_STATUS_COLORS} />
          ) : (
            <span style={{ fontSize: "0.82rem", color: "var(--muted)" }}>Not set</span>
          )}
        </div>

        {!isVerificationDisabled && (
          <>
            {renderInput("Document URL (optional)", verificationDocUrl, setVerificationDocUrl, {
              placeholder: "https://link-to-verification-document...",
            })}
            {renderTextarea("Notes (optional)", verificationNotes, setVerificationNotes, {
              rows: 3,
              placeholder: "Any additional information for the review team...",
            })}
          </>
        )}

        <button
          type="button"
          className="btn-brand"
          disabled={isVerificationDisabled || requestingVerification}
          onClick={() => void requestVerification()}
          style={{
            ...saveBtnStyle,
            opacity: isVerificationDisabled ? 0.5 : 1,
            cursor: isVerificationDisabled ? "not-allowed" : "pointer",
          }}
        >
          {requestingVerification ? "Submitting..." : "Request Verification"}
        </button>

        {isVerificationDisabled && (
          <p style={{ fontSize: "0.78rem", color: "var(--muted)", marginTop: 8 }}>
            {vendor.verificationStatus === "VERIFIED"
              ? "Your vendor account is already verified."
              : "A verification request is already pending."}
          </p>
        )}
      </div>

      {/* Order receiving section */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", marginBottom: 20 }}>
          Order Receiving
        </h3>

        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20 }}>
          <span
            style={{
              display: "inline-block",
              width: 10,
              height: 10,
              borderRadius: "50%",
              background: vendor.acceptingOrders ? "var(--success)" : "var(--warning-text)",
            }}
          />
          <span style={{ fontSize: "0.9rem", fontWeight: 600, color: "var(--ink)" }}>
            {vendor.acceptingOrders ? "Accepting Orders" : "Orders Paused"}
          </span>
        </div>

        <button
          type="button"
          className="btn-brand"
          disabled={togglingOrders}
          onClick={() =>
            setConfirmModal({
              open: true,
              action: vendor.acceptingOrders ? "stop" : "resume",
            })
          }
          style={{
            ...saveBtnStyle,
            background: vendor.acceptingOrders ? "var(--danger-soft)" : undefined,
            color: vendor.acceptingOrders ? "#f87171" : undefined,
            border: vendor.acceptingOrders ? "1px solid rgba(239,68,68,0.25)" : undefined,
          }}
        >
          {vendor.acceptingOrders ? "Stop Orders" : "Resume Orders"}
        </button>
      </div>

      {/* Confirm modal */}
      <ConfirmModal
        open={confirmModal.open}
        title={confirmModal.action === "stop" ? "Stop Accepting Orders" : "Resume Accepting Orders"}
        message={
          confirmModal.action === "stop"
            ? "Are you sure you want to stop accepting new orders? Existing orders will not be affected."
            : "Are you sure you want to resume accepting new orders?"
        }
        confirmLabel={confirmModal.action === "stop" ? "Stop Orders" : "Resume Orders"}
        danger={confirmModal.action === "stop"}
        loading={togglingOrders}
        onConfirm={() => void toggleOrders(confirmModal.action)}
        onCancel={() => setConfirmModal({ open: false, action: "stop" })}
      />
    </div>
  );

  /* ---------------------------------------------------------------- */
  /*  Render                                                           */
  /* ---------------------------------------------------------------- */

  const tabs: { key: Tab; label: string }[] = [
    { key: "profile", label: "Profile" },
    { key: "payout", label: "Payout" },
    { key: "actions", label: "Actions" },
  ];

  return (
    <AdminPageShell
      title="Vendor Settings"
      breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Settings" }]}
    >
      {/* Tab navigation */}
      <div
        style={{
          display: "flex",
          gap: 0,
          borderBottom: "1px solid var(--line)",
          marginBottom: 28,
        }}
      >
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setActiveTab(t.key)}
            style={{
              ...tabBtnBase,
              color: activeTab === t.key ? "var(--brand)" : "var(--muted)",
              borderBottom: activeTab === t.key ? "2px solid var(--brand)" : "2px solid transparent",
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === "profile" && renderProfileTab()}
      {activeTab === "payout" && renderPayoutTab()}
      {activeTab === "actions" && renderActionsTab()}
    </AdminPageShell>
  );
}
