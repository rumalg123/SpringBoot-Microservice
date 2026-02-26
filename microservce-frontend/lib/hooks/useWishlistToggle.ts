"use client";

import { useCallback, useEffect, useState } from "react";
import type { AxiosInstance } from "axios";
import type { WishlistItem, WishlistResponse } from "../types/wishlist";
import { emitWishlistUpdate } from "../navEvents";
import toast from "react-hot-toast";

/**
 * Reusable hook that manages a wishlisted-product map.
 * Returns a Record<productId, wishlistItemId> so you can check
 * `isWishlisted(productId)` and call `toggle(event, productId)`.
 */
export function useWishlistToggle(apiClient: AxiosInstance | null | undefined, isAuthenticated: boolean) {
  const [wishlistByProductId, setWishlistByProductId] = useState<Record<string, string>>({});
  const [pendingProductId, setPendingProductId] = useState<string | null>(null);

  /* ── fetch current wishlist ── */
  useEffect(() => {
    if (!isAuthenticated || !apiClient) {
      setWishlistByProductId({});
      return;
    }
    let cancelled = false;
    const run = async () => {
      try {
        const res = await apiClient.get("/wishlist/me");
        const raw = res.data as Record<string, unknown>;
        const items = (Array.isArray(raw.content) ? raw.content : raw.items || []) as WishlistItem[];
        if (cancelled) return;
        const map: Record<string, string> = {};
        for (const item of items) {
          if (item.productId && item.id) map[item.productId] = item.id;
        }
        setWishlistByProductId(map);
      } catch {
        if (!cancelled) setWishlistByProductId({});
      }
    };
    void run();
    return () => { cancelled = true; };
  }, [isAuthenticated, apiClient]);

  /* ── apply a WishlistResponse payload to the local map ── */
  const applyResponse = useCallback((payload: WishlistResponse) => {
    const map: Record<string, string> = {};
    for (const item of payload.items || []) {
      if (item.productId && item.id) map[item.productId] = item.id;
    }
    setWishlistByProductId(map);
  }, []);

  /* ── toggle wishlist for a product (e.g. from a card button) ── */
  const toggle = useCallback(
    async (event: React.MouseEvent<HTMLButtonElement>, productId: string) => {
      event.preventDefault();
      event.stopPropagation();
      if (!apiClient || !isAuthenticated) return;
      if (pendingProductId === productId) return;

      setPendingProductId(productId);
      try {
        const existingItemId = wishlistByProductId[productId];
        if (existingItemId) {
          await apiClient.delete(`/wishlist/me/items/${existingItemId}`);
          setWishlistByProductId((old) => {
            const next = { ...old };
            delete next[productId];
            return next;
          });
          toast.success("Removed from wishlist");
        } else {
          const res = await apiClient.post("/wishlist/me/items", { productId });
          applyResponse((res.data as WishlistResponse) || { items: [], itemCount: 0 });
          toast.success("Added to wishlist");
        }
        emitWishlistUpdate();
      } catch (err) {
        toast.error(err instanceof Error ? err.message : "Wishlist update failed");
      } finally {
        setPendingProductId(null);
      }
    },
    [apiClient, isAuthenticated, pendingProductId, wishlistByProductId, applyResponse],
  );

  const isWishlisted = useCallback(
    (productId: string) => Boolean(wishlistByProductId[productId]),
    [wishlistByProductId],
  );

  const isBusy = useCallback(
    (productId: string) => pendingProductId === productId,
    [pendingProductId],
  );

  return { wishlistByProductId, toggle, isWishlisted, isBusy, applyResponse };
}
