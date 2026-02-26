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
      className="animate-rise"
      style={{
        position: "relative",
        overflow: "hidden",
        background: "linear-gradient(135deg, #060618 0%, #0e0820 40%, #071428 100%)",
        padding: "0",
        marginBottom: "0",
      }}
    >
      {/* Decorative orbs */}
      <div style={{ position: "absolute", top: "-80px", left: "-80px", width: "450px", height: "450px", borderRadius: "50%", background: "radial-gradient(circle, var(--brand-soft) 0%, transparent 70%)", pointerEvents: "none" }} />
      <div style={{ position: "absolute", bottom: "-100px", right: "-60px", width: "500px", height: "500px", borderRadius: "50%", background: "radial-gradient(circle, var(--accent-soft) 0%, transparent 70%)", pointerEvents: "none" }} />
      <div style={{ position: "absolute", top: "30%", left: "40%", width: "200px", height: "200px", borderRadius: "50%", background: "radial-gradient(circle, var(--brand-soft) 0%, transparent 70%)", pointerEvents: "none" }} />

      {/* Subtle grid */}
      <div
        style={{
          position: "absolute", inset: 0, pointerEvents: "none",
          backgroundImage: "linear-gradient(var(--brand-soft) 1px, transparent 1px), linear-gradient(90deg, var(--brand-soft) 1px, transparent 1px)",
          backgroundSize: "48px 48px",
        }}
      />

      <div className="mx-auto max-w-7xl px-4 py-16 md:py-24" style={{ position: "relative", zIndex: 1 }}>
        <div className="grid items-center gap-12 md:grid-cols-[1fr,auto]">
          <div style={{ maxWidth: "680px" }}>
            {/* Tag pill */}
            <div
              className="mb-6 inline-flex items-center gap-2 rounded-full px-4 py-1.5"
              style={{
                background: "var(--brand-soft)",
                border: "1px solid var(--line-bright)",
                fontSize: "0.72rem",
                fontWeight: 800,
                color: "var(--brand)",
                letterSpacing: "0.12em",
                textTransform: "uppercase",
              }}
            >
              <span
                style={{
                  width: "6px", height: "6px", borderRadius: "50%",
                  background: "var(--brand)",
                  boxShadow: "0 0 8px var(--brand)",
                  display: "inline-block",
                  animation: "glowPulse 2s ease-in-out infinite",
                }}
              />
              Mega Sale - Live Now
            </div>

            {/* Headline */}
            <h1
              style={{
                fontFamily: "'Syne', sans-serif",
                fontSize: "clamp(2.4rem, 6vw, 4.5rem)",
                fontWeight: 800,
                lineHeight: 1.08,
                letterSpacing: "-0.03em",
                margin: "0 0 24px",
                color: "#fff",
              }}
            >
              Your Next-Gen
              <br />
              <span
                style={{
                  background: "var(--gradient-brand)",
                  WebkitBackgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                  backgroundClip: "text",
                }}
              >
                Shopping Hub
              </span>
            </h1>

            <p style={{ fontSize: "1rem", color: "#8888bb", lineHeight: 1.7, margin: "0 0 36px", maxWidth: "520px" }}>
              Discover thousands of premium products at unbeatable prices. Secure payments, lightning-fast delivery, and a shopping experience built for the future.
              {isAuthenticated ? " Welcome back - your deals are waiting." : " Sign in to unlock your personal store."}
            </p>

            {/* CTA Buttons */}
            <div className="flex flex-wrap gap-3">
              <Link
                href="/products"
                className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition"
                style={{
                  background: "var(--gradient-brand)",
                  color: "#fff",
                  fontSize: "0.9rem",
                  boxShadow: "0 0 28px var(--brand-glow)",
                }}
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
                  className="no-underline inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition"
                  style={{ border: "1.5px solid var(--brand-glow)", color: "var(--brand)", background: "var(--brand-soft)", fontSize: "0.9rem" }}
                >
                  {canViewAdmin ? "Go to Admin" : "My Profile"}
                </Link>
              ) : (
                <button
                  onClick={onSignup}
                  disabled={authBusy}
                  className="inline-flex items-center gap-2 rounded-xl px-7 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                  style={{ border: "1.5px solid var(--brand-glow)", color: "var(--brand)", background: "var(--brand-soft)", fontSize: "0.9rem", cursor: "pointer" }}
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
                  <p style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 800, color: "var(--brand)", margin: "0 0 2px", textShadow: "0 0 16px var(--brand-glow)" }}>
                    {value}
                  </p>
                  <p style={{ fontSize: "0.72rem", color: "var(--muted)", margin: 0, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.1em" }}>{label}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Side discount badge */}
          <div className="hidden md:flex flex-col items-center gap-4">
            <div
              style={{
                padding: "32px 28px",
                borderRadius: "24px",
                border: "1px solid var(--line-bright)",
                background: "var(--brand-soft)",
                backdropFilter: "blur(12px)",
                textAlign: "center",
                boxShadow: "0 0 40px var(--brand-soft)",
              }}
            >
              <p style={{ fontSize: "0.7rem", fontWeight: 800, color: "var(--brand)", letterSpacing: "0.16em", textTransform: "uppercase", margin: "0 0 4px" }}>UP TO</p>
              <p style={{ fontFamily: "'Syne', sans-serif", fontSize: "5rem", fontWeight: 900, color: "#fff", lineHeight: 1, margin: "0 0 4px", textShadow: "0 0 32px var(--brand-glow)" }}>
                70<span style={{ color: "var(--brand)" }}>%</span>
              </p>
              <p style={{ fontSize: "1rem", fontWeight: 800, color: "rgba(255,255,255,0.6)", letterSpacing: "0.08em", margin: 0 }}>OFF TODAY</p>
            </div>
            <div
              style={{
                padding: "14px 20px",
                borderRadius: "14px",
                border: "1px solid var(--accent-glow)",
                background: "var(--accent-soft)",
                textAlign: "center",
                fontSize: "0.78rem",
                color: "#a78bfa",
                fontWeight: 600,
              }}
            >
              * Free shipping on orders $25+
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
