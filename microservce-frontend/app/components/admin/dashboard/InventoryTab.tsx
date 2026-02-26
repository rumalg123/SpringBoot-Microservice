"use client";

import {
  PieChart, Pie, Cell,
  Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import type { InventoryHealthData } from "./types";
import {
  CHART_TEXT,
  num, ChartTooltip, SkeletonGrid,
} from "./helpers";

interface InventoryTabProps {
  invHealth: InventoryHealthData | null;
}

export default function InventoryTab({ invHealth }: InventoryTabProps) {
  if (!invHealth) {
    return <div className="mt-6"><SkeletonGrid count={2} height={200} /></div>;
  }

  return (
    <>
      <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
        <h2 className="mb-4 text-lg font-bold text-ink">Inventory Health</h2>
        <div className="flex flex-wrap items-center gap-8">
          <ResponsiveContainer width={280} height={280}>
            <PieChart>
              <Pie
                data={[
                  { name: "In Stock", value: invHealth.summary?.inStockCount ?? 0 },
                  { name: "Low Stock", value: invHealth.summary?.lowStockCount ?? 0 },
                  { name: "Out of Stock", value: invHealth.summary?.outOfStockCount ?? 0 },
                  { name: "Backorder", value: invHealth.summary?.backorderCount ?? 0 },
                ].filter(d => d.value > 0)}
                dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} innerRadius={50} paddingAngle={3}
              >
                <Cell fill="#34d399" /><Cell fill="#fbbf24" /><Cell fill="#f87171" /><Cell fill="#fb923c" />
              </Pie>
              <Tooltip content={<ChartTooltip />} />
              <Legend wrapperStyle={{ fontSize: "0.78rem", color: CHART_TEXT }} />
            </PieChart>
          </ResponsiveContainer>
          <div className="grid grid-cols-2 gap-4">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total On Hand</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(invHealth.summary?.totalQuantityOnHand)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Reserved</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#7c3aed]">{num(invHealth.summary?.totalQuantityReserved)}</p></div>
          </div>
        </div>
      </div>

      {invHealth.lowStockAlerts?.length > 0 && (
        <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
          <h2 className="mb-4 text-lg font-bold text-ink">Low Stock Alerts</h2>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-base">
              <thead>
                <tr>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">SKU</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Available</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Threshold</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Status</th>
                </tr>
              </thead>
              <tbody>
                {invHealth.lowStockAlerts.slice(0, 20).map((a, i) => (
                  <tr key={i}>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 font-semibold text-ink">{a.sku || a.productId}</td>
                    <td className={`border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 font-semibold ${a.quantityAvailable === 0 ? "text-danger" : "text-warning-text"}`}>{num(a.quantityAvailable)}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">{num(a.lowStockThreshold)}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">
                      <span className={`rounded-sm px-2.5 py-[3px] text-xs font-semibold ${a.stockStatus === "OUT_OF_STOCK" ? "bg-danger-soft text-danger" : "bg-warning-soft text-warning-text"}`}>
                        {(a.stockStatus ?? "").replace(/_/g, " ")}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
