"use client";

import { useMemo, useState } from "react";
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
  const [vendorOrdersOrderId, setVendorOrdersOrderId] = useState<string | null>(null);
  const [vendorOrdersLoading, setVendorOrdersLoading] = useState(false);
  const [vendorOrdersRows, setVendorOrdersRows] = useState<VendorOrder[]>([]);
  const [vendorOrderStatusDrafts, setVendorOrderStatusDrafts] = useState<Record<string, string>>({});
  const [vendorOrderStatusSavingId, setVendorOrderStatusSavingId] = useState<string | null>(null);
  const [vendorHistoryVendorOrderId, setVendorHistoryVendorOrderId] = useState<string | null>(null);
  const [vendorHistoryLoading, setVendorHistoryLoading] = useState(false);
  const [vendorHistoryRows, setVendorHistoryRows] = useState<VendorOrderStatusAudit[]>([]);
  const [vendorHistoryActorTypeFilter, setVendorHistoryActorTypeFilter] = useState<string>("ALL");
  const [vendorHistorySourceFilter, setVendorHistorySourceFilter] = useState<string>("ALL");
  const [vendorHistoryExpanded, setVendorHistoryExpanded] = useState(false);

  const closeVendorOrdersPanel = () => {
    setVendorOrdersOrderId(null);
    setVendorOrdersRows([]);
    setVendorOrderStatusDrafts({});
    setVendorHistoryVendorOrderId(null);
    setVendorHistoryRows([]);
    setVendorHistoryExpanded(false);
  };

  const loadVendorOrders = async (orderId: string) => {
    if (!apiClient || vendorOrdersLoading) return;
    if (vendorOrdersOrderId === orderId) {
      closeVendorOrdersPanel();
      return;
    }
    setVendorOrdersLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/${orderId}/vendor-orders`);
      const rows = (res.data as VendorOrder[]) || [];
      setVendorOrdersRows(rows);
      setVendorOrdersOrderId(orderId);
      setVendorOrderStatusDrafts((prev) => {
        const next = { ...prev };
        for (const row of rows) {
          if (!next[row.id]) next[row.id] = row.status || "PENDING";
        }
        return next;
      });
      setVendorHistoryVendorOrderId(null);
      setVendorHistoryRows([]);
      setVendorHistoryActorTypeFilter("ALL");
      setVendorHistorySourceFilter("ALL");
      setVendorHistoryExpanded(false);
      setStatusMessage(`Loaded ${rows.length} vendor order${rows.length === 1 ? "" : "s"} for order ${orderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to load vendor orders.");
    } finally {
      setVendorOrdersLoading(false);
    }
  };

  const refreshOpenVendorOrders = async () => {
    if (!apiClient || !vendorOrdersOrderId || vendorOrdersLoading) return;
    setVendorOrdersLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/${vendorOrdersOrderId}/vendor-orders`);
      const rows = (res.data as VendorOrder[]) || [];
      setVendorOrdersRows(rows);
      setVendorOrderStatusDrafts((prev) => {
        const next = { ...prev };
        for (const row of rows) next[row.id] = row.status || next[row.id] || "PENDING";
        return next;
      });
      setStatusMessage(`Refreshed ${rows.length} vendor order${rows.length === 1 ? "" : "s"} for order ${vendorOrdersOrderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to refresh vendor orders.");
    } finally {
      setVendorOrdersLoading(false);
    }
  };

  const loadVendorOrderHistory = async (vendorOrderId: string) => {
    if (!apiClient || vendorHistoryLoading) return;
    if (vendorHistoryVendorOrderId === vendorOrderId) {
      setVendorHistoryVendorOrderId(null);
      setVendorHistoryRows([]);
      setVendorHistoryExpanded(false);
      return;
    }
    setVendorHistoryLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/vendor-orders/${vendorOrderId}/status-history`);
      setVendorHistoryRows((res.data as VendorOrderStatusAudit[]) || []);
      setVendorHistoryVendorOrderId(vendorOrderId);
      setVendorHistoryExpanded(false);
      setVendorHistoryActorTypeFilter("ALL");
      setVendorHistorySourceFilter("ALL");
      setStatusMessage(`Loaded status history for vendor order ${vendorOrderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to load vendor-order status history.");
    } finally {
      setVendorHistoryLoading(false);
    }
  };

  const refreshOpenVendorOrderHistory = async () => {
    if (!apiClient || !vendorHistoryVendorOrderId || vendorHistoryLoading) return;
    setVendorHistoryLoading(true);
    try {
      const res = await apiClient.get(`/admin/orders/vendor-orders/${vendorHistoryVendorOrderId}/status-history`);
      setVendorHistoryRows((res.data as VendorOrderStatusAudit[]) || []);
      setStatusMessage(`Refreshed status history for vendor order ${vendorHistoryVendorOrderId}.`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : "Failed to refresh vendor-order status history.");
    } finally {
      setVendorHistoryLoading(false);
    }
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
      setVendorOrdersRows((prev) => prev.map((row) => (row.id === vendorOrderId ? { ...row, ...updated } : row)));
      setVendorOrderStatusDrafts((prev) => ({ ...prev, [vendorOrderId]: updated.status || nextStatus }));
      setStatusMessage(`Vendor order ${vendorOrderId} updated to ${updated.status || nextStatus}.`);
      if (vendorHistoryVendorOrderId === vendorOrderId) {
        try {
          const historyRes = await apiClient.get(`/admin/orders/vendor-orders/${vendorOrderId}/status-history`);
          setVendorHistoryRows((historyRes.data as VendorOrderStatusAudit[]) || []);
        } catch {
          // Keep status update success if history refresh fails.
        }
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
    setVendorHistoryRows,
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

