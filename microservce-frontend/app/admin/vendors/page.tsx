"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

type VendorStatus = "PENDING" | "ACTIVE" | "SUSPENDED";
type VendorUserRole = "OWNER" | "MANAGER";
type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

type Vendor = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  contactPersonName: string | null;
  status: VendorStatus;
  active: boolean;
  deleted: boolean;
};

type VendorUser = {
  id: string;
  vendorId: string;
  keycloakUserId: string;
  email: string;
  displayName: string | null;
  role: VendorUserRole;
  active: boolean;
};

type VendorForm = {
  id?: string;
  name: string;
  slug: string;
  contactEmail: string;
  contactPersonName: string;
  status: VendorStatus;
  active: boolean;
};

type OnboardForm = {
  keycloakUserId: string;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  vendorUserRole: VendorUserRole;
  createIfMissing: boolean;
};

const emptyVendorForm: VendorForm = {
  name: "",
  slug: "",
  contactEmail: "",
  contactPersonName: "",
  status: "PENDING",
  active: true,
};

const emptyOnboardForm: OnboardForm = {
  keycloakUserId: "",
  email: "",
  firstName: "",
  lastName: "",
  displayName: "",
  vendorUserRole: "OWNER",
  createIfMissing: true,
};

function slugify(value: string) {
  return value.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}

function splitName(fullName: string) {
  const normalized = fullName.trim().replace(/\s+/g, " ");
  if (!normalized) return { firstName: "", lastName: "" };
  const i = normalized.indexOf(" ");
  if (i < 0) return { firstName: normalized, lastName: "" };
  return { firstName: normalized.slice(0, i).trim(), lastName: normalized.slice(i + 1).trim() };
}

function getApiErrorMessage(err: unknown, fallback: string) {
  if (typeof err === "object" && err !== null) {
    const maybe = err as { message?: string; response?: { data?: { message?: string; error?: string } | string } };
    const data = maybe.response?.data;
    if (typeof data === "string" && data.trim()) return data.trim();
    if (data && typeof data === "object") {
      if (typeof data.message === "string" && data.message.trim()) return data.message.trim();
      if (typeof data.error === "string" && data.error.trim()) return data.error.trim();
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) return maybe.message.trim();
  }
  return fallback;
}

export default function AdminVendorsPage() {
  const router = useRouter();
  const session = useAuthSession();

  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState("Loading vendors...");
  const [form, setForm] = useState<VendorForm>(emptyVendorForm);
  const [slugEdited, setSlugEdited] = useState(false);
  const [slugStatus, setSlugStatus] = useState<SlugStatus>("idle");
  const [savingVendor, setSavingVendor] = useState(false);
  const [selectedVendorId, setSelectedVendorId] = useState("");
  const [vendorUsers, setVendorUsers] = useState<VendorUser[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [onboardForm, setOnboardForm] = useState<OnboardForm>(emptyOnboardForm);
  const [onboarding, setOnboarding] = useState(false);
  const [onboardStatus, setOnboardStatus] = useState("Select a vendor to onboard a vendor admin.");

  const selectedVendor = useMemo(() => vendors.find((v) => v.id === selectedVendorId) || null, [vendors, selectedVendorId]);

  const loadVendors = async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const res = await session.apiClient.get("/admin/vendors");
      setVendors((((res.data as Vendor[]) || []).filter((v) => !v.deleted)).sort((a, b) => a.name.localeCompare(b.name)));
      setStatus("Vendors loaded.");
    } catch (err) {
      setStatus(getApiErrorMessage(err, "Failed to load vendors."));
    } finally {
      setLoading(false);
    }
  };

  const loadVendorUsers = async (vendorId: string) => {
    if (!session.apiClient || !vendorId) return;
    setLoadingUsers(true);
    try {
      const res = await session.apiClient.get(`/admin/vendors/${vendorId}/users`);
      setVendorUsers((res.data as VendorUser[]) || []);
      setOnboardStatus("Vendor users loaded.");
    } catch (err) {
      setVendorUsers([]);
      setOnboardStatus(getApiErrorMessage(err, "Failed to load vendor users."));
    } finally {
      setLoadingUsers(false);
    }
  };

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.isSuperAdmin) {
      router.replace("/products");
      return;
    }
    void loadVendors();
  }, [session.status, session.isAuthenticated, session.isSuperAdmin, router]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (slugEdited) return;
    setForm((old) => ({ ...old, slug: slugify(old.name).slice(0, 180) }));
  }, [form.name, slugEdited]);

  useEffect(() => {
    if (!session.apiClient) return;
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
        const res = await session.apiClient!.get(`/vendors/slug-available?${params.toString()}`);
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
  }, [form.slug, form.id, session.apiClient]);

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
    void loadVendorUsers(vendor.id);
  };

  const handleEditVendor = (vendor: Vendor) => {
    setForm({
      id: vendor.id,
      name: vendor.name,
      slug: vendor.slug || "",
      contactEmail: vendor.contactEmail || "",
      contactPersonName: vendor.contactPersonName || "",
      status: vendor.status,
      active: vendor.active,
    });
    setSlugEdited(true);
    setSlugStatus("available");
    handleSelectVendor(vendor);
    toast.success("Vendor loaded into form");
  };

  const saveVendor = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient || savingVendor) return;
    const normalizedSlug = slugify(form.slug).slice(0, 180);
    if (!normalizedSlug) return toast.error("Vendor slug is required");
    if (slugStatus === "checking") return toast.error("Wait until slug check completes");
    if (slugStatus === "taken" || slugStatus === "invalid") return toast.error("Use a unique valid vendor slug");

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
      };
      const res = form.id
        ? await session.apiClient.put(`/admin/vendors/${form.id}`, payload)
        : await session.apiClient.post("/admin/vendors", payload);
      const saved = res.data as Vendor;
      await loadVendors();
      setSelectedVendorId(saved.id);
      await loadVendorUsers(saved.id);
      fillOnboardFromVendor(saved);
      setForm(emptyVendorForm);
      setSlugEdited(false);
      setSlugStatus("idle");
      setStatus(form.id ? "Vendor updated." : "Vendor created.");
      toast.success(form.id ? "Vendor updated" : "Vendor created");
    } catch (err) {
      const message = getApiErrorMessage(err, "Failed to save vendor.");
      setStatus(message);
      toast.error(message);
    } finally {
      setSavingVendor(false);
    }
  };

  const onboardVendorAdmin = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient || onboarding) return;
    if (!selectedVendorId) return toast.error("Select a vendor first");
    setOnboarding(true);
    try {
      await session.apiClient.post(`/admin/vendors/${selectedVendorId}/users/onboard`, {
        keycloakUserId: onboardForm.keycloakUserId.trim() || null,
        email: onboardForm.email.trim(),
        firstName: onboardForm.firstName.trim() || null,
        lastName: onboardForm.lastName.trim() || null,
        displayName: onboardForm.displayName.trim() || null,
        vendorUserRole: onboardForm.vendorUserRole,
        createIfMissing: onboardForm.createIfMissing,
      });
      await loadVendorUsers(selectedVendorId);
      setOnboardStatus("Vendor admin onboarded.");
      setOnboardForm((old) => ({ ...emptyOnboardForm, vendorUserRole: old.vendorUserRole, createIfMissing: old.createIfMissing }));
      toast.success("Vendor admin onboarded");
    } catch (err) {
      const message = getApiErrorMessage(err, "Failed to onboard vendor admin.");
      setOnboardStatus(message);
      toast.error(message);
    } finally {
      setOnboarding(false);
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)" }} />;
  }
  if (!session.isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <Link href="/admin/orders">Admin</Link>
          <span className="breadcrumb-sep">{">"}</span>
          <span className="breadcrumb-current">Vendors</span>
        </nav>

        <section className="animate-rise space-y-4 rounded-xl p-5" style={{ background: "rgba(17,17,40,0.7)", border: "1px solid rgba(0,212,255,0.1)", backdropFilter: "blur(16px)" }}>
          <div className="flex items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN VENDORS</p>
              <h1 className="text-2xl font-bold text-[var(--ink)]">Vendor Setup & Onboarding</h1>
              <p className="mt-1 text-xs text-[var(--muted)]">Create vendors and onboard vendor admins without calling APIs manually.</p>
            </div>
            <button
              type="button"
              onClick={() => void loadVendors()}
              disabled={loading}
              className="rounded-md border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
              style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
            >
              {loading ? "Refreshing..." : "Refresh Vendors"}
            </button>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.05fr,0.95fr]">
            <div className="space-y-4">
              <section className="card-surface rounded-2xl p-5">
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-2xl text-[var(--ink)]">{form.id ? "Update Vendor" : "Create Vendor"}</h2>
                  <button
                    type="button"
                    onClick={() => { setForm(emptyVendorForm); setSlugEdited(false); setSlugStatus("idle"); }}
                    disabled={savingVendor}
                    className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                    style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                  >
                    Reset
                  </button>
                </div>

                <form onSubmit={(e) => { void saveVendor(e); }} className="grid gap-3 text-sm">
                  <div className="form-group">
                    <label className="form-label">Vendor Name</label>
                    <input value={form.name} onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" placeholder="e.g. ElectroHub" required disabled={savingVendor} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Slug</label>
                    <input value={form.slug} onChange={(e) => { setSlugEdited(true); setForm((s) => ({ ...s, slug: e.target.value })); }} className="rounded-lg border border-[var(--line)] px-3 py-2" placeholder="electrohub" required disabled={savingVendor} />
                    <p className={`mt-1 text-[11px] ${slugStatus === "taken" || slugStatus === "invalid" ? "text-red-600" : slugStatus === "available" ? "text-emerald-600" : "text-[var(--muted)]"}`}>
                      {slugStatus === "checking" ? "Checking slug..." : slugStatus === "available" ? "Slug is available" : slugStatus === "taken" ? "Slug is already taken" : slugStatus === "invalid" ? "Enter a valid slug" : "Vendor URL slug"}
                    </p>
                  </div>
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                    <div className="form-group">
                      <label className="form-label">Contact Email</label>
                      <input type="email" value={form.contactEmail} onChange={(e) => setForm((s) => ({ ...s, contactEmail: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" placeholder="owner@vendor.com" required disabled={savingVendor} />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Contact Person</label>
                      <input value={form.contactPersonName} onChange={(e) => setForm((s) => ({ ...s, contactPersonName: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" placeholder="Vendor Owner" disabled={savingVendor} />
                    </div>
                  </div>
                  <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                    <div className="form-group">
                      <label className="form-label">Status</label>
                      <select value={form.status} onChange={(e) => setForm((s) => ({ ...s, status: e.target.value as VendorStatus }))} className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={savingVendor}>
                        <option value="PENDING">PENDING</option>
                        <option value="ACTIVE">ACTIVE</option>
                        <option value="SUSPENDED">SUSPENDED</option>
                      </select>
                    </div>
                    <label className="mt-6 flex items-center gap-2 text-xs text-[var(--muted)]">
                      <input type="checkbox" checked={form.active} onChange={(e) => setForm((s) => ({ ...s, active: e.target.checked }))} disabled={savingVendor} />
                      Vendor active
                    </label>
                  </div>
                  <button type="submit" disabled={savingVendor || slugStatus === "checking" || slugStatus === "taken" || slugStatus === "invalid"} className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50">
                    {savingVendor ? "Saving..." : form.id ? "Update Vendor" : "Create Vendor"}
                  </button>
                </form>
              </section>

              <section className="card-surface rounded-2xl p-5">
                <h2 className="mb-3 text-2xl text-[var(--ink)]">Vendors</h2>
                <div className="overflow-hidden rounded-2xl border border-[var(--line)]" style={{ background: "var(--surface)" }}>
                  <table className="w-full text-left text-sm">
                    <thead style={{ background: "var(--surface-2)", color: "var(--ink)" }}>
                      <tr>
                        <th className="px-3 py-2">Vendor</th>
                        <th className="px-3 py-2">Contact</th>
                        <th className="px-3 py-2">Status</th>
                        <th className="px-3 py-2">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {vendors.length === 0 && (
                        <tr><td colSpan={4} className="px-3 py-5 text-center text-sm text-[var(--muted)]">No vendors found.</td></tr>
                      )}
                      {vendors.map((vendor) => (
                        <tr key={vendor.id} className="border-t border-[var(--line)]">
                          <td className="px-3 py-2">
                            <p className="font-semibold text-[var(--ink)]">{vendor.name}</p>
                            <p className="text-xs text-[var(--muted)]">{vendor.slug}</p>
                          </td>
                          <td className="px-3 py-2 text-xs text-[var(--muted)]">
                            <div>{vendor.contactEmail}</div>
                            {vendor.contactPersonName && <div>{vendor.contactPersonName}</div>}
                          </td>
                          <td className="px-3 py-2 text-xs text-[var(--muted)]">{vendor.status} • {vendor.active ? "active" : "inactive"}</td>
                          <td className="px-3 py-2">
                            <div className="flex flex-wrap gap-2">
                              <button type="button" onClick={() => handleEditVendor(vendor)} className="rounded-md border border-[var(--line)] px-2 py-1 text-xs" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}>Edit</button>
                              <button type="button" onClick={() => handleSelectVendor(vendor)} className="rounded-md border border-[var(--line)] px-2 py-1 text-xs" style={{ background: selectedVendorId === vendor.id ? "var(--brand-soft)" : "var(--surface-2)", color: selectedVendorId === vendor.id ? "var(--brand)" : "var(--ink-light)" }}>
                                {selectedVendorId === vendor.id ? "Selected" : "Users"}
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>

            <section className="card-surface rounded-2xl p-5">
              <div className="mb-3 flex items-center justify-between gap-2">
                <div>
                  <h2 className="text-2xl text-[var(--ink)]">Vendor Admin Onboarding</h2>
                  <p className="mt-1 text-xs text-[var(--muted)]">Select a vendor and onboard an OWNER or MANAGER.</p>
                </div>
                {selectedVendor && (
                  <button type="button" onClick={() => fillOnboardFromVendor(selectedVendor)} className="rounded-md border border-[var(--line)] px-2 py-1 text-xs" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}>
                    Use Vendor Info
                  </button>
                )}
              </div>

              <div className="mb-3 rounded-lg border border-[var(--line)] p-3">
                <label className="form-label">Selected Vendor</label>
                <select
                  value={selectedVendorId}
                  onChange={(e) => {
                    const id = e.target.value;
                    setSelectedVendorId(id);
                    const vendor = vendors.find((v) => v.id === id);
                    if (vendor) {
                      fillOnboardFromVendor(vendor);
                      void loadVendorUsers(id);
                    } else {
                      setVendorUsers([]);
                      setOnboardStatus("Select a vendor to onboard a vendor admin.");
                    }
                  }}
                  className="w-full rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
                  disabled={onboarding}
                >
                  <option value="">Select Vendor</option>
                  {vendors.map((vendor) => (
                    <option key={vendor.id} value={vendor.id}>{vendor.name} ({vendor.slug})</option>
                  ))}
                </select>
                {selectedVendor && (
                  <p className="mt-2 text-[11px] text-[var(--muted)]">{selectedVendor.contactEmail} • {selectedVendor.id}</p>
                )}
              </div>

              <form onSubmit={(e) => { void onboardVendorAdmin(e); }} className="grid gap-3 text-sm">
                <div className="form-group">
                  <label className="form-label">Admin Email</label>
                  <input type="email" value={onboardForm.email} onChange={(e) => setOnboardForm((s) => ({ ...s, email: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" placeholder="vendor.admin@example.com" required disabled={onboarding} />
                </div>
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">First Name</label>
                    <input value={onboardForm.firstName} onChange={(e) => setOnboardForm((s) => ({ ...s, firstName: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={onboarding} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Last Name</label>
                    <input value={onboardForm.lastName} onChange={(e) => setOnboardForm((s) => ({ ...s, lastName: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={onboarding} />
                  </div>
                </div>
                <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">Display Name (optional)</label>
                    <input value={onboardForm.displayName} onChange={(e) => setOnboardForm((s) => ({ ...s, displayName: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={onboarding} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Vendor Role</label>
                    <select value={onboardForm.vendorUserRole} onChange={(e) => setOnboardForm((s) => ({ ...s, vendorUserRole: e.target.value as VendorUserRole }))} className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={onboarding}>
                      <option value="OWNER">OWNER</option>
                      <option value="MANAGER">MANAGER</option>
                    </select>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Keycloak User ID (optional)</label>
                  <input value={onboardForm.keycloakUserId} onChange={(e) => setOnboardForm((s) => ({ ...s, keycloakUserId: e.target.value }))} className="rounded-lg border border-[var(--line)] px-3 py-2 font-mono text-xs" placeholder="Link existing Keycloak user directly" disabled={onboarding} />
                </div>
                <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
                  <input type="checkbox" checked={onboardForm.createIfMissing} onChange={(e) => setOnboardForm((s) => ({ ...s, createIfMissing: e.target.checked }))} disabled={onboarding} />
                  Create Keycloak user if email is not found
                </label>
                <button type="submit" disabled={onboarding || !selectedVendorId} className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50">
                  {onboarding ? "Onboarding..." : "Onboard Vendor Admin"}
                </button>
              </form>

              <p className="mt-3 text-xs text-[var(--muted)]">{onboardStatus}</p>

              <div className="mt-4 rounded-lg border border-[var(--line)] p-3">
                <div className="mb-2 flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-[var(--ink)]">Vendor Users</h3>
                  <button
                    type="button"
                    onClick={() => { if (selectedVendorId) void loadVendorUsers(selectedVendorId); }}
                    disabled={!selectedVendorId || loadingUsers}
                    className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                    style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                  >
                    {loadingUsers ? "Loading..." : "Refresh"}
                  </button>
                </div>
                {vendorUsers.length === 0 ? (
                  <p className="text-xs text-[var(--muted)]">{selectedVendorId ? "No vendor users yet." : "Select a vendor to view users."}</p>
                ) : (
                  <div className="space-y-2">
                    {vendorUsers.map((user) => (
                      <div key={user.id} className="rounded-md border border-[var(--line)] p-2 text-xs">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="font-semibold text-[var(--ink)]">{user.displayName || user.email}</p>
                          <span className="rounded px-2 py-0.5 text-[10px]" style={{ background: "rgba(255,255,255,0.04)", color: "var(--ink-light)", border: "1px solid rgba(255,255,255,0.08)" }}>{user.role}</span>
                        </div>
                        <p className="mt-1 text-[var(--muted)]">{user.email}</p>
                        <p className="mt-1 font-mono text-[10px] text-[var(--muted)]">{user.keycloakUserId}</p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </section>
          </div>

          <p className="text-xs text-[var(--muted)]">{status}</p>
        </section>
      </main>

      <Footer />
    </div>
  );
}
