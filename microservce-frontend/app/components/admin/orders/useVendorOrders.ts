"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canTransitionOrderStatus } from "./orderStatus";
import type { VendorOrder, VendorOrderStatusAudit } from "./types";

type ApiClientLike = {
  get: (url: string) => Promise<{ data: unknown }>;
  patch: (url: string, body: unknown) => Promise<{ data: unknown }>;
} | null;

type Params = {
  apiClient: ApiClientLike;
  setStatusMessage: (message: string) => void;
};

export default function useVendorOrders({ apiClient, setStatusMessage }: Params) {
  const queryClient = useQueryClient();
  const [vendorOrdersOrderId, setVendorOrdersOrderId] = useState<string | null>(null);
  const [vendorOrderStatusDrafts, setVendorOrderStatusDrafts] = useState<Record<string, string>>({});
  const [vendorOrderStatusSavingId, setVendorOrderStatusSavingId] = useState<string | null>(null);
  const [vendorHistoryVendorOrderId, setVendorHistoryVendorOrderId] = useState<string | null>(null);
  const [vendorHistoryActorTypeFilter, setVendorHistoryActorTypeFilter] = useState<string>("ALL");
  const [vendorHistorySourceFilter, setVendorHistorySourceFilter] = useState<string>("ALL");
  const [vendorHistoryExpanded, setVendorHistoryExpanded] = useState(false);

  // Vendor orders query
  const vendorOrdersQuery = useQuery<VendorOrder[]>({
    queryKey: ["admin-vendor-orders", vendorOrdersOrderId],
    queryFn: async () => {
      if (!apiClient || !vendorOrdersOrderId) throw new Error("No API client or order ID");
      const res = await apiClient.get(`/admin/orders/${vendorOrdersOrderId}/vendor-orders`);
      return (res.data as VendorOrder[]) || [];
    },
    enabled: Boolean(apiClient) && Boolean(vendorOrdersOrderId),
  });

  const vendorOrdersRows = vendorOrdersQuery.data ?? [];
  const vendorOrdersLoading = vendorOrdersQuery.isLoading || vendorOrdersQuery.isFetching;

  // Sync statusDrafts when vendor orders data changes
  useEffect(() => {
    if (!vendorOrdersQuery.data || !vendorOrdersOrderId) return;
    const rows = vendorOrdersQuery.data;
    setVendorOrderStatusDrafts((prev) => {
      const next = { ...prev };
      for (const row of rows) {
        if (!next[row.id]) next[row.id] = row.status || "PENDING";
      }
      return next;
    });
    setStatusMessage(`Loaded ${rows.length} vendor order${rows.length === 1 ? "" : "s"} for order ${vendorOrdersOrderId}.`);
  }, [vendorOrdersQuery.data, vendorOrdersOrderId, setStatusMessage]);

  useEffect(() => {
    if (vendorOrdersQuery.error && vendorOrdersOrderId) {
      setStatusMessage(vendorOrdersQuery.error instanceof Error ? vendorOrdersQuery.error.message : "Failed to load vendor orders.");
    }
  }, [vendorOrdersQuery.error, vendorOrdersOrderId, setStatusMessage]);

  // Vendor order history query
  const vendorHistoryQuery = useQuery<VendorOrderStatusAudit[]>({
    queryKey: ["admin-vendor-order-history", vendorHistoryVendorOrderId],
    queryFn: async () => {
      if (!apiClient || !vendorHistoryVendorOrderId) throw new Error("No API client or vendor order ID");
      const res = await apiClient.get(`/admin/orders/vendor-orders/${vendorHistoryVendorOrderId}/status-history`);
      return (res.data as VendorOrderStatusAudit[]) || [];
    },
    enabled: Boolean(apiClient) && Boolean(vendorHistoryVendorOrderId),
  });

  const vendorHistoryRows = vendorHistoryQuery.data ?? [];
  const vendorHistoryLoading = vendorHistoryQuery.isLoading || vendorHistoryQuery.isFetching;

  useEffect(() => {
    if (vendorHistoryQuery.data && vendorHistoryVendorOrderId) {
      setStatusMessage(`Loaded status history for vendor order ${vendorHistoryVendorOrderId}.`);
    }
  }, [vendorHistoryQuery.data, vendorHistoryVendorOrderId, setStatusMessage]);

  useEffect(() => {
    if (vendorHistoryQuery.error && vendorHistoryVendorOrderId) {
      setStatusMessage(vendorHistoryQuery.error instanceof Error ? vendorHistoryQuery.error.message : "Failed to load vendor-order status history.");
    }
  }, [vendorHistoryQuery.error, vendorHistoryVendorOrderId, setStatusMessage]);

  const closeVendorOrdersPanel = () => {
    setVendorOrdersOrderId(null);
    setVendorOrderStatusDrafts({});
    setVendorHistoryVendorOrderId(null);
    setVendorHistoryExpanded(false);
  };

  const loadVendorOrders = async (orderId: string) => {
    if (vendorOrdersLoading) return;
    if (vendorOrdersOrderId === orderId) {
      closeVendorOrdersPanel();
      return;
    }
    setVendorOrderStatusDrafts({});
    setVendorHistoryVendorOrderId(null);
    setVendorHistoryActorTypeFilter("ALL");
    setVendorHistorySourceFilter("ALL");
    setVendorHistoryExpanded(false);
    setVendorOrdersOrderId(orderId);
  };

  const refreshOpenVendorOrders = async () => {
    if (!apiClient || !vendorOrdersOrderId || vendorOrdersLoading) return;
    await queryClient.invalidateQueries({ queryKey: ["admin-vendor-orders", vendorOrdersOrderId] });
    setStatusMessage(`Refreshed vendor orders for order ${vendorOrdersOrderId}.`);
  };

  const loadVendorOrderHistory = async (vendorOrderId: string) => {
    if (vendorHistoryLoading) return;
    if (vendorHistoryVendorOrderId === vendorOrderId) {
      setVendorHistoryVendorOrderId(null);
      setVendorHistoryExpanded(false);
      return;
    }
    setVendorHistoryVendorOrderId(vendorOrderId);
    setVendorHistoryExpanded(false);
    setVendorHistoryActorTypeFilter("ALL");
    setVendorHistorySourceFilter("ALL");
  };

  const refreshOpenVendorOrderHistory = async () => {
    if (!apiClient || !vendorHistoryVendorOrderId || vendorHistoryLoading) return;
    await queryClient.invalidateQueries({ queryKey: ["admin-vendor-order-history", vendorHistoryVendorOrderId] });
    setStatusMessage(`Refreshed status history for vendor order ${vendorHistoryVendorOrderId}.`);
  };

  const updateVendorOrderStatus = async (vendorOrderId: string, opts?: { blockIfBusy?: boolean }) => {
    if (!apiClient || vendorOrderStatusSavingId || opts?.blockIfBusy) return;
    const nextStatus = (vendorOrderStatusDrafts[vendorOrderId] || "").trim().toUpperCase();
    if (!nextStatus) return;
    const currentRow = vendorOrdersRows.find((row) => row.id === vendorOrderId);
    if (currentRow && !canTransitionOrderStatus(currentRow.status, nextStatus)) {
      setStatusMessage(`Invalid vendor-order status transition: ${currentRow.status} -> ${nextStatus}`);
      return;
    }
    setVendorOrderStatusSavingId(vendorOrderId);
    try {
      const res = await apiClient.patch(`/admin/orders/vendor-orders/${vendorOrderId}/status`, { status: nextStatus });
      const updated = res.data as VendorOrder;
      // Optimistically update the cache
      queryClient.setQueryData<VendorOrder[]>(
        ["admin-vendor-orders", vendorOrdersOrderId],
        (prev) => prev ? prev.map((row) => (row.id === vendorOrderId ? { ...row, ...updated } : row)) : prev
      );
      setVendorOrderStatusDrafts((prev) => ({ ...prev, [vendorOrderId]: updated.status || nextStatus }));
      setStatusMessage(`Vendor order ${vendorOrderId} updated to ${updated.status || nextStatus}.`);
      if (vendorHistoryVendorOrderId === vendorOrderId) {
        await queryClient.invalidateQueries({ queryKey: ["admin-vendor-order-history", vendorOrderId] });
      }
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to update vendor order status.");
    } finally {
      setVendorOrderStatusSavingId(null);
    }
  };

  const resetVendorPanels = () => {
    closeVendorOrdersPanel();
    setVendorHistoryActorTypeFilter("ALL");
    setVendorHistorySourceFilter("ALL");
  };

  const vendorHistoryActorTypeOptions = useMemo(
    () => Array.from(new Set(vendorHistoryRows.map((r) => r.actorType).filter(Boolean))).sort(),
    [vendorHistoryRows]
  );
  const vendorHistorySourceOptions = useMemo(
    () => Array.from(new Set(vendorHistoryRows.map((r) => r.changeSource).filter(Boolean))).sort(),
    [vendorHistoryRows]
  );

  return {
    vendorOrdersOrderId,
    vendorOrdersLoading,
    vendorOrdersRows,
    vendorOrderStatusDrafts,
    vendorOrderStatusSavingId,
    vendorHistoryVendorOrderId,
    vendorHistoryLoading,
    vendorHistoryRows,
    vendorHistoryActorTypeFilter,
    vendorHistorySourceFilter,
    vendorHistoryExpanded,
    vendorHistoryActorTypeOptions,
    vendorHistorySourceOptions,
    setVendorOrderStatusDrafts,
    setVendorHistoryVendorOrderId,
    setVendorHistoryRows: (_rows: VendorOrderStatusAudit[]) => {
      // For compatibility with page close handler
    },
    setVendorHistoryActorTypeFilter,
    setVendorHistorySourceFilter,
    setVendorHistoryExpanded,
    closeVendorOrdersPanel,
    loadVendorOrders,
    refreshOpenVendorOrders,
    loadVendorOrderHistory,
    refreshOpenVendorOrderHistory,
    updateVendorOrderStatus,
    resetVendorPanels,
  };
}
