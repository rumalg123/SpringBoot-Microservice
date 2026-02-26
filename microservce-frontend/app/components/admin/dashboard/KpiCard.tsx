"use client";

export default function KpiCard({ label, value, accent, sub }: { label: string; value: string; accent?: string; sub?: string }) {
  return (
    <div className="rounded-lg border border-line bg-white/[0.03] px-6 py-5">
      <p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">{label}</p>
      <p className="mt-2 text-[1.6rem] font-extrabold" style={{ color: accent || "var(--ink)" }}>{value}</p>
      {sub && <p className="mt-1 text-xs text-muted">{sub}</p>}
    </div>
  );
}
