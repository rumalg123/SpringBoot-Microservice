"use client";

import Link from "next/link";

type Props = {
  isAuthenticated: boolean;
  canViewAdmin: boolean;
  authBusy: boolean;
  authActionPending: "login" | "signup" | "forgot" | null;
  onSignup: () => void;
};

export default function HeroSection({
  isAuthenticated,
  canViewAdmin,
  authBusy,
  authActionPending,
  onSignup,
}: Props) {
  return (
    <section
      className="animate-rise relative overflow-hidden bg-[linear-gradient(135deg,#060618_0%,#0e0820_40%,#071428_100%)] p-0 mb-0"
    >
      {/* Decorative orbs */}
      <div className="absolute -top-[80px] -left-[80px] w-[450px] h-[450px] rounded-full bg-[radial-gradient(circle,var(--brand-soft)_0%,transparent_70%)] pointer-events-none" />
      <div className="absolute -bottom-[100px] -right-[60px] w-[500px] h-[500px] rounded-full bg-[radial-gradient(circle,var(--accent-soft)_0%,transparent_70%)] pointer-events-none" />
      <div className="absolute top-[30%] left-[40%] w-[200px] h-[200px] rounded-full bg-[radial-gradient(circle,var(--brand-soft)_0%,transparent_70%)] pointer-events-none" />

      {/* Subtle grid */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          backgroundImage: "linear-gradient(var(--brand-soft) 1px, transparent 1px), linear-gradient(90deg, var(--brand-soft) 1px, transparent 1px)",
          backgroundSize: "48px 48px",
        }}
      />

      <div className="mx-auto max-w-7xl px-4 py-16 md:py-24 relative z-[1]">
        <div className="grid items-center gap-12 md:grid-cols-[1fr,auto]">
          <div className="max-w-[680px]">
            {/* Tag pill */}
            <div className="mb-6 inline-flex items-center gap-2 rounded-full px-4 py-1.5 bg-brand-soft border border-line-bright text-xs font-extrabold text-brand tracking-[0.12em] uppercase">
              <span className="w-1.5 h-1.5 rounded-full bg-brand shadow-[0_0_8px_var(--brand)] inline-block animate-[glowPulse_2s_ease-in-out_infinite]" />
              Mega Sale - Live Now
            </div>

            {/* Headline */}
            <h1 className="font-[Syne,sans-serif] text-[clamp(2.4rem,6vw,4.5rem)] font-extrabold leading-[1.08] tracking-tight mb-6 text-white">
              Your Next-Gen
              <br />
              <span className="bg-[image:var(--gradient-brand)] bg-clip-text [-webkit-background-clip:text] [-webkit-text-fill-color:transparent]">
                Shopping Hub
              </span>
            </h1>

            <p className="text-lg text-[#8888bb] leading-[1.7] mb-9 max-w-[520px]">
              Discover thousands of premium products at unbeatable prices. Secure payments, lightning-fast delivery, and a shopping experience built for the future.
              {isAuthenticated ? " Welcome back - your deals are waiting." : " Sign in to unlock your personal store."}
            </p>

            {/* CTA Buttons */}
            <div className="flex flex-wrap gap-3">
              <Link
                href="/products"
                className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition bg-[image:var(--gradient-brand)] text-white text-[0.9rem] shadow-[0_0_28px_var(--brand-glow)]"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                  <path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
                Shop Now
              </Link>
              {isAuthenticated ? (
                <Link
                  href={canViewAdmin ? "/admin/orders" : "/profile"}
                  className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition border-[1.5px] border-brand-glow text-brand bg-brand-soft text-[0.9rem]"
                >
                  {canViewAdmin ? "Go to Admin" : "My Profile"}
                </Link>
              ) : (
                <button
                  onClick={onSignup}
                  disabled={authBusy}
                  className="inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50 border-[1.5px] border-brand-glow text-brand bg-brand-soft text-[0.9rem] cursor-pointer"
                >
                  {authActionPending === "signup" ? "Redirecting..." : "Create Account ->"}
                </button>
              )}
            </div>

            {/* Stats row */}
            <div className="mt-10 flex flex-wrap gap-6">
              {[
                { value: "50K+", label: "Products" },
                { value: "99%", label: "Satisfaction" },
                { value: "24/7", label: "Support" },
              ].map(({ value, label }) => (
                <div key={label}>
                  <p className="font-[Syne,sans-serif] text-[1.5rem] font-extrabold text-brand mb-0.5 [text-shadow:0_0_16px_var(--brand-glow)]">
                    {value}
                  </p>
                  <p className="text-xs text-muted m-0 font-semibold uppercase tracking-widest">{label}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Side discount badge */}
          <div className="hidden md:flex flex-col items-center gap-4">
            <div className="px-7 py-8 rounded-[24px] border border-line-bright bg-brand-soft backdrop-blur-[12px] text-center shadow-[0_0_40px_var(--brand-soft)]">
              <p className="text-xs font-extrabold text-brand tracking-[0.16em] uppercase mb-1">UP TO</p>
              <p className="font-[Syne,sans-serif] text-[5rem] font-black text-white leading-none mb-1 [text-shadow:0_0_32px_var(--brand-glow)]">
                70<span className="text-brand">%</span>
              </p>
              <p className="text-lg font-extrabold text-white/60 tracking-wide m-0">OFF TODAY</p>
            </div>
            <div className="px-5 py-3.5 rounded-[14px] border border-accent-glow bg-accent-soft text-center text-[0.78rem] text-[#a78bfa] font-semibold">
              * Free shipping on orders $25+
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
