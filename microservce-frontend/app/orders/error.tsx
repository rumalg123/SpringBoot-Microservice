"use client";

import { useEffect } from "react";

export default function OrdersError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error("Orders error boundary:", error);
  }, [error]);

  return (
    <div className="min-h-[350px] flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-4 grid h-14 w-14 place-items-center rounded-full border border-danger/25 bg-danger/10">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--danger)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </div>
      <p className="mb-1.5 text-lg font-bold text-ink">Orders Error</p>
      <p className="mb-5 max-w-[420px] text-sm text-muted">{error.message || "Something went wrong loading your orders."}</p>
      <button onClick={reset} className="btn-primary cursor-pointer rounded-md border-none px-6 py-2.5 text-sm font-bold text-white">
        Try Again
      </button>
    </div>
  );
}
