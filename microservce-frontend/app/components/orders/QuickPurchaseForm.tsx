"use client";

import Link from "next/link";
import type { ProductSummary } from "../../../lib/types/product";
import type { CustomerAddress } from "../../../lib/types/customer";
import { FormEvent } from "react";

export type QuickPurchaseFormData = {
  productId: string;
  quantity: number;
  shippingAddressId: string;
  billingAddressId: string;
};

export type QuickPurchaseFormProps = {
  products: ProductSummary[];
  addresses: CustomerAddress[];
  form: QuickPurchaseFormData;
  onFormChange: (updater: (old: QuickPurchaseFormData) => QuickPurchaseFormData) => void;
  billingSameAsShipping: boolean;
  onBillingSameChange: (checked: boolean) => void;
  isCreating: boolean;
  onSubmit: (e: FormEvent) => void;
};

export default function QuickPurchaseForm({
  products,
  addresses,
  form,
  onFormChange,
  billingSameAsShipping,
  onBillingSameChange,
  isCreating,
  onSubmit,
}: QuickPurchaseFormProps) {
  const disabled = isCreating || addresses.length === 0;

  return (
    <section className="glass-card p-5">
      <div className="flex items-center gap-[10px] mb-[14px]">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
          <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
        </svg>
        <h3 className="font-[Syne,sans-serif] font-extrabold text-lg text-white m-0">
          Quick Purchase
        </h3>
      </div>

      {addresses.length === 0 && (
        <div className="rounded-md border border-warning-border bg-warning-soft px-3 py-[10px] text-[0.78rem] text-warning-text mb-3">
          Add at least one address in your profile before placing an order.{" "}
          <Link href="/profile" className="text-brand font-bold">Open Profile</Link>
        </div>
      )}

      <form className="flex flex-col gap-[10px]" onSubmit={onSubmit}>
        <select
          value={form.productId}
          onChange={(e) => onFormChange((old) => ({ ...old, productId: e.target.value }))}
          disabled={disabled}
          className="form-select"
          required
        >
          <option value="">Select product...</option>
          {products.map((p) => (
            <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
          ))}
        </select>
        <select
          value={form.shippingAddressId}
          onChange={(e) => {
            const v = e.target.value;
            onFormChange((old) => ({ ...old, shippingAddressId: v, billingAddressId: billingSameAsShipping ? v : old.billingAddressId }));
          }}
          disabled={disabled}
          className="form-select"
          required
        >
          <option value="">Select shipping address...</option>
          {addresses.map((a) => (
            <option key={`shipping-${a.id}`} value={a.id}>{a.label || "Address"} — {a.line1}, {a.city}</option>
          ))}
        </select>
        <label className="inline-flex items-center gap-2 text-sm text-muted cursor-pointer">
          <input
            type="checkbox"
            checked={billingSameAsShipping}
            onChange={(e) => onBillingSameChange(e.target.checked)}
            disabled={disabled}
            className="accent-brand w-[14px] h-[14px]"
          />
          Billing same as shipping
        </label>
        {!billingSameAsShipping && (
          <select
            value={form.billingAddressId}
            onChange={(e) => onFormChange((old) => ({ ...old, billingAddressId: e.target.value }))}
            disabled={disabled}
            className="form-select"
            required
          >
            <option value="">Select billing address...</option>
            {addresses.map((a) => (
              <option key={`billing-${a.id}`} value={a.id}>{a.label || "Address"} — {a.line1}, {a.city}</option>
            ))}
          </select>
        )}
        <div className="flex gap-2">
          <input
            type="number" min={1} value={form.quantity}
            onChange={(e) => onFormChange((old) => ({ ...old, quantity: Number(e.target.value) }))}
            disabled={disabled}
            className="form-input w-[80px]"
            placeholder="Qty"
          />
          <button
            type="submit"
            disabled={disabled}
            className={`flex-1 p-[10px] rounded-md border-none text-white text-base font-bold inline-flex items-center justify-center gap-2 ${disabled ? "bg-line-bright cursor-not-allowed" : "bg-[image:var(--gradient-brand)] cursor-pointer"}`}
          >
            {isCreating && <span className="spinner-sm" />}
            {isCreating ? "Placing..." : "Place Order"}
          </button>
        </div>
      </form>
    </section>
  );
}
