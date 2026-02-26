import type { PublicPromotion } from "../../../lib/types/promotion";
import { formatDate } from "../../../lib/format";

function formatBenefit(p: PublicPromotion): string {
  switch (p.benefitType) {
    case "PERCENTAGE_OFF":
      return `${p.benefitValue ?? 0}% OFF`;
    case "FIXED_AMOUNT_OFF":
      return `$${p.benefitValue ?? 0} OFF`;
    case "FREE_SHIPPING":
      return "FREE SHIPPING";
    case "BUY_X_GET_Y":
      return `Buy ${p.buyQuantity ?? 0} Get ${p.getQuantity ?? 0}`;
    case "TIERED_SPEND":
      return "Spend & Save";
    default:
      return p.benefitType.replace(/_/g, " ");
  }
}

function formatScopeLabel(scope: string): string {
  switch (scope) {
    case "PLATFORM": return "Platform";
    case "VENDOR": return "Vendor";
    case "CATEGORY": return "Category";
    default: return scope;
  }
}

function benefitColor(benefitType: string): { bg: string; border: string; text: string } {
  switch (benefitType) {
    case "PERCENTAGE_OFF":
      return { bg: "var(--brand-soft)", border: "var(--brand-glow)", text: "var(--brand)" };
    case "FIXED_AMOUNT_OFF":
      return { bg: "var(--success-soft)", border: "var(--success-glow)", text: "var(--success)" };
    case "FREE_SHIPPING":
      return { bg: "var(--accent-soft)", border: "var(--accent-glow)", text: "var(--accent)" };
    case "BUY_X_GET_Y":
      return { bg: "var(--warning-soft)", border: "var(--warning-border)", text: "var(--warning)" };
    case "TIERED_SPEND":
      return { bg: "var(--accent-soft)", border: "var(--accent-glow)", text: "var(--accent)" };
    default:
      return { bg: "rgba(255,255,255,0.05)", border: "var(--line-bright)", text: "var(--muted)" };
  }
}

export default function PromotionCard({ promo }: { promo: PublicPromotion }) {
  const bc = benefitColor(promo.benefitType);
  const hasDateRange = promo.startsAt || promo.endsAt;

  return (
    <article
      className="animate-rise flex flex-col gap-3 rounded-lg border border-line bg-[rgba(255,255,255,0.03)] p-5 transition-all duration-200 hover:border-line-bright hover:shadow-[0_4px_24px_rgba(0,0,0,0.3)]"
    >
      <div>
        <h3 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold leading-[1.35] text-white">
          {promo.name}
        </h3>
        {promo.description && (
          <p className="mt-1.5 line-clamp-2 text-sm leading-[1.5] text-muted">
            {promo.description}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <span
          style={{
            display: "inline-block",
            padding: "4px 12px",
            borderRadius: "20px",
            background: bc.bg,
            border: `1px solid ${bc.border}`,
            color: bc.text,
            fontSize: "0.72rem",
            fontWeight: 800,
            letterSpacing: "0.02em",
          }}
        >
          {formatBenefit(promo)}
        </span>

        <span
          style={{
            display: "inline-block",
            padding: "3px 10px",
            borderRadius: "20px",
            background: "var(--brand-soft)",
            border: "1px solid var(--line-bright)",
            color: "var(--brand)",
            fontSize: "0.65rem",
            fontWeight: 700,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
          }}
        >
          {formatScopeLabel(promo.scopeType)}
        </span>

        {promo.flashSale && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "4px",
              padding: "3px 10px",
              borderRadius: "20px",
              background: "rgba(251,191,36,0.12)",
              border: "1px solid rgba(251,191,36,0.25)",
              color: "#fbbf24",
              fontSize: "0.65rem",
              fontWeight: 700,
              letterSpacing: "0.06em",
            }}
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
            FLASH
          </span>
        )}

        {promo.autoApply && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "4px",
              padding: "3px 10px",
              borderRadius: "20px",
              background: "rgba(52,211,153,0.1)",
              border: "1px solid rgba(52,211,153,0.25)",
              color: "#34d399",
              fontSize: "0.65rem",
              fontWeight: 700,
            }}
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
            Auto-applies
          </span>
        )}

        {promo.stackable && (
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "4px",
              padding: "3px 10px",
              borderRadius: "20px",
              background: "rgba(167,139,250,0.1)",
              border: "1px solid rgba(167,139,250,0.25)",
              color: "#a78bfa",
              fontSize: "0.65rem",
              fontWeight: 700,
            }}
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2" /><path d="M16 2v4" /><path d="M8 2v4" /></svg>
            Stackable
          </span>
        )}
      </div>

      <div className="flex flex-wrap gap-3 text-xs text-muted">
        {promo.minimumOrderAmount != null && promo.minimumOrderAmount > 0 && (
          <span>Min. order: <strong className="text-ink-light">${promo.minimumOrderAmount}</strong></span>
        )}
        {promo.maximumDiscountAmount != null && promo.maximumDiscountAmount > 0 && (
          <span>Max discount: <strong className="text-ink-light">${promo.maximumDiscountAmount}</strong></span>
        )}
        {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
          <span>
            {promo.spendTiers.length} tier{promo.spendTiers.length !== 1 ? "s" : ""}
          </span>
        )}
      </div>

      {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
        <div className="rounded-md border border-[rgba(244,114,182,0.15)] bg-[rgba(244,114,182,0.05)] px-3.5 py-2.5">
          <p className="mb-1.5 mt-0 text-xs font-bold uppercase tracking-[0.06em] text-[#f472b6]">
            Spend Tiers
          </p>
          {promo.spendTiers
            .slice()
            .sort((a, b) => a.thresholdAmount - b.thresholdAmount)
            .map((tier, idx) => (
              <div key={idx} className="py-0.5 text-xs text-muted">
                Spend <strong className="text-ink-light">${tier.thresholdAmount}</strong> &rarr; Save <strong className="text-[#f472b6]">${tier.discountAmount}</strong>
              </div>
            ))}
        </div>
      )}

      {hasDateRange && (
        <div className="flex items-center gap-1.5 text-xs text-muted">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
          </svg>
          <span>
            {promo.startsAt ? formatDate(promo.startsAt) : "Open"}{" "}&ndash;{" "}
            {promo.endsAt ? formatDate(promo.endsAt) : "Ongoing"}
          </span>
        </div>
      )}
    </article>
  );
}
