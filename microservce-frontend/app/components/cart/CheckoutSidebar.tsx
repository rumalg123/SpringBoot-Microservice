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
    <aside className="cart-summary-aside glass-card" style={{ alignSelf: "start", position: "sticky", top: "80px" }}>
      <h2 style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "1.1rem", color: "#fff", margin: "0 0 16px" }}>
        Checkout Summary
      </h2>

      {/* Coupon Code */}
      <div style={{ marginBottom: "14px" }}>
        <label className="form-label" style={{ marginBottom: "6px", display: "block" }}>Coupon Code</label>
        <div style={{ display: "flex", gap: "8px" }}>
          <input
            type="text"
            value={couponCode}
            onChange={(e) => onCouponChange(e.target.value)}
            placeholder="Enter coupon code..."
            className="form-input"
            style={{ flex: 1 }}
            disabled={busy || cart.items.length === 0}
          />
          <button
            onClick={onApplyCoupon}
            disabled={busy || cart.items.length === 0}
            className="btn-outline"
            style={{ padding: "9px 14px", fontSize: "0.78rem", whiteSpace: "nowrap" }}
          >
            {previewing ? <span className="spinner-sm" /> : "Apply"}
          </button>
        </div>
        {couponError && (
          <p style={{ margin: "6px 0 0", fontSize: "0.75rem", color: "var(--danger)" }}>{couponError}</p>
        )}
        {preview?.couponCode && !couponError && (
          <p style={{ margin: "6px 0 0", fontSize: "0.75rem", color: "var(--success)" }}>
            Coupon &quot;{preview.couponCode}&quot; applied
          </p>
        )}
      </div>

      {/* Totals */}
      <div style={{ borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--brand-soft)", padding: "12px 14px", marginBottom: "16px" }}>
        {[
          { label: "Distinct items", value: String(preview?.itemCount ?? cart.itemCount) },
          { label: "Total quantity", value: String(preview?.totalQuantity ?? cart.totalQuantity) },
        ].map(({ label, value }) => (
          <div key={label} style={{ display: "flex", justifyContent: "space-between", marginBottom: "8px", fontSize: "0.82rem" }}>
            <span style={{ color: "var(--muted)" }}>{label}</span>
            <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>{value}</span>
          </div>
        ))}

        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px", fontSize: "0.82rem" }}>
          <span style={{ color: "var(--muted)" }}>Subtotal</span>
          <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>{money(preview?.subtotal ?? cart.subtotal)}</span>
        </div>

        {(preview?.lineDiscountTotal ?? 0) > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px", fontSize: "0.82rem" }}>
            <span style={{ color: "var(--success)" }}>Line Discounts</span>
            <span style={{ color: "var(--success)", fontWeight: 600 }}>-{money(preview!.lineDiscountTotal)}</span>
          </div>
        )}
        {(preview?.cartDiscountTotal ?? 0) > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px", fontSize: "0.82rem" }}>
            <span style={{ color: "var(--success)" }}>Cart Discounts</span>
            <span style={{ color: "var(--success)", fontWeight: 600 }}>-{money(preview!.cartDiscountTotal)}</span>
          </div>
        )}
        {(preview?.shippingDiscountTotal ?? 0) > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px", fontSize: "0.82rem" }}>
            <span style={{ color: "var(--success)" }}>Shipping Discount</span>
            <span style={{ color: "var(--success)", fontWeight: 600 }}>-{money(preview!.shippingDiscountTotal)}</span>
          </div>
        )}
        {(preview?.totalDiscount ?? 0) > 0 && (
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px", fontSize: "0.82rem", fontWeight: 700 }}>
            <span style={{ color: "var(--success)" }}>Total Savings</span>
            <span style={{ color: "var(--success)" }}>-{money(preview!.totalDiscount)}</span>
          </div>
        )}

        <div style={{ display: "flex", justifyContent: "space-between", borderTop: "1px solid var(--line-bright)", paddingTop: "10px", fontWeight: 800 }}>
          <span style={{ color: "#fff" }}>Grand Total</span>
          <span style={{ color: "var(--brand)", fontSize: "1rem" }}>{money(preview?.grandTotal ?? cart.subtotal)}</span>
        </div>
      </div>

      {/* Applied Promotions */}
      {preview && preview.appliedPromotions.length > 0 && (
        <div style={{ marginBottom: "14px" }}>
          <p style={{ fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
            Applied Promotions
          </p>
          {preview.appliedPromotions.map((p) => (
            <div key={p.promotionId} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "6px 0", fontSize: "0.78rem", borderBottom: "1px solid var(--line)" }}>
              <span style={{ color: "var(--ink-light)" }}>{p.promotionName}</span>
              <span style={{ color: "var(--success)", fontWeight: 700 }}>-{money(p.discountAmount)}</span>
            </div>
          ))}
        </div>
      )}

      {/* Rejected Promotions */}
      {preview && preview.rejectedPromotions.length > 0 && (
        <div style={{ marginBottom: "14px" }}>
          <p style={{ fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
            Ineligible Promotions
          </p>
          {preview.rejectedPromotions.map((p) => (
            <div key={p.promotionId} style={{ padding: "6px 0", fontSize: "0.75rem", borderBottom: "1px solid var(--line)" }}>
              <span style={{ color: "var(--muted)" }}>{p.promotionName}</span>
              <p style={{ margin: "2px 0 0", fontSize: "0.7rem", color: "var(--danger)", opacity: 0.8 }}>{p.reason}</p>
            </div>
          ))}
        </div>
      )}

      {/* Address notices */}
      {addresses.length === 0 && !addressLoading && (
        <div style={{ borderRadius: "10px", border: "1px solid rgba(245,158,11,0.25)", background: "rgba(245,158,11,0.06)", padding: "10px 12px", fontSize: "0.78rem", color: "var(--warning-text)", marginBottom: "14px" }}>
          Add at least one address in your profile before checkout.{" "}
          <Link href="/profile" style={{ color: "var(--brand)", fontWeight: 700 }}>Open Profile</Link>
        </div>
      )}
      {addressLoading && (
        <p style={{ fontSize: "0.8rem", color: "var(--muted)", marginBottom: "12px" }}>Loading addresses...</p>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
        {/* Shipping */}
        <div>
          <label style={{ display: "block", fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
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
        <label style={{ display: "inline-flex", alignItems: "center", gap: "8px", fontSize: "0.8rem", color: "var(--muted)", cursor: "pointer" }}>
          <input
            type="checkbox"
            checked={billingSameAsShipping}
            onChange={(e) => onBillingSameChange(e.target.checked)}
            disabled={busy || addressLoading || addresses.length === 0}
            style={{ accentColor: "var(--brand)", width: "14px", height: "14px" }}
          />
          Billing same as shipping
        </label>

        {/* Billing address */}
        {!billingSameAsShipping && (
          <div>
            <label style={{ display: "block", fontSize: "0.7rem", fontWeight: 700, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: "6px" }}>
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
          style={{
            width: "100%",
            padding: "12px",
            borderRadius: "10px",
            border: "none",
            background: checkoutDisabled ? "rgba(0,212,255,0.2)" : "var(--gradient-brand)",
            color: "#fff",
            fontSize: "0.9rem",
            fontWeight: 800,
            cursor: checkoutDisabled ? "not-allowed" : "pointer",
            boxShadow: "0 0 20px var(--line-bright)",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "8px",
            marginTop: "6px",
          }}
        >
          {checkingOut && <span className="spinner-sm" />}
          {checkingOut ? "Processing..." : "Checkout & Pay"}
        </button>
      </div>
    </aside>
  );
}
