"use client";

import Link from "next/link";
import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import type { Review, VendorReply } from "../../../lib/types/review";
import VendorPageShell from "../../components/ui/VendorPageShell";

type PagedReviews = { content: Review[]; totalPages: number; totalElements: number; number: number };

export default function VendorReviewsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, isVendorAdmin, apiClient } = session;
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);

  // Reply state
  const [replyingToId, setReplyingToId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");

  // Edit reply state
  const [editingReplyId, setEditingReplyId] = useState<string | null>(null);
  const [editReplyText, setEditReplyText] = useState("");

  // Delete reply state
  const [deletingReplyId, setDeletingReplyId] = useState<string | null>(null);

  const ready = sessionStatus === "ready" && isAuthenticated && isVendorAdmin && !!apiClient;

  const { data: reviewsData, isLoading: loading } = useQuery({
    queryKey: ["vendor-reviews", page],
    queryFn: async () => {
      const res = await apiClient!.get(`/reviews/vendor?page=${page}&size=20`);
      return res.data as PagedReviews;
    },
    enabled: ready,
  });

  const reviews = reviewsData?.content || [];
  const totalPages = reviewsData?.totalPages || 0;
  const totalElements = reviewsData?.totalElements || 0;

  const submitReplyMutation = useMutation({
    mutationFn: async (reviewId: string) => {
      const res = await apiClient!.post(`/reviews/vendor/${reviewId}/reply`, { comment: replyText.trim() });
      return res.data as VendorReply;
    },
    onSuccess: () => {
      setReplyingToId(null);
      setReplyText("");
      toast.success("Reply posted");
      void queryClient.invalidateQueries({ queryKey: ["vendor-reviews", page] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to post reply");
    },
  });

  const updateReplyMutation = useMutation({
    mutationFn: async ({ replyId }: { replyId: string; reviewId: string }) => {
      const res = await apiClient!.put(`/reviews/vendor/replies/${replyId}`, { comment: editReplyText.trim() });
      return res.data as VendorReply;
    },
    onSuccess: () => {
      setEditingReplyId(null);
      setEditReplyText("");
      toast.success("Reply updated");
      void queryClient.invalidateQueries({ queryKey: ["vendor-reviews", page] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to update reply");
    },
  });

  const deleteReplyMutation = useMutation({
    mutationFn: async ({ replyId }: { replyId: string; reviewId: string }) => {
      await apiClient!.delete(`/reviews/vendor/replies/${replyId}`);
    },
    onMutate: ({ replyId }) => { setDeletingReplyId(replyId); },
    onSuccess: () => {
      toast.success("Reply deleted");
      void queryClient.invalidateQueries({ queryKey: ["vendor-reviews", page] });
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to delete reply");
    },
    onSettled: () => { setDeletingReplyId(null); },
  });

  const submittingReply = submitReplyMutation.isPending;
  const savingEdit = updateReplyMutation.isPending;

  const stars = (rating: number) => "\u2605".repeat(rating) + "\u2606".repeat(5 - rating);

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <VendorPageShell title="Customer Reviews" breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Reviews" }]}>
        <p className="text-muted text-center py-10">Loading...</p>
      </VendorPageShell>
    );
  }

  return (
      <VendorPageShell
        title="Customer Reviews"
        breadcrumbs={[{ label: "Vendor Portal", href: "/vendor" }, { label: "Reviews" }]}
        actions={
          <span className="text-sm text-muted">
            {totalElements} {totalElements === 1 ? "review" : "reviews"} for your products
          </span>
        }
      >

        {loading ? (
          <div className="text-center py-12 text-muted">Loading reviews...</div>
        ) : reviews.length === 0 ? (
          <div className="bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-line-bright rounded-xl px-6 py-12 text-center">
            <p className="text-[1.1rem] font-bold text-ink-light mb-2">No reviews yet</p>
            <p className="text-base text-muted m-0">Reviews from customers will appear here.</p>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            {reviews.map((review) => (
              <div
                key={review.id}
                className="animate-rise bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-line-bright rounded-lg px-6 py-5"
              >
                {/* Review header */}
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-brand-glow text-[0.9rem] tracking-[1px]">{stars(review.rating)}</span>
                      <span className="text-[0.75rem] text-muted">{review.rating}/5</span>
                      {review.verifiedPurchase && (
                        <span className="text-[0.65rem] font-bold text-success bg-[rgba(52,211,153,0.1)] px-2 py-0.5 rounded-[8px]">
                          Verified Purchase
                        </span>
                      )}
                    </div>
                    <p className="m-0 text-sm text-muted">
                      by <span className="text-ink-light font-semibold">{review.customerDisplayName}</span>
                      {" \u00b7 "}{new Date(review.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <Link
                    href={`/products/${encodeURIComponent(review.productId)}`}
                    className="text-xs text-brand no-underline whitespace-nowrap"
                  >
                    View Product &rarr;
                  </Link>
                </div>

                {/* Review content */}
                {review.title && (
                  <p className="mb-1.5 mt-0 text-[0.95rem] font-bold text-white">{review.title}</p>
                )}
                <p className="mb-3 mt-0 text-base text-ink-light leading-relaxed">
                  {review.comment}
                </p>

                {/* Helpful counts */}
                <div className="flex gap-3 text-xs text-muted mb-3">
                  <span>&#128077; {review.helpfulCount} helpful</span>
                  <span>&#128078; {review.notHelpfulCount} not helpful</span>
                </div>

                {/* Vendor reply */}
                {review.vendorReply && editingReplyId !== review.vendorReply.id && (
                  <div className="bg-[rgba(52,211,153,0.06)] border border-[rgba(52,211,153,0.15)] rounded-[12px] px-4 py-3.5 mt-2">
                    <div className="flex items-center justify-between mb-1.5">
                      <p className="m-0 text-xs font-bold text-[#34d399] uppercase tracking-[0.05em]">
                        Your Reply
                      </p>
                      <div className="flex gap-2">
                        <button
                          onClick={() => {
                            setEditingReplyId(review.vendorReply!.id);
                            setEditReplyText(review.vendorReply!.comment);
                          }}
                          className="text-xs text-brand bg-transparent border-none cursor-pointer p-0"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => deleteReplyMutation.mutate({ replyId: review.vendorReply!.id, reviewId: review.id })}
                          disabled={deletingReplyId === review.vendorReply!.id}
                          className="text-xs text-[var(--error)] bg-transparent border-none cursor-pointer p-0"
                          style={{ opacity: deletingReplyId === review.vendorReply!.id ? 0.5 : 1 }}
                        >
                          {deletingReplyId === review.vendorReply!.id ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                    </div>
                    <p className="m-0 text-base text-ink-light leading-relaxed">
                      {review.vendorReply.comment}
                    </p>
                    <p className="mt-1.5 mb-0 text-[0.65rem] text-muted-2">
                      {new Date(review.vendorReply.updatedAt || review.vendorReply.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                )}

                {/* Edit reply form */}
                {review.vendorReply && editingReplyId === review.vendorReply.id && (
                  <div className="mt-2">
                    <textarea
                      value={editReplyText}
                      onChange={(e) => setEditReplyText(e.target.value)}
                      maxLength={1000}
                      rows={3}
                      placeholder="Edit your reply..."
                      className="w-full px-3.5 py-2.5 rounded-md border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-base resize-y outline-none"
                    />
                    <div className="flex gap-2 mt-2 justify-end">
                      <button
                        onClick={() => { setEditingReplyId(null); setEditReplyText(""); }}
                        className="px-3.5 py-1.5 rounded-[8px] border border-line-bright bg-transparent text-muted text-sm cursor-pointer"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={() => updateReplyMutation.mutate({ replyId: review.vendorReply!.id, reviewId: review.id })}
                        disabled={savingEdit || !editReplyText.trim()}
                        className="px-3.5 py-1.5 rounded-[8px] border-none text-white text-sm font-bold"
                        style={{
                          background: editReplyText.trim() ? "var(--gradient-brand)" : "rgba(255,255,255,0.1)",
                          cursor: savingEdit || !editReplyText.trim() ? "not-allowed" : "pointer",
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
                    className="mt-1 px-3.5 py-1.5 rounded-[8px] border border-[rgba(52,211,153,0.3)] bg-[rgba(52,211,153,0.08)] text-[#34d399] text-sm font-semibold cursor-pointer"
                  >
                    Reply to Review
                  </button>
                )}

                {!review.vendorReply && replyingToId === review.id && (
                  <div className="mt-2">
                    <textarea
                      value={replyText}
                      onChange={(e) => setReplyText(e.target.value)}
                      maxLength={1000}
                      rows={3}
                      placeholder="Write your reply to this review..."
                      autoFocus
                      className="w-full px-3.5 py-2.5 rounded-md border border-line-bright bg-[rgba(255,255,255,0.04)] text-white text-base resize-y outline-none"
                    />
                    <div className="flex gap-2 mt-2 justify-end">
                      <button
                        onClick={() => { setReplyingToId(null); setReplyText(""); }}
                        className="px-3.5 py-1.5 rounded-[8px] border border-line-bright bg-transparent text-muted text-sm cursor-pointer"
                      >
                        Cancel
                      </button>
                      <button
                        onClick={() => submitReplyMutation.mutate(review.id)}
                        disabled={submittingReply || !replyText.trim()}
                        className="px-3.5 py-1.5 rounded-[8px] border-none text-white text-sm font-bold"
                        style={{
                          background: replyText.trim() ? "linear-gradient(135deg, #34d399, #059669)" : "rgba(255,255,255,0.1)",
                          cursor: submittingReply || !replyText.trim() ? "not-allowed" : "pointer",
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
              <div className="flex justify-center gap-2 mt-4">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded-md border border-line-bright text-sm font-semibold"
                  style={{
                    background: page === 0 ? "transparent" : "rgba(0,212,255,0.08)",
                    color: page === 0 ? "var(--muted-2)" : "var(--brand)",
                    cursor: page === 0 ? "not-allowed" : "pointer",
                  }}
                >
                  &larr; Previous
                </button>
                <span className="flex items-center text-sm text-muted px-3">
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded-md border border-line-bright text-sm font-semibold"
                  style={{
                    background: page >= totalPages - 1 ? "transparent" : "rgba(0,212,255,0.08)",
                    color: page >= totalPages - 1 ? "var(--muted-2)" : "var(--brand)",
                    cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
                  }}
                >
                  Next &rarr;
                </button>
              </div>
            )}
          </div>
        )}
      </VendorPageShell>
  );
}
