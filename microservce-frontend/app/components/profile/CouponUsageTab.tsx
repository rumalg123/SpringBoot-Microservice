"use client";

import Link from "next/link";
import type { CouponUsageEntry } from "../../../lib/types/customer";

type CouponUsageData = {
  content: CouponUsageEntry[];
  totalPages: number;
  totalElements: number;
  number: number;
};

type CouponUsageTabProps = {
  couponUsage: CouponUsageData | null;
  couponUsageLoading: boolean;
  couponUsagePage: number;
  onPageChange: (page: number) => void;
};

export default function CouponUsageTab({
  couponUsage,
  couponUsageLoading,
  couponUsagePage,
  onPageChange,
}: CouponUsageTabProps) {
  return (
    <article className="glass-card p-6">
      <div className="flex items-center gap-3 mb-5">
        <div className="w-[48px] h-[48px] rounded-full shrink-0 bg-[image:var(--gradient-brand)] grid place-items-center">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 12 20 22 4 22 4 12" />
            <rect x="2" y="7" width="20" height="5" />
            <line x1="12" y1="22" x2="12" y2="7" />
            <path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z" />
            <path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z" />
          </svg>
        </div>
        <div>
          <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-1 mt-0">Coupon Usage History</h2>
          <p className="text-[0.75rem] text-muted m-0">
            Coupons and discounts applied to your orders.
            {couponUsage ? ` ${couponUsage.totalElements} total.` : ""}
          </p>
        </div>
      </div>

      {couponUsageLoading && !couponUsage && (
        <div className="text-center py-6">
          <div className="spinner-lg" />
          <p className="mt-3 text-muted text-[0.82rem]">Loading coupon history...</p>
        </div>
      )}

      {couponUsage && couponUsage.content.length === 0 && (
        <p className="text-[0.82rem] text-muted">No coupons used yet.</p>
      )}

      {couponUsage && couponUsage.content.length > 0 && (
        <>
          {/* Table */}
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-[0.82rem]">
              <thead>
                <tr>
                  {["Coupon Code", "Promotion", "Discount", "Order ID", "Date"].map((header) => (
                    <th
                      key={header}
                      className="text-left px-[14px] py-[10px] text-[0.65rem] font-bold uppercase tracking-[0.1em] text-muted border-b border-line"
                    >
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {couponUsage.content.map((entry) => (
                  <tr key={entry.reservationId} className="border-b border-line">
                    <td className="px-[14px] py-3 font-mono font-bold text-brand">
                      {entry.couponCode}
                    </td>
                    <td className="px-[14px] py-3 text-ink-light">
                      {entry.promotionName}
                    </td>
                    <td className="px-[14px] py-3 font-bold text-[#4ade80]">
                      -${entry.discountAmount.toFixed(2)}
                    </td>
                    <td className="px-[14px] py-3">
                      <Link
                        href={`/orders/${entry.orderId}`}
                        className="text-brand underline font-mono text-[0.78rem]"
                      >
                        {entry.orderId.length > 12 ? `${entry.orderId.slice(0, 12)}...` : entry.orderId}
                      </Link>
                    </td>
                    <td className="px-[14px] py-3 text-muted text-[0.78rem]">
                      {new Date(entry.committedAt).toLocaleDateString("en-US", {
                        year: "numeric", month: "short", day: "numeric",
                      })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {couponUsage.totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <button
                onClick={() => onPageChange(couponUsagePage - 1)}
                disabled={couponUsagePage === 0 || couponUsageLoading}
                className={`px-[14px] py-2 rounded-[8px] border border-line-bright bg-brand-soft text-brand text-[0.78rem] font-bold ${couponUsagePage === 0 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}
              >
                Previous
              </button>
              <span className="text-[0.78rem] text-muted px-2">
                Page {couponUsagePage + 1} of {couponUsage.totalPages}
              </span>
              <button
                onClick={() => onPageChange(couponUsagePage + 1)}
                disabled={couponUsagePage >= couponUsage.totalPages - 1 || couponUsageLoading}
                className={`px-[14px] py-2 rounded-[8px] border border-line-bright bg-brand-soft text-brand text-[0.78rem] font-bold ${couponUsagePage >= couponUsage.totalPages - 1 ? "cursor-not-allowed opacity-40" : "cursor-pointer"}`}
              >
                Next
              </button>
            </div>
          )}

          {/* Loading overlay for pagination */}
          {couponUsageLoading && couponUsage && (
            <div className="text-center py-3">
              <div className="spinner-sm inline-block" />
              <span className="ml-2 text-muted text-[0.78rem]">Loading...</span>
            </div>
          )}
        </>
      )}
    </article>
  );
}
