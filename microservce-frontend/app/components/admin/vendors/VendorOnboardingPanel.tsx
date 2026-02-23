"use client";

import type { AxiosInstance } from "axios";
import KeycloakUserLookupField from "@/app/components/admin/access/KeycloakUserLookupField";
import type { OnboardForm, Vendor, VendorOnboardResponse, VendorUser, VendorUserRole } from "./types";
import VendorUsersList from "./VendorUsersList";

type VendorOnboardingPanelProps = {
  apiClient: AxiosInstance | null;
  panelId?: string;
  emailInputId?: string;
  vendorUsersSectionId?: string;
  vendors: Vendor[];
  selectedVendorId: string;
  selectedVendor: Vendor | null;
  onboardForm: OnboardForm;
  onboarding: boolean;
  onboardStatus: string;
  lastOnboardResult?: VendorOnboardResponse | null;
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
  apiClient,
  panelId,
  emailInputId,
  vendorUsersSectionId,
  vendors,
  selectedVendorId,
  selectedVendor,
  onboardForm,
  onboarding,
  onboardStatus,
  lastOnboardResult = null,
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
  const hasLinkedKeycloakUser = Boolean(onboardForm.keycloakUserId.trim());

  return (
    <section id={panelId} className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div>
          <h2 className="text-2xl text-[var(--ink)]">Vendor Admin Onboarding</h2>
          <p className="mt-1 text-xs text-[var(--muted)]">
            Step 3: select a vendor, then create or link a Keycloak user and grant vendor access (OWNER or MANAGER).
          </p>
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
        <div
          className="mb-3 rounded-lg border border-[var(--line)] p-3 text-xs"
          style={{ background: "rgba(0,212,255,0.03)" }}
        >
          <p className="font-semibold text-[var(--ink)]">What happens on onboarding</p>
          <ul className="mt-2 list-disc space-y-1 pl-4 text-[var(--muted)]">
            <li>Checks the selected vendor exists in `vendor-service`.</li>
            <li>Finds the user in Keycloak, or creates one if enabled.</li>
            <li>Assigns Keycloak realm role `vendor_admin`.</li>
            <li>Creates or updates vendor membership in `vendor_users`.</li>
            <li>Sends verify-email and password-setup email for newly created Keycloak users.</li>
          </ul>
        </div>

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
            {selectedVendor.contactEmail} | {selectedVendor.id}
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
        <KeycloakUserLookupField
          apiClient={apiClient}
          disabled={onboarding}
          helperText="Search existing Keycloak users to autofill email, names and Keycloak user ID. Use for linking existing accounts."
          onSelect={(user) =>
            onChangeOnboardForm((s) => ({
              ...s,
              keycloakUserId: user.id || s.keycloakUserId,
              email: (user.email || "").trim() || s.email,
              firstName: (user.firstName || "").trim() || s.firstName,
              lastName: (user.lastName || "").trim() || s.lastName,
              displayName: (user.displayName || user.email || "").trim() || s.displayName,
              createIfMissing: false,
            }))
          }
        />

        {hasLinkedKeycloakUser && (
          <div
            className="rounded-lg border px-3 py-2 text-xs"
            style={{ background: "rgba(59,130,246,0.06)", borderColor: "rgba(59,130,246,0.22)" }}
          >
            <p className="font-semibold text-[var(--ink)]">Existing Keycloak user selected</p>
            <p className="mt-1 text-[var(--muted)]">
              The onboarding request will link this user and assign `vendor_admin`. `Create if missing` is disabled while a Keycloak User ID is set.
            </p>
          </div>
        )}

        <div className="form-group">
          <label className="form-label">Admin Email</label>
          <input
            id={emailInputId}
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
          <div className="mb-1 flex items-center justify-between gap-2">
            <label className="form-label mb-0">Keycloak User ID (optional)</label>
            {hasLinkedKeycloakUser && (
              <button
                type="button"
                onClick={() =>
                  onChangeOnboardForm((s) => ({
                    ...s,
                    keycloakUserId: "",
                  }))
                }
                className="rounded-md border border-[var(--line)] px-2 py-1 text-[11px]"
                style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                disabled={onboarding}
              >
                Clear Linked User
              </button>
            )}
          </div>
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
            disabled={onboarding || hasLinkedKeycloakUser}
          />
          Create Keycloak user if email is not found (sends verify/password setup email)
        </label>
        {hasLinkedKeycloakUser && (
          <p className="text-[11px] text-[var(--muted)]">
            Clear the linked Keycloak User ID to re-enable automatic user creation by email.
          </p>
        )}

        <button
          type="submit"
          disabled={onboarding || !selectedVendorId}
          className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {onboarding ? "Onboarding..." : "Onboard Vendor Admin"}
        </button>
      </form>

      <p className="mt-3 text-xs text-[var(--muted)]">{onboardStatus}</p>

      {lastOnboardResult && (
        <div
          className="mt-3 rounded-lg border p-3 text-xs"
          style={{ background: "rgba(16,185,129,0.06)", borderColor: "rgba(16,185,129,0.25)" }}
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-semibold text-[var(--ink)]">Last onboarding result</p>
            <span
              className="rounded px-2 py-0.5 text-[10px]"
              style={{
                background: "rgba(16,185,129,0.14)",
                border: "1px solid rgba(16,185,129,0.25)",
                color: "#34d399",
              }}
            >
              {lastOnboardResult.keycloakUserCreated ? "KEYCLOAK USER CREATED" : "EXISTING USER LINKED"}
            </span>
          </div>
          <div className="mt-2 grid gap-1 text-[var(--muted)]">
            <p>
              <span className="text-[var(--ink-light)]">Email:</span> {lastOnboardResult.email}
            </p>
            <p>
              <span className="text-[var(--ink-light)]">Keycloak User ID:</span>{" "}
              <span className="font-mono">{lastOnboardResult.keycloakUserId}</span>
            </p>
            <p>
              <span className="text-[var(--ink-light)]">Vendor Membership:</span>{" "}
              {lastOnboardResult.vendorMembership.role} ({lastOnboardResult.vendorMembership.active ? "active" : "inactive"})
            </p>
            <p>
              <span className="text-[var(--ink-light)]">Action Email:</span>{" "}
              {lastOnboardResult.keycloakActionEmailSent
                ? "Sent (verify email + password setup)"
                : lastOnboardResult.keycloakUserCreated
                  ? "Not sent"
                  : "Not applicable (existing user)"}
            </p>
          </div>
        </div>
      )}

      <VendorUsersList
        containerId={vendorUsersSectionId}
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
