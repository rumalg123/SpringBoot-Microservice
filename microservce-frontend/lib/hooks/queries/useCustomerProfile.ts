import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { Customer, CommunicationPreferences, ActivityLogEntry, LinkedAccounts, CouponUsageEntry } from "../../types/customer";
import type { PagedResponse } from "../../types/pagination";
import { normalizePage } from "../../types/pagination";
import { queryKeys } from "./keys";

export function useCustomerProfile(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.customer.me(),
    queryFn: async () => {
      const res = await apiClient!.get<Customer>("/customers/me");
      return res.data;
    },
    enabled: !!apiClient,
  });
}

export function useUpdateProfile(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ firstName, lastName }: { firstName: string; lastName: string }) => {
      const res = await apiClient!.put<Customer>("/customers/me", { firstName, lastName });
      return res.data;
    },
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.customer.me(), data);
    },
  });
}

export function useCommunicationPrefs(apiClient: AxiosInstance | null, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.customer.commPrefs(),
    queryFn: async () => {
      const res = await apiClient!.get<CommunicationPreferences>("/customers/me/communication-preferences");
      return res.data;
    },
    enabled: !!apiClient && enabled,
  });
}

export function useUpdateCommPref(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ key, value }: { key: string; value: boolean }) => {
      const res = await apiClient!.put<CommunicationPreferences>("/customers/me/communication-preferences", {
        [key]: value,
      });
      return res.data;
    },
    onSuccess: (data) => {
      qc.setQueryData(queryKeys.customer.commPrefs(), data);
    },
  });
}

export function useLinkedAccounts(apiClient: AxiosInstance | null, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.customer.linkedAccounts(),
    queryFn: async () => {
      const res = await apiClient!.get<LinkedAccounts>("/customers/me/linked-accounts");
      return res.data;
    },
    enabled: !!apiClient && enabled,
  });
}

export function useActivityLog(apiClient: AxiosInstance | null, page: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.customer.activityLog(page),
    queryFn: async () => {
      const res = await apiClient!.get<PagedResponse<ActivityLogEntry>>(`/customers/me/activity-log?page=${page}&size=20`);
      return normalizePage(res.data);
    },
    enabled: !!apiClient && enabled,
  });
}

export function useCouponUsage(apiClient: AxiosInstance | null, page: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.customer.couponUsage(page),
    queryFn: async () => {
      const res = await apiClient!.get<PagedResponse<CouponUsageEntry>>(`/promotions/me/coupon-usage?page=${page}&size=20`);
      return normalizePage(res.data);
    },
    enabled: !!apiClient && enabled,
  });
}
