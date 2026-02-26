import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { Order, OrderDetail } from "../../types/order";
import { queryKeys } from "./keys";

type PagedOrder = { content: Order[] };
type PaymentInfo = { status: string; paymentMethod: string; cardNoMasked: string | null; paidAt: string | null };

export function useOrders(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.orders.me(),
    queryFn: async () => {
      const res = await apiClient!.get<PagedOrder>("/orders/me");
      return res.data.content ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useOrderDetail(apiClient: AxiosInstance | null, orderId: string | null) {
  return useQuery({
    queryKey: queryKeys.orders.detail(orderId ?? ""),
    queryFn: async () => {
      const res = await apiClient!.get<OrderDetail>(`/orders/me/${orderId}`);
      return res.data;
    },
    enabled: !!apiClient && !!orderId,
  });
}

export function useOrderPayment(apiClient: AxiosInstance | null, orderId: string | null) {
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
    enabled: !!apiClient && !!orderId,
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
