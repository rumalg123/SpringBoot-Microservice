"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";

type Customer = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
};

type CustomerAddress = {
  id: string;
  customerId: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
};

type AddressForm = {
  id?: string;
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
};

const emptyAddressForm: AddressForm = {
  label: "",
  recipientName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  postalCode: "",
  countryCode: "US",
  defaultShipping: false,
  defaultBilling: false,
};

function splitDisplayName(name: string): { firstName: string; lastName: string } {
  const normalized = name.trim().replace(/\s+/g, " ");
  if (!normalized) {
    return { firstName: "", lastName: "" };
  }
  const firstSpace = normalized.indexOf(" ");
  if (firstSpace < 0) {
    return { firstName: normalized, lastName: "" };
  }
  return {
    firstName: normalized.slice(0, firstSpace).trim(),
    lastName: normalized.slice(firstSpace + 1).trim(),
  };
}

export default function ProfilePage() {
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus,
    isAuthenticated,
    canViewAdmin,
    apiClient,
    ensureCustomer,
    resendVerificationEmail,
    changePassword,
    profile,
    logout,
    emailVerified,
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
  const [settingDefaultAddressId, setSettingDefaultAddressId] = useState<string | null>(null);
  const initialNameParts = splitDisplayName(customer?.name || "");

  const resetAddressForm = () => {
    setAddressForm(emptyAddressForm);
  };

  const loadAddresses = useCallback(async () => {
    if (!apiClient) return;
    setAddressLoading(true);
    try {
      const response = await apiClient.get("/customers/me/addresses");
      setAddresses((response.data as CustomerAddress[]) || []);
    } finally {
      setAddressLoading(false);
    }
  }, [apiClient]);

  const startEditAddress = (address: CustomerAddress) => {
    setAddressForm({
      id: address.id,
      label: address.label || "",
      recipientName: address.recipientName,
      phone: address.phone,
      line1: address.line1,
      line2: address.line2 || "",
      city: address.city,
      state: address.state,
      postalCode: address.postalCode,
      countryCode: (address.countryCode || "US").toUpperCase(),
      defaultShipping: address.defaultShipping,
      defaultBilling: address.defaultBilling,
    });
  };

  const saveAddress = async () => {
    if (!apiClient || savingAddress) return;
    if (!addressForm.recipientName.trim() || !addressForm.phone.trim() || !addressForm.line1.trim()
      || !addressForm.city.trim() || !addressForm.state.trim() || !addressForm.postalCode.trim() || !addressForm.countryCode.trim()) {
      toast.error("Fill all required address fields");
      return;
    }

    setSavingAddress(true);
    setStatus(addressForm.id ? "Updating address..." : "Adding address...");
    try {
      const payload = {
        label: addressForm.label.trim() || null,
        recipientName: addressForm.recipientName.trim(),
        phone: addressForm.phone.trim(),
        line1: addressForm.line1.trim(),
        line2: addressForm.line2.trim() || null,
        city: addressForm.city.trim(),
        state: addressForm.state.trim(),
        postalCode: addressForm.postalCode.trim(),
        countryCode: addressForm.countryCode.trim().toUpperCase(),
        defaultShipping: addressForm.defaultShipping,
        defaultBilling: addressForm.defaultBilling,
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
      setStatus(message);
      toast.error(message);
    } finally {
      setSavingAddress(false);
    }
  };

  const deleteAddress = async (addressId: string) => {
    if (!apiClient || deletingAddressId) return;
    setDeletingAddressId(addressId);
    setStatus("Deleting address...");
    try {
      await apiClient.delete(`/customers/me/addresses/${addressId}`);
      toast.success("Address deleted");
      if (addressForm.id === addressId) {
        resetAddressForm();
      }
      await loadAddresses();
      setStatus("Address deleted.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete address";
      setStatus(message);
      toast.error(message);
    } finally {
      setDeletingAddressId(null);
    }
  };

  const setDefaultAddress = async (addressId: string, type: "shipping" | "billing") => {
    if (!apiClient || settingDefaultAddressId) return;
    setSettingDefaultAddressId(addressId);
    setStatus(type === "shipping" ? "Setting default shipping address..." : "Setting default billing address...");
    try {
      const suffix = type === "shipping" ? "default-shipping" : "default-billing";
      await apiClient.post(`/customers/me/addresses/${addressId}/${suffix}`);
      toast.success(type === "shipping" ? "Default shipping address updated" : "Default billing address updated");
      await loadAddresses();
      setStatus("Address defaults updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to set default address";
      setStatus(message);
      toast.error(message);
    } finally {
      setSettingDefaultAddressId(null);
    }
  };

  useEffect(() => {
    const resetPasswordAction = () => {
      setPasswordActionPending(false);
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        resetPasswordAction();
      }
    };

    window.addEventListener("pageshow", resetPasswordAction);
    window.addEventListener("focus", resetPasswordAction);
    document.addEventListener("visibilitychange", onVisibilityChange);

    return () => {
      window.removeEventListener("pageshow", resetPasswordAction);
      window.removeEventListener("focus", resetPasswordAction);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) {
      router.replace("/");
      return;
    }
    if (canViewAdmin) {
      return;
    }

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
    setStatus("Requesting verification email...");
    try {
      await resendVerificationEmail();
      setStatus("Verification email sent. Please verify and sign in again.");
      toast.success("Verification email sent");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to resend verification email.";
      setStatus(message);
      toast.error(message);
    } finally {
      setResendingVerification(false);
    }
  };

  const saveProfile = async () => {
    if (!apiClient || !customer || savingProfile) return;

    const normalizedFirstName = editFirstName.trim();
    const normalizedLastName = editLastName.trim();
    if (!normalizedFirstName || !normalizedLastName) {
      toast.error("First name and last name are required");
      return;
    }
    if (
      normalizedFirstName === initialNameParts.firstName
      && normalizedLastName === initialNameParts.lastName
    ) {
      setStatus("No changes to save.");
      return;
    }

    setSavingProfile(true);
    setStatus("Saving profile...");
    try {
      const response = await apiClient.put("/customers/me", {
        firstName: normalizedFirstName,
        lastName: normalizedLastName,
      });
      const updated = response.data as Customer;
      setCustomer(updated);
      const updatedParts = splitDisplayName(updated.name || `${normalizedFirstName} ${normalizedLastName}`);
      setEditFirstName(updatedParts.firstName);
      setEditLastName(updatedParts.lastName);
      setStatus("Profile updated.");
      toast.success("Profile updated");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to update profile";
      setStatus(message);
      toast.error(message);
    } finally {
      setSavingProfile(false);
    }
  };

  const startChangePassword = async () => {
    if (passwordActionPending) return;
    setPasswordActionPending(true);
    try {
      await changePassword("/profile");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to open change password flow";
      setStatus(message);
      toast.error(message);
    } finally {
      setPasswordActionPending(false);
    }
  };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
          <p className="mt-4">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      <AppNav
        email={(profile?.email as string) || ""}
        canViewAdmin={canViewAdmin}
        onLogout={() => {
          void logout();
        }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">My Profile</span>
        </nav>

        {emailVerified === false && (
          <section className="mb-4 flex items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <span className="text-lg font-semibold">!</span>
            <div className="flex-1">
              <p className="font-semibold">Email Not Verified</p>
              <p className="text-xs">Profile and order actions are blocked until verification.</p>
            </div>
            <button
              onClick={() => {
                void resendVerification();
              }}
              disabled={resendingVerification}
              className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-amber-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {resendingVerification ? "Sending..." : "Resend Email"}
            </button>
          </section>
        )}

        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-[var(--ink)]">My Profile</h1>
            <p className="mt-0.5 text-sm text-[var(--muted)]">Manage your account and view your details.</p>
          </div>
          <div className="flex gap-2">
            <Link href="/products" className="btn-primary no-underline px-4 py-2.5 text-sm">
              Shop
            </Link>
            <Link href="/orders" className="btn-outline no-underline px-4 py-2.5 text-sm">
              Orders
            </Link>
            <button
              onClick={() => {
                void startChangePassword();
              }}
              disabled={passwordActionPending}
              className="btn-outline px-4 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
            >
              {passwordActionPending ? "Redirecting..." : "Change Password"}
            </button>
          </div>
        </div>

        <div className="grid gap-5 md:grid-cols-2">
          <article className="animate-rise rounded-xl bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--brand-soft)] text-xl font-semibold">
                P
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-[var(--muted)]">Customer Profile</p>
                <p className="text-sm font-semibold text-[var(--ink)]">
                  {canViewAdmin ? "Admin Account" : customer?.name || "Loading..."}
                </p>
              </div>
            </div>

            {!canViewAdmin && (
              <div className="space-y-4">
                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">First Name</p>
                  <div className="mt-2 flex flex-col gap-2">
                    <input
                      value={editFirstName}
                      onChange={(event) => setEditFirstName(event.target.value)}
                      disabled={savingProfile || emailVerified === false}
                      className="w-full rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm text-[var(--ink)] outline-none focus:border-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-60"
                      placeholder="Enter first name"
                    />
                  </div>
                </div>

                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Last Name</p>
                  <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                    <input
                      value={editLastName}
                      onChange={(event) => setEditLastName(event.target.value)}
                      disabled={savingProfile || emailVerified === false}
                      className="w-full rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm text-[var(--ink)] outline-none focus:border-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-60"
                      placeholder="Enter last name"
                    />
                    <button
                      onClick={() => {
                        void saveProfile();
                      }}
                      disabled={
                        savingProfile
                        || emailVerified === false
                        || !customer
                        || !editFirstName.trim()
                        || !editLastName.trim()
                        || (
                          editFirstName.trim() === initialNameParts.firstName
                          && editLastName.trim() === initialNameParts.lastName
                        )
                      }
                      className="rounded-lg bg-[var(--brand)] px-3 py-2 text-xs font-semibold text-white hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {savingProfile ? "Saving..." : "Save"}
                    </button>
                  </div>
                </div>

                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Email</p>
                  <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{customer?.email || "-"}</p>
                </div>

                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Customer ID</p>
                  <p className="mt-0.5 break-all font-mono text-xs text-[var(--ink)]">{customer?.id || "-"}</p>
                </div>

                <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                  <p className="text-xs text-[var(--muted)]">Member Since</p>
                  <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">
                    {customer?.createdAt
                      ? new Date(customer.createdAt).toLocaleDateString("en-US", {
                          year: "numeric",
                          month: "long",
                          day: "numeric",
                        })
                      : "-"}
                  </p>
                </div>
              </div>
            )}

            {canViewAdmin && (
              <div className="rounded-lg bg-blue-50 px-4 py-3 text-sm text-blue-700">
                <p className="font-semibold">Admin Account Detected</p>
                <p className="mt-1 text-xs">Customer profile bootstrap is not required for admin operations.</p>
              </div>
            )}
          </article>

          <article className="animate-rise rounded-xl bg-white p-6 shadow-sm" style={{ animationDelay: "100ms" }}>
            <div className="mb-4 flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--accent-soft)] text-xl font-semibold">
                S
              </div>
              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-[var(--muted)]">Session Info</p>
                <p className="text-sm font-semibold text-[var(--ink)]">Authentication Details</p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Auth Email</p>
                <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{(profile?.email as string) || "-"}</p>
              </div>

              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Auth Name</p>
                <p className="mt-0.5 text-sm font-semibold text-[var(--ink)]">{(profile?.name as string) || "-"}</p>
              </div>

              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Role</p>
                <p className="mt-0.5">
                  <span
                    className={`rounded-full px-3 py-1 text-xs font-bold ${
                      canViewAdmin ? "bg-purple-100 text-purple-700" : "bg-green-100 text-green-700"
                    }`}
                  >
                    {canViewAdmin ? "Admin" : "Customer"}
                  </span>
                </p>
              </div>

              <div className="rounded-lg bg-[#fafafa] px-4 py-3">
                <p className="text-xs text-[var(--muted)]">Email Verified</p>
                <p className="mt-0.5">
                  <span
                    className={`rounded-full px-3 py-1 text-xs font-bold ${
                      emailVerified ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
                    }`}
                  >
                    {emailVerified ? "Verified" : "Not Verified"}
                  </span>
                </p>
              </div>
            </div>
          </article>
        </div>

        {!canViewAdmin && (
          <section className="mt-5 rounded-xl bg-white p-6 shadow-sm">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-xl font-bold text-[var(--ink)]">Address Book</h2>
                <p className="text-xs text-[var(--muted)]">
                  Manage shipping and billing addresses. Deleted addresses are soft-deleted for order history safety.
                </p>
              </div>
              <span className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs font-semibold text-white">
                {addresses.length} active
              </span>
            </div>

            <div className="grid gap-4 lg:grid-cols-[0.9fr,1.1fr]">
              <div className="rounded-lg border border-[var(--line)] p-4">
                <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">
                  {addressForm.id ? "Edit Address" : "Add Address"}
                </p>
                <div className="grid gap-2 text-sm">
                  <input
                    value={addressForm.label}
                    onChange={(e) => setAddressForm((old) => ({ ...old, label: e.target.value }))}
                    placeholder="Label (Home, Office)"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={savingAddress || emailVerified === false}
                  />
                  <input
                    value={addressForm.recipientName}
                    onChange={(e) => setAddressForm((old) => ({ ...old, recipientName: e.target.value }))}
                    placeholder="Recipient name"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={savingAddress || emailVerified === false}
                    required
                  />
                  <input
                    value={addressForm.phone}
                    onChange={(e) => setAddressForm((old) => ({ ...old, phone: e.target.value }))}
                    placeholder="Phone number"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={savingAddress || emailVerified === false}
                    required
                  />
                  <input
                    value={addressForm.line1}
                    onChange={(e) => setAddressForm((old) => ({ ...old, line1: e.target.value }))}
                    placeholder="Address line 1"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={savingAddress || emailVerified === false}
                    required
                  />
                  <input
                    value={addressForm.line2}
                    onChange={(e) => setAddressForm((old) => ({ ...old, line2: e.target.value }))}
                    placeholder="Address line 2 (optional)"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={savingAddress || emailVerified === false}
                  />
                  <div className="grid gap-2 sm:grid-cols-2">
                    <input
                      value={addressForm.city}
                      onChange={(e) => setAddressForm((old) => ({ ...old, city: e.target.value }))}
                      placeholder="City"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={savingAddress || emailVerified === false}
                      required
                    />
                    <input
                      value={addressForm.state}
                      onChange={(e) => setAddressForm((old) => ({ ...old, state: e.target.value }))}
                      placeholder="State"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={savingAddress || emailVerified === false}
                      required
                    />
                  </div>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <input
                      value={addressForm.postalCode}
                      onChange={(e) => setAddressForm((old) => ({ ...old, postalCode: e.target.value }))}
                      placeholder="Postal code"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={savingAddress || emailVerified === false}
                      required
                    />
                    <input
                      value={addressForm.countryCode}
                      onChange={(e) => setAddressForm((old) => ({ ...old, countryCode: e.target.value.toUpperCase() }))}
                      placeholder="Country code (US)"
                      maxLength={2}
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={savingAddress || emailVerified === false}
                      required
                    />
                  </div>
                  <div className="flex flex-wrap gap-3 pt-1 text-xs text-[var(--muted)]">
                    <label className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={addressForm.defaultShipping}
                        onChange={(e) => setAddressForm((old) => ({ ...old, defaultShipping: e.target.checked }))}
                        disabled={savingAddress || emailVerified === false}
                      />
                      Set as default shipping
                    </label>
                    <label className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={addressForm.defaultBilling}
                        onChange={(e) => setAddressForm((old) => ({ ...old, defaultBilling: e.target.checked }))}
                        disabled={savingAddress || emailVerified === false}
                      />
                      Set as default billing
                    </label>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-2">
                    <button
                      onClick={() => { void saveAddress(); }}
                      disabled={savingAddress || emailVerified === false}
                      className="btn-primary px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {savingAddress ? "Saving..." : addressForm.id ? "Update Address" : "Add Address"}
                    </button>
                    {addressForm.id && (
                      <button
                        onClick={resetAddressForm}
                        disabled={savingAddress}
                        className="btn-outline px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        Cancel Edit
                      </button>
                    )}
                  </div>
                </div>
              </div>

              <div className="rounded-lg border border-[var(--line)] p-4">
                <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-[var(--muted)]">Saved Addresses</p>
                {addressLoading && (
                  <p className="text-sm text-[var(--muted)]">Loading addresses...</p>
                )}
                {!addressLoading && addresses.length === 0 && (
                  <p className="text-sm text-[var(--muted)]">No addresses added yet.</p>
                )}
                <div className="grid gap-3">
                  {addresses.map((address) => (
                    <article key={address.id} className="rounded-lg border border-[var(--line)] bg-[#fafafa] p-3">
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <p className="text-sm font-semibold text-[var(--ink)]">
                            {address.label ? `${address.label} - ` : ""}{address.recipientName}
                          </p>
                          <p className="text-xs text-[var(--muted)]">{address.phone}</p>
                          <p className="mt-1 text-xs text-[var(--ink)]">
                            {address.line1}{address.line2 ? `, ${address.line2}` : ""}, {address.city}, {address.state} {address.postalCode}, {address.countryCode}
                          </p>
                          <div className="mt-2 flex flex-wrap gap-1">
                            {address.defaultShipping && (
                              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                                Default Shipping
                              </span>
                            )}
                            {address.defaultBilling && (
                              <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-semibold text-blue-700">
                                Default Billing
                              </span>
                            )}
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-1">
                          <button
                            onClick={() => startEditAddress(address)}
                            disabled={savingAddress || emailVerified === false}
                            className="rounded border border-[var(--line)] bg-white px-2 py-1 text-[10px] disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => { void setDefaultAddress(address.id, "shipping"); }}
                            disabled={settingDefaultAddressId !== null || savingAddress || emailVerified === false}
                            className="rounded border border-emerald-200 bg-emerald-50 px-2 py-1 text-[10px] text-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {settingDefaultAddressId === address.id ? "Saving..." : "Set Shipping"}
                          </button>
                          <button
                            onClick={() => { void setDefaultAddress(address.id, "billing"); }}
                            disabled={settingDefaultAddressId !== null || savingAddress || emailVerified === false}
                            className="rounded border border-blue-200 bg-blue-50 px-2 py-1 text-[10px] text-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {settingDefaultAddressId === address.id ? "Saving..." : "Set Billing"}
                          </button>
                          <button
                            onClick={() => { void deleteAddress(address.id); }}
                            disabled={deletingAddressId !== null || savingAddress || emailVerified === false}
                            className="rounded border border-red-200 bg-red-50 px-2 py-1 text-[10px] text-red-700 disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {deletingAddressId === address.id ? "Deleting..." : "Delete"}
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              </div>
            </div>
          </section>
        )}

        <p className="mt-5 text-xs text-[var(--muted)]">{canViewAdmin ? "Admin account detected." : status}</p>
      </main>

      <Footer />
    </div>
  );
}
