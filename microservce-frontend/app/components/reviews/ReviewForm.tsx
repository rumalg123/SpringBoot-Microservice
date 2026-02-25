"use client";

import { useState } from "react";
import StarRating from "./StarRating";
import toast from "react-hot-toast";

type Props = {
  productId: string;
  vendorId: string;
  apiClient: any; // Axios instance from useAuthSession
  onSuccess: () => void;
  onCancel: () => void;
};

export default function ReviewForm({ productId, vendorId, apiClient, onSuccess, onCancel }: Props) {
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState("");
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const canSubmit = rating >= 1 && comment.trim().length > 0 && !submitting;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await apiClient.post("/reviews/me", {
        productId,
        vendorId,
        rating,
        title: title.trim() || null,
        comment: comment.trim(),
        images: [],
      });
      toast.success("Review submitted successfully!");
      onSuccess();
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || "Failed to submit review";
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: "24px", borderRadius: "16px", border: "1px solid var(--line-bright)", background: "var(--surface)" }}>
      <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink)", margin: "0 0 16px" }}>Write a Review</h3>

      {/* Star rating */}
      <div style={{ marginBottom: "16px" }}>
        <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "6px" }}>
          Rating
        </label>
        <StarRating value={rating} onChange={setRating} size={28} interactive />
        {rating === 0 && <p style={{ fontSize: "0.72rem", color: "var(--muted)", margin: "4px 0 0" }}>Click to rate</p>}
      </div>

      {/* Title */}
      <div style={{ marginBottom: "16px" }}>
        <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "6px" }}>
          Title (optional)
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="Summarize your experience"
          className="form-input"
          style={{ width: "100%", padding: "10px 14px", borderRadius: "10px", border: "1px solid var(--line)", background: "var(--bg)", color: "var(--ink)", fontSize: "0.85rem" }}
        />
      </div>

      {/* Comment */}
      <div style={{ marginBottom: "16px" }}>
        <label style={{ display: "block", fontSize: "0.72rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "6px" }}>
          Your Review
        </label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={2000}
          rows={4}
          placeholder="Share your experience with this product..."
          style={{ width: "100%", padding: "10px 14px", borderRadius: "10px", border: "1px solid var(--line)", background: "var(--bg)", color: "var(--ink)", fontSize: "0.85rem", resize: "vertical", fontFamily: "inherit" }}
        />
        <p style={{ fontSize: "0.68rem", color: "var(--muted)", margin: "4px 0 0", textAlign: "right" }}>{comment.length}/2000</p>
      </div>

      {/* Actions */}
      <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end" }}>
        <button
          onClick={onCancel}
          disabled={submitting}
          style={{ padding: "10px 20px", borderRadius: "10px", border: "1px solid var(--line)", background: "transparent", color: "var(--ink-light)", fontSize: "0.85rem", fontWeight: 600, cursor: "pointer" }}
        >
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          style={{
            padding: "10px 20px", borderRadius: "10px", border: "none",
            background: canSubmit ? "var(--gradient-brand)" : "var(--line-bright)",
            color: canSubmit ? "#fff" : "var(--muted)",
            fontSize: "0.85rem", fontWeight: 700, cursor: canSubmit ? "pointer" : "not-allowed",
          }}
        >
          {submitting ? "Submitting..." : "Submit Review"}
        </button>
      </div>
    </div>
  );
}
