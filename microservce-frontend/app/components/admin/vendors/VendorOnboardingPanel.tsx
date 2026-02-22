"use client";

import type { OnboardForm, Vendor, VendorUser, VendorUserRole } from "./types";
import VendorUsersList from "./VendorUsersList";

type VendorOnboardingPanelProps = {
  vendors: Vendor[];
  selectedVendorId: string;
  selectedVendor: Vendor | null;
  onboardForm: OnboardForm;
  onboarding: boolean;
  onboardStatus: string;
  loadingUsers: boolean;
  vendorUsers: VendorUser[];
  removingMembershipId?: string | null;
  onSelectVendorId: (vendorId: string) => void;
  onFillFromVendor: () => void;
  onChangeOnboardForm: (updater: (prev: OnboardForm) => OnboardForm) => void;
  onSubmit: () => void;
  onRefreshUsers: () => void;
  onRemoveUser: (user: VendorUser) => void;
};

export default function VendorOnboardingPanel({
  vendors,
  selectedVendorId,
  selectedVendor,
  onboardForm,
  onboarding,
  onboardStatus,
  loadingUsers,
  vendorUsers,
  removingMembershipId = null,
  onSelectVendorId,
  onFillFromVendor,
  onChangeOnboardForm,
  onSubmit,
  onRefreshUsers,
  onRemoveUser,
}: VendorOnboardingPanelProps) {
  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div>
          <h2 className="text-2xl text-[var(--ink)]">Vendor Admin Onboarding</h2>
          <p className="mt-1 text-xs text-[var(--muted)]">Select a vendor and onboard an OWNER or MANAGER.</p>
        </div>
        {selectedVendor && (
          <button
            type="button"
            onClick={onFillFromVendor}
            className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
            style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
          >
            Use Vendor Info
          </button>
        )}
      </div>

      <div className="mb-3 rounded-lg border border-[var(--line)] p-3">
        <label className="form-label">Selected Vendor</label>
        <select
          value={selectedVendorId}
          onChange={(e) => onSelectVendorId(e.target.value)}
          className="w-full rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
          disabled={onboarding}
        >
          <option value="">Select Vendor</option>
          {vendors.map((vendor) => (
            <option key={vendor.id} value={vendor.id}>
              {vendor.name} ({vendor.slug})
            </option>
          ))}
        </select>
        {selectedVendor && (
          <p className="mt-2 text-[11px] text-[var(--muted)]">
            {selectedVendor.contactEmail} • {selectedVendor.id}
          </p>
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit();
        }}
        className="grid gap-3 text-sm"
      >
        <div className="form-group">
          <label className="form-label">Admin Email</label>
          <input
            type="email"
            value={onboardForm.email}
            onChange={(e) => onChangeOnboardForm((s) => ({ ...s, email: e.target.value }))}
            className="rounded-lg border border-[var(--line)] px-3 py-2"
            placeholder="vendor.admin@example.com"
            required
            disabled={onboarding}
          />
        </div>

        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          <div className="form-group">
            <label className="form-label">First Name</label>
            <input
              value={onboardForm.firstName}
              onChange={(e) => onChangeOnboardForm((s) => ({ ...s, firstName: e.target.value }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              disabled={onboarding}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Last Name</label>
            <input
              value={onboardForm.lastName}
              onChange={(e) => onChangeOnboardForm((s) => ({ ...s, lastName: e.target.value }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              disabled={onboarding}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          <div className="form-group">
            <label className="form-label">Display Name (optional)</label>
            <input
              value={onboardForm.displayName}
              onChange={(e) => onChangeOnboardForm((s) => ({ ...s, displayName: e.target.value }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              disabled={onboarding}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Vendor Role</label>
            <select
              value={onboardForm.vendorUserRole}
              onChange={(e) =>
                onChangeOnboardForm((s) => ({ ...s, vendorUserRole: e.target.value as VendorUserRole }))
              }
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              disabled={onboarding}
            >
              <option value="OWNER">OWNER</option>
              <option value="MANAGER">MANAGER</option>
            </select>
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Keycloak User ID (optional)</label>
          <input
            value={onboardForm.keycloakUserId}
            onChange={(e) => onChangeOnboardForm((s) => ({ ...s, keycloakUserId: e.target.value }))}
            className="rounded-lg border border-[var(--line)] px-3 py-2 font-mono text-xs"
            placeholder="Link existing Keycloak user directly"
            disabled={onboarding}
          />
        </div>

        <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
          <input
            type="checkbox"
            checked={onboardForm.createIfMissing}
            onChange={(e) => onChangeOnboardForm((s) => ({ ...s, createIfMissing: e.target.checked }))}
            disabled={onboarding}
          />
          Create Keycloak user if email is not found (sends verify/password setup email)
        </label>

        <button
          type="submit"
          disabled={onboarding || !selectedVendorId}
          className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {onboarding ? "Onboarding..." : "Onboard Vendor Admin"}
        </button>
      </form>

      <p className="mt-3 text-xs text-[var(--muted)]">{onboardStatus}</p>

      <VendorUsersList
        vendorUsers={vendorUsers}
        selectedVendorId={selectedVendorId}
        loadingUsers={loadingUsers}
        onRefresh={onRefreshUsers}
        onRemoveUser={onRemoveUser}
        removingMembershipId={removingMembershipId}
      />
    </section>
  );
}

