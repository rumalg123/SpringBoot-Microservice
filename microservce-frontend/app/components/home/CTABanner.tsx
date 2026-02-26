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
      className="animate-rise mx-auto max-w-7xl px-4 pb-16"
      style={{ animationDelay: "400ms" }}
    >
      <div
        style={{
          borderRadius: "24px",
          padding: "56px 40px",
          textAlign: "center",
          background: "linear-gradient(135deg, #0a0a22 0%, #12082e 50%, #080e28 100%)",
          border: "1px solid var(--line-bright)",
          position: "relative",
          overflow: "hidden",
        }}
      >
        {/* Decorative orbs */}
        <div style={{ position: "absolute", top: "-60px", left: "20%", width: "300px", height: "300px", borderRadius: "50%", background: "radial-gradient(circle, var(--brand-soft) 0%, transparent 70%)", pointerEvents: "none" }} />
        <div style={{ position: "absolute", bottom: "-60px", right: "15%", width: "250px", height: "250px", borderRadius: "50%", background: "radial-gradient(circle, var(--accent-soft) 0%, transparent 70%)", pointerEvents: "none" }} />

        <div style={{ position: "relative", zIndex: 1 }}>
          <span style={{ display: "inline-block", fontFamily: "'Syne', sans-serif", fontSize: "2.4rem", fontWeight: 900, lineHeight: 1.15, color: "#fff", marginBottom: "16px" }}>
            {isAuthenticated ? "Welcome Back!" : (
              <>
                Join{" "}
                <span style={{ background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", backgroundClip: "text" }}>
                  Rumal Store
                </span>{" "}
                Today
              </>
            )}
          </span>
          <p style={{ fontSize: "0.95rem", color: "#8888bb", margin: "0 auto 36px", maxWidth: "500px", lineHeight: 1.7 }}>
            {isAuthenticated
              ? "Continue shopping, check your orders, and manage your account all in one place."
              : "Create your free account and unlock exclusive deals, fast checkout, order tracking, and a premium shopping experience."}
          </p>
          <div style={{ display: "flex", justifyContent: "center", gap: "14px", flexWrap: "wrap" }}>
            {isAuthenticated ? (
              <Link
                href={canViewAdmin ? "/admin/orders" : "/profile"}
                className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition"
                style={{ background: "var(--gradient-brand)", color: "#fff", fontSize: "0.9rem", boxShadow: "0 0 24px var(--line-bright)" }}
              >
                {canViewAdmin ? "Open Admin ->" : "Open Profile ->"}
              </Link>
            ) : (
              <button
                onClick={onSignup}
                disabled={authBusy}
                className="inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition disabled:cursor-not-allowed disabled:opacity-50"
                style={{ background: "var(--gradient-brand)", color: "#fff", fontSize: "0.9rem", cursor: "pointer", border: "none", boxShadow: "0 0 24px var(--line-bright)" }}
              >
                {authActionPending === "signup" ? "Redirecting..." : "Sign Up Free ->"}
              </button>
            )}
            <Link
              href="/products"
              className="no-underline inline-flex items-center gap-2 rounded-xl px-8 py-3.5 font-bold transition"
              style={{ border: "1.5px solid var(--brand-glow)", color: "var(--brand)", background: "var(--brand-soft)", fontSize: "0.9rem" }}
            >
              Browse Products
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
