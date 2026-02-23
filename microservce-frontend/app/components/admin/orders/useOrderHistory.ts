"use client";

import { useMemo, useState } from "react";
import type { OrderStatusAudit } from "./types";

type ApiClientLike = {
  get: (url: string) => Promise<{ data: unknown }>;
} | null;

type Params = {
  apiClient: ApiClientLike;
  setStatusMessage: (message: string) => void;
};

export default function useOrderHistory({ apiClient, setStatusMessage }: Params) {
  const [historyOrderId, setHistoryOrderId] = useState<string | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyRows, setHistoryRows] = useState<OrderStatusAudit[]>([]);
  const [historyActorTypeFilter, setHistoryActorTypeFilter] = useState<string>("ALL");
  const [historySourceFilter, setHistorySourceFilter] = useState<string>("ALL");
  const [historyExpanded, setHistoryExpanded] = useState(false);

  const loadOrderHistory = async (orderId: string) => {
    if (!apiClient || historyLoading) return;
    if (historyOrderId === orderId) {
      setHistoryOrderId(null);
      setHistoryRows([]);
      setHistoryExpanded(false);
      return;
    }
    setHistoryLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/${orderId}/status-history`);
      setHistoryRows((res.data as OrderStatusAudit[]) || []);
      setHistoryOrderId(orderId);
      setHistoryExpanded(false);
      setStatusMessage(`Loaded status history for order ${orderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to load order status history.");
    } finally {
      setHistoryLoading(false);
    }
  };

  const refreshOpenOrderHistory = async () => {
    if (!apiClient || !historyOrderId || historyLoading) return;
    setHistoryLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/${historyOrderId}/status-history`);
      setHistoryRows((res.data as OrderStatusAudit[]) || []);
      setStatusMessage(`Refreshed status history for order ${historyOrderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to refresh order status history.");
    } finally {
      setHistoryLoading(false);
    }
  };

  const resetOrderHistory = () => {
    setHistoryOrderId(null);
    setHistoryRows([]);
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
    setHistoryRows,
    setHistoryActorTypeFilter,
    setHistorySourceFilter,
    setHistoryExpanded,
    loadOrderHistory,
    refreshOpenOrderHistory,
    resetOrderHistory,
  };
}

