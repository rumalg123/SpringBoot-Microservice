"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import PromotionDetailPanel from "../components/promotions/PromotionDetailPanel";
import PromotionCouponManager from "../components/promotions/PromotionCouponManager";
import { useAuthSession } from "../../lib/authSession";
import type { PublicPromotion } from "../../lib/types/promotion";
import type { PagedResponse } from "../../lib/types/pagination";
import { API_BASE, PAGE_SIZE_DEFAULT as PAGE_SIZE } from "../../lib/constants";

/* ───── main page ───── */

export default function PromotionsPage() {
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = session;

  /* flash sales */
  const [flashSales, setFlashSales] = useState<PublicPromotion[]>([]);
  const [flashLoading, setFlashLoading] = useState(true);

  /* all promotions */
  const [promotions, setPromotions] = useState<PublicPromotion[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  /* filters */
  const [search, setSearch] = useState("");
  const [scopeFilter, setScopeFilter] = useState("");
  const [benefitFilter, setBenefitFilter] = useState("");

  /* debounced search */
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 400);
    return () => { if (searchTimerRef.current) clearTimeout(searchTimerRef.current); };
  }, [search]);

  /* reset page on filter change */
  useEffect(() => { setPage(0); }, [scopeFilter, benefitFilter]);

  /* fetch flash sales */
  useEffect(() => {
    const run = async () => {
      setFlashLoading(true);
      try {
        const res = await fetch(`${API_BASE}/promotions/flash-sales?page=0&size=10`, { cache: "no-store" });
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        const data = (await res.json()) as PagedResponse<PublicPromotion>;
        setFlashSales(data.content || []);
      } catch (err) {
        console.error("Failed to load flash sales", err);
        setFlashSales([]);
      } finally {
        setFlashLoading(false);
      }
    };
    void run();
  }, []);

  /* fetch all promotions */
  useEffect(() => {
    const run = async () => {
      setLoading(true);
      try {
        const params = new URLSearchParams();
        if (debouncedSearch.trim()) params.set("q", debouncedSearch.trim());
        if (scopeFilter) params.set("scopeType", scopeFilter);
        if (benefitFilter) params.set("benefitType", benefitFilter);
        params.set("page", String(page));
        params.set("size", String(PAGE_SIZE));

        const res = await fetch(`${API_BASE}/promotions?${params.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        const data = (await res.json()) as PagedResponse<PublicPromotion>;
        setPromotions(data.content || []);
        setTotalPages(data.totalPages ?? data.page?.totalPages ?? 0);
        setTotalElements(data.totalElements ?? data.page?.totalElements ?? 0);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : "Failed to load promotions");
        setPromotions([]);
        setTotalPages(0);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    };
    void run();
  }, [debouncedSearch, scopeFilter, benefitFilter, page]);

  /* loading state */
  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-base text-muted">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg">
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumb */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&rsaquo;</span>
          <span className="breadcrumb-current">Promotions</span>
        </nav>

        {/* Page Header */}
        <div className="mb-8">
          <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
            Promotions & Deals
          </h1>
          <p className="mt-1.5 text-sm text-muted">
            Discover current offers, flash sales, and savings across the platform.
          </p>
        </div>

        {/* Flash Sales Section */}
        <PromotionCouponManager
          flashSales={flashSales}
          flashLoading={flashLoading}
        />

        {/* All Promotions Section */}
        <PromotionDetailPanel
          promotions={promotions}
          loading={loading}
          totalPages={totalPages}
          totalElements={totalElements}
          page={page}
          search={search}
          scopeFilter={scopeFilter}
          benefitFilter={benefitFilter}
          debouncedSearch={debouncedSearch}
          onSearchChange={setSearch}
          onScopeFilterChange={setScopeFilter}
          onBenefitFilterChange={setBenefitFilter}
          onPageChange={setPage}
        />
      </main>

      <Footer />
    </div>
  );
}
