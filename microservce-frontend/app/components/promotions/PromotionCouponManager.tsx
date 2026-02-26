"use client";

import { useEffect, useRef, useState } from "react";
import type { PublicPromotion } from "../../../lib/types/promotion";

/* ───── helpers ───── */

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

/* ───── countdown hook ───── */

function useCountdown(endIso: string | null): string {
  const [label, setLabel] = useState("");

  useEffect(() => {
    if (!endIso) { setLabel(""); return; }

    const tick = () => {
      const diff = new Date(endIso).getTime() - Date.now();
      if (diff <= 0) { setLabel("Ended"); return; }
      const d = Math.floor(diff / 86_400_000);
      const h = Math.floor((diff % 86_400_000) / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1_000);
      if (d > 0) setLabel(`${d}d ${h}h ${m}m ${s}s`);
      else if (h > 0) setLabel(`${h}h ${m}m ${s}s`);
      else setLabel(`${m}m ${s}s`);
    };

    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [endIso]);

  return label;
}

/* ───── flash sale card ───── */

function FlashSaleCard({ promo }: { promo: PublicPromotion }) {
  const countdown = useCountdown(promo.flashSaleEndAt);
  const bc = benefitColor(promo.benefitType);

  return (
    <article
      className="relative shrink-0 overflow-hidden rounded-lg border border-transparent bg-[rgba(255,255,255,0.03)] px-5 py-6"
      style={{ minWidth: "280px", maxWidth: "340px", boxShadow: "0 0 20px rgba(0,212,255,0.08), inset 0 0 20px rgba(0,212,255,0.02)" }}
    >
      {/* gradient border overlay */}
      <div
        className="pointer-events-none absolute inset-0 rounded-lg p-px"
        style={{
          background: "linear-gradient(135deg, var(--brand), var(--accent))",
          WebkitMask: "linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)",
          WebkitMaskComposite: "xor",
          maskComposite: "exclude",
        }}
      />
      {/* flash badge */}
      <div className="mb-3 inline-flex items-center gap-1.5 rounded-xl border border-[rgba(251,191,36,0.3)] bg-[linear-gradient(135deg,rgba(251,191,36,0.15),rgba(244,114,182,0.15))] px-3 py-1 text-[0.65rem] font-extrabold uppercase tracking-[0.1em] text-[#fbbf24]">

        <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
        FLASH SALE
      </div>

      <h3 className="mb-2 mt-0 font-[Syne,sans-serif] text-[1.05rem] font-extrabold leading-[1.3] text-white">
        {promo.name}
      </h3>

      {/* benefit */}
      <span
        style={{
          display: "inline-block",
          padding: "4px 14px",
          borderRadius: "20px",
          background: bc.bg,
          border: `1px solid ${bc.border}`,
          color: bc.text,
          fontSize: "0.78rem",
          fontWeight: 800,
          marginBottom: "14px",
        }}
      >
        {formatBenefit(promo)}
      </span>

      {/* countdown */}
      <div className="mt-1 flex items-center gap-2">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
        </svg>
        <span
          className="text-sm font-bold tabular-nums"
          style={{ color: countdown === "Ended" ? "var(--danger)" : "var(--brand)" }}
        >
          {countdown || "..."}
        </span>
      </div>
    </article>
  );
}

/* ───── props ───── */

type PromotionCouponManagerProps = {
  flashSales: PublicPromotion[];
  flashLoading: boolean;
};

export default function PromotionCouponManager({ flashSales, flashLoading }: PromotionCouponManagerProps) {
  const flashScrollRef = useRef<HTMLDivElement>(null);

  const scrollFlash = (dir: "left" | "right") => {
    if (!flashScrollRef.current) return;
    const amount = 310;
    flashScrollRef.current.scrollBy({ left: dir === "left" ? -amount : amount, behavior: "smooth" });
  };

  if (flashLoading || flashSales.length === 0) return null;

  return (
    <section className="mb-10">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="#fbbf24"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
          <h2 className="m-0 font-[Syne,sans-serif] text-[1.2rem] font-extrabold text-white">
            Flash Sales
          </h2>
          <span className="rounded-xl border border-[rgba(251,191,36,0.25)] bg-[rgba(251,191,36,0.12)] px-2.5 py-[3px] text-[0.65rem] font-bold tracking-[0.08em] text-[#fbbf24]">

            LIMITED TIME
          </span>
        </div>
        {/* scroll arrows */}
        <div className="flex gap-1.5">
          <button
            type="button"
            onClick={() => scrollFlash("left")}
            className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.03)] text-muted"
            aria-label="Scroll left"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
          </button>
          <button
            type="button"
            onClick={() => scrollFlash("right")}
            className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-[8px] border border-line-bright bg-[rgba(255,255,255,0.03)] text-muted"
            aria-label="Scroll right"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
          </button>
        </div>
      </div>

      {/* Horizontal scroll container */}
      <div
        ref={flashScrollRef}
        className="flex gap-4 overflow-x-auto pb-2"
        style={{ scrollbarWidth: "thin", scrollbarColor: "var(--line-bright) transparent" }}
      >
        {flashSales.map((fs) => (
          <FlashSaleCard key={fs.id} promo={fs} />
        ))}
      </div>
    </section>
  );
}
