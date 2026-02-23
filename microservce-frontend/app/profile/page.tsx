"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

type Customer = { id: string; name: string; email: string; createdAt: string };
type CustomerAddress = {
  id: string; customerId: string; label: string | null; recipientName: string;
  phone: string; line1: string; line2: string | null; city: string; state: string;
  postalCode: string; countryCode: string; defaultShipping: boolean; defaultBilling: boolean;
  deleted: boolean; createdAt: string; updatedAt: string;
};
type AddressForm = {
  id?: string; label: string; recipientName: string; phone: string;
  line1: string; line2: string; city: string; state: string;
  postalCode: string; countryCode: string; defaultShipping: boolean; defaultBilling: boolean;
};
type DefaultAction = { addressId: string; type: "shipping" | "billing" };

const emptyAddressForm: AddressForm = {
  label: "", recipientName: "", phone: "", line1: "", line2: "", city: "",
  state: "", postalCode: "", countryCode: "US", defaultShipping: false, defaultBilling: false,
};

function splitDisplayName(name: string) {
  const normalized = name.trim().replace(/\s+/g, " ");
  if (!normalized) return { firstName: "", lastName: "" };
  const firstSpace = normalized.indexOf(" ");
  if (firstSpace < 0) return { firstName: normalized, lastName: "" };
  return { firstName: normalized.slice(0, firstSpace).trim(), lastName: normalized.slice(firstSpace + 1).trim() };
}


export default function ProfilePage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, apiClient, ensureCustomer,
    resendVerificationEmail, changePassword, profile, logout, emailVerified,
  } = session;

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [status, setStatus] = useState("Loading account...");
  const [resendingVerification, setResendingVerification] = useState(false);
  const [editFirstName, setEditFirstName] = useState("");
  const [editLastName, setEditLastName] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [passwordActionPending, setPasswordActionPending] = useState(false);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [addressForm, setAddressForm] = useState<AddressForm>(emptyAddressForm);
  const [savingAddress, setSavingAddress] = useState(false);
  const [addressLoading, setAddressLoading] = useState(false);
  const [deletingAddressId, setDeletingAddressId] = useState<string | null>(null);
  const [settingDefaultAddress, setSettingDefaultAddress] = useState<DefaultAction | null>(null);
  const initialNameParts = splitDisplayName(customer?.name || "");

  const resetAddressForm = () => setAddressForm(emptyAddressForm);

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    setAddressLoading(true);
    try {
      const response = await apiClient.get("/customers/me/addresses");
      setAddresses((response.data as CustomerAddress[]) || []);
    } finally { setAddressLoading(false); }
  }, [apiClient]);

  const startEditAddress = (address: CustomerAddress) => {
    setAddressForm({
      id: address.id, label: address.label || "", recipientName: address.recipientName,
      phone: address.phone, line1: address.line1, line2: address.line2 || "",
      city: address.city, state: address.state, postalCode: address.postalCode,
      countryCode: (address.countryCode || "US").toUpperCase(),
      defaultShipping: address.defaultShipping, defaultBilling: address.defaultBilling,
    });
  };

  const saveAddress = async () => {
    if (!apiClient || savingAddress) return;
    const { recipientName, phone, line1, city, state, postalCode, countryCode } = addressForm;
    if (!recipientName.trim() || !phone.trim() || !line1.trim() || !city.trim() || !state.trim() || !postalCode.trim() || !countryCode.trim()) {
      toast.error("Fill all required address fields"); return;
    }
    setSavingAddress(true);
    setStatus(addressForm.id ? "Updating address..." : "Adding address...");
    try {
      const payload = {
        label: addressForm.label.trim() || null,
        recipientName: addressForm.recipientName.trim(), phone: addressForm.phone.trim(),
        line1: addressForm.line1.trim(), line2: addressForm.line2.trim() || null,
        city: addressForm.city.trim(), state: addressForm.state.trim(),
        postalCode: addressForm.postalCode.trim(), countryCode: addressForm.countryCode.trim().toUpperCase(),
        defaultShipping: addressForm.defaultShipping, defaultBilling: addressForm.defaultBilling,
      };
      if (addressForm.id) {
        await apiClient.put(`/customers/me/addresses/${addressForm.id}`, payload);
        toast.success("Address updated");
      } else {
        await apiClient.post("/customers/me/addresses", payload);
        toast.success("Address added");
      }
      resetAddressForm();
      await loadAddresses();
      setStatus("Address book updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save address";
      setStatus(message); toast.error(message);
    } finally { setSavingAddress(false); }
  };

  const deleteAddress = async (addressId: string) => {
    if (!apiClient || deletingAddressId) return;
    setDeletingAddressId(addressId);
    try {
      await apiClient.delete(`/customers/me/addresses/${addressId}`);
      toast.success("Address deleted");
      if (addressForm.id === addressId) resetAddressForm();
      await loadAddresses(); setStatus("Address deleted.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete address";
      setStatus(message); toast.error(message);
    } finally { setDeletingAddressId(null); }
  };

  const setDefaultAddress = async (addressId: string, type: "shipping" | "billing") => {
    if (!apiClient || settingDefaultAddress) return;
    const address = addresses.find((item) => item.id === addressId);
    if (!address) return;
    if (type === "shipping" && address.defaultShipping) return;
    if (type === "billing" && address.defaultBilling) return;
    setSettingDefaultAddress({ addressId, type });
    try {
      const suffix = type === "shipping" ? "default-shipping" : "default-billing";
      await apiClient.post(`/customers/me/addresses/${addressId}/${suffix}`);
      toast.success(type === "shipping" ? "Default shipping address updated" : "Default billing address updated");
      await loadAddresses(); setStatus("Address defaults updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to set default address";
      setStatus(message); toast.error(message);
    } finally { setSettingDefaultAddress(null); }
  };

  useEffect(() => {
    const reset = () => setPasswordActionPending(false);
    const onVis = () => { if (document.visibilityState === "visible") reset(); };
    window.addEventListener("pageshow", reset);
    window.addEventListener("focus", reset);
    document.addEventListener("visibilitychange", onVis);
    return () => {
      window.removeEventListener("pageshow", reset);
      window.removeEventListener("focus", reset);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, []);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    if (canViewAdmin) return;
    const run = async () => {
      if (!apiClient) return;
      try {
        await ensureCustomer();
        const response = await apiClient.get("/customers/me");
        const loaded = response.data as Customer;
        setCustomer(loaded);
        const nameParts = splitDisplayName(loaded.name || "");
        setEditFirstName(nameParts.firstName);
        setEditLastName(nameParts.lastName);
        await loadAddresses();
        setStatus("Account loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load account");
      }
    };
    void run();
  }, [router, sessionStatus, isAuthenticated, canViewAdmin, apiClient, ensureCustomer, loadAddresses]);

  const resendVerification = async () => {
    if (resendingVerification) return;
    setResendingVerification(true);
    try {
      await resendVerificationEmail();
      toast.success("Verification email sent");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to resend verification email");
    } finally { setResendingVerification(false); }
  };

  const saveProfile = async () => {
    if (!apiClient || !customer || savingProfile) return;
    const f = editFirstName.trim(), l = editLastName.trim();
    if (!f || !l) { toast.error("First name and last name are required"); return; }
    if (f === initialNameParts.firstName && l === initialNameParts.lastName) { setStatus("No changes to save."); return; }
    setSavingProfile(true);
    try {
      const response = await apiClient.put("/customers/me", { firstName: f, lastName: l });
      const updated = response.data as Customer;
      setCustomer(updated);
      const parts = splitDisplayName(updated.name || `${f} ${l}`);
      setEditFirstName(parts.firstName); setEditLastName(parts.lastName);
      setStatus("Profile updated."); toast.success("Profile updated");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update profile";
      setStatus(message); toast.error(message);
    } finally { setSavingProfile(false); }
  };

  const startChangePassword = async () => {
    if (passwordActionPending) return;
    setPasswordActionPending(true);
    try { await changePassword("/profile"); } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to open change password flow";
      setStatus(message); toast.error(message);
    } finally { setPasswordActionPending(false); }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }
  if (!isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">My Profile</span>
        </nav>

        {/* Email verification warning */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl px-4 py-3 text-sm"
            style={{ border: "1px solid var(--warning-border)", background: "var(--warning-soft)", color: "var(--warning-text)" }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p style={{ fontWeight: 700, margin: 0 }}>Email Not Verified</p>
              <p style={{ fontSize: "0.75rem", opacity: 0.8, margin: 0 }}>Profile and order actions are blocked until verification.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              style={{ background: "var(--warning-soft)", border: "1px solid var(--warning-border)", color: "var(--warning-text)", padding: "6px 14px", borderRadius: "8px", fontSize: "0.75rem", fontWeight: 700, cursor: resendingVerification ? "not-allowed" : "pointer" }}
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>
              My Profile
            </h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>Manage your account and addresses</p>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <Link href="/products" className="no-underline" style={{ padding: "9px 18px", borderRadius: "10px", background: "var(--gradient-brand)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
              Shop
            </Link>
            <Link href="/orders" className="no-underline" style={{ padding: "9px 18px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontSize: "0.8rem", fontWeight: 700 }}>
              Orders
            </Link>
            <button
              onClick={() => { void startChangePassword(); }}
              disabled={passwordActionPending}
              style={{ padding: "9px 18px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontSize: "0.8rem", fontWeight: 700, cursor: passwordActionPending ? "not-allowed" : "pointer", opacity: passwordActionPending ? 0.5 : 1 }}
            >
              {passwordActionPending ? "Redirecting..." : "Change Password"}
            </button>
          </div>
        </div>

        {/* Top 2 Cards: Profile + Session Info */}
        <div style={{ display: "grid", gap: "16px", gridTemplateColumns: "1fr 1fr", marginBottom: "20px" }}>
          {/* Customer Profile Card */}
          <article className="animate-rise glass-card" style={{ padding: "24px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
              <div style={{
                width: "48px", height: "48px", borderRadius: "50%", flexShrink: 0,
                background: "var(--gradient-brand)",
                display: "grid", placeItems: "center",
              }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                </svg>
              </div>
              <div>
                <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: 0 }}>Customer Profile</p>
                <p style={{ fontSize: "0.875rem", fontWeight: 700, color: "#fff", margin: 0 }}>
                  {canViewAdmin ? "Admin Account" : (customer?.name || "Loading...")}
                </p>
              </div>
            </div>

            {!canViewAdmin && (
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                {/* First Name */}
                <div style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                  <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 6px" }}>First Name</p>
                  <input
                    value={editFirstName}
                    onChange={(e) => setEditFirstName(e.target.value)}
                    disabled={savingProfile || emailVerified === false}
                    className="form-input"
                    style={{ padding: "7px 10px" }}
                    placeholder="Enter first name"
                  />
                </div>
                {/* Last Name + Save */}
                <div style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                  <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 6px" }}>Last Name</p>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <input
                      value={editLastName}
                      onChange={(e) => setEditLastName(e.target.value)}
                      disabled={savingProfile || emailVerified === false}
                      className="form-input"
                      style={{ flex: 1, padding: "7px 10px" }}
                      placeholder="Enter last name"
                    />
                    <button
                      onClick={() => { void saveProfile(); }}
                      disabled={
                        savingProfile || emailVerified === false || !customer || !editFirstName.trim() || !editLastName.trim()
                        || (editFirstName.trim() === initialNameParts.firstName && editLastName.trim() === initialNameParts.lastName)
                      }
                      style={{
                        padding: "7px 14px", borderRadius: "8px", border: "none", flexShrink: 0,
                        background: "var(--gradient-brand)",
                        color: "#fff", fontSize: "0.75rem", fontWeight: 700, cursor: "pointer",
                        opacity: savingProfile || !editFirstName.trim() || !editLastName.trim() ? 0.5 : 1,
                      }}
                    >
                      {savingProfile ? "Saving..." : "Save"}
                    </button>
                  </div>
                </div>

                {[
                  { label: "Email", value: customer?.email || "—" },
                  { label: "Customer ID", value: customer?.id || "—", mono: true },
                  { label: "Member Since", value: customer?.createdAt ? new Date(customer.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" }) : "—" },
                ].map(({ label, value, mono }) => (
                  <div key={label} style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                    <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 3px" }}>{label}</p>
                    <p style={{ fontSize: "0.82rem", fontWeight: 700, color: "var(--ink-light)", fontFamily: mono ? "monospace" : undefined, wordBreak: "break-all", margin: 0 }}>{value}</p>
                  </div>
                ))}
              </div>
            )}

            {canViewAdmin && (
              <div style={{ borderRadius: "10px", border: "1px solid rgba(124,58,237,0.25)", background: "rgba(124,58,237,0.08)", padding: "12px 14px", fontSize: "0.82rem", color: "#a78bfa" }}>
                <p style={{ fontWeight: 700, margin: "0 0 4px" }}>Admin Account Detected</p>
                <p style={{ fontSize: "0.75rem", opacity: 0.8, margin: 0 }}>Customer profile bootstrap is not required for admin operations.</p>
              </div>
            )}
          </article>

          {/* Session Info Card */}
          <article className="animate-rise glass-card" style={{ padding: "24px", animationDelay: "80ms" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "16px" }}>
              <div style={{ width: "48px", height: "48px", borderRadius: "50%", flexShrink: 0, background: "rgba(124,58,237,0.2)", border: "1px solid rgba(124,58,237,0.35)", display: "grid", placeItems: "center" }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#a78bfa" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                  <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                </svg>
              </div>
              <div>
                <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: 0 }}>Session Info</p>
                <p style={{ fontSize: "0.875rem", fontWeight: 700, color: "#fff", margin: 0 }}>Authentication Details</p>
              </div>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              {[
                { label: "Auth Email", value: (profile?.email as string) || "—" },
                { label: "Auth Name", value: (profile?.name as string) || "—" },
              ].map(({ label, value }) => (
                <div key={label} style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                  <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 3px" }}>{label}</p>
                  <p style={{ fontSize: "0.82rem", fontWeight: 700, color: "var(--ink-light)", margin: 0 }}>{value}</p>
                </div>
              ))}
              <div style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 6px" }}>Role</p>
                <span style={{
                  borderRadius: "20px", padding: "3px 12px", fontSize: "0.72rem", fontWeight: 800,
                  background: canViewAdmin ? "rgba(124,58,237,0.15)" : "rgba(34,197,94,0.1)",
                  border: `1px solid ${canViewAdmin ? "rgba(124,58,237,0.3)" : "rgba(34,197,94,0.25)"}`,
                  color: canViewAdmin ? "#a78bfa" : "#4ade80",
                }}>
                  {canViewAdmin ? "Admin" : "Customer"}
                </span>
              </div>
              <div style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "10px 14px" }}>
                <p style={{ fontSize: "0.65rem", color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", margin: "0 0 6px" }}>Email Verified</p>
                <span style={{
                  borderRadius: "20px", padding: "3px 12px", fontSize: "0.72rem", fontWeight: 800,
                  background: emailVerified ? "rgba(34,197,94,0.1)" : "var(--warning-soft)",
                  border: `1px solid ${emailVerified ? "rgba(34,197,94,0.25)" : "var(--warning-border)"}`,
                  color: emailVerified ? "#4ade80" : "var(--warning-text)",
                }}>
                  {emailVerified ? "Verified" : "Not Verified"}
                </span>
              </div>
            </div>
          </article>
        </div>

        {/* Address Book */}
        {!canViewAdmin && (
          <section className="glass-card" style={{ padding: "24px" }}>
            <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-end", justifyContent: "space-between", gap: "12px", marginBottom: "20px" }}>
              <div>
                <h2 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1.1rem", color: "#fff", margin: "0 0 4px" }}>Address Book</h2>
                <p style={{ fontSize: "0.75rem", color: "var(--muted)", margin: 0 }}>Manage shipping and billing addresses.</p>
              </div>
              <span style={{ background: "var(--gradient-brand)", color: "#fff", padding: "3px 12px", borderRadius: "20px", fontSize: "0.72rem", fontWeight: 800 }}>
                {addresses.length} active
              </span>
            </div>

            <div style={{ display: "grid", gap: "20px", gridTemplateColumns: "0.9fr 1.1fr" }}>
              {/* Address Form */}
              <div style={{ borderRadius: "12px", border: "1px solid var(--line-bright)", padding: "16px" }}>
                <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--brand)", margin: "0 0 12px" }}>
                  {addressForm.id ? "Edit Address" : "Add Address"}
                </p>
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  {[
                    { key: "label", placeholder: "Label (Home, Office)", required: false },
                    { key: "recipientName", placeholder: "Recipient name *", required: true },
                    { key: "phone", placeholder: "Phone number *", required: true },
                    { key: "line1", placeholder: "Address line 1 *", required: true },
                    { key: "line2", placeholder: "Address line 2 (optional)", required: false },
                  ].map(({ key, placeholder }) => (
                    <input
                      key={key}
                      value={(addressForm as unknown as Record<string, string>)[key]}
                      onChange={(e) => setAddressForm((old) => ({ ...old, [key]: e.target.value }))}
                      placeholder={placeholder}
                      disabled={savingAddress || emailVerified === false}
                      className="form-input"
                    />
                  ))}
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
                    {[
                      { key: "city", placeholder: "City *" },
                      { key: "state", placeholder: "State *" },
                    ].map(({ key, placeholder }) => (
                      <input
                        key={key}
                        value={(addressForm as unknown as Record<string, string>)[key]}
                        onChange={(e) => setAddressForm((old) => ({ ...old, [key]: e.target.value }))}
                        placeholder={placeholder}
                        disabled={savingAddress || emailVerified === false}
                        className="form-input"
                      />
                    ))}
                  </div>
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
                    <input
                      value={addressForm.postalCode}
                      onChange={(e) => setAddressForm((old) => ({ ...old, postalCode: e.target.value }))}
                      placeholder="Postal code *"
                      disabled={savingAddress || emailVerified === false}
                      className="form-input"
                    />
                    <input
                      value={addressForm.countryCode}
                      onChange={(e) => setAddressForm((old) => ({ ...old, countryCode: e.target.value.toUpperCase() }))}
                      placeholder="Country (US)"
                      maxLength={2}
                      disabled={savingAddress || emailVerified === false}
                      className="form-input"
                    />
                  </div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "12px", padding: "4px 0" }}>
                    {[
                      { key: "defaultShipping", label: "Default Shipping" },
                      { key: "defaultBilling", label: "Default Billing" },
                    ].map(({ key, label }) => (
                      <label key={key} style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "0.78rem", color: "var(--muted)", cursor: "pointer" }}>
                        <input
                          type="checkbox"
                          checked={(addressForm as unknown as Record<string, boolean>)[key]}
                          onChange={(e) => setAddressForm((old) => ({ ...old, [key]: e.target.checked }))}
                          disabled={savingAddress || emailVerified === false}
                          style={{ accentColor: "var(--brand)", width: "14px", height: "14px" }}
                        />
                        {label}
                      </label>
                    ))}
                  </div>
                  <div style={{ display: "flex", gap: "8px", marginTop: "4px" }}>
                    <button
                      onClick={() => { void saveAddress(); }}
                      disabled={savingAddress || emailVerified === false}
                      style={{
                        flex: 1, padding: "10px", borderRadius: "10px", border: "none",
                        background: savingAddress || emailVerified === false ? "var(--line-bright)" : "var(--gradient-brand)",
                        color: "#fff", fontSize: "0.82rem", fontWeight: 700,
                        cursor: savingAddress || emailVerified === false ? "not-allowed" : "pointer",
                        display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px",
                      }}
                    >
                      {savingAddress && <span className="spinner-sm" />}
                      {savingAddress ? "Saving..." : addressForm.id ? "Update Address" : "Add Address"}
                    </button>
                    {addressForm.id && (
                      <button
                        onClick={resetAddressForm}
                        disabled={savingAddress}
                        style={{ padding: "10px 14px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: "transparent", color: "var(--muted)", fontSize: "0.82rem", cursor: "pointer" }}
                      >
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              </div>

              {/* Saved Addresses */}
              <div style={{ borderRadius: "12px", border: "1px solid var(--brand-soft)", padding: "16px" }}>
                <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--accent)", margin: "0 0 12px" }}>Saved Addresses</p>
                {addressLoading && <p style={{ fontSize: "0.82rem", color: "var(--muted)" }}>Loading addresses...</p>}
                {!addressLoading && addresses.length === 0 && (
                  <p style={{ fontSize: "0.82rem", color: "var(--muted)" }}>No addresses added yet.</p>
                )}
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {addresses.map((address) => {
                    const isSettingShipping = settingDefaultAddress?.addressId === address.id && settingDefaultAddress.type === "shipping";
                    const isSettingBilling = settingDefaultAddress?.addressId === address.id && settingDefaultAddress.type === "billing";
                    const isSingle = addresses.length === 1;
                    return (
                      <article
                        key={address.id}
                        style={{ borderRadius: "12px", border: "1px solid var(--brand-soft)", background: "var(--brand-soft)", padding: "12px 14px" }}
                      >
                        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "8px" }}>
                          <div style={{ flex: 1, minWidth: "140px" }}>
                            <p style={{ fontWeight: 700, color: "#fff", fontSize: "0.85rem", margin: "0 0 2px" }}>
                              {address.label ? `${address.label} · ` : ""}{address.recipientName}
                            </p>
                            <p style={{ fontSize: "0.72rem", color: "var(--muted)", margin: "0 0 4px" }}>{address.phone}</p>
                            <p style={{ fontSize: "0.7rem", color: "rgba(200,200,232,0.7)", margin: "0 0 6px", lineHeight: 1.5 }}>
                              {address.line1}{address.line2 ? `, ${address.line2}` : ""}, {address.city}, {address.state} {address.postalCode}, {address.countryCode}
                            </p>
                            <div style={{ display: "flex", flexWrap: "wrap", gap: "4px" }}>
                              {address.defaultShipping && (
                                <span style={{ fontSize: "0.6rem", fontWeight: 700, padding: "2px 8px", borderRadius: "20px", background: "rgba(34,197,94,0.1)", border: "1px solid rgba(34,197,94,0.2)", color: "#4ade80" }}>
                                  Default Shipping
                                </span>
                              )}
                              {address.defaultBilling && (
                                <span style={{ fontSize: "0.6rem", fontWeight: 700, padding: "2px 8px", borderRadius: "20px", background: "var(--line-bright)", border: "1px solid var(--line-bright)", color: "var(--brand)" }}>
                                  Default Billing
                                </span>
                              )}
                            </div>
                          </div>
                          <div style={{ display: "flex", flexWrap: "wrap", gap: "4px" }}>
                            {/* Edit */}
                            <button
                              onClick={() => startEditAddress(address)}
                              disabled={savingAddress || emailVerified === false}
                              style={{ padding: "4px 10px", borderRadius: "7px", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontSize: "0.62rem", fontWeight: 700, cursor: "pointer" }}
                            >
                              Edit
                            </button>
                            {/* Set Shipping */}
                            <button
                              onClick={() => { void setDefaultAddress(address.id, "shipping"); }}
                              disabled={isSingle || address.defaultShipping || settingDefaultAddress !== null || savingAddress || emailVerified === false}
                              style={{ padding: "4px 10px", borderRadius: "7px", border: "1px solid rgba(34,197,94,0.2)", background: "rgba(34,197,94,0.06)", color: "#4ade80", fontSize: "0.62rem", fontWeight: 700, cursor: "pointer", opacity: isSingle || address.defaultShipping ? 0.4 : 1 }}
                            >
                              {isSettingShipping ? "Saving..." : address.defaultShipping ? "✓ Shipping" : "Set Shipping"}
                            </button>
                            {/* Set Billing */}
                            <button
                              onClick={() => { void setDefaultAddress(address.id, "billing"); }}
                              disabled={isSingle || address.defaultBilling || settingDefaultAddress !== null || savingAddress || emailVerified === false}
                              style={{ padding: "4px 10px", borderRadius: "7px", border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "var(--brand)", fontSize: "0.62rem", fontWeight: 700, cursor: "pointer", opacity: isSingle || address.defaultBilling ? 0.4 : 1 }}
                            >
                              {isSettingBilling ? "Saving..." : address.defaultBilling ? "✓ Billing" : "Set Billing"}
                            </button>
                            {/* Delete */}
                            <button
                              onClick={() => { void deleteAddress(address.id); }}
                              disabled={deletingAddressId !== null || savingAddress || emailVerified === false}
                              style={{ padding: "4px 10px", borderRadius: "7px", border: "1px solid rgba(239,68,68,0.2)", background: "rgba(239,68,68,0.05)", color: "var(--danger)", fontSize: "0.62rem", fontWeight: 700, cursor: deletingAddressId ? "not-allowed" : "pointer" }}
                            >
                              {deletingAddressId === address.id ? "Deleting..." : "Delete"}
                            </button>
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </div>
            </div>
          </section>
        )}

        <p style={{ marginTop: "16px", fontSize: "0.72rem", color: "var(--muted-2)" }}>
          {canViewAdmin ? "Admin account detected." : status}
        </p>
      </main>

      <Footer />
    </div>
  );
}
