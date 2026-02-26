"use client";

type VendorSetupStepperProps = {
  hasAnyVendors: boolean;
  selectedVendorName?: string | null;
  vendorUserCount: number;
};

type StepState = "done" | "current" | "pending";

function StepBadge({ state, index }: { state: StepState; index: number }) {
  const cls =
    state === "done"
      ? "bg-[rgba(16,185,129,0.14)] border border-[rgba(16,185,129,0.35)] text-[#34d399]"
      : state === "current"
        ? "bg-brand-soft border border-[rgba(0,212,255,0.25)] text-brand"
        : "bg-[rgba(255,255,255,0.03)] border border-[rgba(255,255,255,0.08)] text-muted";

  return (
    <span
      className={`inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${cls}`}
    >
      {index}
    </span>
  );
}

export default function VendorSetupStepper({
  hasAnyVendors,
  selectedVendorName,
  vendorUserCount,
}: VendorSetupStepperProps) {
  const step1: StepState = hasAnyVendors ? "done" : "current";
  const step2: StepState = selectedVendorName ? "done" : hasAnyVendors ? "current" : "pending";
  const step3: StepState =
    vendorUserCount > 0 ? "done" : selectedVendorName ? "current" : "pending";
  const step4: StepState =
    selectedVendorName && vendorUserCount > 0 ? "current" : "pending";

  return (
    <section className="rounded-2xl border border-[var(--line)] bg-surface p-4">
      <div className="mb-3">
        <h2 className="text-sm font-semibold text-[var(--ink)]">Vendor Setup Flow</h2>
        <p className="mt-1 text-xs text-[var(--muted)]">
          Left side creates the vendor business record. Right side links a Keycloak user and vendor membership.
        </p>
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-[var(--line)] p-3">
          <div className="mb-2 flex items-center gap-2">
            <StepBadge state={step1} index={1} />
            <p className="text-xs font-semibold text-[var(--ink)]">Create Vendor</p>
          </div>
          <p className="text-xs text-[var(--muted)]">
            Use the left form to create the vendor record in `vendor-service`.
          </p>
        </div>

        <div className="rounded-xl border border-[var(--line)] p-3">
          <div className="mb-2 flex items-center gap-2">
            <StepBadge state={step2} index={2} />
            <p className="text-xs font-semibold text-[var(--ink)]">Select Vendor</p>
          </div>
          <p className="text-xs text-[var(--muted)]">
            Pick a vendor from the list to load vendor users and enable onboarding.
          </p>
          {selectedVendorName && <p className="mt-2 text-[11px] text-[var(--brand)]">Selected: {selectedVendorName}</p>}
        </div>

        <div className="rounded-xl border border-[var(--line)] p-3">
          <div className="mb-2 flex items-center gap-2">
            <StepBadge state={step3} index={3} />
            <p className="text-xs font-semibold text-[var(--ink)]">Onboard Admin</p>
          </div>
          <p className="text-xs text-[var(--muted)]">
            Right panel creates/links a Keycloak user, assigns `vendor_admin`, and writes vendor membership.
          </p>
        </div>

        <div className="rounded-xl border border-[var(--line)] p-3">
          <div className="mb-2 flex items-center gap-2">
            <StepBadge state={step4} index={4} />
            <p className="text-xs font-semibold text-[var(--ink)]">Review Users</p>
          </div>
          <p className="text-xs text-[var(--muted)]">
            Manage vendor memberships in the Vendor Users list (refresh/remove). Email comes from Keycloak if new user.
          </p>
          <p className="mt-2 text-[11px] text-[var(--muted)]">Users: {vendorUserCount}</p>
        </div>
      </div>
    </section>
  );
}

