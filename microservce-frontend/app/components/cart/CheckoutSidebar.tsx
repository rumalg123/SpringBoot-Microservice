"use client";

import Link from "next/link";
import { money } from "../../../lib/format";
import type { CartResponse, CheckoutPreviewResponse } from "../../../lib/types/cart";
import type { CustomerAddress } from "../../../lib/types/customer";

type Props = {
  cart: CartResponse;
  preview: CheckoutPreviewResponse | null;
  couponCode: string;
  onCouponChange: (code: string) => void;
  couponError: string;
  onApplyCoupon: () => void;
  previewing: boolean;
  addresses: CustomerAddress[];
  addressLoading: boolean;
  shippingAddressId: string;
  onShippingChange: (id: string) => void;
  billingAddressId: string;
  onBillingChange: (id: string) => void;
  billingSameAsShipping: boolean;
  onBillingSameChange: (same: boolean) => void;
  hasRequiredAddresses: boolean;
  emailVerified: boolean | null | undefined;
  busy: boolean;
  checkingOut: boolean;
  onCheckout: () => void;
};

export default function CheckoutSidebar({
  cart,
  preview,
  couponCode,
  onCouponChange,
  couponError,
  onApplyCoupon,
  previewing,
  addresses,
  addressLoading,
  shippingAddressId,
  onShippingChange,
  billingAddressId,
  onBillingChange,
  billingSameAsShipping,
  onBillingSameChange,
  hasRequiredAddresses,
  emailVerified,
  busy,
  checkingOut,
  onCheckout,
}: Props) {
  const checkoutDisabled = busy || cart.items.length === 0 || !hasRequiredAddresses || emailVerified === false;

  return (
    <aside className="cart-summary-aside glass-card self-start sticky top-[80px]">
      <h2 className="font-[Syne,sans-serif] font-extrabold text-[1.1rem] text-white mb-4">
        Checkout Summary
      </h2>

      {/* Coupon Code */}
      <div className="mb-3.5">
        <label className="form-label mb-1.5 block">Coupon Code</label>
        <div className="flex gap-2">
          <input
            type="text"
            value={couponCode}
            onChange={(e) => onCouponChange(e.target.value)}
            placeholder="Enter coupon code..."
            className="form-input flex-1"
            disabled={busy || cart.items.length === 0}
          />
          <button
            onClick={onApplyCoupon}
            disabled={busy || cart.items.length === 0}
            className="btn-outline px-3.5 py-[9px] text-[0.78rem] whitespace-nowrap"
          >
            {previewing ? <span className="spinner-sm" /> : "Apply"}
          </button>
        </div>
        {couponError && (
          <p className="mt-1.5 text-[0.75rem] text-danger">{couponError}</p>
        )}
        {preview?.couponCode && !couponError && (
          <p className="mt-1.5 text-[0.75rem] text-success">
            Coupon &quot;{preview.couponCode}&quot; applied
          </p>
        )}
      </div>

      {/* Totals */}
      <div className="rounded-md bg-brand-soft border border-brand-soft px-3.5 py-3 mb-4">
        {[
          { label: "Distinct items", value: String(preview?.itemCount ?? cart.itemCount) },
          { label: "Total quantity", value: String(preview?.totalQuantity ?? cart.totalQuantity) },
        ].map(({ label, value }) => (
          <div key={label} className="flex justify-between mb-2 text-[0.82rem]">
            <span className="text-muted">{label}</span>
            <span className="text-ink-light font-semibold">{value}</span>
          </div>
        ))}

        <div className="flex justify-between mb-1.5 text-[0.82rem]">
          <span className="text-muted">Subtotal</span>
          <span className="text-ink-light font-semibold">{money(preview?.subtotal ?? cart.subtotal)}</span>
        </div>

        {(preview?.lineDiscountTotal ?? 0) > 0 && (
          <div className="flex justify-between mb-1.5 text-[0.82rem]">
            <span className="text-success">Line Discounts</span>
            <span className="text-success font-semibold">-{money(preview!.lineDiscountTotal)}</span>
          </div>
        )}
        {(preview?.cartDiscountTotal ?? 0) > 0 && (
          <div className="flex justify-between mb-1.5 text-[0.82rem]">
            <span className="text-success">Cart Discounts</span>
            <span className="text-success font-semibold">-{money(preview!.cartDiscountTotal)}</span>
          </div>
        )}
        {(preview?.shippingDiscountTotal ?? 0) > 0 && (
          <div className="flex justify-between mb-1.5 text-[0.82rem]">
            <span className="text-success">Shipping Discount</span>
            <span className="text-success font-semibold">-{money(preview!.shippingDiscountTotal)}</span>
          </div>
        )}
        {(preview?.totalDiscount ?? 0) > 0 && (
          <div className="flex justify-between mb-1.5 text-[0.82rem] font-bold">
            <span className="text-success">Total Savings</span>
            <span className="text-success">-{money(preview!.totalDiscount)}</span>
          </div>
        )}

        <div className="flex justify-between border-t border-line-bright pt-2.5 font-extrabold">
          <span className="text-white">Grand Total</span>
          <span className="text-brand text-lg">{money(preview?.grandTotal ?? cart.subtotal)}</span>
        </div>
      </div>

      {/* Applied Promotions */}
      {preview && preview.appliedPromotions.length > 0 && (
        <div className="mb-3.5">
          <p className="text-xs font-bold text-muted uppercase tracking-wide mb-1.5">
            Applied Promotions
          </p>
          {preview.appliedPromotions.map((p) => (
            <div key={p.promotionId} className="flex justify-between items-center py-1.5 text-[0.78rem] border-b border-line">
              <span className="text-ink-light">{p.promotionName}</span>
              <span className="text-success font-bold">-{money(p.discountAmount)}</span>
            </div>
          ))}
        </div>
      )}

      {/* Rejected Promotions */}
      {preview && preview.rejectedPromotions.length > 0 && (
        <div className="mb-3.5">
          <p className="text-xs font-bold text-muted uppercase tracking-wide mb-1.5">
            Ineligible Promotions
          </p>
          {preview.rejectedPromotions.map((p) => (
            <div key={p.promotionId} className="py-1.5 text-[0.75rem] border-b border-line">
              <span className="text-muted">{p.promotionName}</span>
              <p className="mt-0.5 text-xs text-danger opacity-80">{p.reason}</p>
            </div>
          ))}
        </div>
      )}

      {/* Address notices */}
      {addresses.length === 0 && !addressLoading && (
        <div className="rounded-md border border-[rgba(245,158,11,0.25)] bg-[rgba(245,158,11,0.06)] px-3 py-2.5 text-[0.78rem] text-warning-text mb-3.5">
          Add at least one address in your profile before checkout.{" "}
          <Link href="/profile" className="text-brand font-bold">Open Profile</Link>
        </div>
      )}
      {addressLoading && (
        <p className="text-sm text-muted mb-3">Loading addresses...</p>
      )}

      <div className="flex flex-col gap-2.5">
        {/* Shipping */}
        <div>
          <label className="block text-xs font-bold text-muted uppercase tracking-wide mb-1.5">
            Shipping Address
          </label>
          <select
            value={shippingAddressId}
            onChange={(e) => onShippingChange(e.target.value)}
            disabled={busy || addressLoading || addresses.length === 0}
            className="form-select"
          >
            <option value="">Select shipping address...</option>
            {addresses.map((a) => (
              <option key={`shipping-${a.id}`} value={a.id}>
                {a.label || "Address"} — {a.line1}, {a.city}
              </option>
            ))}
          </select>
        </div>

        {/* Billing same toggle */}
        <label className="inline-flex items-center gap-2 text-sm text-muted cursor-pointer">
          <input
            type="checkbox"
            checked={billingSameAsShipping}
            onChange={(e) => onBillingSameChange(e.target.checked)}
            disabled={busy || addressLoading || addresses.length === 0}
            className="accent-brand w-3.5 h-3.5"
          />
          Billing same as shipping
        </label>

        {/* Billing address */}
        {!billingSameAsShipping && (
          <div>
            <label className="block text-xs font-bold text-muted uppercase tracking-wide mb-1.5">
              Billing Address
            </label>
            <select
              value={billingAddressId}
              onChange={(e) => onBillingChange(e.target.value)}
              disabled={busy || addressLoading || addresses.length === 0}
              className="form-select"
            >
              <option value="">Select billing address...</option>
              {addresses.map((a) => (
                <option key={`billing-${a.id}`} value={a.id}>
                  {a.label || "Address"} — {a.line1}, {a.city}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* Checkout button */}
        <button
          onClick={onCheckout}
          disabled={checkoutDisabled}
          className={`w-full py-3 rounded-md border-none text-white text-[0.9rem] font-extrabold inline-flex items-center justify-center gap-2 mt-1.5 shadow-[0_0_20px_var(--line-bright)] ${checkoutDisabled ? "bg-brand/20 cursor-not-allowed" : "bg-[image:var(--gradient-brand)] cursor-pointer"}`}
        >
          {checkingOut && <span className="spinner-sm" />}
          {checkingOut ? "Processing..." : "Checkout & Pay"}
        </button>
      </div>
    </aside>
  );
}
