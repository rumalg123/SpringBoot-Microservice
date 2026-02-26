"use client";

import { money } from "../../../../lib/format";
import type { PromotionRoiData } from "./types";
import {
  num, pct, shortMoney, SkeletonGrid,
} from "./helpers";

interface PromotionsTabProps {
  promoRoi: PromotionRoiData | null;
}

export default function PromotionsTab({ promoRoi }: PromotionsTabProps) {
  return (
    <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
      <h2 className="mb-4 text-lg font-bold text-ink">Promotion ROI</h2>
      {!promoRoi ? (
        <SkeletonGrid count={3} height={60} />
      ) : (
        <>
          <div className="mb-5 grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-3">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total Campaigns</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(promoRoi.summary?.totalCampaigns)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Active</p><p className="mt-2 text-[1.2rem] font-extrabold text-success">{num(promoRoi.summary?.activeCampaigns)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total Budget</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{shortMoney(promoRoi.summary?.totalBudget)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Burned</p><p className="mt-2 text-[1.2rem] font-extrabold text-warning-text">{shortMoney(promoRoi.summary?.totalBurnedBudget)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Utilization</p><p className="mt-2 text-[1.2rem] font-extrabold text-brand">{pct(promoRoi.summary?.budgetUtilizationPercent)}</p></div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-base">
              <thead>
                <tr>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Campaign</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Type</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Budget</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Burned</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Utilization</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Flash Sale</th>
                </tr>
              </thead>
              <tbody>
                {promoRoi.campaigns?.length > 0 ? promoRoi.campaigns.map((c) => (
                  <tr key={c.campaignId}>
                    <td className="border-b border-line px-3 py-2.5 font-semibold text-ink">{c.name}</td>
                    <td className="border-b border-line px-3 py-2.5 text-ink">{(c.benefitType ?? "").replace(/_/g, " ")}</td>
                    <td className="border-b border-line px-3 py-2.5 text-ink">{money(c.budgetAmount)}</td>
                    <td className="border-b border-line px-3 py-2.5 text-ink">{money(c.burnedBudgetAmount)}</td>
                    <td className="border-b border-line px-3 py-2.5 text-ink">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 overflow-hidden rounded-[3px] bg-white/[0.06]" style={{ height: 6 }}>
                          <div
                            className={`h-full rounded-[3px] ${c.utilizationPercent > 80 ? "bg-danger" : c.utilizationPercent > 50 ? "bg-warning-text" : "bg-success"}`}
                            style={{ width: `${Math.min(100, c.utilizationPercent)}%` }}
                          />
                        </div>
                        <span className="min-w-[40px] text-xs text-muted">{pct(c.utilizationPercent)}</span>
                      </div>
                    </td>
                    <td className="border-b border-line px-3 py-2.5 text-ink">{c.isFlashSale ? <span className="font-semibold text-danger">Yes</span> : "No"}</td>
                  </tr>
                )) : (
                  <tr><td colSpan={6} className="border-b border-line px-3 py-2.5 text-center text-muted">No campaigns.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
