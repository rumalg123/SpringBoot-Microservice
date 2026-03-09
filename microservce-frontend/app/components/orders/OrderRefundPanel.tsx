"use client";

import { useMemo, useState } from "react";
import { money } from "../../../lib/format";
import type { VendorOrder } from "../../../lib/types/order";
import type { RefundRequest } from "../../../lib/hooks/queries/useRefunds";

type StatusColorEntry = { bg: string; border: string; color: string };

type Props = {
  orderId: string;
  vendorOrders: VendorOrder[];
  refunds: RefundRequest[];
  statusColors: Record<string, StatusColorEntry>;
  creating: boolean;
  onCreateRefund: (payload: { orderId: string; vendorOrderId: string; refundAmount: number; reason: string }) => void;
};

const OPENABLE_STATUSES = new Set(["DELIVERED", "RETURN_REJECTED"]);
const ACTIVE_REFUND_STATUSES = new Set(["REQUESTED", "VENDOR_APPROVED", "VENDOR_REJECTED", "ESCALATED_TO_ADMIN", "REFUND_PROCESSING", "REFUND_FAILED"]);

function formatStatusLabel(value: string) {
  return value.replace(/_/g, " ");
}

export default function OrderRefundPanel({
  orderId,
  vendorOrders,
  refunds,
  statusColors,
  creating,
  onCreateRefund,
}: Props) {
  const [expandedVendorOrderId, setExpandedVendorOrderId] = useState<string | null>(null);
  const [amountByVendorOrder, setAmountByVendorOrder] = useState<Record<string, string>>({});
  const [reasonByVendorOrder, setReasonByVendorOrder] = useState<Record<string, string>>({});

  const refundsByVendorOrder = useMemo(() => {
    return refunds.reduce<Record<string, RefundRequest[]>>((acc, refund) => {
      const key = refund.vendorOrderId;
      if (!acc[key]) {
        acc[key] = [];
      }
      acc[key].push(refund);
      acc[key].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
      return acc;
    }, {});
  }, [refunds]);

  if (vendorOrders.length === 0) {
    return null;
  }

  return (
    <section className="glass-card p-5">
      <div className="mb-3.5 flex items-center gap-2.5">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 7h18" />
          <path d="M6 7V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2" />
          <path d="M6 7v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7" />
          <path d="M9 12h6" />
        </svg>
        <div>
          <h3 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold text-white">
            Refunds by Vendor
          </h3>
          <p className="m-0 text-[0.72rem] text-muted">
            Marketplace refunds are opened per vendor shipment, not against the entire mixed cart order.
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        {vendorOrders.map((vendorOrder) => {
          const vendorRefunds = refundsByVendorOrder[vendorOrder.id] ?? [];
          const latestRefund = vendorRefunds[0] ?? null;
          const activeRefund = vendorRefunds.find((refund) => ACTIVE_REFUND_STATUSES.has(refund.status)) ?? null;
          const openable = OPENABLE_STATUSES.has(vendorOrder.status);
          const remainingRefundable = Math.max(
            0,
            Number(vendorOrder.orderTotal || 0) - Number(vendorOrder.refundedAmount || 0)
          );
          const isExpanded = expandedVendorOrderId === vendorOrder.id;
          const canRequestRefund = openable && !activeRefund && remainingRefundable > 0;
          const enteredAmount = amountByVendorOrder[vendorOrder.id] ?? `${remainingRefundable.toFixed(2)}`;
          const enteredReason = reasonByVendorOrder[vendorOrder.id] ?? "";
          const statusTheme = statusColors[vendorOrder.status] || statusColors.CONFIRMED;

          return (
            <article key={vendorOrder.id} className="rounded-[12px] border border-line-bright bg-brand-soft/40 px-4 py-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <span
                      className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full border"
                      style={{ background: statusTheme.bg, borderColor: statusTheme.border, color: statusTheme.color }}
                    >
                      {formatStatusLabel(vendorOrder.status)}
                    </span>
                    <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full bg-brand-soft border border-line-bright text-brand">
                      {money(vendorOrder.orderTotal)}
                    </span>
                    <span className="text-[0.72rem] text-muted">
                      Refundable left: {money(remainingRefundable)}
                    </span>
                  </div>
                  <p className="m-0 text-[0.78rem] text-ink-light font-semibold">Vendor shipment {vendorOrder.id}</p>
                  <p className="mt-1 mb-0 text-[0.72rem] text-muted">
                    {vendorOrder.itemCount} item{vendorOrder.itemCount === 1 ? "" : "s"} • {vendorOrder.quantity} unit{vendorOrder.quantity === 1 ? "" : "s"}
                  </p>
                  {latestRefund && (
                    <div className="mt-3 rounded-[10px] border border-line-bright bg-[rgba(255,255,255,0.03)] px-3 py-3">
                      <p className="m-0 text-[0.72rem] font-bold text-white">
                        Latest refund: {formatStatusLabel(latestRefund.status)} for {money(latestRefund.refundAmount)}
                      </p>
                      <p className="mt-1 mb-0 text-[0.72rem] text-muted">
                        Requested {new Date(latestRefund.createdAt).toLocaleString()}
                      </p>
                      {latestRefund.customerReason && (
                        <p className="mt-2 mb-0 text-[0.74rem] text-ink-light">Reason: {latestRefund.customerReason}</p>
                      )}
                      {latestRefund.vendorResponseNote && (
                        <p className="mt-2 mb-0 text-[0.74rem] text-warning-text">Vendor: {latestRefund.vendorResponseNote}</p>
                      )}
                      {latestRefund.adminNote && (
                        <p className="mt-2 mb-0 text-[0.74rem] text-brand">Platform: {latestRefund.adminNote}</p>
                      )}
                    </div>
                  )}
                </div>

                <div className="flex flex-col items-end gap-2">
                  {canRequestRefund && (
                    <button
                      type="button"
                      onClick={() => setExpandedVendorOrderId(isExpanded ? null : vendorOrder.id)}
                      className="px-[14px] py-[7px] rounded-[9px] border border-line-bright bg-brand-soft text-brand text-[0.75rem] font-bold cursor-pointer"
                    >
                      {isExpanded ? "Hide Request Form" : "Request Refund"}
                    </button>
                  )}
                  {!canRequestRefund && activeRefund && (
                    <span className="text-[0.72rem] text-warning-text font-semibold">
                      Refund already in progress
                    </span>
                  )}
                  {!canRequestRefund && !activeRefund && !openable && (
                    <span className="text-[0.72rem] text-muted">
                      Available only after delivery
                    </span>
                  )}
                </div>
              </div>

              {isExpanded && canRequestRefund && (
                <div className="mt-4 rounded-[10px] border border-line-bright bg-[rgba(255,255,255,0.03)] px-4 py-4">
                  <div className="grid gap-3 md:grid-cols-[180px_minmax(0,1fr)]">
                    <label className="flex flex-col gap-1">
                      <span className="text-[0.72rem] font-bold uppercase tracking-[0.08em] text-muted">Refund Amount</span>
                      <input
                        type="number"
                        min="0.01"
                        max={remainingRefundable.toFixed(2)}
                        step="0.01"
                        value={enteredAmount}
                        onChange={(event) =>
                          setAmountByVendorOrder((current) => ({
                            ...current,
                            [vendorOrder.id]: event.target.value,
                          }))
                        }
                        className="form-input"
                      />
                    </label>
                    <label className="flex flex-col gap-1">
                      <span className="text-[0.72rem] font-bold uppercase tracking-[0.08em] text-muted">Reason</span>
                      <textarea
                        rows={3}
                        maxLength={1000}
                        value={enteredReason}
                        onChange={(event) =>
                          setReasonByVendorOrder((current) => ({
                            ...current,
                            [vendorOrder.id]: event.target.value,
                          }))
                        }
                        placeholder="Describe the issue with this vendor shipment"
                        className="form-input resize-y"
                      />
                    </label>
                  </div>
                  <div className="mt-3 flex items-center justify-between gap-3 flex-wrap">
                    <p className="m-0 text-[0.72rem] text-muted">
                      Refund requests stay with the vendor first, then escalate to platform review if needed.
                    </p>
                    <button
                      type="button"
                      disabled={
                        creating
                        || Number.isNaN(Number(enteredAmount))
                        || Number(enteredAmount) <= 0
                        || Number(enteredAmount) > remainingRefundable
                        || enteredReason.trim().length < 10
                      }
                      onClick={() =>
                        onCreateRefund({
                          orderId,
                          vendorOrderId: vendorOrder.id,
                          refundAmount: Number(enteredAmount),
                          reason: enteredReason.trim(),
                        })
                      }
                      className="rounded-md bg-[var(--gradient-brand)] px-[18px] py-[9px] text-sm font-bold text-white shadow-[0_0_14px_var(--line-bright)] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {creating ? "Submitting..." : "Submit Refund Request"}
                    </button>
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}
