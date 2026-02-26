"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import { API_BASE } from "../../../lib/constants";
import ReviewSummaryBar from "./ReviewSummaryBar";
import ReviewCard from "./ReviewCard";
import ReviewForm from "./ReviewForm";

type VendorReply = { id: string; vendorId: string; comment: string; createdAt: string; updatedAt: string };
type Review = {
  id: string; customerId: string; customerDisplayName: string;
  productId: string; vendorId: string; orderId: string;
  rating: number; title: string | null; comment: string;
  images: string[]; helpfulCount: number; notHelpfulCount: number;
  verifiedPurchase: boolean; active: boolean;
  vendorReply: VendorReply | null; createdAt: string; updatedAt: string;
};
type Summary = { productId: string; averageRating: number; totalReviews: number; ratingDistribution: Record<number, number> };

type Props = {
  productId: string;
  vendorId: string;
  isAuthenticated: boolean;
  apiClient: any | null; // Axios instance, null when not authenticated
};

export default function ReviewSection({ productId, vendorId, isAuthenticated, apiClient }: Props) {
  const apiBase = API_BASE;

  const [summary, setSummary] = useState<Summary | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sortBy, setSortBy] = useState("recent");
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [reportingId, setReportingId] = useState<string | null>(null);
  const [reportReason, setReportReason] = useState("INAPPROPRIATE");
  const [reportDescription, setReportDescription] = useState("");

  const loadSummary = useCallback(async () => {
    try {
      const res = await fetch(`${apiBase}/reviews/products/${productId}/summary`, { cache: "no-store" });
      if (res.ok) setSummary(await res.json());
    } catch { /* ignore */ }
  }, [apiBase, productId]);

  const loadReviews = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch(`${apiBase}/reviews/products/${productId}?page=${page}&size=10&sortBy=${sortBy}`, { cache: "no-store" });
      if (res.ok) {
        const data = await res.json();
        setReviews(data.content || []);
        setTotalPages(data.totalPages || 0);
      }
    } catch { /* ignore */ }
    setLoading(false);
  }, [apiBase, productId, page, sortBy]);

  useEffect(() => { loadSummary(); }, [loadSummary]);
  useEffect(() => { loadReviews(); }, [loadReviews]);

  const handleVote = async (reviewId: string, helpful: boolean) => {
    if (!apiClient) { toast.error("Sign in to vote"); return; }
    try {
      await apiClient.post(`/reviews/me/${reviewId}/vote`, { helpful });
      loadReviews();
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Failed to vote");
    }
  };

  const handleReport = (reviewId: string) => {
    if (!apiClient) { toast.error("Sign in to report"); return; }
    setReportingId(reviewId);
    setReportReason("INAPPROPRIATE");
    setReportDescription("");
  };

  const submitReport = async () => {
    if (!apiClient || !reportingId) return;
    try {
      await apiClient.post(`/reviews/me/${reportingId}/report`, {
        reason: reportReason,
        description: reportDescription.trim() || undefined,
      });
      toast.success("Review reported");
      setReportingId(null);
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Failed to report");
    }
  };

  const handleReviewSubmitted = () => {
    setShowForm(false);
    setPage(0);
    loadSummary();
    loadReviews();
  };

  return (
    <section className="max-w-[1200px] mx-auto px-6 py-10">
      <h2 className="text-[1.3rem] font-extrabold text-ink mb-5">Customer Reviews</h2>

      {/* Summary */}
      {summary && (
        <ReviewSummaryBar
          averageRating={summary.averageRating}
          totalReviews={summary.totalReviews}
          ratingDistribution={summary.ratingDistribution}
          canWriteReview={isAuthenticated && !showForm}
          onWriteReview={() => setShowForm(true)}
        />
      )}

      {/* Review Form */}
      {showForm && apiClient && (
        <div className="mt-5">
          <ReviewForm
            productId={productId}
            vendorId={vendorId}
            apiClient={apiClient}
            onSuccess={handleReviewSubmitted}
            onCancel={() => setShowForm(false)}
          />
        </div>
      )}

      {/* Sort */}
      <div className="flex items-center justify-between mt-6 mb-2">
        <span className="text-sm text-muted">
          {summary ? `${summary.totalReviews} review${summary.totalReviews !== 1 ? "s" : ""}` : ""}
        </span>
        <select
          value={sortBy}
          onChange={(e) => { setSortBy(e.target.value); setPage(0); }}
          className="form-select px-3 py-[6px] rounded-[8px] border border-line bg-bg text-ink text-[0.78rem]"
        >
          <option value="recent">Most Recent</option>
          <option value="helpful">Most Helpful</option>
          <option value="rating_high">Highest Rating</option>
          <option value="rating_low">Lowest Rating</option>
        </select>
      </div>

      {/* Review list */}
      {loading && reviews.length === 0 ? (
        <p className="text-[0.85rem] text-muted py-5">Loading reviews...</p>
      ) : reviews.length === 0 ? (
        <p className="text-[0.85rem] text-muted py-5">No reviews yet. Be the first to review this product!</p>
      ) : (
        reviews.map((review) => (
          <ReviewCard
            key={review.id}
            review={review}
            onVote={handleVote}
            onReport={handleReport}
            votingDisabled={!isAuthenticated}
          />
        ))
      )}

      {/* Report Dialog */}
      {reportingId && (
        <div className="px-5 py-4 rounded-[12px] border border-line-bright bg-surface mt-3">
          <p className="text-[0.85rem] font-bold text-ink mb-[10px]">Report this review</p>
          <select
            value={reportReason}
            onChange={(e) => setReportReason(e.target.value)}
            className="form-select w-full mb-2 px-3 py-2 rounded-[8px] border border-line bg-bg text-ink text-sm"
          >
            <option value="INAPPROPRIATE">Inappropriate content</option>
            <option value="SPAM">Spam</option>
            <option value="FAKE">Fake review</option>
            <option value="OFF_TOPIC">Off topic</option>
            <option value="OTHER">Other</option>
          </select>
          <textarea
            value={reportDescription}
            onChange={(e) => setReportDescription(e.target.value)}
            placeholder="Additional details (optional)"
            maxLength={500}
            rows={2}
            className="form-input w-full mb-[10px] px-3 py-2 rounded-[8px] border border-line bg-bg text-ink text-sm resize-y"
          />
          <div className="flex gap-2 justify-end">
            <button
              onClick={() => setReportingId(null)}
              className="px-[14px] py-[7px] rounded-[8px] border border-line bg-transparent text-muted text-[0.78rem] font-bold cursor-pointer"
            >
              Cancel
            </button>
            <button
              onClick={() => { void submitReport(); }}
              className="px-[14px] py-[7px] rounded-[8px] border-none bg-danger text-white text-[0.78rem] font-bold cursor-pointer"
            >
              Submit Report
            </button>
          </div>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-5">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className={`px-4 py-2 rounded-[8px] border border-line bg-transparent text-[0.78rem] ${page === 0 ? "text-muted cursor-not-allowed" : "text-ink cursor-pointer"}`}
          >
            Previous
          </button>
          <span className="px-3 py-2 text-[0.78rem] text-muted">
            Page {page + 1} of {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className={`px-4 py-2 rounded-[8px] border border-line bg-transparent text-[0.78rem] ${page >= totalPages - 1 ? "text-muted cursor-not-allowed" : "text-ink cursor-pointer"}`}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
