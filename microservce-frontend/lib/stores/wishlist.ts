import { create } from "zustand";
import type { AxiosInstance } from "axios";
import type { WishlistResponse, WishlistItem } from "../types/wishlist";
import { emptyWishlist } from "../types/wishlist";

type WishlistState = {
  wishlist: WishlistResponse;
  loading: boolean;
  fetchedAt: string | null;

  /* ── actions ── */
  fetchWishlist: (api: AxiosInstance) => Promise<void>;
  addItem: (api: AxiosInstance, productId: string) => Promise<void>;
  removeItem: (api: AxiosInstance, itemId: string) => Promise<void>;
  removeByProductId: (api: AxiosInstance, productId: string) => Promise<void>;
  clearAll: (api: AxiosInstance) => Promise<void>;
  /** Check if a productId is wishlisted, returns the wishlist item id or null. */
  findByProductId: (productId: string) => string | null;
  reset: () => void;
};

export const useWishlistStore = create<WishlistState>((set, get) => ({
  wishlist: emptyWishlist,
  loading: false,
  fetchedAt: null,

  fetchWishlist: async (api) => {
    set({ loading: true });
    try {
      const res = await api.get("/wishlist/me");
      const data = res.data as WishlistResponse;
      set({ wishlist: data, fetchedAt: new Date().toISOString() });
    } catch {
      // Keep previous state
    } finally {
      set({ loading: false });
    }
  },

  addItem: async (api, productId) => {
    const res = await api.post("/wishlist/me/items", { productId });
    set({ wishlist: res.data as WishlistResponse, fetchedAt: new Date().toISOString() });
  },

  removeItem: async (api, itemId) => {
    await api.delete(`/wishlist/me/items/${itemId}`);
    const current = get().wishlist;
    const updatedItems = current.items.filter((i) => i.id !== itemId);
    set({
      wishlist: { ...current, items: updatedItems, itemCount: updatedItems.length },
      fetchedAt: new Date().toISOString(),
    });
  },

  removeByProductId: async (api, productId) => {
    await api.delete(`/wishlist/me/items/by-product/${productId}`);
    const current = get().wishlist;
    const updatedItems = current.items.filter((i) => i.productId !== productId);
    set({
      wishlist: { ...current, items: updatedItems, itemCount: updatedItems.length },
      fetchedAt: new Date().toISOString(),
    });
  },

  clearAll: async (api) => {
    await api.delete("/wishlist/me");
    set({ wishlist: emptyWishlist, fetchedAt: new Date().toISOString() });
  },

  findByProductId: (productId) => {
    const item = get().wishlist.items.find((i) => i.productId === productId);
    return item?.id ?? null;
  },

  reset: () => set({ wishlist: emptyWishlist, fetchedAt: null }),
}));
