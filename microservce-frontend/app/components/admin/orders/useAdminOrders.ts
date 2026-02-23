"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuthSession } from "../../../../lib/authSession";
import useOrderHistory from "./useOrderHistory";
import useOrderList from "./useOrderList";
import useVendorOrders from "./useVendorOrders";

export default function useAdminOrders() {
  const router = useRouter();
  const session = useAuthSession();
  const [status, setStatus] = useState("Loading session...");

  const orderHistory = useOrderHistory({
    apiClient: session.apiClient,
    setStatusMessage: setStatus,
  });

  const vendorOrders = useVendorOrders({
    apiClient: session.apiClient,
    setStatusMessage: setStatus,
  });

  const orderList = useOrderList({
    session,
    router,
    setStatusMessage: setStatus,
    onResetPanelsForListReload: () => {
      orderHistory.resetOrderHistory();
      vendorOrders.resetVendorPanels();
    },
    onVendorScopedOrderStatusAttempt: vendorOrders.loadVendorOrders,
    onRefreshOrderHistoryIfOpen: async (orderId: string) => {
      if (orderHistory.historyOrderId !== orderId) return;
      await orderHistory.refreshOpenOrderHistory();
    },
  });

  return {
    session,
    status,
    ...orderList,
    ...orderHistory,
    ...vendorOrders,
  };
}

