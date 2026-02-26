"use client";

import { useMemo } from "react";
import { type PosterAnalytics } from "./types";

type Props = {
  analytics: PosterAnalytics[];
  analyticsLoading: boolean;
};

export default function PosterAnalyticsSummary({ analytics, analyticsLoading }: Props) {
  const analyticsSummary = useMemo(() => {
    if (analytics.length === 0) return { totalImpressions: 0, totalClicks: 0, avgCtr: 0 };
    const totalImpressions = analytics.reduce((sum, a) => sum + a.impressionCount, 0);
    const totalClicks = analytics.reduce((sum, a) => sum + a.clickCount, 0);
    const avgCtr = analytics.reduce((sum, a) => sum + a.clickThroughRate, 0) / analytics.length;
    return { totalImpressions, totalClicks, avgCtr };
  }, [analytics]);

  return (
    <section className="mb-5 bg-[rgba(17,17,40,0.7)] border border-line rounded-lg p-4">
      <h2 className="mb-3 text-ink text-[1.05rem]">Analytics Overview</h2>
      {analyticsLoading ? (
        <div className="skeleton h-[80px] rounded-[12px]" />
      ) : analytics.length === 0 ? (
        <p className="text-muted text-base">No analytics data available yet.</p>
      ) : (
        <>
          <div className="grid gap-3 grid-cols-[repeat(auto-fit,minmax(160px,1fr))]">
            <div className="bg-[rgba(255,255,255,0.04)] border border-line rounded-[12px] px-4.5 py-3.5 text-center">
              <div className="text-[1.5rem] font-extrabold text-[#60a5fa]">{analyticsSummary.totalImpressions.toLocaleString()}</div>
              <div className="text-[0.75rem] text-muted mt-0.5">Total Impressions</div>
            </div>
            <div className="bg-[rgba(255,255,255,0.04)] border border-line rounded-[12px] px-4.5 py-3.5 text-center">
              <div className="text-[1.5rem] font-extrabold text-[#34d399]">{analyticsSummary.totalClicks.toLocaleString()}</div>
              <div className="text-[0.75rem] text-muted mt-0.5">Total Clicks</div>
            </div>
            <div className="bg-[rgba(255,255,255,0.04)] border border-line rounded-[12px] px-4.5 py-3.5 text-center">
              <div className="text-[1.5rem] font-extrabold text-[#c084fc]">{analyticsSummary.avgCtr.toFixed(2)}%</div>
              <div className="text-[0.75rem] text-muted mt-0.5">Average CTR</div>
            </div>
          </div>

          {/* Per-poster analytics table */}
          <div className="mt-4 overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
                <tr className="border-b-2 border-line">
                  {["Poster", "Placement", "Impressions", "Clicks", "CTR", "Last Click", "Last Impression"].map((h) => (
                    <th key={h} className="px-2.5 py-2 text-left text-xs text-muted font-bold uppercase tracking-[0.04em]">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {analytics.map((a) => (
                  <tr key={a.id} className="border-b border-line">
                    <td className="px-2.5 py-2 text-[0.82rem] text-ink">{a.name}</td>
                    <td className="px-2.5 py-2 text-[0.78rem] text-ink-light">{a.placement}</td>
                    <td className="px-2.5 py-2 text-[0.82rem] text-[#60a5fa] font-semibold">{a.impressionCount.toLocaleString()}</td>
                    <td className="px-2.5 py-2 text-[0.82rem] text-[#34d399] font-semibold">{a.clickCount.toLocaleString()}</td>
                    <td className="px-2.5 py-2 text-[0.82rem] text-[#c084fc] font-semibold">{a.clickThroughRate.toFixed(2)}%</td>
                    <td className="px-2.5 py-2 text-[0.75rem] text-muted">{a.lastClickAt ? new Date(a.lastClickAt).toLocaleDateString() : "--"}</td>
                    <td className="px-2.5 py-2 text-[0.75rem] text-muted">{a.lastImpressionAt ? new Date(a.lastImpressionAt).toLocaleDateString() : "--"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
