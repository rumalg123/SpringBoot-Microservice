"use client";

import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
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
  const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";

  const [summary, setSummary] = useState<Summary | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sortBy, setSortBy] = useState("recent");
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

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

  const handleReport = async (reviewId: string) => {
    if (!apiClient) { toast.error("Sign in to report"); return; }
    try {
      await apiClient.post(`/reviews/me/${reviewId}/report`, { reason: "INAPPROPRIATE" });
      toast.success("Review reported");
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
    <section style={{ maxWidth: "1200px", margin: "0 auto", padding: "40px 24px" }}>
      <h2 style={{ fontSize: "1.3rem", fontWeight: 800, color: "var(--ink)", margin: "0 0 20px" }}>Customer Reviews</h2>

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
        <div style={{ marginTop: "20px" }}>
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
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: "24px", marginBottom: "8px" }}>
        <span style={{ fontSize: "0.8rem", color: "var(--muted)" }}>
          {summary ? `${summary.totalReviews} review${summary.totalReviews !== 1 ? "s" : ""}` : ""}
        </span>
        <select
          value={sortBy}
          onChange={(e) => { setSortBy(e.target.value); setPage(0); }}
          className="form-select"
          style={{ padding: "6px 12px", borderRadius: "8px", border: "1px solid var(--line)", background: "var(--bg)", color: "var(--ink)", fontSize: "0.78rem" }}
        >
          <option value="recent">Most Recent</option>
          <option value="helpful">Most Helpful</option>
          <option value="rating_high">Highest Rating</option>
          <option value="rating_low">Lowest Rating</option>
        </select>
      </div>

      {/* Review list */}
      {loading && reviews.length === 0 ? (
        <p style={{ fontSize: "0.85rem", color: "var(--muted)", padding: "20px 0" }}>Loading reviews...</p>
      ) : reviews.length === 0 ? (
        <p style={{ fontSize: "0.85rem", color: "var(--muted)", padding: "20px 0" }}>No reviews yet. Be the first to review this product!</p>
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

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "20px" }}>
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            style={{ padding: "8px 16px", borderRadius: "8px", border: "1px solid var(--line)", background: "transparent", color: page === 0 ? "var(--muted)" : "var(--ink)", fontSize: "0.78rem", cursor: page === 0 ? "not-allowed" : "pointer" }}
          >
            Previous
          </button>
          <span style={{ padding: "8px 12px", fontSize: "0.78rem", color: "var(--muted)" }}>
            Page {page + 1} of {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            style={{ padding: "8px 16px", borderRadius: "8px", border: "1px solid var(--line)", background: "transparent", color: page >= totalPages - 1 ? "var(--muted)" : "var(--ink)", fontSize: "0.78rem", cursor: page >= totalPages - 1 ? "not-allowed" : "pointer" }}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}
