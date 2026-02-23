import Link from "next/link";

export default function Footer() {
    return (
        <footer
            style={{
                marginTop: "80px",
                background: "linear-gradient(180deg, var(--footer-bg) 0%, #04040c 100%)",
                borderTop: "1px solid var(--line-bright)",
                position: "relative",
                overflow: "hidden",
            }}
        >
            {/* Decorative glow blobs */}
            <div
                style={{
                    position: "absolute",
                    top: "-80px",
                    left: "10%",
                    width: "400px",
                    height: "400px",
                    borderRadius: "50%",
                    background: "radial-gradient(circle, var(--brand-soft) 0%, transparent 70%)",
                    pointerEvents: "none",
                }}
            />
            <div
                style={{
                    position: "absolute",
                    top: "-80px",
                    right: "5%",
                    width: "300px",
                    height: "300px",
                    borderRadius: "50%",
                    background: "radial-gradient(circle, var(--accent-soft) 0%, transparent 70%)",
                    pointerEvents: "none",
                }}
            />

            {/* Main Footer Grid */}
            <div className="mx-auto grid max-w-7xl gap-10 px-6 py-14 sm:grid-cols-2 lg:grid-cols-4">
                {/* Brand Column */}
                <div>
                    <Link href="/" className="mb-5 flex items-center gap-3 no-underline">
                        <div
                            style={{
                                width: "40px",
                                height: "40px",
                                borderRadius: "12px",
                                background: "var(--gradient-brand)",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontWeight: "900",
                                fontSize: "0.75rem",
                                color: "#fff",
                                boxShadow: "0 0 16px var(--brand-glow)",
                                flexShrink: 0,
                            }}
                        >
                            RS
                        </div>
                        <div>
                            <p style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, color: "#fff", fontSize: "1rem", margin: 0 }}>
                                Rumal Store
                            </p>
                            <p style={{ fontSize: "9px", color: "var(--brand)", letterSpacing: "0.2em", margin: 0, fontWeight: 600, opacity: 0.5 }}>
                                ONLINE MARKETPLACE
                            </p>
                        </div>
                    </Link>
                    <p className="text-sm leading-relaxed" style={{ color: "var(--muted)" }}>
                        Your trusted next-gen online marketplace. Discover amazing deals on top products with fast delivery and secure payments.
                    </p>

                    {/* Trust badges */}
                    <div className="mt-5 flex flex-wrap gap-2">
                        {["SSL Secure", "Safe Pay", "Fast Ship"].map((badge) => (
                            <span
                                key={badge}
                                className="rounded-full px-3 py-1 text-[10px] font-bold tracking-wider"
                                style={{
                                    background: "var(--brand-soft)",
                                    border: "1px solid var(--line-bright)",
                                    color: "var(--brand)",
                                }}
                            >
                                ✓ {badge}
                            </span>
                        ))}
                    </div>
                </div>

                {/* Customer Service */}
                <div>
                    <h4
                        className="mb-5 text-xs font-bold uppercase tracking-widest"
                        style={{ color: "#fff", letterSpacing: "0.14em" }}
                    >
                        Customer Service
                    </h4>
                    <ul className="space-y-3 text-sm" style={{ listStyle: "none", padding: 0, margin: 0 }}>
                        {[
                            { href: "/products", label: "Help Center" },
                            { href: "/orders", label: "Track Order" },
                            { href: "/products", label: "Returns & Refunds" },
                            { href: "/products", label: "Shipping Info" },
                            { href: "/products", label: "FAQs" },
                        ].map(({ href, label }) => (
                            <li key={label}>
                                <Link
                                    href={href}
                                    className="flex items-center gap-2 no-underline transition"
                                    style={{ color: "var(--muted)" }}
                                    onMouseEnter={(e) => { e.currentTarget.style.color = "var(--brand)"; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.color = "var(--muted)"; }}
                                >
                                    <span style={{ color: "var(--brand-glow)", fontSize: "0.6rem" }}>▶</span>
                                    {label}
                                </Link>
                            </li>
                        ))}
                    </ul>
                </div>

                {/* Quick Links */}
                <div>
                    <h4
                        className="mb-5 text-xs font-bold uppercase tracking-widest"
                        style={{ color: "#fff", letterSpacing: "0.14em" }}
                    >
                        Quick Links
                    </h4>
                    <ul className="space-y-3 text-sm" style={{ listStyle: "none", padding: 0, margin: 0 }}>
                        {[
                            { href: "/products", label: "Shop All" },
                            { href: "/products", label: "New Arrivals" },
                            { href: "/products", label: "Best Sellers" },
                            { href: "/products", label: "Flash Deals" },
                            { href: "/products", label: "Clearance Sale" },
                        ].map(({ href, label }) => (
                            <li key={label}>
                                <Link
                                    href={href}
                                    className="flex items-center gap-2 no-underline transition"
                                    style={{ color: "var(--muted)" }}
                                    onMouseEnter={(e) => { e.currentTarget.style.color = "var(--brand)"; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.color = "var(--muted)"; }}
                                >
                                    <span style={{ color: "var(--brand-glow)", fontSize: "0.6rem" }}>▶</span>
                                    {label}
                                </Link>
                            </li>
                        ))}
                    </ul>
                </div>

                {/* My Account + Payment */}
                <div>
                    <h4
                        className="mb-5 text-xs font-bold uppercase tracking-widest"
                        style={{ color: "#fff", letterSpacing: "0.14em" }}
                    >
                        My Account
                    </h4>
                    <ul className="space-y-3 text-sm" style={{ listStyle: "none", padding: 0, margin: 0 }}>
                        {[
                            { href: "/profile", label: "My Profile" },
                            { href: "/orders", label: "My Orders" },
                            { href: "/wishlist", label: "Wishlist" },
                            { href: "/", label: "Sign In" },
                        ].map(({ href, label }) => (
                            <li key={label}>
                                <Link
                                    href={href}
                                    className="flex items-center gap-2 no-underline"
                                    style={{ color: "var(--muted)" }}
                                    onMouseEnter={(e) => { e.currentTarget.style.color = "var(--brand)"; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.color = "var(--muted)"; }}
                                >
                                    <span style={{ color: "var(--brand-glow)", fontSize: "0.6rem" }}>▶</span>
                                    {label}
                                </Link>
                            </li>
                        ))}
                    </ul>

                    <div className="mt-6">
                        <p className="mb-3 text-xs font-bold uppercase tracking-widest" style={{ color: "#fff", letterSpacing: "0.14em" }}>
                            We Accept
                        </p>
                        <div className="flex flex-wrap gap-2">
                            {["VISA", "MC", "PayPal", "COD"].map((pm) => (
                                <span
                                    key={pm}
                                    className="rounded-lg px-3 py-1.5 text-[10px] font-black"
                                    style={{
                                        background: "rgba(255,255,255,0.04)",
                                        border: "1px solid rgba(255,255,255,0.1)",
                                        color: "rgba(255,255,255,0.6)",
                                        letterSpacing: "0.04em",
                                    }}
                                >
                                    {pm}
                                </span>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* Bottom Bar */}
            <div style={{ borderTop: "1px solid var(--brand-soft)", background: "rgba(0,0,0,0.3)" }}>
                <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-6 py-4">
                    <p className="text-xs" style={{ color: "#4a4a70" }}>
                        © {new Date().getFullYear()} Rumal Store. All rights reserved. Built with{" "}
                        <span style={{ color: "var(--brand)", opacity: 0.5 }}>♦</span> precision.
                    </p>
                    <div className="flex items-center gap-3">
                        {/* Social icons */}
                        {[
                            { label: "Twitter / X", path: "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" },
                            { label: "Instagram", path: "M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z" },
                            { label: "LinkedIn", path: "M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6zM2 9h4v12H2z M4 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4z" },
                        ].map(({ label, path }) => (
                            <button
                                key={label}
                                aria-label={label}
                                className="flex h-8 w-8 items-center justify-center rounded-lg transition"
                                style={{
                                    background: "rgba(255,255,255,0.04)",
                                    border: "1px solid var(--line-bright)",
                                    color: "#4a4a70",
                                    cursor: "pointer",
                                }}
                                onMouseEnter={(e) => {
                                    e.currentTarget.style.color = "var(--brand)";
                                    e.currentTarget.style.borderColor = "var(--brand-glow)";
                                    e.currentTarget.style.boxShadow = "0 0 12px var(--line-bright)";
                                }}
                                onMouseLeave={(e) => {
                                    e.currentTarget.style.color = "#4a4a70";
                                    e.currentTarget.style.borderColor = "var(--line-bright)";
                                    e.currentTarget.style.boxShadow = "none";
                                }}
                            >
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                                    <path d={path} />
                                </svg>
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </footer>
    );
}
