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
    <div style={{ borderTop: "1px solid var(--brand-soft)", paddingTop: "20px" }}>
      {isAuthenticated ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
          {/* Variation Selector */}
          {productType === "PARENT" && (
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: 0 }}>Select Variation</p>
              {parentAttributeNames.length === 0 && <p style={{ fontSize: "0.8rem", color: "var(--muted)" }}>No variation attributes configured.</p>}
              {parentAttributeNames.map((attributeName) => (
                <div key={attributeName}>
                  <label style={{ display: "block", fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "6px" }}>
                    {attributeName}
                  </label>
                  <select
                    value={selectedAttributes[attributeName] || ""}
                    onChange={(e) => onAttributeChange(attributeName, e.target.value)}
                    disabled={addingToCart || (variationOptionsByAttribute[attributeName] || []).length === 0}
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
                <p style={{ fontSize: "0.78rem", color: "var(--muted)" }}>Select all attributes to continue.</p>
              )}
              {allAttributesSelected && !selectedVariationId && variationsCount > 0 && (
                <p style={{ fontSize: "0.78rem", fontWeight: 700, color: "var(--danger)" }}>No variation matches the selected combination.</p>
              )}
              {variationsCount === 0 && <p style={{ fontSize: "0.78rem", color: "var(--muted)" }}>No variations available.</p>}
            </div>
          )}

          {/* Quantity */}
          <div>
            <label style={{ display: "block", fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "8px" }}>Quantity</label>
            <div className="qty-stepper">
              <button disabled={addingToCart || buyingNow} onClick={() => onQuantityChange(Math.max(1, quantity - 1))}>-</button>
              <span>{quantity}</span>
              <button disabled={addingToCart || buyingNow} onClick={() => onQuantityChange(quantity + 1)}>+</button>
            </div>
          </div>

          {/* Action Buttons */}
          <div style={{ display: "flex", flexWrap: "wrap", gap: "10px" }}>
            <button
              disabled={!canAddToCart}
              onClick={onAddToCart}
              style={{
                flex: 1, minWidth: "120px", padding: "13px 20px", borderRadius: "12px",
                border: "1px solid var(--line-bright)", background: canAddToCart ? "rgba(0,0,10,0.4)" : "var(--line-bright)",
                color: canAddToCart ? "var(--brand)" : "var(--muted)", fontSize: "0.9rem", fontWeight: 800,
                cursor: canAddToCart ? "pointer" : "not-allowed",
                display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
              }}
            >
              {addingToCart && <span className="spinner-sm" />}
              {addingToCart ? "Adding..." : "Add to Cart"}
            </button>
            <button
              disabled={!canBuyNow}
              onClick={onBuyNow}
              style={{
                flex: 1, minWidth: "120px", padding: "13px 20px", borderRadius: "12px", border: "none",
                background: canBuyNow ? "var(--gradient-brand)" : "var(--line-bright)",
                color: "#fff", fontSize: "0.9rem", fontWeight: 800,
                cursor: canBuyNow ? "pointer" : "not-allowed",
                boxShadow: canBuyNow ? "0 0 20px var(--line-bright)" : "none",
                display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
              }}
            >
              {buyingNow && <span className="spinner-sm" />}
              {buyingNow ? "Processing..." : "Buy Now"}
            </button>
            <button
              disabled={wishlistPending}
              onClick={onToggleWishlist}
              style={{
                padding: "13px 18px", borderRadius: "12px",
                border: wishlistItemId ? "1px solid var(--danger)" : "1px solid var(--line-bright)",
                background: wishlistItemId ? "var(--danger-soft)" : "var(--brand-soft)",
                color: wishlistItemId ? "var(--danger)" : "var(--brand)",
                fontSize: "0.8rem", fontWeight: 700, cursor: wishlistPending ? "not-allowed" : "pointer",
                display: "inline-flex", alignItems: "center", gap: "6px",
              }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill={wishlistItemId ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
              </svg>
              {wishlistPending ? "Saving..." : (wishlistItemId ? "Wishlisted" : "Wishlist")}
            </button>
            <Link
              href="/cart"
              className="no-underline"
              style={{
                padding: "13px 18px", borderRadius: "12px",
                border: "1px solid var(--line-bright)", background: "rgba(0,0,10,0.4)",
                color: "var(--ink-light)", fontSize: "0.8rem", fontWeight: 700,
                display: "inline-flex", alignItems: "center", gap: "6px",
              }}
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
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          <p style={{ fontSize: "0.875rem", color: "var(--muted)", margin: 0 }}>Sign in to add this product to cart</p>
          <button
            disabled={signingInToBuy}
            onClick={onSignIn}
            style={{
              padding: "13px 20px", borderRadius: "12px", border: "none",
              background: "var(--gradient-brand)",
              color: "#fff", fontSize: "0.9rem", fontWeight: 800, cursor: "pointer",
              display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
            }}
          >
            {signingInToBuy ? "Redirecting..." : "Sign In to Continue"}
          </button>
        </div>
      )}
    </div>
  );
}
