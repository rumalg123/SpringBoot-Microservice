"use client";

import Link from "next/link";

type VerificationStatus = string;

type Props = {
  verificationStatus: VerificationStatus | null;
  loading?: boolean;
};

export default function VendorVerificationBanner({ verificationStatus, loading }: Props) {
  if (loading || !verificationStatus) return null;
  if (verificationStatus === "VERIFIED") return null;

  const config = STATUS_CONFIG[verificationStatus] ?? STATUS_CONFIG.UNVERIFIED;

  return (
    <section className={`mb-5 flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-3 rounded-xl px-4 py-3 text-sm border ${config.className}`}>
      <div className="flex items-start gap-3 flex-1">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
          className="shrink-0 mt-0.5"
        >
          {config.icon}
        </svg>

        <div>
          <p className="m-0 font-bold">{config.title}</p>
          <p className="m-0 text-sm opacity-80">{config.message}</p>
        </div>
      </div>

      {config.showAction && (
        <Link
          href="/vendor/settings?tab=actions"
          className={`shrink-0 no-underline rounded-lg py-1.5 px-4 text-sm font-bold border ${config.actionClassName}`}
        >
          {config.actionLabel}
        </Link>
      )}
    </section>
  );
}

/* ── status configs ── */

const WARNING_ICON = (
  <>
    <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
    <line x1="12" y1="9" x2="12" y2="13" />
    <line x1="12" y1="17" x2="12.01" y2="17" />
  </>
);

const CLOCK_ICON = (
  <>
    <circle cx="12" cy="12" r="10" />
    <polyline points="12 6 12 12 16 14" />
  </>
);

const X_ICON = (
  <>
    <circle cx="12" cy="12" r="10" />
    <line x1="15" y1="9" x2="9" y2="15" />
    <line x1="9" y1="9" x2="15" y2="15" />
  </>
);

type StatusConfig = {
  title: string;
  message: string;
  className: string;
  icon: React.ReactNode;
  showAction: boolean;
  actionLabel: string;
  actionClassName: string;
};

const STATUS_CONFIG: Record<string, StatusConfig> = {
  UNVERIFIED: {
    title: "Account Not Verified",
    message: "Your vendor account is not yet verified. Product creation and updates are blocked until an admin verifies your account.",
    className: "border-warning-border bg-warning-soft text-warning-text",
    icon: WARNING_ICON,
    showAction: true,
    actionLabel: "Request Verification",
    actionClassName: "border-[rgba(245,158,11,0.4)] bg-[rgba(245,158,11,0.15)] text-warning-text",
  },
  PENDING_VERIFICATION: {
    title: "Verification Pending",
    message: "Your verification request is under review. Product creation and updates remain blocked until approved.",
    className: "border-brand/20 bg-brand/5 text-brand",
    icon: CLOCK_ICON,
    showAction: false,
    actionLabel: "",
    actionClassName: "",
  },
  VERIFICATION_REJECTED: {
    title: "Verification Rejected",
    message: "Your verification request was rejected. Please review the feedback and submit a new request.",
    className: "border-[rgba(239,68,68,0.25)] bg-danger-soft text-[#f87171]",
    icon: X_ICON,
    showAction: true,
    actionLabel: "Resubmit Verification",
    actionClassName: "border-[rgba(239,68,68,0.3)] bg-[rgba(239,68,68,0.12)] text-[#f87171]",
  },
};
