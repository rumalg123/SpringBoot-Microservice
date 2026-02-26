"use client";

type Props = {
  emailVerified: boolean | null;
  resending: boolean;
  onResend: () => void;
  /** Context-specific message shown below the title. */
  message?: string;
};

export default function EmailVerificationWarning({
  emailVerified,
  resending,
  onResend,
  message = "Some features are blocked until your email is verified.",
}: Props) {
  if (emailVerified !== false) return null;

  return (
    <section
      className="mb-4 flex items-center gap-3 rounded-xl px-4 py-3 text-sm border border-warning-border bg-warning-soft text-warning-text"
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>

      <div className="flex-1">
        <p className="m-0 font-bold">Email Not Verified</p>
        <p className="m-0 text-sm opacity-80">
          {message}
        </p>
      </div>

      <button
        type="button"
        onClick={onResend}
        disabled={resending}
        className={`btn-ghost text-sm font-bold rounded-[8px] py-1.5 px-3.5 border border-[rgba(245,158,11,0.4)] bg-[rgba(245,158,11,0.2)] text-warning-text ${resending ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
      >
        {resending ? "Sending..." : "Resend Email"}
      </button>
    </section>
  );
}
