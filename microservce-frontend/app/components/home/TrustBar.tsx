"use client";

const TrustIcons = {
  ship: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v3" />
      <polyline points="9 11 9 6 14 6 14 9" />
      <rect x="9" y="11" width="14" height="10" rx="2" />
      <circle cx="12" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
    </svg>
  ),
  lock: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  ),
  refresh: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="23 4 23 10 17 10" /><polyline points="1 20 1 14 7 14" />
      <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
    </svg>
  ),
  support: (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
    </svg>
  ),
};

const items = [
  { icon: TrustIcons.ship, title: "Free Shipping", desc: "On orders over $25" },
  { icon: TrustIcons.lock, title: "Secure Payment", desc: "100% encrypted checkout" },
  { icon: TrustIcons.refresh, title: "Easy Returns", desc: "30-day return policy" },
  { icon: TrustIcons.support, title: "24/7 Support", desc: "Always here to help you" },
];

export default function TrustBar() {
  return (
    <section className="animate-rise mx-auto max-w-7xl px-4 py-8" style={{ animationDelay: "100ms" }}>
      <div className="trust-bar">
        {items.map(({ icon, title, desc }) => (
          <div key={title} className="trust-item">
            <span className="trust-icon">{icon}</span>
            <div className="trust-text">
              <h4>{title}</h4>
              <p>{desc}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
