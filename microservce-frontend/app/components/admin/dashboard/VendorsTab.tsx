"use client";

import type { VendorLeaderboardData } from "./types";
import {
  num, pct, SkeletonGrid,
} from "./helpers";

interface VendorsTabProps {
  vendorBoard: VendorLeaderboardData | null;
}

export default function VendorsTab({ vendorBoard }: VendorsTabProps) {
  return (
    <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
      <h2 className="mb-4 text-lg font-bold text-ink">Vendor Leaderboard</h2>
      {!vendorBoard ? (
        <SkeletonGrid count={3} height={60} />
      ) : (
        <>
          <div className="mb-5 grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3">
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Total</p><p className="mt-2 text-[1.2rem] font-extrabold text-ink">{num(vendorBoard.summary?.totalVendors)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Active</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#34d399]">{num(vendorBoard.summary?.activeVendors)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Pending</p><p className="mt-2 text-[1.2rem] font-extrabold text-[#fbbf24]">{num(vendorBoard.summary?.pendingVendors)}</p></div>
            <div><p className="m-0 text-xs uppercase tracking-[0.08em] text-muted">Verified</p><p className="mt-2 text-[1.2rem] font-extrabold text-brand">{num(vendorBoard.summary?.verifiedVendors)}</p></div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-base">
              <thead>
                <tr>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">#</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Vendor</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Orders</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Rating</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Fulfillment</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Disputes</th>
                  <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Verified</th>
                </tr>
              </thead>
              <tbody>
                {vendorBoard.leaderboard?.length > 0 ? vendorBoard.leaderboard.map((v, i) => (
                  <tr key={v.id}>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">{i + 1}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 font-semibold text-ink">{v.name}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">{num(v.totalOrdersCompleted)}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-[#fbbf24]">{(v.averageRating ?? 0).toFixed(1)}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">{pct(v.fulfillmentRate)}</td>
                    <td className={`border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 ${(v.disputeRate ?? 0) > 5 ? "text-[#f87171]" : "text-ink"}`}>{pct(v.disputeRate)}</td>
                    <td className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-ink">{v.verified ? "Yes" : "No"}</td>
                  </tr>
                )) : (
                  <tr><td colSpan={7} className="border-b border-[rgba(120,120,200,0.06)] px-3 py-2.5 text-center text-muted">No vendor data.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
