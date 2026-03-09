import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import { queryKeys } from "./keys";

export type RefundRequest = {
  id: string;
  paymentId: string;
  orderId: string;
  vendorOrderId: string;
  vendorId: string;
  customerId: string;
  refundAmount: number;
  currency: string;
  customerReason: string | null;
  vendorResponseNote: string | null;
  adminNote: string | null;
  status: string;
  vendorResponseDeadline: string | null;
  payhereRefundRef: string | null;
  vendorRespondedAt: string | null;
  adminFinalizedAt: string | null;
  refundCompletedAt: string | null;
  createdAt: string;
};

type PagedRefundResponse = {
  content?: RefundRequest[];
};

function buildParams(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && `${value}`.trim() !== "") {
      search.set(key, `${value}`);
    }
  });
  return search.toString();
}

export function useCustomerRefunds(
  apiClient: AxiosInstance | null,
  params: { orderId?: string | null; vendorOrderId?: string | null; status?: string | null } = {}
) {
  const normalized = {
    orderId: params.orderId?.trim() || null,
    vendorOrderId: params.vendorOrderId?.trim() || null,
    status: params.status?.trim() || null,
  };

  return useQuery({
    queryKey: queryKeys.refunds.me(normalized),
    queryFn: async () => {
      const query = buildParams(normalized);
      const res = await apiClient!.get<PagedRefundResponse | RefundRequest[]>(`/payments/me/refunds${query ? `?${query}` : ""}`);
      const data = res.data;
      if (Array.isArray(data)) {
        return data;
      }
      return data.content ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useCreateRefundRequest(apiClient: AxiosInstance | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: {
      orderId: string;
      vendorOrderId: string;
      refundAmount: number;
      reason: string;
    }) => {
      const res = await apiClient!.post<RefundRequest>("/payments/me/refunds", payload);
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.refunds.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
    },
  });
}

export function useVendorRefunds(
  apiClient: AxiosInstance | null,
  params: { status?: string | null } = {}
) {
  const normalized = {
    status: params.status?.trim() || null,
  };

  return useQuery({
    queryKey: queryKeys.refunds.vendor(normalized),
    queryFn: async () => {
      const query = buildParams(normalized);
      const res = await apiClient!.get<PagedRefundResponse | RefundRequest[]>(`/payments/vendor/me/refunds${query ? `?${query}` : ""}`);
      const data = res.data;
      if (Array.isArray(data)) {
        return data;
      }
      return data.content ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useRespondToVendorRefund(apiClient: AxiosInstance | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: { refundId: string; approved: boolean; note?: string }) => {
      const res = await apiClient!.post<RefundRequest>(
        `/payments/vendor/me/refunds/${payload.refundId}/respond`,
        {
          approved: payload.approved,
          note: payload.note?.trim() || null,
        }
      );
      return res.data;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.refunds.all });
    },
  });
}
