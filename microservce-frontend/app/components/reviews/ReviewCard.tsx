"use client";

import { useState } from "react";
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

export default function ReviewCard({ review, onVote, onReport, votingDisabled }: Props) {
  const [expandedImage, setExpandedImage] = useState<string | null>(null);
  const date = new Date(review.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });

  return (
    <div style={{ padding: "20px 0", borderBottom: "1px solid var(--line)" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "8px", flexWrap: "wrap" }}>
        <div style={{ width: "32px", height: "32px", borderRadius: "50%", background: "var(--brand-soft)", border: "1px solid var(--line-bright)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "0.75rem", fontWeight: 700, color: "var(--brand)" }}>
          {(review.customerDisplayName || "C").charAt(0).toUpperCase()}
        </div>
        <span style={{ fontWeight: 700, fontSize: "0.85rem", color: "var(--ink)" }}>{review.customerDisplayName}</span>
        {review.verifiedPurchase && (
          <span style={{ fontSize: "0.68rem", fontWeight: 700, padding: "2px 8px", borderRadius: "999px", background: "var(--success-soft)", border: "1px solid rgba(34,197,94,0.3)", color: "var(--success)" }}>
            Verified Purchase
          </span>
        )}
        <span style={{ fontSize: "0.75rem", color: "var(--muted)", marginLeft: "auto" }}>{date}</span>
      </div>

      {/* Stars + Title */}
      <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
        <StarRating value={review.rating} size={14} />
        {review.title && <span style={{ fontWeight: 700, fontSize: "0.9rem", color: "var(--ink)" }}>{review.title}</span>}
      </div>

      {/* Comment */}
      <p style={{ fontSize: "0.85rem", lineHeight: 1.7, color: "var(--ink-light)", margin: "0 0 12px" }}>{review.comment}</p>

      {/* Images */}
      {review.images.length > 0 && (
        <div style={{ display: "flex", gap: "8px", marginBottom: "12px", flexWrap: "wrap" }}>
          {review.images.map((img, i) => (
            <div
              key={i}
              onClick={() => setExpandedImage(img)}
              style={{ width: "64px", height: "64px", borderRadius: "8px", overflow: "hidden", border: "1px solid var(--line)", cursor: "pointer" }}
            >
              <img src={resolveImageUrl(img.replace(/(\.\w+)$/, "-thumb$1"))} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            </div>
          ))}
        </div>
      )}

      {/* Image lightbox */}
      {expandedImage && (
        <div
          onClick={() => setExpandedImage(null)}
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.85)", zIndex: 9999, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }}
        >
          <img src={resolveImageUrl(expandedImage)} alt="" style={{ maxWidth: "90vw", maxHeight: "90vh", borderRadius: "12px" }} />
        </div>
      )}

      {/* Helpful / Report */}
      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <span style={{ fontSize: "0.72rem", color: "var(--muted)" }}>Helpful?</span>
        <button
          disabled={votingDisabled}
          onClick={() => onVote?.(review.id, true)}
          style={{ display: "inline-flex", alignItems: "center", gap: "4px", padding: "4px 10px", borderRadius: "6px", border: "1px solid var(--line)", background: "transparent", color: "var(--ink-light)", fontSize: "0.72rem", cursor: votingDisabled ? "not-allowed" : "pointer" }}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z" /><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" /></svg>
          {review.helpfulCount > 0 && review.helpfulCount}
        </button>
        <button
          disabled={votingDisabled}
          onClick={() => onVote?.(review.id, false)}
          style={{ display: "inline-flex", alignItems: "center", gap: "4px", padding: "4px 10px", borderRadius: "6px", border: "1px solid var(--line)", background: "transparent", color: "var(--ink-light)", fontSize: "0.72rem", cursor: votingDisabled ? "not-allowed" : "pointer" }}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z" /><path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17" /></svg>
          {review.notHelpfulCount > 0 && review.notHelpfulCount}
        </button>
        <button
          onClick={() => onReport?.(review.id)}
          style={{ marginLeft: "auto", padding: "4px 10px", borderRadius: "6px", border: "none", background: "transparent", color: "var(--muted)", fontSize: "0.68rem", cursor: "pointer" }}
        >
          Report
        </button>
      </div>

      {/* Vendor Reply */}
      {review.vendorReply && (
        <div style={{ marginTop: "16px", marginLeft: "20px", padding: "14px 16px", borderRadius: "10px", background: "var(--brand-soft)", border: "1px solid var(--line-bright)" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px" }}>
            <span style={{ fontSize: "0.72rem", fontWeight: 700, color: "var(--brand)" }}>Seller Response</span>
            <span style={{ fontSize: "0.68rem", color: "var(--muted)" }}>
              {new Date(review.vendorReply.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" })}
            </span>
          </div>
          <p style={{ fontSize: "0.82rem", lineHeight: 1.6, color: "var(--ink-light)", margin: 0 }}>{review.vendorReply.comment}</p>
        </div>
      )}
    </div>
  );
}
