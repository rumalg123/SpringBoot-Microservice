import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { AxiosInstance } from "axios";
import type { WishlistItem, WishlistResponse, WishlistCollection, WishlistApiRaw } from "../../types/wishlist";
import { emptyWishlist } from "../../types/wishlist";
import { queryKeys } from "./keys";

export function useWishlist(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.wishlist.me(),
    queryFn: async () => {
      const res = await apiClient!.get<WishlistApiRaw>("/wishlist/me");
      const raw = res.data;
      const items: WishlistItem[] = raw.content ?? raw.items ?? [];
      const itemCount = Number(raw.page?.totalElements ?? raw.itemCount ?? items.length);
      return { keycloakId: raw.keycloakId ?? "", items, itemCount } as WishlistResponse;
    },
    enabled: !!apiClient,
  });
}

export function useWishlistCollections(apiClient: AxiosInstance | null) {
  return useQuery({
    queryKey: queryKeys.wishlist.collections(),
    queryFn: async () => {
      const res = await apiClient!.get<WishlistCollection[]>("/wishlist/me/collections");
      return res.data ?? [];
    },
    enabled: !!apiClient,
  });
}

export function useRemoveWishlistItem(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (itemId: string) => {
      await apiClient!.delete(`/wishlist/me/items/${itemId}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.all });
    },
  });
}

export function useMoveWishlistItemToCart(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (item: WishlistItem) => {
      await apiClient!.post("/cart/me/items", {
        productId: item.productId,
        quantity: 1,
      });
      await apiClient!.delete(`/wishlist/me/items/${item.id}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.all });
      void qc.invalidateQueries({ queryKey: queryKeys.cart.all });
    },
  });
}

export function useClearWishlist(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await apiClient!.delete("/wishlist/me");
    },
    onSuccess: () => {
      qc.setQueryData(queryKeys.wishlist.me(), emptyWishlist);
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.collections() });
    },
  });
}

export function useCreateCollection(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (name: string) => {
      const res = await apiClient!.post<WishlistCollection>("/wishlist/me/collections", { name });
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.collections() });
    },
  });
}

export function useUpdateCollection(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ collectionId, body }: { collectionId: string; body: { name?: string; description?: string } }) => {
      const res = await apiClient!.put<WishlistCollection>(`/wishlist/me/collections/${collectionId}`, body);
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.collections() });
    },
  });
}

export function useDeleteCollection(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (collectionId: string) => {
      await apiClient!.delete(`/wishlist/me/collections/${collectionId}`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.all });
    },
  });
}

export function useToggleCollectionShare(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ collectionId, shared }: { collectionId: string; shared: boolean }) => {
      if (shared) {
        const res = await apiClient!.post<WishlistCollection>(`/wishlist/me/collections/${collectionId}/share`);
        return res.data;
      }
      const res = await apiClient!.delete<WishlistCollection>(`/wishlist/me/collections/${collectionId}/share`);
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.collections() });
    },
  });
}

export function useUpdateItemNote(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ itemId, note }: { itemId: string; note: string }) => {
      await apiClient!.put(`/wishlist/me/items/${itemId}/note`, { note: note.trim() || null });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.all });
    },
  });
}

export function useMoveItemToCollection(apiClient: AxiosInstance | null) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ itemId, collectionId }: { itemId: string; collectionId: string | null }) => {
      await apiClient!.put(`/wishlist/me/items/${itemId}/move`, { collectionId });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.wishlist.all });
    },
  });
}
