import { create } from "zustand";
import type { AxiosInstance } from "axios";
import type { CartResponse } from "../types/cart";
import { emptyCart } from "../types/cart";

type CartState = {
  cart: CartResponse;
  loading: boolean;
  /** ISO timestamp of last successful fetch */
  fetchedAt: string | null;

  /* ── actions ── */
  fetchCart: (api: AxiosInstance) => Promise<void>;
  addItem: (api: AxiosInstance, productId: string, quantity?: number) => Promise<void>;
  updateItem: (api: AxiosInstance, itemId: string, quantity: number) => Promise<void>;
  removeItem: (api: AxiosInstance, itemId: string) => Promise<void>;
  saveForLater: (api: AxiosInstance, itemId: string) => Promise<void>;
  moveToCart: (api: AxiosInstance, itemId: string) => Promise<void>;
  clearCart: (api: AxiosInstance) => Promise<void>;
  /** Reset to empty (e.g. on logout) */
  reset: () => void;
};

export const useCartStore = create<CartState>((set, get) => ({
  cart: emptyCart,
  loading: false,
  fetchedAt: null,

  fetchCart: async (api) => {
    set({ loading: true });
    try {
      const res = await api.get("/cart/me");
      const data = res.data as CartResponse;
      set({ cart: data, fetchedAt: new Date().toISOString() });
    } catch {
      // Keep previous state on fetch failure
    } finally {
      set({ loading: false });
    }
  },

  addItem: async (api, productId, quantity = 1) => {
    const res = await api.post("/cart/me/items", { productId, quantity });
    set({ cart: res.data as CartResponse, fetchedAt: new Date().toISOString() });
  },

  updateItem: async (api, itemId, quantity) => {
    const res = await api.put(`/cart/me/items/${itemId}`, { quantity });
    set({ cart: res.data as CartResponse, fetchedAt: new Date().toISOString() });
  },

  removeItem: async (api, itemId) => {
    await api.delete(`/cart/me/items/${itemId}`);
    // Optimistically remove the item from local state
    const current = get().cart;
    const updatedItems = current.items.filter((i) => i.id !== itemId);
    set({
      cart: {
        ...current,
        items: updatedItems,
        itemCount: updatedItems.length,
        totalQuantity: updatedItems.reduce((sum, i) => sum + i.quantity, 0),
        subtotal: updatedItems.reduce((sum, i) => sum + i.lineTotal, 0),
      },
      fetchedAt: new Date().toISOString(),
    });
  },

  saveForLater: async (api, itemId) => {
    const res = await api.post(`/cart/me/items/${itemId}/save-for-later`);
    set({ cart: res.data as CartResponse, fetchedAt: new Date().toISOString() });
  },

  moveToCart: async (api, itemId) => {
    const res = await api.post(`/cart/me/items/${itemId}/move-to-cart`);
    set({ cart: res.data as CartResponse, fetchedAt: new Date().toISOString() });
  },

  clearCart: async (api) => {
    await api.delete("/cart/me");
    set({ cart: emptyCart, fetchedAt: new Date().toISOString() });
  },

  reset: () => set({ cart: emptyCart, fetchedAt: null }),
}));
