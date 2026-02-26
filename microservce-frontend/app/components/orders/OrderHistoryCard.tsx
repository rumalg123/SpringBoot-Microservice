"use client";

import type { Order } from "../../../lib/types/order";
import { money } from "../../../lib/format";

type StatusColorEntry = { bg: string; border: string; color: string };

const CANCELLABLE = new Set(["PENDING", "PAYMENT_PENDING", "PAYMENT_FAILED", "CONFIRMED"]);

export type OrderHistoryCardProps = {
  order: Order;
  statusColors: Record<string, StatusColorEntry>;
  onViewDetails: (orderId: string) => void;
  onPayNow: (orderId: string) => void;
  onCancelOrder: (orderId: string) => void;
  payingOrderId: string | null;
  detailLoadingTarget: string | null;
  cancellingOrderId: string | null;
  cancelReason: string;
  onCancelReasonChange: (reason: string) => void;
  onConfirmCancel: (orderId: string) => void;
  onDismissCancel: () => void;
  index: number;
};

export default function OrderHistoryCard({
  order,
  statusColors,
  onViewDetails,
  onPayNow,
  onCancelOrder,
  payingOrderId,
  detailLoadingTarget,
  cancellingOrderId,
  cancelReason,
  onCancelReasonChange,
  onConfirmCancel,
  onDismissCancel,
  index,
}: OrderHistoryCardProps) {
  const sc = statusColors[order.status] || statusColors.CONFIRMED;

  return (
    <article
      className="animate-rise glass-card px-5 py-4"
      style={{ animationDelay: `${index * 50}ms` }}
    >
      <div className="flex flex-wrap items-start justify-between gap-[10px]">
        <div className="flex-1">
          <p className="font-bold text-white text-[0.9rem] m-0">{order.item}</p>
          {order.orderNumber && (
            <p className="mt-1 text-[0.65rem] text-muted-2">
              Order {order.orderNumber}
            </p>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full border" style={{ background: sc.bg, borderColor: sc.border, color: sc.color }}>
              {order.status || "PLACED"}
            </span>
            <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full bg-brand-soft border border-line-bright text-brand">
              {money(order.orderTotal || order.subtotal)}
            </span>
            {order.couponCode && (
              <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full bg-success-soft border border-success-glow text-success">
                🏷 {order.couponCode}
              </span>
            )}
            <span className="text-[0.72rem] text-muted">
              {new Date(order.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" })}
            </span>
          </div>
        </div>
        <div className="flex flex-col gap-[6px] items-end">
          {(order.status === "PAYMENT_PENDING" || order.status === "PAYMENT_FAILED") && (
            <button
              onClick={() => { onPayNow(order.id); }}
              disabled={Boolean(payingOrderId)}
              className={`px-[14px] py-[7px] rounded-[9px] border-none bg-[image:var(--gradient-brand)] text-white text-[0.75rem] font-bold whitespace-nowrap inline-flex items-center gap-[6px] ${payingOrderId ? "cursor-not-allowed" : "cursor-pointer"}`}
              style={{ opacity: payingOrderId && payingOrderId !== order.id ? 0.5 : 1 }}
            >
              {payingOrderId === order.id && <span className="spinner-sm" />}
              {payingOrderId === order.id ? "Redirecting..." : "Pay Now"}
            </button>
          )}
          <button
            onClick={() => { onViewDetails(order.id); }}
            disabled={Boolean(detailLoadingTarget)}
            className={`px-[14px] py-[7px] rounded-[9px] border border-line-bright bg-brand-soft text-brand text-[0.75rem] font-bold whitespace-nowrap ${detailLoadingTarget ? "cursor-not-allowed" : "cursor-pointer"}`}
            style={{ opacity: detailLoadingTarget && detailLoadingTarget !== order.id ? 0.5 : 1 }}
          >
            {detailLoadingTarget === order.id ? "Loading..." : "View Details"}
          </button>
          {CANCELLABLE.has(order.status) && (
            <>
              {cancellingOrderId === order.id ? (
                <div className="flex gap-1">
                  <input
                    value={cancelReason}
                    onChange={(e) => onCancelReasonChange(e.target.value)}
                    placeholder="Reason (optional)"
                    className="form-input text-[0.7rem] px-2 py-[5px] w-[130px]"
                    maxLength={240}
                  />
                  <button
                    onClick={() => { onConfirmCancel(order.id); }}
                    className="px-[10px] py-[5px] rounded-[8px] border-none bg-danger text-white text-[0.7rem] font-bold cursor-pointer whitespace-nowrap"
                  >
                    Confirm
                  </button>
                  <button
                    onClick={() => { onDismissCancel(); }}
                    className="px-2 py-[5px] rounded-[8px] border border-line-bright bg-transparent text-muted text-[0.7rem] font-bold cursor-pointer"
                  >
                    No
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => onCancelOrder(order.id)}
                  disabled={Boolean(cancellingOrderId)}
                  className={`px-[14px] py-[7px] rounded-[9px] border border-[rgba(239,68,68,0.25)] bg-[rgba(239,68,68,0.06)] text-danger text-[0.75rem] font-bold whitespace-nowrap ${cancellingOrderId ? "cursor-not-allowed" : "cursor-pointer"}`}
                >
                  Cancel Order
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </article>
  );
}
