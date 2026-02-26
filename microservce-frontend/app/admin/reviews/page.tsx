"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";
import type { Review } from "../../../lib/types/review";

type PagedReviews = { content: Review[]; totalPages: number; totalElements: number; number: number };

type ReviewReport = {
  id: string;
  reviewId: string;
  reporterUserId: string;
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
  const router = useRouter();
  const session = useAuthSession();
  const {
    status: sessionStatus, isAuthenticated, canViewAdmin, profile, logout,
    canManageAdminOrders, canManageAdminProducts, canManageAdminCategories,
    canManageAdminVendors, canManageAdminPosters, apiClient, emailVerified, isSuperAdmin, isVendorAdmin,
  } = session;

  const [tab, setTab] = useState<Tab>("reviews");

  // Reviews state
  const [reviews, setReviews] = useState<Review[]>([]);
  const [reviewPage, setReviewPage] = useState(0);
  const [reviewTotalPages, setReviewTotalPages] = useState(0);
  const [reviewTotal, setReviewTotal] = useState(0);
  const [reviewLoading, setReviewLoading] = useState(true);
  const [activeFilter, setActiveFilter] = useState<string>("all");
  const [ratingFilter, setRatingFilter] = useState<string>("all");
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // Reports state
  const [reports, setReports] = useState<ReviewReport[]>([]);
  const [reportPage, setReportPage] = useState(0);
  const [reportTotalPages, setReportTotalPages] = useState(0);
  const [reportTotal, setReportTotal] = useState(0);
  const [reportLoading, setReportLoading] = useState(true);
  const [reportStatusFilter, setReportStatusFilter] = useState<string>("PENDING");
  const [updatingReportId, setUpdatingReportId] = useState<string | null>(null);
  const [reportAdminNotes, setReportAdminNotes] = useState("");
  const [reportNewStatus, setReportNewStatus] = useState<"REVIEWED" | "DISMISSED">("REVIEWED");
  const [savingReport, setSavingReport] = useState(false);

  const loadReviews = useCallback(async () => {
    if (!apiClient) return;
    setReviewLoading(true);
    try {
      const params = new URLSearchParams({ page: String(reviewPage), size: "20" });
      if (activeFilter !== "all") params.set("active", activeFilter);
      if (ratingFilter !== "all") params.set("rating", ratingFilter);
      const res = await apiClient.get(`/admin/reviews?${params.toString()}`);
      const data = res.data as PagedReviews;
      setReviews(data.content || []);
      setReviewTotalPages(data.totalPages || 0);
      setReviewTotal(data.totalElements || 0);
    } catch {
      toast.error("Failed to load reviews");
    } finally {
      setReviewLoading(false);
    }
  }, [apiClient, reviewPage, activeFilter, ratingFilter]);

  const loadReports = useCallback(async () => {
    if (!apiClient) return;
    setReportLoading(true);
    try {
      const params = new URLSearchParams({ page: String(reportPage), size: "20" });
      if (reportStatusFilter !== "all") params.set("status", reportStatusFilter);
      const res = await apiClient.get(`/admin/reviews/reports?${params.toString()}`);
      const data = res.data as PagedReports;
      setReports(data.content || []);
      setReportTotalPages(data.totalPages || 0);
      setReportTotal(data.totalElements || 0);
    } catch {
      toast.error("Failed to load reports");
    } finally {
      setReportLoading(false);
    }
  }, [apiClient, reportPage, reportStatusFilter]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated || !canViewAdmin) { router.replace("/"); return; }
    void loadReviews();
    void loadReports();
  }, [sessionStatus, isAuthenticated, canViewAdmin, router, loadReviews, loadReports]);

  const toggleActive = async (reviewId: string, currentlyActive: boolean) => {
    if (!apiClient || togglingId) return;
    setTogglingId(reviewId);
    try {
      const action = currentlyActive ? "deactivate" : "activate";
      await apiClient.put(`/admin/reviews/${reviewId}/${action}`);
      setReviews((old) => old.map((r) => (r.id === reviewId ? { ...r, active: !currentlyActive } : r)));
      toast.success(currentlyActive ? "Review deactivated" : "Review activated");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update review");
    } finally {
      setTogglingId(null);
    }
  };

  const deleteReview = async (reviewId: string) => {
    if (!apiClient || deletingId) return;
    setDeletingId(reviewId);
    try {
      await apiClient.delete(`/admin/reviews/${reviewId}`);
      setReviews((old) => old.filter((r) => r.id !== reviewId));
      setReviewTotal((t) => t - 1);
      toast.success("Review deleted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete review");
    } finally {
      setDeletingId(null);
    }
  };

  const updateReport = async (reportId: string) => {
    if (!apiClient || savingReport) return;
    setSavingReport(true);
    try {
      await apiClient.put(`/admin/reviews/reports/${reportId}`, { status: reportNewStatus, adminNotes: reportAdminNotes.trim() || null });
      setReports((old) => old.map((r) => (r.id === reportId ? { ...r, status: reportNewStatus, adminNotes: reportAdminNotes.trim() || null } : r)));
      setUpdatingReportId(null);
      setReportAdminNotes("");
      toast.success("Report updated");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update report");
    } finally {
      setSavingReport(false);
    }
  };

  const stars = (rating: number) => "★".repeat(rating) + "☆".repeat(5 - rating);

  const reasonLabel: Record<string, string> = { SPAM: "Spam", INAPPROPRIATE: "Inappropriate", FAKE: "Fake", OFF_TOPIC: "Off Topic", OTHER: "Other" };
  const statusColor: Record<string, string> = { PENDING: "var(--warning, #f59e0b)", REVIEWED: "var(--success)", DISMISSED: "var(--muted)" };

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <p style={{ color: "var(--muted)" }}>Loading...</p>
      </div>
    );
  }

  const tabStyle = (t: Tab) => ({
    padding: "10px 20px",
    borderRadius: "10px 10px 0 0",
    border: "1px solid var(--line-bright)",
    borderBottom: tab === t ? "none" : "1px solid var(--line-bright)",
    background: tab === t ? "rgba(17,17,40,0.7)" : "transparent",
    color: tab === t ? "#fff" : "var(--muted)",
    fontSize: "0.85rem",
    fontWeight: 700 as const,
    cursor: "pointer" as const,
    marginBottom: "-1px",
  });

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={isSuperAdmin}
        isVendorAdmin={isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={canManageAdminOrders}
        canManageAdminProducts={canManageAdminProducts}
        canManageAdminCategories={canManageAdminCategories}
        canManageAdminVendors={canManageAdminVendors}
        canManageAdminPosters={canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-8">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Admin Reviews</span>
        </nav>

        <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 900, color: "#fff", margin: "0 0 20px" }}>
          Review Moderation
        </h1>

        {/* Tabs */}
        <div style={{ display: "flex", gap: "4px", marginBottom: "0" }}>
          <button onClick={() => setTab("reviews")} style={tabStyle("reviews")}>
            Reviews ({reviewTotal})
          </button>
          <button onClick={() => setTab("reports")} style={tabStyle("reports")}>
            Reports ({reportTotal})
          </button>
        </div>

        <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "0 16px 16px 16px", padding: "24px" }}>

          {/* ── REVIEWS TAB ── */}
          {tab === "reviews" && (
            <>
              {/* Filters */}
              <div style={{ display: "flex", gap: "12px", marginBottom: "20px", flexWrap: "wrap" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Status:
                  <select
                    value={activeFilter}
                    onChange={(e) => { setActiveFilter(e.target.value); setReviewPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}
                  >
                    <option value="all">All</option>
                    <option value="true">Active</option>
                    <option value="false">Inactive</option>
                  </select>
                </label>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Rating:
                  <select
                    value={ratingFilter}
                    onChange={(e) => { setRatingFilter(e.target.value); setReviewPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}
                  >
                    <option value="all">All</option>
                    {[1, 2, 3, 4, 5].map((r) => <option key={r} value={String(r)}>{r} star{r !== 1 && "s"}</option>)}
                  </select>
                </label>
              </div>

              {reviewLoading ? (
                <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>Loading reviews...</p>
              ) : reviews.length === 0 ? (
                <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>No reviews match the current filters.</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  {reviews.map((review) => (
                    <div key={review.id} style={{ border: "1px solid var(--line-bright)", borderRadius: "12px", padding: "16px 20px", background: review.active ? "transparent" : "rgba(255,0,0,0.03)" }}>
                      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px", flexWrap: "wrap" }}>
                            <span style={{ color: "var(--brand-glow)", fontSize: "0.85rem" }}>{stars(review.rating)}</span>
                            <span style={{ fontSize: "0.72rem", padding: "2px 8px", borderRadius: "6px", fontWeight: 700, background: review.active ? "rgba(52,211,153,0.1)" : "rgba(239,68,68,0.1)", color: review.active ? "var(--success)" : "var(--error)" }}>
                              {review.active ? "Active" : "Inactive"}
                            </span>
                            {review.verifiedPurchase && (
                              <span style={{ fontSize: "0.65rem", fontWeight: 700, color: "var(--brand)", background: "rgba(0,212,255,0.08)", padding: "2px 8px", borderRadius: "6px" }}>
                                Verified
                              </span>
                            )}
                          </div>
                          <p style={{ margin: "0 0 4px", fontSize: "0.78rem", color: "var(--muted)" }}>
                            by {review.customerDisplayName} · {new Date(review.createdAt).toLocaleDateString()}
                          </p>
                          {review.title && <p style={{ margin: "0 0 4px", fontSize: "0.9rem", fontWeight: 700, color: "#fff" }}>{review.title}</p>}
                          <p style={{ margin: 0, fontSize: "0.82rem", color: "var(--ink-light)", lineHeight: 1.5 }}>{review.comment}</p>
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: "6px", flexShrink: 0 }}>
                          <button
                            onClick={() => void toggleActive(review.id, review.active)}
                            disabled={togglingId === review.id}
                            style={{
                              padding: "5px 12px", borderRadius: "8px", fontSize: "0.72rem", fontWeight: 700, cursor: togglingId === review.id ? "not-allowed" : "pointer",
                              border: "1px solid var(--line-bright)", background: review.active ? "rgba(239,68,68,0.08)" : "rgba(52,211,153,0.08)",
                              color: review.active ? "var(--error)" : "var(--success)", opacity: togglingId === review.id ? 0.5 : 1,
                            }}
                          >
                            {togglingId === review.id ? "..." : review.active ? "Deactivate" : "Activate"}
                          </button>
                          <button
                            onClick={() => void deleteReview(review.id)}
                            disabled={deletingId === review.id}
                            style={{
                              padding: "5px 12px", borderRadius: "8px", fontSize: "0.72rem", fontWeight: 700, cursor: deletingId === review.id ? "not-allowed" : "pointer",
                              border: "1px solid rgba(239,68,68,0.3)", background: "rgba(239,68,68,0.08)", color: "var(--error)",
                              opacity: deletingId === review.id ? 0.5 : 1,
                            }}
                          >
                            {deletingId === review.id ? "Deleting..." : "Delete"}
                          </button>
                          <Link
                            href={`/products/${encodeURIComponent(review.productId)}`}
                            style={{ fontSize: "0.68rem", color: "var(--brand)", textDecoration: "none", textAlign: "center" }}
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
                <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "20px" }}>
                  <button
                    onClick={() => setReviewPage((p) => Math.max(0, p - 1))}
                    disabled={reviewPage === 0}
                    style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: reviewPage === 0 ? "transparent" : "rgba(0,212,255,0.08)", color: reviewPage === 0 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: reviewPage === 0 ? "not-allowed" : "pointer" }}
                  >
                    ← Prev
                  </button>
                  <span style={{ display: "flex", alignItems: "center", fontSize: "0.8rem", color: "var(--muted)" }}>
                    {reviewPage + 1} / {reviewTotalPages}
                  </span>
                  <button
                    onClick={() => setReviewPage((p) => Math.min(reviewTotalPages - 1, p + 1))}
                    disabled={reviewPage >= reviewTotalPages - 1}
                    style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: reviewPage >= reviewTotalPages - 1 ? "transparent" : "rgba(0,212,255,0.08)", color: reviewPage >= reviewTotalPages - 1 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: reviewPage >= reviewTotalPages - 1 ? "not-allowed" : "pointer" }}
                  >
                    Next →
                  </button>
                </div>
              )}
            </>
          )}

          {/* ── REPORTS TAB ── */}
          {tab === "reports" && (
            <>
              {/* Filter */}
              <div style={{ display: "flex", gap: "12px", marginBottom: "20px" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.8rem", color: "var(--muted)" }}>
                  Status:
                  <select
                    value={reportStatusFilter}
                    onChange={(e) => { setReportStatusFilter(e.target.value); setReportPage(0); }}
                    style={{ padding: "6px 10px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)", color: "#fff", fontSize: "0.8rem" }}
                  >
                    <option value="all">All</option>
                    <option value="PENDING">Pending</option>
                    <option value="REVIEWED">Reviewed</option>
                    <option value="DISMISSED">Dismissed</option>
                  </select>
                </label>
              </div>

              {reportLoading ? (
                <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>Loading reports...</p>
              ) : reports.length === 0 ? (
                <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>No reports match the current filter.</p>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                  {reports.map((report) => (
                    <div key={report.id} style={{ border: "1px solid var(--line-bright)", borderRadius: "12px", padding: "16px 20px" }}>
                      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px", flexWrap: "wrap" }}>
                            <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", background: "rgba(239,68,68,0.1)", color: "var(--error)" }}>
                              {reasonLabel[report.reason] || report.reason}
                            </span>
                            <span style={{ fontSize: "0.72rem", fontWeight: 700, padding: "2px 8px", borderRadius: "6px", border: "1px solid var(--line-bright)", color: statusColor[report.status] || "var(--muted)" }}>
                              {report.status}
                            </span>
                          </div>
                          {report.description && (
                            <p style={{ margin: "0 0 6px", fontSize: "0.82rem", color: "var(--ink-light)", lineHeight: 1.5 }}>
                              {report.description}
                            </p>
                          )}
                          <p style={{ margin: 0, fontSize: "0.72rem", color: "var(--muted-2)" }}>
                            Review ID: {report.reviewId.slice(0, 8)}... · Reported {new Date(report.createdAt).toLocaleDateString()}
                          </p>
                          {report.adminNotes && (
                            <p style={{ margin: "6px 0 0", fontSize: "0.78rem", color: "var(--brand)", fontStyle: "italic" }}>
                              Admin notes: {report.adminNotes}
                            </p>
                          )}
                        </div>

                        {report.status === "PENDING" && updatingReportId !== report.id && (
                          <button
                            onClick={() => { setUpdatingReportId(report.id); setReportAdminNotes(""); setReportNewStatus("REVIEWED"); }}
                            style={{
                              padding: "5px 12px", borderRadius: "8px", fontSize: "0.72rem", fontWeight: 700,
                              border: "1px solid var(--line-bright)", background: "rgba(0,212,255,0.08)", color: "var(--brand)", cursor: "pointer", whiteSpace: "nowrap",
                            }}
                          >
                            Review Report
                          </button>
                        )}
                      </div>

                      {/* Update report form */}
                      {updatingReportId === report.id && (
                        <div style={{ marginTop: "12px", padding: "12px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: "rgba(0,212,255,0.03)" }}>
                          <div style={{ display: "flex", gap: "12px", marginBottom: "8px", alignItems: "center" }}>
                            <label style={{ fontSize: "0.8rem", color: "var(--muted)" }}>Action:</label>
                            <label style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.8rem", cursor: "pointer" }}>
                              <input
                                type="radio"
                                name={`report-${report.id}`}
                                checked={reportNewStatus === "REVIEWED"}
                                onChange={() => setReportNewStatus("REVIEWED")}
                              />
                              <span style={{ color: "var(--success)" }}>Mark Reviewed</span>
                            </label>
                            <label style={{ display: "flex", alignItems: "center", gap: "4px", fontSize: "0.8rem", cursor: "pointer" }}>
                              <input
                                type="radio"
                                name={`report-${report.id}`}
                                checked={reportNewStatus === "DISMISSED"}
                                onChange={() => setReportNewStatus("DISMISSED")}
                              />
                              <span style={{ color: "var(--muted)" }}>Dismiss</span>
                            </label>
                          </div>
                          <textarea
                            value={reportAdminNotes}
                            onChange={(e) => setReportAdminNotes(e.target.value)}
                            maxLength={500}
                            rows={2}
                            placeholder="Admin notes (optional)..."
                            style={{
                              width: "100%", padding: "8px 12px", borderRadius: "8px",
                              border: "1px solid var(--line-bright)", background: "rgba(255,255,255,0.04)",
                              color: "#fff", fontSize: "0.82rem", resize: "vertical", outline: "none",
                            }}
                          />
                          <div style={{ display: "flex", gap: "8px", marginTop: "8px", justifyContent: "flex-end" }}>
                            <button
                              onClick={() => setUpdatingReportId(null)}
                              style={{ padding: "6px 14px", borderRadius: "8px", border: "1px solid var(--line-bright)", background: "transparent", color: "var(--muted)", fontSize: "0.8rem", cursor: "pointer" }}
                            >
                              Cancel
                            </button>
                            <button
                              onClick={() => void updateReport(report.id)}
                              disabled={savingReport}
                              style={{
                                padding: "6px 14px", borderRadius: "8px", border: "none",
                                background: "var(--gradient-brand)", color: "#fff", fontSize: "0.8rem", fontWeight: 700,
                                cursor: savingReport ? "not-allowed" : "pointer", opacity: savingReport ? 0.6 : 1,
                              }}
                            >
                              {savingReport ? "Saving..." : "Save"}
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
                <div style={{ display: "flex", justifyContent: "center", gap: "8px", marginTop: "20px" }}>
                  <button
                    onClick={() => setReportPage((p) => Math.max(0, p - 1))}
                    disabled={reportPage === 0}
                    style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: reportPage === 0 ? "transparent" : "rgba(0,212,255,0.08)", color: reportPage === 0 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: reportPage === 0 ? "not-allowed" : "pointer" }}
                  >
                    ← Prev
                  </button>
                  <span style={{ display: "flex", alignItems: "center", fontSize: "0.8rem", color: "var(--muted)" }}>
                    {reportPage + 1} / {reportTotalPages}
                  </span>
                  <button
                    onClick={() => setReportPage((p) => Math.min(reportTotalPages - 1, p + 1))}
                    disabled={reportPage >= reportTotalPages - 1}
                    style={{ padding: "8px 16px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: reportPage >= reportTotalPages - 1 ? "transparent" : "rgba(0,212,255,0.08)", color: reportPage >= reportTotalPages - 1 ? "var(--muted-2)" : "var(--brand)", fontSize: "0.8rem", fontWeight: 600, cursor: reportPage >= reportTotalPages - 1 ? "not-allowed" : "pointer" }}
                  >
                    Next →
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}
