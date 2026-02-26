"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthSession } from "../../../../lib/authSession";
import { canTransitionOrderStatus } from "./orderStatus";
import type { AdminOrder, AdminOrdersPageResponse } from "./types";

const DEFAULT_PAGE_SIZE = 20;

type SessionLike = ReturnType<typeof useAuthSession>;
type ApiClientLike = SessionLike["apiClient"];

type Params = {
  session: SessionLike;
  router: ReturnType<typeof useRouter>;
  setStatusMessage: (message: string) => void;
  onResetPanelsForListReload: () => void;
  onVendorScopedOrderStatusAttempt: (orderId: string) => Promise<void>;
  onRefreshOrderHistoryIfOpen: (orderId: string) => Promise<void>;
};

export default function useOrderList({
  session,
  router,
  setStatusMessage,
  onResetPanelsForListReload,
  onVendorScopedOrderStatusAttempt,
  onRefreshOrderHistoryIfOpen,
}: Params) {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [customerEmailInput, setCustomerEmailInput] = useState("");
  const [customerEmailFilter, setCustomerEmailFilter] = useState("");
  const [statusDrafts, setStatusDrafts] = useState<Record<string, string>>({});
  const [statusSavingId, setStatusSavingId] = useState<string | null>(null);
  const [selectedOrderIds, setSelectedOrderIds] = useState<string[]>([]);
  const [bulkStatus, setBulkStatus] = useState<string>("PROCESSING");
  const [bulkSaving, setBulkSaving] = useState(false);
  const resetPanelsForListReloadRef = useRef(onResetPanelsForListReload);

  useEffect(() => {
    resetPanelsForListReloadRef.current = onResetPanelsForListReload;
  }, [onResetPanelsForListReload]);

  // Auth guards
  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) { router.replace("/"); return; }
    if (!session.canManageAdminOrders) { router.replace("/orders"); return; }
  }, [router, session.status, session.isAuthenticated, session.canManageAdminOrders]);

  const canFetch =
    session.status === "ready" &&
    session.isAuthenticated &&
    Boolean(session.canManageAdminOrders) &&
    Boolean(session.apiClient);

  const ordersQuery = useQuery<AdminOrdersPageResponse>({
    queryKey: ["admin-orders", page, customerEmailFilter],
    queryFn: async () => {
      const apiClient: ApiClientLike = session.apiClient;
      if (!apiClient) throw new Error("No API client");
      const params = new URLSearchParams();
      params.set("page", String(page));
      params.set("size", String(DEFAULT_PAGE_SIZE));
      params.set("sort", "createdAt,DESC");
      if (customerEmailFilter.trim()) params.set("customerEmail", customerEmailFilter.trim());
      const res = await apiClient.get(`/admin/orders?${params.toString()}`);
      return res.data as AdminOrdersPageResponse;
    },
    enabled: canFetch,
  });

  const ordersPage = ordersQuery.data ?? null;
  const ordersLoading = ordersQuery.isLoading || ordersQuery.isFetching;

  // Sync statusDrafts and reset panels/selection when data changes
  const prevDataRef = useRef<AdminOrdersPageResponse | null>(null);
  useEffect(() => {
    if (!ordersQuery.data || ordersQuery.data === prevDataRef.current) return;
    prevDataRef.current = ordersQuery.data;
    const nextPageData = ordersQuery.data;
    setStatusDrafts((prev) => {
      const next = { ...prev };
      for (const order of nextPageData.content || []) {
        if (!next[order.id]) next[order.id] = order.status || "PENDING";
      }
      return next;
    });
    setSelectedOrderIds([]);
    resetPanelsForListReloadRef.current();
    setStatusMessage("Admin orders loaded.");
  }, [ordersQuery.data, setStatusMessage]);

  useEffect(() => {
    if (ordersQuery.error) {
      setStatusMessage(ordersQuery.error instanceof Error ? ordersQuery.error.message : "Failed to load admin orders.");
    }
  }, [ordersQuery.error, setStatusMessage]);

  const applyFilter = () => {
    if (ordersLoading) return;
    const nextFilter = customerEmailInput.trim();
    setCustomerEmailFilter(nextFilter);
    setPage(0);
  };

  const clearFilter = () => {
    if (ordersLoading) return;
    setCustomerEmailInput("");
    setCustomerEmailFilter("");
    setPage(0);
  };

  const goToPage = (nextPage: number) => {
    if (nextPage < 0 || ordersLoading) return;
    setPage(nextPage);
  };

  const updateOrderStatus = async (orderId: string) => {
    const apiClient: ApiClientLike = session.apiClient;
    if (!apiClient || statusSavingId || bulkSaving || ordersLoading) return;
    if (session.isVendorAdmin || session.isVendorStaff) {
      await onVendorScopedOrderStatusAttempt(orderId);
      setStatusMessage("Use vendor-order status updates for vendor-scoped order management.");
      return;
    }
    const nextStatus = (statusDrafts[orderId] || "").trim().toUpperCase();
    if (!nextStatus) return;
    const currentOrder = ordersPage?.content.find((o) => o.id === orderId);
    if (currentOrder && !canTransitionOrderStatus(currentOrder.status, nextStatus)) {
      setStatusMessage(`Invalid status transition: ${currentOrder.status} -> ${nextStatus}`);
      return;
    }
    setStatusSavingId(orderId);
    try {
      const res = await apiClient.patch(`/admin/orders/${orderId}/status`, { status: nextStatus });
      const updated = res.data as AdminOrder;
      queryClient.setQueryData<AdminOrdersPageResponse>(
        ["admin-orders", page, customerEmailFilter],
        (prev) => prev ? ({
          ...prev,
          content: prev.content.map((order) => (order.id === orderId ? { ...order, ...updated } : order)),
        }) : prev!
      );
      setStatusDrafts((prev) => ({ ...prev, [orderId]: updated.status || nextStatus }));
      setStatusMessage(`Order ${orderId} updated to ${updated.status || nextStatus}.`);
      await onRefreshOrderHistoryIfOpen(orderId);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to update order status.");
    } finally {
      setStatusSavingId(null);
    }
  };

  const toggleOrderSelection = (orderId: string, checked: boolean) => {
    setSelectedOrderIds((prev) => {
      if (checked) return prev.includes(orderId) ? prev : [...prev, orderId];
      return prev.filter((id) => id !== orderId);
    });
  };

  const orders = ordersPage?.content || [];
  const toggleSelectAllCurrentPage = (checked: boolean) => {
    if (!checked) {
      setSelectedOrderIds([]);
      return;
    }
    setSelectedOrderIds(orders.map((o) => o.id));
  };

  const applyBulkStatus = async () => {
    const apiClient: ApiClientLike = session.apiClient;
    if (!apiClient || bulkSaving || statusSavingId || selectedOrderIds.length === 0) return;
    const nextStatus = bulkStatus.trim().toUpperCase();
    if (!nextStatus) return;
    setBulkSaving(true);
    let success = 0;
    let failed = 0;
    let skippedInvalid = 0;
    for (const orderId of selectedOrderIds) {
      const row = ordersPage?.content.find((o) => o.id === orderId);
      if (row && !canTransitionOrderStatus(row.status, nextStatus)) {
        skippedInvalid += 1;
        continue;
      }
      try {
        const res = await apiClient.patch(`/admin/orders/${orderId}/status`, { status: nextStatus });
        const updated = res.data as AdminOrder;
        queryClient.setQueryData<AdminOrdersPageResponse>(
          ["admin-orders", page, customerEmailFilter],
          (prev) => prev ? ({
            ...prev,
            content: prev.content.map((order) => (order.id === orderId ? { ...order, ...updated } : order)),
          }) : prev!
        );
        setStatusDrafts((prev) => ({ ...prev, [orderId]: updated.status || nextStatus }));
        success += 1;
      } catch {
        failed += 1;
      }
    }
    setStatusMessage(
      [
        `Updated ${success} order${success === 1 ? "" : "s"} to ${nextStatus}.`,
        skippedInvalid > 0 ? `${skippedInvalid} skipped (invalid transitions).` : "",
        failed > 0 ? `${failed} failed.` : "",
      ].filter(Boolean).join(" ")
    );
    if (failed === 0) setSelectedOrderIds([]);
    setBulkSaving(false);
  };

  const isVendorScopedActor = session.isVendorAdmin || session.isVendorStaff;
  const currentPage = ordersPage?.number ?? ordersPage?.page?.number ?? page;
  const totalPages = ordersPage?.totalPages ?? ordersPage?.page?.totalPages ?? 0;
  const totalElements = ordersPage?.totalElements ?? ordersPage?.page?.totalElements ?? 0;
  const filterBusy = ordersLoading;
  const allCurrentSelected = orders.length > 0 && orders.every((o) => selectedOrderIds.includes(o.id));
  const someCurrentSelected = selectedOrderIds.length > 0 && !allCurrentSelected;
  const selectedOrders = useMemo(() => orders.filter((o) => selectedOrderIds.includes(o.id)), [orders, selectedOrderIds]);
  const bulkInvalidSelectionCount = selectedOrders.filter((o) => !canTransitionOrderStatus(o.status, bulkStatus)).length;

  return {
    ordersPage,
    orders,
    page,
    currentPage,
    totalPages,
    totalElements,
    customerEmailInput,
    customerEmailFilter,
    ordersLoading,
    filterSubmitting: ordersLoading,
    filterBusy,
    statusDrafts,
    statusSavingId,
    selectedOrderIds,
    bulkStatus,
    bulkSaving,
    isVendorScopedActor,
    allCurrentSelected,
    someCurrentSelected,
    bulkInvalidSelectionCount,
    setCustomerEmailInput,
    setStatusDrafts,
    setSelectedOrderIds,
    setBulkStatus,
    applyFilter,
    clearFilter,
    goToPage,
    updateOrderStatus,
    toggleOrderSelection,
    toggleSelectAllCurrentPage,
    applyBulkStatus,
    loadAdminOrders: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-orders"] });
    },
  };
}
