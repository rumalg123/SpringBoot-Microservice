"use client";

import type { VendorOrderDetail } from "./types";
import { formatCurrency } from "./types";

type Props = {
  orderDetail: VendorOrderDetail | null;
  detailLoading: boolean;
};

export default function VendorOrderDetailPanel({ orderDetail, detailLoading }: Props) {
  return (
    <div className="bg-[rgba(17,17,40,0.5)] border border-line rounded-[12px] px-5 py-4 mt-1 mb-2">
      {detailLoading ? (
        <p className="text-muted text-[0.82rem] text-center py-4">
          Loading order details...
        </p>
      ) : orderDetail ? (
        <div className="flex flex-col gap-5">
          {/* ── Summary row ── */}
          <div className="grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-4">
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Vendor Order ID</p>
              <p className="text-[0.82rem] text-ink font-mono text-[0.74rem]">{orderDetail.id}</p>
            </div>
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Order Total</p>
              <p className="text-[0.82rem] text-ink">{formatCurrency(orderDetail.orderTotal)} {orderDetail.currency}</p>
            </div>
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Discount</p>
              <p className="text-[0.82rem] text-ink">{formatCurrency(orderDetail.discountAmount)}</p>
            </div>
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Shipping</p>
              <p className="text-[0.82rem] text-ink">{formatCurrency(orderDetail.shippingAmount)}</p>
            </div>
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Platform Fee</p>
              <p className="text-[0.82rem] text-ink">{formatCurrency(orderDetail.platformFee)}</p>
            </div>
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Payout</p>
              <p className="text-[0.82rem] text-success font-bold">
                {formatCurrency(orderDetail.payoutAmount)}
              </p>
            </div>
          </div>

          {/* ── Tracking info ── */}
          {(orderDetail.trackingNumber || orderDetail.carrierCode || orderDetail.estimatedDeliveryDate) && (
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-2.5">Tracking Information</p>
              <div className="grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-3">
                {orderDetail.trackingNumber && (
                  <div>
                    <p className="text-[0.64rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Tracking Number</p>
                    <p className="text-[0.82rem] text-ink font-mono text-[0.78rem]">
                      {orderDetail.trackingNumber}
                    </p>
                  </div>
                )}
                {orderDetail.carrierCode && (
                  <div>
                    <p className="text-[0.64rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Carrier</p>
                    <p className="text-[0.82rem] text-ink">{orderDetail.carrierCode}</p>
                  </div>
                )}
                {orderDetail.estimatedDeliveryDate && (
                  <div>
                    <p className="text-[0.64rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Est. Delivery</p>
                    <p className="text-[0.82rem] text-ink">
                      {new Date(orderDetail.estimatedDeliveryDate).toLocaleDateString()}
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ── Refund info ── */}
          {orderDetail.refundAmount !== null && orderDetail.refundAmount > 0 && (
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-1.5">Refund Amount</p>
              <p className="text-[0.82rem] text-[#f87171] font-bold">
                {formatCurrency(orderDetail.refundAmount)}
              </p>
            </div>
          )}

          {/* ── Items table ── */}
          <div>
            <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-2.5">Order Items</p>
            {orderDetail.items.length === 0 ? (
              <p className="text-muted text-sm">No items.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse">
                  <thead>
                    <tr>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Product</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">SKU</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Qty</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Unit Price</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Line Total</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Discount</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Fulfilled</th>
                      <th className="bg-surface-2 px-3 py-2.5 text-[0.65rem] font-bold uppercase tracking-[0.08em] text-muted text-left whitespace-nowrap border-b border-line">Cancelled</th>
                    </tr>
                  </thead>
                  <tbody>
                    {orderDetail.items.map((item) => (
                      <tr key={item.id}>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap max-w-[200px] overflow-hidden text-ellipsis">
                          {item.item}
                        </td>
                        <td className="px-3 py-2.5 text-[0.74rem] font-mono text-muted border-b border-line whitespace-nowrap">
                          {item.productSku}
                        </td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap">{item.quantity}</td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap">{formatCurrency(item.unitPrice)}</td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap font-semibold">
                          {formatCurrency(item.lineTotal)}
                        </td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap">
                          {formatCurrency(item.discountAmount)}
                        </td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap">
                          {item.fulfilledQuantity ?? "--"}
                        </td>
                        <td className="px-3 py-2.5 text-[0.78rem] text-ink border-b border-line whitespace-nowrap">
                          {item.cancelledQuantity ?? "--"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Shipping address ── */}
          {orderDetail.shippingAddress && (
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.08em] text-muted mb-2.5">Shipping Address</p>
              <div className="bg-[rgba(0,212,255,0.04)] border border-line rounded-[8px] px-4 py-3">
                <p className="text-[0.82rem] font-bold text-ink mb-1">
                  {orderDetail.shippingAddress.recipientName}
                  {orderDetail.shippingAddress.label && (
                    <span className="text-[0.68rem] text-muted font-medium ml-2">
                      ({orderDetail.shippingAddress.label})
                    </span>
                  )}
                </p>
                <p className="text-[0.78rem] text-muted leading-relaxed">
                  {orderDetail.shippingAddress.line1}
                  {orderDetail.shippingAddress.line2 && (
                    <>, {orderDetail.shippingAddress.line2}</>
                  )}
                  <br />
                  {orderDetail.shippingAddress.city}, {orderDetail.shippingAddress.state}{" "}
                  {orderDetail.shippingAddress.postalCode}
                  <br />
                  {orderDetail.shippingAddress.countryCode}
                </p>
                {orderDetail.shippingAddress.phone && (
                  <p className="text-[0.75rem] text-muted mt-1">
                    Phone: {orderDetail.shippingAddress.phone}
                  </p>
                )}
              </div>
            </div>
          )}
        </div>
      ) : (
        <p className="text-muted text-[0.82rem] text-center py-4">
          Failed to load order details.
        </p>
      )}
    </div>
  );
}
