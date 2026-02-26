"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import type { Review, VendorReply } from "../../../lib/types/review";

type PagedReviews = { content: Review[]; totalPages: number; totalElements: number; number: number };

export default function VendorReviewsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, isVendorAdmin, apiClient } = session;

  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  // Reply state
  const [replyingToId, setReplyingToId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const [submittingReply, setSubmittingReply] = useState(false);

  // Edit reply state
  const [editingReplyId, setEditingReplyId] = useState<string | null>(null);
  const [editReplyText, setEditReplyText] = useState("");
  const [savingEdit, setSavingEdit] = useState(false);

  // Delete reply state
  const [deletingReplyId, setDeletingReplyId] = useState<string | null>(null);

  const loadReviews = useCallback(async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const res = await apiClient.get(`/reviews/vendor?page=${page}&size=20`);
      const data = res.data as PagedReviews;
      setReviews(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch {
      toast.error("Failed to load reviews");
    } finally {
      setLoading(false);
    }
  }, [apiClient, page]);

  useEffect(() => {
    if (sessionStatus !== "ready" || !isAuthenticated || !isVendorAdmin) return;
    void loadReviews();
  }, [sessionStatus, isAuthenticated, isVendorAdmin, loadReviews]);

  const submitReply = async (reviewId: string) => {
    if (!apiClient || !replyText.trim() || submittingReply) return;
    setSubmittingReply(true);
    try {
      const res = await apiClient.post(`/reviews/vendor/${reviewId}/reply`, { comment: replyText.trim() });
      const reply = res.data as VendorReply;
      setReviews((old) => old.map((r) => (r.id === reviewId ? { ...r, vendorReply: reply } : r)));
      setReplyingToId(null);
      setReplyText("");
      toast.success("Reply posted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to post reply");
    } finally {
      setSubmittingReply(false);
    }
  };

  const updateReply = async (replyId: string, reviewId: string) => {
    if (!apiClient || !editReplyText.trim() || savingEdit) return;
    setSavingEdit(true);
    try {
      const res = await apiClient.put(`/reviews/vendor/replies/${replyId}`, { comment: editReplyText.trim() });
      const updated = res.data as VendorReply;
      setReviews((old) => old.map((r) => (r.id === reviewId ? { ...r, vendorReply: updated } : r)));
      setEditingReplyId(null);
      setEditReplyText("");
      toast.success("Reply updated");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update reply");
    } finally {
      setSavingEdit(false);
    }
  };

  const deleteReply = async (replyId: string, reviewId: string) => {
    if (!apiClient || deletingReplyId) return;
    setDeletingReplyId(replyId);
    try {
      await apiClient.delete(`/reviews/vendor/replies/${replyId}`);
      setReviews((old) => old.map((r) => (r.id === reviewId ? { ...r, vendorReply: null } : r)));
      toast.success("Reply deleted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete reply");
    } finally {
      setDeletingReplyId(null);
    }
  };

  const stars = (rating: number) => "★".repeat(rating) + "☆".repeat(5 - rating);

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <p style={{ color: "var(--muted)" }}>Loading...</p>
      </div>
    );
  }

  return (
      <main className="mx-auto max-w-7xl px-4 py-8">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <Link href="/vendor">Vendor Portal</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Reviews</span>
        </nav>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
          <div>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 900, color: "#fff", margin: "0 0 4px" }}>
              Customer Reviews
            </h1>
            <p style={{ fontSize: "0.8rem", color: "var(--muted)", margin: 0 }}>
              {totalElements} {totalElements === 1 ? "review" : "reviews"} for your products
            </p>
          </div>
        </div>

        {loading ? (
          <div style={{ textAlign: "center", padding: "48px 0", color: "var(--muted)" }}>Loading reviews...</div>
        ) : reviews.length === 0 ? (
          <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "20px", padding: "48px 24px", textAlign: "center" }}>
            <p style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--ink-light)", margin: "0 0 8px" }}>No reviews yet</p>
            <p style={{ fontSize: "0.85rem", color: "var(--muted)", margin: 0 }}>Reviews from customers will appear here.</p>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            {reviews.map((review) => (
              <div
                key={review.id}
                className="animate-rise"
                style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "16px", padding: "20px 24px" }}
              >
                {/* Review header */}
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px", marginBottom: "12px" }}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                      <span style={{ color: "var(--brand-glow)", fontSize: "0.9rem", letterSpacing: "1px" }}>{stars(review.rating)}</span>
                      <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>{review.rating}/5</span>
                      {review.verifiedPurchase && (
                        <span style={{ fontSize: "0.65rem", fontWeight: 700, color: "var(--success)", background: "rgba(52,211,153,0.1)", padding: "2px 8px", borderRadius: "8px" }}>
                          Verified Purchase
                        </span>
                      )}
                    </div>
                    <p style={{ margin: 0, fontSize: "0.8rem", color: "var(--muted)" }}>
                      by <span style={{ color: "var(--ink-light)", fontWeight: 600 }}>{review.customerDisplayName}</span>
                      {" · "}{new Date(review.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <Link
                    href={`/products/${encodeURIComponent(review.productId)}`}
                    style={{ fontSize: "0.72rem", color: "var(--brand)", textDecoration: "none", whiteSpace: "nowrap" }}
                  >
                    View Product →
                  </Link>
                </div>

                {/* Review content */}
                {review.title && (
                  <p style={{ margin: "0 0 6px", fontSize: "0.95rem", fontWeight: 700, color: "#fff" }}>{review.title}</p>
                )}
                <p style={{ margin: "0 0 12px", fontSize: "0.85rem", color: "var(--ink-light)", lineHeight: 1.6 }}>
                  {review.comment}
                </p>

                {/* Helpful counts */}
                <div style={{ display: "flex", gap: "12px", fontSize: "0.72rem", color: "var(--muted)", marginBottom: "12px" }}>
                  <span>👍 {review.helpfulCount} helpful</span>
                  <span>👎 {review.notHelpfulCount} not helpful</span>
                </div>

                {/* Vendor reply */}
                {review.vendorReply && editingReplyId !== review.vendorReply.id && (
                  <div style={{ background: "rgba(52,211,153,0.06)", border: "1px solid rgba(52,211,153,0.15)", borderRadius: "12px", padding: "14px 16px", marginTop: "8px" }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "6px" }}>
                      <p style={{ margin: 0, fontSize: "0.72rem", fontWeight: 700, color: "#34d399", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                        Your Reply
                      </p>
                      <div style={{ display: "flex", gap: "8px" }}>
                        <button
                          onClick={() => {
                            setEditingReplyId(review.vendorReply!.id);
                            setEditReplyText(review.vendorReply!.comment);
                          }}
                          style={{ fontSize: "0.72rem", color: "var(--brand)", background: "none", border: "none", cursor: "pointer", padding: 0 }}
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => void deleteReply(review.vendorReply!.id, review.id)}
                          disabled={deletingReplyId === review.vendorReply!.id}
                          style={{ fontSize: "0.72rem", color: "var(--error)", background: "none", border: "none", cursor: "pointer", padding: 0, opacity: deletingReplyId === review.vendorReply!.id ? 0.5 : 1 }}
                        >
                          {deletingReplyId === review.vendorReply!.id ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                    </div>
                    <p style={{ margin: 0, fontSize: "0.85rem", color: "var(--ink-light)", lineHeight: 1.6 }}>
                      {review.vendorReply.comment}
                    </p>
                    <p style={{ margin: "6px 0 0", fontSize: "0.65rem", color: "var(--muted-2)" }}>
                      {new Date(review.vendorReply.updatedAt || review.vendorReply.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                )}

                {/* Edit reply form */}
                {review.vendorReply && editingReplyId === review.vendorReply.id && (
                  <div style={{ marginTop: "8px" }}>
                    <textarea
                      value={editReplyText}
                      onChange={(e) => setEditReplyText(e.target.value)}
                      maxLength={1000}
                      rows={3}
                      placeholder="Edit your reply..."
                      style={{
                        width: "100%", padding: "10px 14px", borderRadius: "10px",
                        border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)",
                        color: "#fff", fontSize: "0.85rem", resize: "vertical", outline: "none",
                      }}
                    />
                    <div style={{ display: "flex", gap: "8px", marginTop: "8px", justifyContent: "flex-end" }}>
                      <button
                        onClick={() => { setEditingReplyId(null); setEditReplyText(""); }}
                        style={{ padding: "6px 14px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "transparent", color: "var(--muted)", fontSize: "0.8rem", cursor: "pointer" }}
                      >
                        Cancel
                      </button>
                      <button
                        onClick={() => void updateReply(review.vendorReply!.id, review.id)}
                        disabled={savingEdit || !editReplyText.trim()}
                        style={{
                          padding: "6px 14px", borderRadius: "8px", border: "none",
                          background: editReplyText.trim() ? "var(--gradient-brand)" : "rgba(255,255,255,0.1)",
                          color: "#fff", fontSize: "0.8rem", fontWeight: 700, cursor: savingEdit || !editReplyText.trim() ? "not-allowed" : "pointer",
                          opacity: savingEdit ? 0.6 : 1,
                        }}
                      >
                        {savingEdit ? "Saving..." : "Save"}
                      </button>
                    </div>
                  </div>
                )}

                {/* Reply button / form */}
                {!review.vendorReply && replyingToId !== review.id && (
                  <button
                    onClick={() => { setReplyingToId(review.id); setReplyText(""); }}
                    style={{
                      marginTop: "4px", padding: "6px 14px", borderRadius: "8px",
                      border: "1px solid rgba(52,211,153,0.3)", background: "rgba(52,211,153,0.08)",
                      color: "#34d399", fontSize: "0.8rem", fontWeight: 600, cursor: "pointer",
                    }}
                  >
                    Reply to Review
                  </button>
                )}

                {!review.vendorReply && replyingToId === review.id && (
                  <div style={{ marginTop: "8px" }}>
                    <textarea
                      value={replyText}
                      onChange={(e) => setReplyText(e.target.value)}
                      maxLength={1000}
                      rows={3}
                      placeholder="Write your reply to this review..."
                      autoFocus
                      style={{
                        width: "100%", padding: "10px 14px", borderRadius: "10px",
                        border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)",
                        color: "#fff", fontSize: "0.85rem", resize: "vertical", outline: "none",
                      }}
                    />
                    <div style={{ display: "flex", gap: "8px", marginTop: "8px", justifyContent: "flex-end" }}>
                      <button
                        onClick={() => { setReplyingToId(null); setReplyText(""); }}
                        style={{ padding: "6px 14px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "transparent", color: "var(--muted)", fontSize: "0.8rem", cursor: "pointer" }}
                      >
                        Cancel
                      </button>
                      <button
                        onClick={() => void submitReply(review.id)}
                        disabled={submittingReply || !replyText.trim()}
                        style={{
                          padding: "6px 14px", borderRadius: "8px", border: "none",
                          background: replyText.trim() ? "linear-gradient(135deg, #34d399, #059669)" : "rgba(255,255,255,0.1)",
                          color: "#fff", fontSize: "0.8rem", fontWeight: 700, cursor: submittingReply || !replyText.trim() ? "not-allowed" : "pointer",
                          opacity: submittingReply ? 0.6 : 1,
                        }}
                      >
                        {submittingReply ? "Posting..." : "Post Reply"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}

            {/* Pagination */}
            {totalPages > 1 && (
              <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "16px" }}>
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  style={{
                    padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)",
                    background: page === 0 ? "transparent" : "rgba(0,212,255,0.08)", color: page === 0 ? "var(--muted-2)" : "var(--brand)",
                    fontSize: "0.8rem", fontWeight: 600, cursor: page === 0 ? "not-allowed" : "pointer",
                  }}
                >
                  ← Previous
                </button>
                <span style={{ display: "flex", alignItems: "center", fontSize: "0.8rem", color: "var(--muted)", padding: "0 12px" }}>
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  style={{
                    padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)",
                    background: page >= totalPages - 1 ? "transparent" : "rgba(0,212,255,0.08)", color: page >= totalPages - 1 ? "var(--muted-2)" : "var(--brand)",
                    fontSize: "0.8rem", fontWeight: 600, cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
                  }}
                >
                  Next →
                </button>
              </div>
            )}
          </div>
        )}
      </main>
  );
}
