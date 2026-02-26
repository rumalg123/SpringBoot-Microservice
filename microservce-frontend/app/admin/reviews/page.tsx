"use client";

import Link from "next/link";
import { useState } from "react";
import toast from "react-hot-toast";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useAuthSession } from "../../../lib/authSession";
import type { Review } from "../../../lib/types/review";
import AdminPageShell from "../../components/ui/AdminPageShell";

type PagedReviews = { content: Review[]; totalPages: number; totalElements: number; number: number };

type ReviewReport = {
  id: string;
  reviewId: string;
  reporterUserId: string;
  reporterName?: string;
  reviewTitle?: string;
  productName?: string;
  reason: "SPAM" | "INAPPROPRIATE" | "FAKE" | "OFF_TOPIC" | "OTHER";
  description: string | null;
  status: "PENDING" | "REVIEWED" | "DISMISSED";
  adminNotes: string | null;
  createdAt: string;
  updatedAt: string;
};

type PagedReports = { content: ReviewReport[]; totalPages: number; totalElements: number; number: number };

type Tab = "reviews" | "reports";

export default function AdminReviewsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, apiClient } = session;

  const [tab, setTab] = useState<Tab>("reviews");

  // Reviews UI state
  const [reviewPage, setReviewPage] = useState(0);
  const [activeFilter, setActiveFilter] = useState<string>("all");
  const [ratingFilter, setRatingFilter] = useState<string>("all");
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // Reports UI state
  const [reportPage, setReportPage] = useState(0);
  const [reportStatusFilter, setReportStatusFilter] = useState<string>("PENDING");
  const [updatingReportId, setUpdatingReportId] = useState<string | null>(null);
  const [reportAdminNotes, setReportAdminNotes] = useState("");
  const [reportNewStatus, setReportNewStatus] = useState<"REVIEWED" | "DISMISSED">("REVIEWED");

  // Reviews query
  const { data: reviewsData, isLoading: reviewLoading, refetch: refetchReviews } = useQuery({
    queryKey: ["admin-reviews", reviewPage, activeFilter, ratingFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(reviewPage), size: "20" });
      if (activeFilter !== "all") params.set("active", activeFilter);
      if (ratingFilter !== "all") params.set("rating", ratingFilter);
      const res = await apiClient!.get(`/admin/reviews?${params.toString()}`);
      return res.data as PagedReviews;
    },
    enabled: sessionStatus === "ready" && !!apiClient,
  });

  const reviews = reviewsData?.content ?? [];
  const reviewTotalPages = reviewsData?.totalPages ?? 0;
  const reviewTotal = reviewsData?.totalElements ?? 0;

  // Reports query
  const { data: reportsData, isLoading: reportLoading, refetch: refetchReports } = useQuery({
    queryKey: ["admin-review-reports", reportPage, reportStatusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(reportPage), size: "20" });
      if (reportStatusFilter !== "all") params.set("status", reportStatusFilter);
      const res = await apiClient!.get(`/admin/reviews/reports?${params.toString()}`);
      return res.data as PagedReports;
    },
    enabled: sessionStatus === "ready" && !!apiClient,
  });

  const reports = reportsData?.content ?? [];
  const reportTotalPages = reportsData?.totalPages ?? 0;
  const reportTotal = reportsData?.totalElements ?? 0;

  // Mutations
  const toggleActiveMutation = useMutation({
    mutationFn: async ({ reviewId, currentlyActive }: { reviewId: string; currentlyActive: boolean }) => {
      const action = currentlyActive ? "deactivate" : "activate";
      await apiClient!.put(`/admin/reviews/${reviewId}/${action}`);
      return { reviewId, currentlyActive };
    },
    onMutate: ({ reviewId }) => {
      setTogglingId(reviewId);
    },
    onSuccess: ({ currentlyActive }) => {
      void refetchReviews();
      toast.success(currentlyActive ? "Review deactivated" : "Review activated");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to update review");
    },
    onSettled: () => {
      setTogglingId(null);
    },
  });

  const deleteReviewMutation = useMutation({
    mutationFn: async (reviewId: string) => {
      await apiClient!.delete(`/admin/reviews/${reviewId}`);
      return reviewId;
    },
    onMutate: (reviewId) => {
      setDeletingId(reviewId);
    },
    onSuccess: () => {
      void refetchReviews();
      toast.success("Review deleted");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to delete review");
    },
    onSettled: () => {
      setDeletingId(null);
    },
  });

  const updateReportMutation = useMutation({
    mutationFn: async (reportId: string) => {
      await apiClient!.put(`/admin/reviews/reports/${reportId}`, { status: reportNewStatus, adminNotes: reportAdminNotes.trim() || null });
      return reportId;
    },
    onSuccess: () => {
      setUpdatingReportId(null);
      setReportAdminNotes("");
      void refetchReports();
      toast.success("Report updated");
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : "Failed to update report");
    },
  });

  const stars = (rating: number) => "★".repeat(rating) + "☆".repeat(5 - rating);

  const reasonLabel: Record<string, string> = { SPAM: "Spam", INAPPROPRIATE: "Inappropriate", FAKE: "Fake", OFF_TOPIC: "Off Topic", OTHER: "Other" };
  const statusColorClass: Record<string, string> = { PENDING: "text-warning", REVIEWED: "text-success", DISMISSED: "text-muted" };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="min-h-screen bg-bg grid place-items-center">
        <p className="text-muted">Loading...</p>
      </div>
    );
  }
  if (!session.canManageAdminReviews) {
    return (
      <AdminPageShell title="Reviews" breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Reviews" }]}>
        <p className="text-center text-muted py-20">You do not have permission to manage reviews.</p>
      </AdminPageShell>
    );
  }

  const tabClass = (t: Tab) =>
    `px-5 py-2.5 rounded-t-md border border-line-bright text-sm font-bold cursor-pointer -mb-px ${
      tab === t
        ? "bg-surface text-white border-b-transparent"
        : "bg-transparent text-muted"
    }`;

  return (
    <AdminPageShell
      title="Review Moderation"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Reviews" }]}
    >

        {/* Tabs */}
        <div className="flex gap-1">
          <button onClick={() => setTab("reviews")} className={tabClass("reviews")}>
            Reviews ({reviewTotal})
          </button>
          <button onClick={() => setTab("reports")} className={tabClass("reports")}>
            Reports ({reportTotal})
          </button>
        </div>

        <div className="bg-surface backdrop-blur-[16px] border border-line-bright rounded-[0_16px_16px_16px] p-6">

          {/* ── REVIEWS TAB ── */}
          {tab === "reviews" && (
            <>
              {/* Filters */}
              <div className="flex gap-3 mb-5 flex-wrap">
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Status:
                  <select
                    value={activeFilter}
                    onChange={(e) => { setActiveFilter(e.target.value); setReviewPage(0); }}
                    className="filter-select"
                  >
                    <option value="all">All</option>
                    <option value="true">Active</option>
                    <option value="false">Inactive</option>
                  </select>
                </label>
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Rating:
                  <select
                    value={ratingFilter}
                    onChange={(e) => { setRatingFilter(e.target.value); setReviewPage(0); }}
                    className="filter-select"
                  >
                    <option value="all">All</option>
                    {[1, 2, 3, 4, 5].map((r) => <option key={r} value={String(r)}>{r} star{r !== 1 && "s"}</option>)}
                  </select>
                </label>
              </div>

              {reviewLoading ? (
                <p className="text-center text-muted py-8">Loading reviews...</p>
              ) : reviews.length === 0 ? (
                <p className="text-center text-muted py-8">No reviews match the current filters.</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {reviews.map((review) => (
                    <div key={review.id} className={`border border-line-bright rounded-lg px-5 py-4 ${review.active ? "bg-transparent" : "bg-danger-soft"}`}>
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1 flex-wrap">
                            <span className="text-brand-glow text-sm">{stars(review.rating)}</span>
                            <span className={`text-xs px-2 py-0.5 rounded-sm font-bold ${review.active ? "bg-success-soft text-success" : "bg-danger-soft text-danger"}`}>
                              {review.active ? "Active" : "Inactive"}
                            </span>
                            {review.verifiedPurchase && (
                              <span className="text-[0.65rem] font-bold text-brand bg-brand-soft px-2 py-0.5 rounded-sm">
                                Verified
                              </span>
                            )}
                          </div>
                          <p className="mb-1 text-sm text-muted">
                            by {review.customerDisplayName} · {new Date(review.createdAt).toLocaleDateString()}
                          </p>
                          {review.title && <p className="mb-1 text-base font-bold text-white">{review.title}</p>}
                          <p className="text-base text-ink-light leading-relaxed">{review.comment}</p>
                        </div>
                        <div className="flex flex-col gap-1.5 shrink-0">
                          <button
                            onClick={() => toggleActiveMutation.mutate({ reviewId: review.id, currentlyActive: review.active })}
                            disabled={togglingId === review.id}
                            className={`px-3 py-1 rounded-sm text-xs font-bold border border-line-bright ${togglingId === review.id ? "cursor-not-allowed opacity-50" : "cursor-pointer"} ${review.active ? "bg-danger-soft text-danger" : "bg-success-soft text-success"}`}
                          >
                            {togglingId === review.id ? "..." : review.active ? "Deactivate" : "Activate"}
                          </button>
                          <button
                            onClick={() => deleteReviewMutation.mutate(review.id)}
                            disabled={deletingId === review.id}
                            className={`px-3 py-1 rounded-sm text-xs font-bold border border-danger-soft bg-danger-soft text-danger ${deletingId === review.id ? "cursor-not-allowed opacity-50" : "cursor-pointer"}`}
                          >
                            {deletingId === review.id ? "Deleting..." : "Delete"}
                          </button>
                          <Link
                            href={`/products/${encodeURIComponent(review.productId)}`}
                            className="text-xs text-brand no-underline text-center"
                          >
                            View Product
                          </Link>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Pagination */}
              {reviewTotalPages > 1 && (
                <div className="flex justify-center gap-2 mt-5">
                  <button
                    onClick={() => setReviewPage((p) => Math.max(0, p - 1))}
                    disabled={reviewPage === 0}
                    className={`px-4 py-2 rounded-md border border-line-bright text-sm font-semibold ${reviewPage === 0 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
                  >
                    &larr; Prev
                  </button>
                  <span className="flex items-center text-sm text-muted">
                    {reviewPage + 1} / {reviewTotalPages}
                  </span>
                  <button
                    onClick={() => setReviewPage((p) => Math.min(reviewTotalPages - 1, p + 1))}
                    disabled={reviewPage >= reviewTotalPages - 1}
                    className={`px-4 py-2 rounded-md border border-line-bright text-sm font-semibold ${reviewPage >= reviewTotalPages - 1 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
                  >
                    Next &rarr;
                  </button>
                </div>
              )}
            </>
          )}

          {/* ── REPORTS TAB ── */}
          {tab === "reports" && (
            <>
              {/* Filter */}
              <div className="flex gap-3 mb-5">
                <label className="flex items-center gap-1.5 text-sm text-muted">
                  Status:
                  <select
                    value={reportStatusFilter}
                    onChange={(e) => { setReportStatusFilter(e.target.value); setReportPage(0); }}
                    className="filter-select"
                  >
                    <option value="all">All</option>
                    <option value="PENDING">Pending</option>
                    <option value="REVIEWED">Reviewed</option>
                    <option value="DISMISSED">Dismissed</option>
                  </select>
                </label>
              </div>

              {reportLoading ? (
                <p className="text-center text-muted py-8">Loading reports...</p>
              ) : reports.length === 0 ? (
                <p className="text-center text-muted py-8">No reports match the current filter.</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {reports.map((report) => (
                    <div key={report.id} className="border border-line-bright rounded-lg px-5 py-4">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                            <span className="text-xs font-bold px-2 py-0.5 rounded-sm bg-danger-soft text-danger">
                              {reasonLabel[report.reason] || report.reason}
                            </span>
                            <span className={`text-xs font-bold px-2 py-0.5 rounded-sm border border-line-bright ${statusColorClass[report.status] || "text-muted"}`}>
                              {report.status}
                            </span>
                          </div>
                          {report.description && (
                            <p className="mb-1.5 text-base text-ink-light leading-relaxed">
                              {report.description}
                            </p>
                          )}
                          <p className="text-xs text-muted-2">
                            {report.reviewTitle || report.productName || "Review"} · {report.reporterName ? `by ${report.reporterName} · ` : ""}{new Date(report.createdAt).toLocaleDateString()}
                          </p>
                          {report.adminNotes && (
                            <p className="mt-1.5 text-sm text-brand italic">
                              Admin notes: {report.adminNotes}
                            </p>
                          )}
                        </div>

                        {report.status === "PENDING" && updatingReportId !== report.id && (
                          <button
                            onClick={() => { setUpdatingReportId(report.id); setReportAdminNotes(""); setReportNewStatus("REVIEWED"); }}
                            className="px-3 py-1 rounded-sm text-xs font-bold border border-line-bright bg-brand-soft text-brand cursor-pointer whitespace-nowrap"
                          >
                            Review Report
                          </button>
                        )}
                      </div>

                      {/* Update report form */}
                      {updatingReportId === report.id && (
                        <div className="mt-3 p-3 rounded-md border border-line-bright bg-[rgba(0,212,255,0.03)]">
                          <div className="flex gap-3 mb-2 items-center">
                            <label className="text-sm text-muted">Action:</label>
                            <label className="flex items-center gap-1 text-sm cursor-pointer">
                              <input
                                type="radio"
                                name={`report-${report.id}`}
                                checked={reportNewStatus === "REVIEWED"}
                                onChange={() => setReportNewStatus("REVIEWED")}
                              />
                              <span className="text-success">Mark Reviewed</span>
                            </label>
                            <label className="flex items-center gap-1 text-sm cursor-pointer">
                              <input
                                type="radio"
                                name={`report-${report.id}`}
                                checked={reportNewStatus === "DISMISSED"}
                                onChange={() => setReportNewStatus("DISMISSED")}
                              />
                              <span className="text-muted">Dismiss</span>
                            </label>
                          </div>
                          <textarea
                            value={reportAdminNotes}
                            onChange={(e) => setReportAdminNotes(e.target.value)}
                            maxLength={500}
                            rows={2}
                            placeholder="Admin notes (optional)..."
                            className="form-input w-full text-base resize-y"
                          />
                          <div className="flex gap-2 mt-2 justify-end">
                            <button
                              onClick={() => setUpdatingReportId(null)}
                              className="btn-ghost px-3.5 py-1.5 text-sm"
                            >
                              Cancel
                            </button>
                            <button
                              onClick={() => updateReportMutation.mutate(report.id)}
                              disabled={updateReportMutation.isPending}
                              className={`btn-primary px-3.5 py-1.5 text-sm font-bold ${updateReportMutation.isPending ? "cursor-not-allowed opacity-60" : ""}`}
                            >
                              {updateReportMutation.isPending ? "Saving..." : "Save"}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {/* Pagination */}
              {reportTotalPages > 1 && (
                <div className="flex justify-center gap-2 mt-5">
                  <button
                    onClick={() => setReportPage((p) => Math.max(0, p - 1))}
                    disabled={reportPage === 0}
                    className={`px-4 py-2 rounded-md border border-line-bright text-sm font-semibold ${reportPage === 0 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
                  >
                    &larr; Prev
                  </button>
                  <span className="flex items-center text-sm text-muted">
                    {reportPage + 1} / {reportTotalPages}
                  </span>
                  <button
                    onClick={() => setReportPage((p) => Math.min(reportTotalPages - 1, p + 1))}
                    disabled={reportPage >= reportTotalPages - 1}
                    className={`px-4 py-2 rounded-md border border-line-bright text-sm font-semibold ${reportPage >= reportTotalPages - 1 ? "bg-transparent text-muted-2 cursor-not-allowed" : "bg-brand-soft text-brand cursor-pointer"}`}
                  >
                    Next &rarr;
                  </button>
                </div>
              )}
            </>
          )}
        </div>
    </AdminPageShell>
  );
}
