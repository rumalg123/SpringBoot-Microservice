import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { Order, OrderDetail, VendorOrder } from "../../types/order";
import { queryKeys } from "./keys";

type PagedOrder = { content?: Order[] } | Order[];
type PaymentInfo = { status: string; paymentMethod: string; cardNoMasked: string | null; paidAt: string | null };

export function useOrders(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.orders.me(),
    queryFn: async () => {
      const res = await apiClient!.get<PagedOrder>("/orders/me");
      const data = res.data;
      if (Array.isArray(data)) return data;
      return data.content ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useOrderDetail(apiClient: AxiosInstance | null, orderId: string | null) {
  const normalizedOrderId = (orderId ?? "").trim();
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(normalizedOrderId);
  return useQuery({
    queryKey: queryKeys.orders.detail(normalizedOrderId),
    queryFn: async () => {
      const res = await apiClient!.get<OrderDetail>(`/orders/me/${normalizedOrderId}`);
      return res.data;
    },
    enabled: !!apiClient && isUuid,
  });
}

export function useOrderPayment(
  apiClient: AxiosInstance | null,
  orderId: string | null,
  shouldFetch = true
) {
  return useQuery({
    queryKey: queryKeys.orders.payment(orderId ?? ""),
    queryFn: async () => {
      try {
        const res = await apiClient!.get<PaymentInfo>(`/payments/me/order/${orderId}`);
        return res.data;
      } catch {
        return null;
      }
    },
    enabled: !!apiClient && !!orderId && shouldFetch,
  });
}

export function useOrderVendorOrders(apiClient: AxiosInstance | null, orderId: string | null) {
  const normalizedOrderId = (orderId ?? "").trim();
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(normalizedOrderId);
  return useQuery({
    queryKey: queryKeys.orders.vendorOrders(normalizedOrderId),
    queryFn: async () => {
      const res = await apiClient!.get<VendorOrder[]>(`/orders/me/${normalizedOrderId}/vendor-orders`);
      return res.data;
    },
    enabled: !!apiClient && isUuid,
  });
}

export function usePlaceOrder(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: {
      productId: string;
      quantity: number;
      shippingAddressId: string;
      billingAddressId: string;
    }) => {
      await apiClient!.post("/orders/me", body);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.orders.me() });
    },
  });
}

export function useCancelOrder(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ orderId, reason }: { orderId: string; reason?: string }) => {
      await apiClient!.post(`/orders/me/${orderId}/cancel`, { reason: reason?.trim() || undefined });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.orders.all });
    },
  });
}
