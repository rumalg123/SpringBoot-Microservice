"use client";

import Link from "next/link";

type Props = {
  isAuthenticated: boolean;
  productType: string;
  parentAttributeNames: string[];
  variationOptionsByAttribute: Record<string, string[]>;
  selectedAttributes: Record<string, string>;
  onAttributeChange: (name: string, value: string) => void;
  allAttributesSelected: boolean;
  selectedVariationId: string;
  variationsCount: number;
  quantity: number;
  onQuantityChange: (qty: number) => void;
  canAddToCart: boolean;
  canBuyNow: boolean;
  addingToCart: boolean;
  buyingNow: boolean;
  wishlistItemId: string;
  wishlistPending: boolean;
  signingInToBuy: boolean;
  onAddToCart: () => void;
  onBuyNow: () => void;
  onToggleWishlist: () => void;
  onSignIn: () => void;
};

export default function PurchasePanel({
  isAuthenticated,
  productType,
  parentAttributeNames,
  variationOptionsByAttribute,
  selectedAttributes,
  onAttributeChange,
  allAttributesSelected,
  selectedVariationId,
  variationsCount,
  quantity,
  onQuantityChange,
  canAddToCart,
  canBuyNow,
  addingToCart,
  buyingNow,
  wishlistItemId,
  wishlistPending,
  signingInToBuy,
  onAddToCart,
  onBuyNow,
  onToggleWishlist,
  onSignIn,
}: Props) {
  return (
    <div className="border-t border-brand-soft pt-5">
      {isAuthenticated ? (
        <div className="flex flex-col gap-3.5">
          {/* Variation Selector */}
          {productType === "PARENT" && (
            <div className="flex flex-col gap-2.5">
              <p className="text-[0.65rem] font-bold uppercase tracking-widest text-muted m-0">Select Variation</p>
              {parentAttributeNames.length === 0 && <p className="text-sm text-muted">No variation attributes configured.</p>}
              {parentAttributeNames.map((attributeName) => (
                <div key={attributeName}>
                  <label className="block text-[0.65rem] font-bold uppercase tracking-widest text-muted mb-1.5">
                    {attributeName}
                  </label>
                  <select
                    value={selectedAttributes[attributeName] || ""}
                    onChange={(e) => onAttributeChange(attributeName, e.target.value)}
                    disabled={addingToCart || (variationOptionsByAttribute[attributeName] || []).length === 0}
                    aria-label={attributeName}
                    className="form-select"
                  >
                    <option value="">Select {attributeName}</option>
                    {(variationOptionsByAttribute[attributeName] || []).map((value) => (
                      <option key={`${attributeName}-${value}`} value={value}>{value}</option>
                    ))}
                  </select>
                </div>
              ))}
              {variationsCount > 0 && !allAttributesSelected && (
                <p className="text-[0.78rem] text-muted">Select all attributes to continue.</p>
              )}
              {allAttributesSelected && !selectedVariationId && variationsCount > 0 && (
                <p className="text-[0.78rem] font-bold text-danger">No variation matches the selected combination.</p>
              )}
              {variationsCount === 0 && <p className="text-[0.78rem] text-muted">No variations available.</p>}
            </div>
          )}

          {/* Quantity */}
          <div>
            <label className="block text-[0.65rem] font-bold uppercase tracking-widest text-muted mb-2">Quantity</label>
            <div className="qty-stepper" role="group" aria-label="Quantity">
              <button aria-label="Decrease quantity" disabled={addingToCart || buyingNow} onClick={() => onQuantityChange(Math.max(1, quantity - 1))}>-</button>
              <span aria-live="polite">{quantity}</span>
              <button aria-label="Increase quantity" disabled={addingToCart || buyingNow} onClick={() => onQuantityChange(quantity + 1)}>+</button>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap gap-2.5">
            <button
              disabled={!canAddToCart}
              onClick={onAddToCart}
              className={`flex-1 min-w-[120px] px-5 py-[13px] rounded-xl border border-line-bright text-[0.9rem] font-extrabold inline-flex items-center justify-center gap-2 ${canAddToCart ? "bg-[rgba(0,0,10,0.4)] text-brand cursor-pointer" : "bg-line-bright text-muted cursor-not-allowed"}`}
            >
              {addingToCart && <span className="spinner-sm" />}
              {addingToCart ? "Adding..." : "Add to Cart"}
            </button>
            <button
              disabled={!canBuyNow}
              onClick={onBuyNow}
              className={`flex-1 min-w-[120px] px-5 py-[13px] rounded-xl border-none text-white text-[0.9rem] font-extrabold inline-flex items-center justify-center gap-2 ${canBuyNow ? "bg-[image:var(--gradient-brand)] cursor-pointer shadow-[0_0_20px_var(--line-bright)]" : "bg-line-bright cursor-not-allowed shadow-none"}`}
            >
              {buyingNow && <span className="spinner-sm" />}
              {buyingNow ? "Processing..." : "Buy Now"}
            </button>
            <button
              disabled={wishlistPending}
              onClick={onToggleWishlist}
              className={`px-[18px] py-[13px] rounded-xl text-sm font-bold inline-flex items-center gap-1.5 ${wishlistItemId ? "border border-danger bg-danger-soft text-danger" : "border border-line-bright bg-brand-soft text-brand"} ${wishlistPending ? "cursor-not-allowed" : "cursor-pointer"}`}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={wishlistItemId ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
              </svg>
              {wishlistPending ? "Saving..." : (wishlistItemId ? "Wishlisted" : "Wishlist")}
            </button>
            <Link
              href="/cart"
              className="no-underline px-[18px] py-[13px] rounded-xl border border-line-bright bg-[rgba(0,0,10,0.4)] text-ink-light text-sm font-bold inline-flex items-center gap-1.5"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                <path d="M16 10a4 4 0 0 1-8 0" />
              </svg>
              Open Cart
            </Link>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-base text-muted m-0">Sign in to add this product to cart</p>
          <button
            disabled={signingInToBuy}
            onClick={onSignIn}
            className="px-5 py-[13px] rounded-xl border-none bg-[image:var(--gradient-brand)] text-white text-[0.9rem] font-extrabold cursor-pointer inline-flex items-center justify-center gap-2"
          >
            {signingInToBuy ? "Redirecting..." : "Sign In to Continue"}
          </button>
        </div>
      )}
    </div>
  );
}
