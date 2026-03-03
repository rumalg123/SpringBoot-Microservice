import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { CartResponse, CheckoutPreviewResponse, CheckoutResponse } from "../../types/cart";
import { emptyCart } from "../../types/cart";
import { queryKeys } from "./keys";
import type { ApiRequestConfig } from "../../apiClient";

function generateCheckoutIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `checkout-${crypto.randomUUID()}`;
  }
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function useCart(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.cart.me(),
    queryFn: async () => {
      const res = await apiClient!.get<CartResponse>("/cart/me");
      const data = res.data ?? emptyCart;
      return { ...emptyCart, ...data, items: data.items || [] } as CartResponse;
    },
    enabled: !!apiClient,
  });
}

export function useUpdateCartItem(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ itemId, quantity }: { itemId: string; quantity: number }) => {
      const res = await apiClient!.put<CartResponse>(`/cart/me/items/${itemId}`, { quantity });
      return res.data ?? emptyCart;
    },
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.cart.me(), data);
    },
  });
}

export function useRemoveCartItem(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: string) => {
      await apiClient!.delete(`/cart/me/items/${itemId}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.cart.me() });
    },
  });
}

export function useSaveForLater(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: string) => {
      const res = await apiClient!.post<CartResponse>(`/cart/me/items/${itemId}/save-for-later`);
      return res.data ?? emptyCart;
    },
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.cart.me(), data);
    },
  });
}

export function useMoveToCart(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: string) => {
      const res = await apiClient!.post<CartResponse>(`/cart/me/items/${itemId}/move-to-cart`);
      return res.data ?? emptyCart;
    },
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.cart.me(), data);
    },
  });
}

export function useClearCart(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await apiClient!.delete("/cart/me");
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.cart.me() });
    },
  });
}

export function useCheckoutPreview(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ couponCode, shippingAmount }: { couponCode: string | null; shippingAmount: number }) => {
      const res = await apiClient!.post<CheckoutPreviewResponse>("/cart/me/checkout/preview", {
        couponCode,
        shippingAmount,
      });
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.cart.me() });
    },
  });
}

export function useCheckout(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: {
      shippingAddressId: string;
      billingAddressId: string;
      couponCode: string | null;
      shippingAmount: number;
    }) => {
      const requestConfig: ApiRequestConfig = {
        // Use an explicit per-click key for checkout to avoid stale fingerprint-cache reuse.
        idempotencyKey: generateCheckoutIdempotencyKey(),
      };
      const res = await apiClient!.post<CheckoutResponse>("/cart/me/checkout", body, requestConfig);
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.cart.me() });
      void qc.invalidateQueries({ queryKey: queryKeys.orders.all });
    },
  });
}
