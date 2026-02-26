export type WishlistItem = {
  id: string;
  collectionId: string | null;
  productId: string;
  productSlug: string;
  productName: string;
  productType: string;
  mainImage: string | null;
  sellingPriceSnapshot: number | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
};

export type WishlistResponse = {
  keycloakId: string;
  items: WishlistItem[];
  itemCount: number;
};

export type WishlistCollection = {
  id: string;
  name: string;
  description: string | null;
  isDefault: boolean;
  shared: boolean;
  shareToken: string | null;
  items: WishlistItem[];
  itemCount: number;
  createdAt: string;
  updatedAt: string;
};

export type SharedWishlistResponse = {
  collectionName: string;
  description: string | null;
  items: WishlistItem[];
  itemCount: number;
};

export type CreateWishlistCollectionRequest = {
  name: string;
  description?: string;
};

export type UpdateWishlistCollectionRequest = {
  name: string;
  description?: string;
};

export type UpdateItemNoteRequest = {
  note?: string;
};

export const emptyWishlist: WishlistResponse = { keycloakId: "", items: [], itemCount: 0 };
