"use client";

import React, { useState } from "react";
import Image from "next/image";
import StarRating from "./StarRating";
import { resolveImageUrl } from "../../../lib/image";

type VendorReply = {
  id: string; vendorId: string; comment: string; createdAt: string; updatedAt: string;
};

type Review = {
  id: string; customerId: string; customerDisplayName: string;
  productId: string; vendorId: string; orderId: string;
  rating: number; title: string | null; comment: string;
  images: string[]; helpfulCount: number; notHelpfulCount: number;
  verifiedPurchase: boolean; active: boolean;
  vendorReply: VendorReply | null;
  createdAt: string; updatedAt: string;
};

type Props = {
  review: Review;
  onVote?: (reviewId: string, helpful: boolean) => void;
  onReport?: (reviewId: string) => void;
  votingDisabled?: boolean;
};

function ReviewCardInner({ review, onVote, onReport, votingDisabled }: Props) {
  const [expandedImage, setExpandedImage] = useState<string | null>(null);
  const date = new Date(review.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });

  return (
    <div className="py-5 border-b border-line">
      {/* Header */}
      <div className="flex items-center gap-3 mb-2 flex-wrap">
        <div className="w-8 h-8 rounded-full bg-brand-soft border border-line-bright flex items-center justify-center text-[0.75rem] font-bold text-brand">
          {(review.customerDisplayName || "C").charAt(0).toUpperCase()}
        </div>
        <span className="font-bold text-[0.85rem] text-ink">{review.customerDisplayName}</span>
        {review.verifiedPurchase && (
          <span className="text-[0.68rem] font-bold px-2 py-[2px] rounded-full bg-success-soft border border-[rgba(34,197,94,0.3)] text-success">
            Verified Purchase
          </span>
        )}
        <span className="text-[0.75rem] text-muted ml-auto">{date}</span>
      </div>

      {/* Stars + Title */}
      <div className="flex items-center gap-2 mb-2">
        <StarRating value={review.rating} size={14} />
        {review.title && <span className="font-bold text-[0.9rem] text-ink">{review.title}</span>}
      </div>

      {/* Comment */}
      <p className="text-[0.85rem] leading-[1.7] text-ink-light mb-3">{review.comment}</p>

      {/* Images */}
      {review.images.length > 0 && (
        <div className="flex gap-2 mb-3 flex-wrap">
          {review.images.map((img, i) => (
            <div
              key={i}
              onClick={() => setExpandedImage(img)}
              className="relative w-16 h-16 rounded-[8px] overflow-hidden border border-line cursor-pointer"
            >
              <Image src={resolveImageUrl(img.replace(/(\.\w+)$/, "-thumb$1")) ?? ""} alt="Review photo thumbnail" fill sizes="80px" className="object-cover" />
            </div>
          ))}
        </div>
      )}

      {/* Image lightbox */}
      {expandedImage && (
        <div
          onClick={() => setExpandedImage(null)}
          className="fixed inset-0 bg-[rgba(0,0,0,0.85)] z-[9999] flex items-center justify-center cursor-pointer"
        >
          <Image src={resolveImageUrl(expandedImage) ?? ""} alt="Review photo" width={900} height={600} unoptimized className="max-w-[90vw] max-h-[90vh] rounded-[12px] object-contain" />
        </div>
      )}

      {/* Helpful / Report */}
      <div className="flex items-center gap-4">
        <span className="text-[0.72rem] text-muted">Helpful?</span>
        <button
          disabled={votingDisabled}
          onClick={() => onVote?.(review.id, true)}
          className={`inline-flex items-center gap-1 px-[10px] py-1 rounded-sm border border-line bg-transparent text-ink-light text-[0.72rem] ${votingDisabled ? "cursor-not-allowed" : "cursor-pointer"}`}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z" /><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" /></svg>
          {review.helpfulCount > 0 && review.helpfulCount}
        </button>
        <button
          disabled={votingDisabled}
          onClick={() => onVote?.(review.id, false)}
          className={`inline-flex items-center gap-1 px-[10px] py-1 rounded-sm border border-line bg-transparent text-ink-light text-[0.72rem] ${votingDisabled ? "cursor-not-allowed" : "cursor-pointer"}`}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z" /><path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17" /></svg>
          {review.notHelpfulCount > 0 && review.notHelpfulCount}
        </button>
        <button
          onClick={() => onReport?.(review.id)}
          className="ml-auto px-[10px] py-1 rounded-sm border-none bg-transparent text-muted text-[0.68rem] cursor-pointer"
        >
          Report
        </button>
      </div>

      {/* Vendor Reply */}
      {review.vendorReply && (
        <div className="mt-4 ml-5 px-4 py-[14px] rounded-md bg-brand-soft border border-line-bright">
          <div className="flex items-center gap-2 mb-[6px]">
            <span className="text-[0.72rem] font-bold text-brand">Seller Response</span>
            <span className="text-[0.68rem] text-muted">
              {new Date(review.vendorReply.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" })}
            </span>
          </div>
          <p className="text-[0.82rem] leading-[1.6] text-ink-light m-0">{review.vendorReply.comment}</p>
        </div>
      )}
    </div>
  );
}

export default React.memo(ReviewCardInner);
