"use client";

import type { Customer } from "../../../lib/types/customer";

type AccountInfoCardProps = {
  customer: Customer | null;
  canViewAdmin: boolean;
  editFirstName: string;
  editLastName: string;
  onFirstNameChange: (value: string) => void;
  onLastNameChange: (value: string) => void;
  savingProfile: boolean;
  onSave: () => void;
  emailVerified: boolean | null;
  profile: Record<string, unknown> | null | undefined;
  initialNameParts: { firstName: string; lastName: string };
};

export default function AccountInfoCard({
  customer,
  canViewAdmin,
  editFirstName,
  editLastName,
  onFirstNameChange,
  onLastNameChange,
  savingProfile,
  onSave,
  emailVerified,
  profile,
  initialNameParts,
}: AccountInfoCardProps) {
  return (
    <div className="grid gap-4 grid-cols-2 mb-5">
      {/* Customer Profile Card */}
      <article className="animate-rise glass-card p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[image:var(--gradient-brand)] grid place-items-center">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.1em] text-muted m-0">Customer Profile</p>
            <p className="text-base font-bold text-white m-0">
              {canViewAdmin ? "Admin Account" : (customer?.name || "Loading...")}
            </p>
          </div>
        </div>

        {!canViewAdmin && (
          <div className="flex flex-col gap-[10px]">
            {/* First Name */}
            <div className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
              <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[6px]">First Name</p>
              <input
                value={editFirstName}
                onChange={(e) => onFirstNameChange(e.target.value)}
                disabled={savingProfile || emailVerified === false}
                className="form-input px-[10px] py-[7px]"
                placeholder="Enter first name"
              />
            </div>
            {/* Last Name + Save */}
            <div className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
              <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[6px]">Last Name</p>
              <div className="flex gap-2">
                <input
                  value={editLastName}
                  onChange={(e) => onLastNameChange(e.target.value)}
                  disabled={savingProfile || emailVerified === false}
                  className="form-input flex-1 px-[10px] py-[7px]"
                  placeholder="Enter last name"
                />
                <button
                  onClick={onSave}
                  disabled={
                    savingProfile || emailVerified === false || !customer || !editFirstName.trim() || !editLastName.trim()
                    || (editFirstName.trim() === initialNameParts.firstName && editLastName.trim() === initialNameParts.lastName)
                  }
                  className="px-[14px] py-[7px] rounded-[8px] border-none shrink-0 bg-[image:var(--gradient-brand)] text-white text-[0.75rem] font-bold cursor-pointer"
                  style={{ opacity: savingProfile || !editFirstName.trim() || !editLastName.trim() ? 0.5 : 1 }}
                >
                  {savingProfile ? "Saving..." : "Save"}
                </button>
              </div>
            </div>

            {[
              { label: "Email", value: customer?.email || "\u2014" },
              { label: "Customer ID", value: customer?.id || "\u2014", mono: true },
              { label: "Member Since", value: customer?.createdAt ? new Date(customer.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "long", day: "numeric" }) : "\u2014" },
            ].map(({ label, value, mono }) => (
              <div key={label} className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
                <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[3px]">{label}</p>
                <p className={`text-[0.82rem] font-bold text-ink-light break-all m-0 ${mono ? "font-mono" : ""}`}>{value}</p>
              </div>
            ))}
          </div>
        )}

        {canViewAdmin && (
          <div className="rounded-md border border-[rgba(124,58,237,0.25)] bg-[rgba(124,58,237,0.08)] px-[14px] py-3 text-[0.82rem] text-accent-light">
            <p className="font-bold mb-1">Admin Account Detected</p>
            <p className="text-[0.75rem] opacity-80 m-0">Customer profile bootstrap is not required for admin operations.</p>
          </div>
        )}
      </article>

      {/* Session Info Card */}
      <article className="animate-rise glass-card p-6" style={{ animationDelay: "80ms" }}>
        <div className="flex items-center gap-3 mb-4">
          <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[rgba(124,58,237,0.2)] border border-[rgba(124,58,237,0.35)] grid place-items-center">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#a78bfa" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </div>
          <div>
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.1em] text-muted m-0">Session Info</p>
            <p className="text-base font-bold text-white m-0">Authentication Details</p>
          </div>
        </div>

        <div className="flex flex-col gap-[10px]">
          {[
            { label: "Auth Email", value: (profile?.email as string) || "\u2014" },
            { label: "Auth Name", value: (profile?.name as string) || "\u2014" },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
              <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[3px]">{label}</p>
              <p className="text-[0.82rem] font-bold text-ink-light m-0">{value}</p>
            </div>
          ))}
          <div className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
            <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[6px]">Role</p>
            <span
              className="rounded-full px-3 py-[3px] text-[0.72rem] font-extrabold"
              style={{
                background: canViewAdmin ? "rgba(124,58,237,0.15)" : "rgba(34,197,94,0.1)",
                border: `1px solid ${canViewAdmin ? "rgba(124,58,237,0.3)" : "rgba(34,197,94,0.25)"}`,
                color: canViewAdmin ? "#a78bfa" : "#4ade80",
              }}
            >
              {canViewAdmin ? "Admin" : "Customer"}
            </span>
          </div>
          <div className="rounded-md bg-brand-soft border border-brand-soft px-[14px] py-[10px]">
            <p className="text-[0.65rem] text-muted uppercase tracking-[0.08em] mb-[6px]">Email Verified</p>
            <span
              className="rounded-full px-3 py-[3px] text-[0.72rem] font-extrabold"
              style={{
                background: emailVerified ? "rgba(34,197,94,0.1)" : "var(--warning-soft)",
                border: `1px solid ${emailVerified ? "rgba(34,197,94,0.25)" : "var(--warning-border)"}`,
                color: emailVerified ? "#4ade80" : "var(--warning-text)",
              }}
            >
              {emailVerified ? "Verified" : "Not Verified"}
            </span>
          </div>
        </div>
      </article>
    </div>
  );
}
