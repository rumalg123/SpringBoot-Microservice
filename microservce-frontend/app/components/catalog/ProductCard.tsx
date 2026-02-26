"use client";

import Link from "next/link";
import Image from "next/image";
import { money, calcDiscount } from "../../../lib/format";
import { resolveImageUrl } from "../../../lib/image";

type ProductLike = {
  id: string;
  slug?: string;
  name: string;
  shortDescription?: string;
  mainImage: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  sku?: string;
};

type Props = {
  product: ProductLike;
  /** Animation stagger index */
  index?: number;
  /** Show wishlist heart button */
  showWishlist?: boolean;
  isWishlisted?: boolean;
  wishlistBusy?: boolean;
  onWishlistToggle?: (e: React.MouseEvent<HTMLButtonElement>) => void;
};

export default function ProductCard({
  product: p,
  index = 0,
  showWishlist = false,
  isWishlisted = false,
  wishlistBusy = false,
  onWishlistToggle,
}: Props) {
  const discount = calcDiscount(p.regularPrice, p.sellingPrice);
  const imgUrl = resolveImageUrl(p.mainImage);
  const href = `/products/${encodeURIComponent(((p.slug || p.id) ?? "").trim())}`;

  return (
    <Link
      href={href}
      className="product-card animate-rise no-underline"
      style={{ animationDelay: `${index * 45}ms` }}
    >
      {discount && <span className="badge-sale">-{discount}%</span>}

      {/* Wishlist Button */}
      {showWishlist && (
        <button
          type="button"
          onClick={onWishlistToggle}
          disabled={wishlistBusy}
          style={{
            position: "absolute",
            top: "10px",
            right: "10px",
            zIndex: 20,
            width: "32px",
            height: "32px",
            borderRadius: "50%",
            border: isWishlisted ? "1.5px solid var(--danger)" : "1.5px solid var(--line-bright)",
            background: isWishlisted ? "var(--danger-soft)" : "var(--header-bg)",
            color: isWishlisted ? "var(--danger)" : "var(--muted)",
            cursor: wishlistBusy ? "not-allowed" : "pointer",
            opacity: wishlistBusy ? 0.5 : 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            transition: "all 0.2s",
            backdropFilter: "blur(8px)",
            boxShadow: isWishlisted ? "0 0 10px var(--danger-soft)" : "none",
          }}
          title={isWishlisted ? "Remove from wishlist" : "Add to wishlist"}
          aria-label={isWishlisted ? "Remove from wishlist" : "Add to wishlist"}
        >
          {wishlistBusy ? (
            <span className="spinner-sm" style={{ width: "10px", height: "10px" }} />
          ) : (
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill={isWishlisted ? "currentColor" : "none"}
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
            </svg>
          )}
        </button>
      )}

      {/* Image */}
      <div
        style={{
          position: "relative",
          aspectRatio: "1/1",
          overflow: "hidden",
          background: "var(--surface-2)",
        }}
      >
        {imgUrl ? (
          <Image
            src={imgUrl}
            alt={p.name}
            width={400}
            height={400}
            className="product-card-img"
            unoptimized
          />
        ) : (
          <div
            style={{
              display: "grid",
              placeItems: "center",
              width: "100%",
              height: "100%",
              background: "linear-gradient(135deg, var(--surface), var(--surface-3))",
              color: "var(--muted-2)",
              fontSize: "0.75rem",
              fontWeight: 600,
            }}
          >
            No Image
          </div>
        )}
        <div className="product-card-overlay" style={{ borderRadius: "15px 15px 0 0" }}>
          <span
            style={{
              background: "var(--gradient-brand)",
              color: "#fff",
              padding: "7px 16px",
              borderRadius: "20px",
              fontSize: "0.72rem",
              fontWeight: 800,
              letterSpacing: "0.04em",
            }}
          >
            View Product →
          </span>
        </div>
      </div>

      {/* Body */}
      <div className="product-card-body">
        <p
          style={{
            margin: "0 0 4px",
            fontSize: "0.875rem",
            fontWeight: 600,
            color: "var(--ink)",
            display: "-webkit-box",
            WebkitLineClamp: 2,
            WebkitBoxOrient: "vertical",
            overflow: "hidden",
          }}
        >
          {p.name}
        </p>
        {p.shortDescription && (
          <p
            style={{
              margin: "0 0 8px",
              fontSize: "0.7rem",
              color: "var(--muted)",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {p.shortDescription}
          </p>
        )}
        <div style={{ display: "flex", alignItems: "center", gap: "4px", flexWrap: "wrap" }}>
          <span className="price-current">{money(p.sellingPrice)}</span>
          {p.discountedPrice !== null && (
            <>
              <span className="price-original">{money(p.regularPrice)}</span>
              {discount && <span className="price-discount-badge">-{discount}%</span>}
            </>
          )}
        </div>
        {p.sku && (
          <p style={{ margin: "6px 0 0", fontSize: "0.65rem", color: "var(--muted-2)" }}>
            SKU: {p.sku}
          </p>
        )}
      </div>
    </Link>
  );
}
