"use client";

import Link from "next/link";

type Props = {
  isAuthenticated: boolean;
  canViewAdmin: boolean;
  authBusy: boolean;
  authActionPending: "login" | "signup" | "forgot" | null;
  onSignup: () => void;
};

export default function CTABanner({
  isAuthenticated,
  canViewAdmin,
  authBusy,
  authActionPending,
  onSignup,
}: Props) {
  return (
    <section
      className="animate-rise mx-auto max-w-7xl px-4 pb-16 [animation-delay:400ms]"
    >
      <div className="rounded-[24px] px-10 py-14 text-center bg-[linear-gradient(135deg,#0a0a22_0%,#12082e_50%,#080e28_100%)] border border-line-bright relative overflow-hidden">
        {/* Decorative orbs */}
        <div className="absolute -top-[60px] left-[20%] w-[300px] h-[300px] rounded-full bg-[radial-gradient(circle,var(--brand-soft)_0%,transparent_70%)] pointer-events-none" />
        <div className="absolute -bottom-[60px] right-[15%] w-[250px] h-[250px] rounded-full bg-[radial-gradient(circle,var(--accent-soft)_0%,transparent_70%)] pointer-events-none" />

        <div className="relative z-[1]">
          <span className="inline-block font-[Syne,sans-serif] text-[2.4rem] font-black leading-[1.15] text-white mb-4">
            {isAuthenticated ? "Welcome Back!" : (
              <>
                Join{" "}
                <span className="bg-[image:var(--gradient-brand)] bg-clip-text [-webkit-background-clip:text] [-webkit-text-fill-color:transparent]">
                  Rumal Store
                </span>{" "}
                Today
              </>
            )}
          </span>
          <p className="text-[0.95rem] text-[#8888bb] mx-auto mb-9 max-w-[500px] leading-[1.7]">
            {isAuthenticated
              ? "Continue shopping, check your orders, and manage your account all in one place."
              : "Create your free account and unlock exclusive deals, fast checkout, order tracking, and a premium shopping experience."}
          </p>
          <div className="flex justify-center gap-3.5 flex-wrap">
            {isAuthenticated ? (
              <Link
                href={canViewAdmin ? "/admin/orders" : "/profile"}
                className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition bg-[image:var(--gradient-brand)] text-white text-[0.9rem] shadow-[0_0_24px_var(--line-bright)]"
              >
                {canViewAdmin ? "Open Admin ->" : "Open Profile ->"}
              </Link>
            ) : (
              <button
                onClick={onSignup}
                disabled={authBusy}
                className="inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50 bg-[image:var(--gradient-brand)] text-white text-[0.9rem] cursor-pointer border-none shadow-[0_0_24px_var(--line-bright)]"
              >
                {authActionPending === "signup" ? "Redirecting..." : "Sign Up Free ->"}
              </button>
            )}
            <Link
              href="/products"
              className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition border-[1.5px] border-brand-glow text-brand bg-brand-soft text-[0.9rem]"
            >
              Browse Products
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
