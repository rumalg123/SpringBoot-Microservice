"use client";

import React from "react";
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

function ProductCardInner({
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
          className={`absolute top-2.5 right-2.5 z-20 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200 backdrop-blur-sm ${isWishlisted ? "border-[1.5px] border-danger bg-danger-soft text-danger shadow-[0_0_10px_var(--danger-soft)]" : "border-[1.5px] border-line-bright bg-header-bg text-muted shadow-none"} ${wishlistBusy ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
          title={isWishlisted ? "Remove from wishlist" : "Add to wishlist"}
          aria-label={isWishlisted ? "Remove from wishlist" : "Add to wishlist"}
        >
          {wishlistBusy ? (
            <span className="spinner-sm w-2.5 h-2.5" />
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
      <div className="relative aspect-square overflow-hidden bg-surface-2">
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
          <div className="grid place-items-center w-full h-full bg-gradient-to-br from-surface to-surface-3 text-muted-2 text-[0.75rem] font-semibold">
            No Image
          </div>
        )}
        <div className="product-card-overlay rounded-t-[15px]">
          <span className="bg-[image:var(--gradient-brand)] text-white px-4 py-[7px] rounded-full text-xs font-extrabold tracking-wide">
            View Product →
          </span>
        </div>
      </div>

      {/* Body */}
      <div className="product-card-body">
        <p className="mb-1 text-base font-semibold text-ink line-clamp-2">
          {p.name}
        </p>
        {p.shortDescription && (
          <p className="mb-2 text-xs text-muted overflow-hidden text-ellipsis whitespace-nowrap">
            {p.shortDescription}
          </p>
        )}
        <div className="flex items-center gap-1 flex-wrap">
          <span className="price-current">{money(p.sellingPrice)}</span>
          {p.discountedPrice !== null && (
            <>
              <span className="price-original">{money(p.regularPrice)}</span>
              {discount && <span className="price-discount-badge">-{discount}%</span>}
            </>
          )}
        </div>
        {p.sku && (
          <p className="mt-1.5 text-[0.65rem] text-muted-2">
            SKU: {p.sku}
          </p>
        )}
      </div>
    </Link>
  );
}

export default React.memo(ProductCardInner);
