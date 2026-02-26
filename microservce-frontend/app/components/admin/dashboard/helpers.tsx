import { money } from "../../../../lib/format";

/* ───── chart palette ───── */

export const COLORS = ["#00d4ff", "#7c3aed", "#34d399", "#fbbf24", "#f87171", "#fb923c", "#a78bfa", "#38bdf8", "#f472b6", "#4ade80"];
export const CHART_GRID = "rgba(120,120,200,0.08)";
export const CHART_TEXT = "#6868a0";

/* ───── helpers ───── */

export function num(v: number | null | undefined): string {
  return (v ?? 0).toLocaleString();
}
export function pct(v: number | null | undefined): string {
  return `${(v ?? 0).toFixed(1)}%`;
}
export function shortMoney(v: number | null | undefined): string {
  const n = v ?? 0;
  if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `$${(n / 1_000).toFixed(1)}K`;
  return money(n);
}

/* ───── custom tooltip ───── */

export function ChartTooltip({ active, payload, label }: { active?: boolean; payload?: { name: string; value: number; color: string }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-line bg-[rgba(17,17,40,0.95)] px-3.5 py-2.5 text-sm">
      <p className="mb-1.5 text-xs text-muted">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="my-0.5 font-semibold" style={{ color: p.color }}>{p.name}: {typeof p.value === "number" && p.name.toLowerCase().includes("revenue") ? money(p.value) : num(p.value)}</p>
      ))}
    </div>
  );
}

/* ───── skeleton ───── */

export function SkeletonGrid({ count, height = 100 }: { count: number; height?: number }) {
  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="skeleton rounded-lg border border-line bg-white/[0.02] px-6 py-5" style={{ height }} />
      ))}
    </div>
  );
}
