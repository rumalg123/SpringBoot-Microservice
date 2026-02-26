import { useQuery } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { SearchResponse, AutocompleteResponse } from "../../types/search";
import { queryKeys } from "./keys";

type ProductSearchParams = {
  q?: string;
  category?: string;
  mainCategory?: string;
  subCategory?: string;
  brand?: string;
  minPrice?: number;
  maxPrice?: number;
  vendorId?: string;
  sortBy?: string;
  page?: number;
  size?: number;
};

export function useProductSearch(apiClient: AxiosInstance | null, params: ProductSearchParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.search.products(params as Record<string, unknown>),
    queryFn: async () => {
      const searchParams = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null && value !== "") {
          searchParams.set(key, String(value));
        }
      }
      const res = await apiClient!.get<SearchResponse>(`/search/products?${searchParams.toString()}`);
      return res.data;
    },
    enabled: !!apiClient && enabled,
    staleTime: 30_000,
  });
}

export function useAutocomplete(apiClient: AxiosInstance | null, prefix: string) {
  return useQuery({
    queryKey: queryKeys.search.autocomplete(prefix),
    queryFn: async () => {
      const res = await apiClient!.get<AutocompleteResponse>(
        `/search/autocomplete?prefix=${encodeURIComponent(prefix)}&limit=8`
      );
      return res.data;
    },
    enabled: !!apiClient && prefix.length >= 2,
    staleTime: 5_000,
  });
}

export function usePopularSearches(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.search.popular(),
    queryFn: async () => {
      const res = await apiClient!.get<{ searches: string[] }>("/search/popular");
      return res.data.searches;
    },
    enabled: !!apiClient,
    staleTime: 60_000,
  });
}
