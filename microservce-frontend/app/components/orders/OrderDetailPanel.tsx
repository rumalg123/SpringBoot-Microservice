"use client";

import type { OrderDetail } from "../../../lib/types/order";
import { money } from "../../../lib/format";

type StatusColorEntry = { bg: string; border: string; color: string };
type PaymentInfo = { status: string; paymentMethod: string; cardNoMasked: string | null; paidAt: string | null };

const CANCELLABLE = new Set(["PENDING", "PAYMENT_PENDING", "PAYMENT_FAILED", "CONFIRMED"]);

export type OrderDetailPanelProps = {
  detail: OrderDetail;
  statusColors: Record<string, StatusColorEntry>;
  onPayNow: (orderId: string) => void;
  payingOrderId: string | null;
  onCancelOrder: (orderId: string) => void;
  cancellingOrderId: string | null;
  cancelReason: string;
  onCancelReasonChange: (reason: string) => void;
  paymentInfo: PaymentInfo | null;
};

export default function OrderDetailPanel({
  detail,
  statusColors,
  onPayNow,
  payingOrderId,
  onCancelOrder,
  cancellingOrderId,
  cancelReason,
  onCancelReasonChange,
  paymentInfo,
}: OrderDetailPanelProps) {
  const dsc = statusColors[detail.status] || statusColors.CONFIRMED;

  return (
    <div className="mt-3 rounded-[12px] border border-line-bright bg-brand-soft overflow-hidden">
      <div className="px-[14px] py-3 border-b border-brand-soft">
        <div className="flex items-center gap-2 mb-[6px]">
          <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full border" style={{ background: dsc.bg, borderColor: dsc.border, color: dsc.color }}>
            {detail.status || "PLACED"}
          </span>
          {detail.couponCode && (
            <span className="text-[0.72rem] font-bold px-[10px] py-[3px] rounded-full bg-success-soft border border-success-glow text-success">
              Coupon: {detail.couponCode}
            </span>
          )}
        </div>
        <p className="font-mono text-[0.65rem] text-muted-2 mb-1">{detail.id}</p>
        <p className="text-[0.78rem] text-muted m-0">
          Placed:{" "}
          <span className="text-ink-light font-semibold">
            {new Date(detail.createdAt).toLocaleString()}
          </span>
        </p>
        {(detail.status === "PAYMENT_PENDING" || detail.status === "PAYMENT_FAILED") && (
          <button
            onClick={() => { onPayNow(detail.id); }}
            disabled={Boolean(payingOrderId)}
            className={`mt-[10px] px-4 py-2 rounded-[9px] border-none bg-[image:var(--gradient-brand)] text-white text-[0.78rem] font-bold inline-flex items-center gap-[6px] ${payingOrderId ? "cursor-not-allowed" : "cursor-pointer"}`}
          >
            {payingOrderId === detail.id && <span className="spinner-sm" />}
            {payingOrderId === detail.id ? "Redirecting..." : "Pay Now"}
          </button>
        )}
        {CANCELLABLE.has(detail.status) && (
          <div className="mt-[10px] flex gap-[6px] items-center flex-wrap">
            <input
              value={cancelReason}
              onChange={(e) => onCancelReasonChange(e.target.value)}
              placeholder="Cancel reason (optional)"
              className="form-input text-[0.72rem] px-[10px] py-[6px] flex-1 min-w-[120px]"
              maxLength={240}
            />
            <button
              onClick={() => { onCancelOrder(detail.id); }}
              disabled={Boolean(cancellingOrderId)}
              className={`px-[14px] py-[7px] rounded-[9px] border border-[rgba(239,68,68,0.25)] bg-[rgba(239,68,68,0.06)] text-danger text-[0.75rem] font-bold inline-flex items-center gap-[6px] ${cancellingOrderId ? "cursor-not-allowed" : "cursor-pointer"}`}
            >
              {cancellingOrderId === detail.id && <span className="spinner-sm" />}
              {cancellingOrderId === detail.id ? "Cancelling..." : "Cancel Order"}
            </button>
          </div>
        )}
      </div>

      {/* Price Breakdown */}
      <div className="px-[14px] py-3 border-b border-brand-soft text-[0.78rem]">
        {[
          { label: "Subtotal", value: detail.subtotal, show: true },
          { label: "Line Discounts", value: -(detail.lineDiscountTotal || 0), show: (detail.lineDiscountTotal || 0) > 0, isDiscount: true },
          { label: "Cart Discounts", value: -(detail.cartDiscountTotal || 0), show: (detail.cartDiscountTotal || 0) > 0, isDiscount: true },
          { label: "Shipping", value: detail.shippingAmount || 0, show: true },
          { label: "Shipping Discount", value: -(detail.shippingDiscountTotal || 0), show: (detail.shippingDiscountTotal || 0) > 0, isDiscount: true },
        ].filter((r) => r.show).map(({ label, value, isDiscount }) => (
          <div key={label} className="flex justify-between mb-1">
            <span className={isDiscount ? "text-success" : "text-muted"}>{label}</span>
            <span className={`font-semibold ${isDiscount ? "text-success" : "text-ink-light"}`}>
              {isDiscount ? `−${money(Math.abs(value))}` : money(value)}
            </span>
          </div>
        ))}
        {(detail.totalDiscount || 0) > 0 && (
          <div className="flex justify-between mb-1 font-bold">
            <span className="text-success">Total Savings</span>
            <span className="text-success">−{money(detail.totalDiscount)}</span>
          </div>
        )}
        <div className="flex justify-between border-t border-line-bright pt-2 mt-1 font-extrabold">
          <span className="text-white">Grand Total</span>
          <span className="text-brand text-lg">{money(detail.orderTotal || detail.subtotal)}</span>
        </div>
      </div>

      {/* Payment Info */}
      {paymentInfo && (
        <div className="px-[14px] py-3 border-b border-brand-soft text-[0.78rem]">
          <p className="text-[0.6rem] font-bold uppercase tracking-[0.1em] text-brand mb-2">Payment</p>
          <div className="grid grid-cols-2 gap-[6px]">
            <div>
              <span className="text-muted">Status: </span>
              <span className={`font-bold ${paymentInfo.status === "COMPLETED" ? "text-success" : paymentInfo.status === "FAILED" ? "text-danger" : "text-warning-text"}`}>
                {paymentInfo.status}
              </span>
            </div>
            <div>
              <span className="text-muted">Method: </span>
              <span className="text-ink-light font-semibold">{paymentInfo.paymentMethod || "—"}</span>
            </div>
            {paymentInfo.cardNoMasked && (
              <div>
                <span className="text-muted">Card: </span>
                <span className="text-ink-light font-mono">{paymentInfo.cardNoMasked}</span>
              </div>
            )}
            {paymentInfo.paidAt && (
              <div>
                <span className="text-muted">Paid: </span>
                <span className="text-ink-light font-semibold">{new Date(paymentInfo.paidAt).toLocaleString()}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {(detail.shippingAddress || detail.billingAddress) && (
        <div className="px-[14px] py-3 grid gap-2 grid-cols-2 border-b border-brand-soft">
          {[
            { label: "Shipping", addr: detail.shippingAddress },
            { label: "Billing", addr: detail.billingAddress },
          ].filter(({ addr }) => addr).map(({ label, addr }) => (
            <div key={label} className="rounded-[8px] border border-line-bright px-[10px] py-2">
              <p className="text-[0.6rem] font-bold uppercase tracking-[0.1em] text-brand mb-1">{label}</p>
              <p className="text-[0.75rem] font-bold text-white m-0">{addr!.recipientName}</p>
              <p className="text-[0.7rem] text-muted mt-[2px] m-0">{addr!.line1}{addr!.line2 ? `, ${addr!.line2}` : ""}, {addr!.city}, {addr!.state}</p>
            </div>
          ))}
        </div>
      )}

      {detail.warnings && detail.warnings.length > 0 && (
        <div className="alert alert-warning mx-[14px] my-3 text-[0.75rem]">
          {detail.warnings.map((w, i) => <p key={i} className={i > 0 ? "mt-1" : ""}>{w}</p>)}
        </div>
      )}

      <div className="overflow-auto">
        <table className="w-full border-collapse text-[0.78rem]">
          <thead>
            <tr className="bg-brand-soft">
              {["Item", "Qty", "Row ID"].map((h) => (
                <th key={h} className="px-3 py-2 text-left font-bold text-muted text-[0.65rem] uppercase tracking-[0.08em]">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {detail.items?.map((row, idx) => (
              <tr key={row.id || `${row.item}-${idx}`} className="border-t border-brand-soft">
                <td className="px-3 py-2 text-ink-light">{row.item}</td>
                <td className="px-3 py-2 text-brand font-bold">{row.quantity}</td>
                <td className="px-3 py-2 font-mono text-[0.6rem] text-muted-2">{row.id || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
