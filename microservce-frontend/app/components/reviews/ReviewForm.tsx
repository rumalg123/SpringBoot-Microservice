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
    <div className="p-6 rounded-lg border border-line-bright bg-surface">
      <h3 className="text-lg font-bold text-ink mb-4">Write a Review</h3>

      {/* Star rating */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Rating
        </label>
        <StarRating value={rating} onChange={setRating} size={28} interactive />
        {rating === 0 && <p className="text-[0.72rem] text-muted mt-1">Click to rate</p>}
      </div>

      {/* Title */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Title (optional)
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="Summarize your experience"
          className="form-input w-full px-[14px] py-[10px] rounded-md border border-line bg-bg text-ink text-[0.85rem]"
        />
      </div>

      {/* Comment */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Your Review
        </label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={2000}
          rows={4}
          placeholder="Share your experience with this product..."
          className="w-full px-[14px] py-[10px] rounded-md border border-line bg-bg text-ink text-[0.85rem] resize-y font-[inherit]"
        />
        <p className="text-[0.68rem] text-muted mt-1 text-right">{comment.length}/2000</p>
      </div>

      {/* Actions */}
      <div className="flex gap-[10px] justify-end">
        <button
          onClick={onCancel}
          disabled={submitting}
          className="px-5 py-[10px] rounded-md border border-line bg-transparent text-ink-light text-[0.85rem] font-semibold cursor-pointer"
        >
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          className={`px-5 py-[10px] rounded-md border-none text-[0.85rem] font-bold ${canSubmit ? "bg-[image:var(--gradient-brand)] text-white cursor-pointer" : "bg-line-bright text-muted cursor-not-allowed"}`}
        >
          {submitting ? "Submitting..." : "Submit Review"}
        </button>
      </div>
    </div>
  );
}
