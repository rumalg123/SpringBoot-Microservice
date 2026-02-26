"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { OrderStatusAudit } from "./types";

type ApiClientLike = {
  get: (url: string) => Promise<{ data: unknown }>;
} | null;

type Params = {
  apiClient: ApiClientLike;
  setStatusMessage: (message: string) => void;
};

export default function useOrderHistory({ apiClient, setStatusMessage }: Params) {
  const queryClient = useQueryClient();
  const [historyOrderId, setHistoryOrderId] = useState<string | null>(null);
  const [historyActorTypeFilter, setHistoryActorTypeFilter] = useState<string>("ALL");
  const [historySourceFilter, setHistorySourceFilter] = useState<string>("ALL");
  const [historyExpanded, setHistoryExpanded] = useState(false);

  const historyQuery = useQuery<OrderStatusAudit[]>({
    queryKey: ["admin-order-history", historyOrderId],
    queryFn: async () => {
      if (!apiClient || !historyOrderId) throw new Error("No API client or order ID");
      const res = await apiClient.get(`/admin/orders/${historyOrderId}/status-history`);
      return (res.data as OrderStatusAudit[]) || [];
    },
    enabled: Boolean(apiClient) && Boolean(historyOrderId),
  });

  const historyRows = historyQuery.data ?? [];
  const historyLoading = historyQuery.isLoading || historyQuery.isFetching;

  useEffect(() => {
    if (historyQuery.data && historyOrderId) {
      setStatusMessage(`Loaded status history for order ${historyOrderId}.`);
    }
  }, [historyQuery.data, historyOrderId, setStatusMessage]);

  useEffect(() => {
    if (historyQuery.error && historyOrderId) {
      setStatusMessage(historyQuery.error instanceof Error ? historyQuery.error.message : "Failed to load order status history.");
    }
  }, [historyQuery.error, historyOrderId, setStatusMessage]);

  const loadOrderHistory = (orderId: string) => {
    if (historyLoading) return;
    if (historyOrderId === orderId) {
      setHistoryOrderId(null);
      setHistoryExpanded(false);
      return;
    }
    setHistoryOrderId(orderId);
    setHistoryExpanded(false);
  };

  const refreshOpenOrderHistory = async () => {
    if (!apiClient || !historyOrderId || historyLoading) return;
    await queryClient.invalidateQueries({ queryKey: ["admin-order-history", historyOrderId] });
    setStatusMessage(`Refreshed status history for order ${historyOrderId}.`);
  };

  const resetOrderHistory = () => {
    setHistoryOrderId(null);
    setHistoryActorTypeFilter("ALL");
    setHistorySourceFilter("ALL");
    setHistoryExpanded(false);
  };

  const historyActorTypeOptions = useMemo(
    () => Array.from(new Set(historyRows.map((r) => r.actorType).filter(Boolean))).sort(),
    [historyRows]
  );
  const historySourceOptions = useMemo(
    () => Array.from(new Set(historyRows.map((r) => r.changeSource).filter(Boolean))).sort(),
    [historyRows]
  );

  return {
    historyOrderId,
    historyLoading,
    historyRows,
    historyActorTypeFilter,
    historySourceFilter,
    historyExpanded,
    historyActorTypeOptions,
    historySourceOptions,
    setHistoryOrderId,
    setHistoryRows: (_rows: OrderStatusAudit[]) => {
      // For compatibility with page close handler that sets rows to []
      // When historyOrderId is set to null, query will return empty anyway
    },
    setHistoryActorTypeFilter,
    setHistorySourceFilter,
    setHistoryExpanded,
    loadOrderHistory,
    refreshOpenOrderHistory,
    resetOrderHistory,
  };
}
