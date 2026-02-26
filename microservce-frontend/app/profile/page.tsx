"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import AccountInfoCard from "../components/profile/AccountInfoCard";
import AddressBook from "../components/profile/AddressBook";
import CommunicationPrefsTab from "../components/profile/CommunicationPrefsTab";
import ActivityLogTab from "../components/profile/ActivityLogTab";
import CouponUsageTab from "../components/profile/CouponUsageTab";
import { useAuthSession } from "../../lib/authSession";
import type { CustomerAddress, AddressForm, CommunicationPreferences } from "../../lib/types/customer";
import { emptyAddressForm } from "../../lib/types/customer";
import { useCustomerProfile, useUpdateProfile, useCommunicationPrefs, useUpdateCommPref, useLinkedAccounts, useActivityLog, useCouponUsage } from "../../lib/hooks/queries/useCustomerProfile";
import { useAddresses, useSaveAddress, useDeleteAddress, useSetDefaultAddress } from "../../lib/hooks/queries/useAddresses";

type DefaultAction = { addressId: string; type: "shipping" | "billing" };
type TabKey = "account" | "preferences" | "activity" | "coupon-history";

function splitDisplayName(name: string) {
  const normalized = name.trim().replace(/\s+/g, " ");
  if (!normalized) return { firstName: "", lastName: "" };
  const firstSpace = normalized.indexOf(" ");
  if (firstSpace < 0) {
    // Single-word names: pre-fill both fields so the user can correct them.
    // Backend requires both firstName and lastName to be non-blank.
    return { firstName: normalized, lastName: normalized };
  }
  return { firstName: normalized.slice(0, firstSpace).trim(), lastName: normalized.slice(firstSpace + 1).trim() };
}


export default function ProfilePage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, apiClient, ensureCustomer,
    resendVerificationEmail, changePassword, profile, logout, emailVerified,
  } = session;

  const [status, setStatus] = useState("Loading account...");
  const [resendingVerification, setResendingVerification] = useState(false);
  const [editFirstName, setEditFirstName] = useState("");
  const [editLastName, setEditLastName] = useState("");
  const [passwordActionPending, setPasswordActionPending] = useState(false);
  const [addressForm, setAddressForm] = useState<AddressForm>(emptyAddressForm);
  const [deletingAddressId, setDeletingAddressId] = useState<string | null>(null);
  const [settingDefaultAddress, setSettingDefaultAddress] = useState<DefaultAction | null>(null);

  /* ── Tab state ── */
  const [activeTab, setActiveTab] = useState<TabKey>("account");

  /* ── Pagination state ── */
  const [activityLogPage, setActivityLogPage] = useState(0);
  const [couponUsagePage, setCouponUsagePage] = useState(0);

  /* ── React Query: Customer Profile ── */
  const customerApi = apiClient;
  const { data: customer } = useCustomerProfile(customerApi);
  const updateProfileMutation = useUpdateProfile(customerApi);
  const initialNameParts = splitDisplayName(customer?.name || "");

  /* ── React Query: Addresses ── */
  const { data: addresses = [], isLoading: addressLoading } = useAddresses(customerApi);
  const saveAddressMutation = useSaveAddress(apiClient);
  const deleteAddressMutation = useDeleteAddress(apiClient);
  const setDefaultAddressMutation = useSetDefaultAddress(apiClient);

  /* ── React Query: Communication Preferences ── */
  const { data: commPrefs, isLoading: commPrefsLoading } = useCommunicationPrefs(apiClient, activeTab === "preferences");
  const updateCommPrefMutation = useUpdateCommPref(apiClient);

  /* ── React Query: Linked Accounts ── */
  const { data: linkedAccounts, isLoading: linkedAccountsLoading } = useLinkedAccounts(apiClient, activeTab === "preferences");

  /* ── React Query: Activity Log ── */
  const { data: activityLogData, isLoading: activityLogLoading } = useActivityLog(apiClient, activityLogPage, activeTab === "activity");

  /* ── React Query: Coupon Usage ── */
  const { data: couponUsageData, isLoading: couponUsageLoading } = useCouponUsage(apiClient, couponUsagePage, activeTab === "coupon-history");

  const resetAddressForm = () => setAddressForm(emptyAddressForm);

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
    if (!apiClient || saveAddressMutation.isPending) return;
    const { recipientName, phone, line1, city, state, postalCode, countryCode } = addressForm;
    if (!recipientName.trim() || !phone.trim() || !line1.trim() || !city.trim() || !state.trim() || !postalCode.trim() || !countryCode.trim()) {
      toast.error("Fill all required address fields"); return;
    }
    setStatus(addressForm.id ? "Updating address..." : "Adding address...");
    saveAddressMutation.mutate(addressForm, {
      onSuccess: () => {
        toast.success(addressForm.id ? "Address updated" : "Address added");
        resetAddressForm();
        setStatus("Address book updated.");
        window.dispatchEvent(new Event("addresses-updated"));
      },
      onError: (err) => {
        const message = err instanceof Error ? err.message : "Failed to save address";
        setStatus(message); toast.error(message);
      },
    });
  };

  const deleteAddress = async (addressId: string) => {
    if (!apiClient || deletingAddressId) return;
    setDeletingAddressId(addressId);
    deleteAddressMutation.mutate(addressId, {
      onSuccess: () => {
        toast.success("Address deleted");
        if (addressForm.id === addressId) resetAddressForm();
        setStatus("Address deleted.");
        window.dispatchEvent(new Event("addresses-updated"));
      },
      onError: (err) => {
        const message = err instanceof Error ? err.message : "Failed to delete address";
        setStatus(message); toast.error(message);
      },
      onSettled: () => { setDeletingAddressId(null); },
    });
  };

  const setDefaultAddressFn = async (addressId: string, type: "shipping" | "billing") => {
    if (!apiClient || settingDefaultAddress) return;
    const address = addresses.find((item) => item.id === addressId);
    if (!address) return;
    if (type === "shipping" && address.defaultShipping) return;
    if (type === "billing" && address.defaultBilling) return;
    setSettingDefaultAddress({ addressId, type });
    setDefaultAddressMutation.mutate({ addressId, type }, {
      onSuccess: () => {
        toast.success(type === "shipping" ? "Default shipping address updated" : "Default billing address updated");
        setStatus("Address defaults updated.");
        window.dispatchEvent(new Event("addresses-updated"));
      },
      onError: (err) => {
        const message = err instanceof Error ? err.message : "Failed to set default address";
        setStatus(message); toast.error(message);
      },
      onSettled: () => { setSettingDefaultAddress(null); },
    });
  };

  const toggleCommPref = async (key: keyof Pick<CommunicationPreferences, "emailMarketing" | "smsMarketing" | "pushNotifications" | "orderUpdates" | "promotionalAlerts">) => {
    if (!apiClient || !commPrefs || updateCommPrefMutation.isPending) return;
    const newValue = !commPrefs[key];
    updateCommPrefMutation.mutate({ key, value: newValue }, {
      onError: () => { toast.error("Failed to update preference"); },
    });
  };

  /* ── Pagination handlers ── */
  const handleActivityPageChange = (page: number) => { setActivityLogPage(page); };
  const handleCouponPageChange = (page: number) => { setCouponUsagePage(page); };

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
    if (!apiClient) return;
    void ensureCustomer();
  }, [router, sessionStatus, isAuthenticated, apiClient, ensureCustomer]);

  /* ── Sync edit fields from React Query customer data ── */
  useEffect(() => {
    if (!customer) return;
    const nameParts = splitDisplayName(customer.name || "");
    setEditFirstName(nameParts.firstName);
    setEditLastName(nameParts.lastName);
    setStatus("Account loaded.");
  }, [customer]);

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
    if (!apiClient || !customer || updateProfileMutation.isPending) return;
    const f = editFirstName.trim(), l = editLastName.trim();
    if (!f || !l) { toast.error("First name and last name are required"); return; }
    if (f === initialNameParts.firstName && l === initialNameParts.lastName) { setStatus("No changes to save."); return; }
    updateProfileMutation.mutate({ firstName: f, lastName: l }, {
      onSuccess: (updated) => {
        const parts = splitDisplayName(updated.name || `${f} ${l}`);
        setEditFirstName(parts.firstName); setEditLastName(parts.lastName);
        setStatus("Profile updated."); toast.success("Profile updated");
      },
      onError: (err) => {
        const message = err instanceof Error ? err.message : "Failed to update profile";
        setStatus(message); toast.error(message);
      },
    });
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
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-base text-muted">Loading...</p>
        </div>
      </div>
    );
  }
  if (!isAuthenticated) return null;

  /* ── Tab definitions ── */
  const tabs: { key: TabKey; label: string }[] = [
    { key: "account", label: "Account" },
    { key: "preferences", label: "Preferences" },
    { key: "activity", label: "Activity" },
    { key: "coupon-history", label: "Coupon History" },
  ];

  return (
    <div className="min-h-screen bg-bg">
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&rsaquo;</span>
          <span className="breadcrumb-current">My Profile</span>
        </nav>

        {/* Email verification warning */}
        {emailVerified === false && (
          <section
            className="mb-4 flex items-center gap-3 rounded-xl border border-warning-border bg-warning-soft px-4 py-3 text-sm text-warning-text"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <div className="flex-1">
              <p className="m-0 font-bold">Email Not Verified</p>
              <p className="m-0 text-xs opacity-80">Profile and order actions are blocked until verification.</p>
            </div>
            <button
              onClick={() => { void resendVerification(); }}
              disabled={resendingVerification}
              className="rounded-[8px] border border-warning-border bg-warning-soft px-3.5 py-1.5 text-xs font-bold text-warning-text disabled:cursor-not-allowed"
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        {/* Page Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
              My Profile
            </h1>
            <p className="mt-1 text-sm text-muted">Manage your account and addresses</p>
          </div>
          <div className="flex gap-2.5">
            <Link href="/products" className="rounded-md bg-[var(--gradient-brand)] px-[18px] py-[9px] text-sm font-bold text-white no-underline">
              Shop
            </Link>
            <Link href="/orders" className="rounded-md border border-line-bright bg-brand-soft px-[18px] py-[9px] text-sm font-bold text-brand no-underline">
              Orders
            </Link>
            <button
              onClick={() => { void startChangePassword(); }}
              disabled={passwordActionPending}
              className="rounded-md border border-line-bright bg-brand-soft px-[18px] py-[9px] text-sm font-bold text-brand disabled:cursor-not-allowed disabled:opacity-50"
            >
              {passwordActionPending ? "Redirecting..." : "Change Password"}
            </button>
          </div>
        </div>

        {/* ── Tab Navigation ── */}
        <div className="mb-5 flex border-b border-line">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className="cursor-pointer border-x-0 border-t-0 border-b-2 bg-transparent px-5 py-2.5 text-sm font-bold transition-colors duration-200"
              style={{
                borderBottomColor: activeTab === tab.key ? "var(--brand)" : "transparent",
                color: activeTab === tab.key ? "var(--brand)" : "var(--muted)",
                marginBottom: "-1px",
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* ══════════════════════════════════════════════════════════════
            TAB: Account
           ══════════════════════════════════════════════════════════════ */}
        {activeTab === "account" && (
          <>
            <AccountInfoCard
              customer={customer ?? null}
              canViewAdmin={canViewAdmin}
              editFirstName={editFirstName}
              editLastName={editLastName}
              onFirstNameChange={setEditFirstName}
              onLastNameChange={setEditLastName}
              savingProfile={updateProfileMutation.isPending}
              onSave={() => { void saveProfile(); }}
              emailVerified={emailVerified}
              profile={profile}
              initialNameParts={initialNameParts}
            />

            <AddressBook
              addresses={addresses}
              addressForm={addressForm}
              onAddressFormChange={setAddressForm}
              savingAddress={saveAddressMutation.isPending}
              onSave={() => { void saveAddress(); }}
              onReset={resetAddressForm}
              onStartEdit={startEditAddress}
              onDelete={(id) => { void deleteAddress(id); }}
              onSetDefault={(id, type) => { void setDefaultAddressFn(id, type); }}
              addressLoading={addressLoading}
              deletingAddressId={deletingAddressId}
              settingDefaultAddress={settingDefaultAddress}
              emailVerified={emailVerified}
            />

            <p className="mt-4 text-xs text-muted-2">
              {status}
            </p>
          </>
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB: Preferences
           ══════════════════════════════════════════════════════════════ */}
        {activeTab === "preferences" && (
          <CommunicationPrefsTab
            commPrefs={commPrefs ?? null}
            commPrefsLoading={commPrefsLoading}
            commPrefsSaving={updateCommPrefMutation.isPending ? (updateCommPrefMutation.variables?.key ?? null) : null}
            onToggle={(key) => { void toggleCommPref(key); }}
            linkedAccounts={linkedAccounts ?? null}
            linkedAccountsLoading={linkedAccountsLoading}
          />
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB: Activity Log
           ══════════════════════════════════════════════════════════════ */}
        {activeTab === "activity" && (
          <ActivityLogTab
            activityLog={activityLogData ?? null}
            activityLogLoading={activityLogLoading}
            activityLogPage={activityLogPage}
            onPageChange={handleActivityPageChange}
          />
        )}

        {/* ══════════════════════════════════════════════════════════════
            TAB: Coupon History
           ══════════════════════════════════════════════════════════════ */}
        {activeTab === "coupon-history" && (
          <CouponUsageTab
            couponUsage={couponUsageData ?? null}
            couponUsageLoading={couponUsageLoading}
            couponUsagePage={couponUsagePage}
            onPageChange={handleCouponPageChange}
          />
        )}
      </main>

      <Footer />
    </div>
  );
}
