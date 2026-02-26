export type PersonalizationProduct = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  brandName: string | null;
  mainImage: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  sku: string;
  mainCategory: string | null;
  subCategories: string[] | null;
  categories: string[] | null;
  productType: string | null;
  approvalStatus: string | null;
  vendorId: string | null;
  viewCount: number;
  soldCount: number;
  active: boolean;
  stockAvailable: number | null;
  stockStatus: string | null;
  backorderable: boolean | null;
};

export type TrackEventPayload = {
  eventType: "PRODUCT_VIEW" | "ADD_TO_CART" | "PURCHASE" | "WISHLIST_ADD" | "SEARCH";
  productId: string;
  categorySlugs?: string;
  vendorId?: string;
  brandName?: string;
  price?: number;
  metadata?: string;
};
