import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { CustomerAddress, AddressForm } from "../../types/customer";
import { queryKeys } from "./keys";

export function useAddresses(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.addresses.me(),
    queryFn: async () => {
      const res = await apiClient!.get<CustomerAddress[]>("/customers/me/addresses");
      return res.data ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useSaveAddress(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (form: AddressForm) => {
      const body = {
        label: form.label.trim() || null,
        recipientName: form.recipientName.trim(),
        phone: form.phone.trim(),
        line1: form.line1.trim(),
        line2: form.line2.trim() || null,
        city: form.city.trim(),
        state: form.state.trim(),
        postalCode: form.postalCode.trim(),
        countryCode: form.countryCode.trim() || "US",
        defaultShipping: form.defaultShipping,
        defaultBilling: form.defaultBilling,
      };
      if (form.id) {
        const res = await apiClient!.put<CustomerAddress>(`/customers/me/addresses/${form.id}`, body);
        return res.data;
      }
      const res = await apiClient!.post<CustomerAddress>("/customers/me/addresses", body);
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.addresses.me() });
    },
  });
}

export function useDeleteAddress(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (addressId: string) => {
      await apiClient!.delete(`/customers/me/addresses/${addressId}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.addresses.me() });
    },
  });
}

export function useSetDefaultAddress(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ addressId, type }: { addressId: string; type: "shipping" | "billing" }) => {
      const suffix = type === "shipping" ? "default-shipping" : "default-billing";
      await apiClient!.post(`/customers/me/addresses/${addressId}/${suffix}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.addresses.me() });
    },
  });
}
