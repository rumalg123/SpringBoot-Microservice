export type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  sku: string;
  categories: string[];
  productType?: string;
};

export type Variation = { name: string; value: string };

export type ProductDetail = {
  id: string;
  slug: string;
  parentProductId: string | null;
  name: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  vendorId: string;
  mainCategory: string | null;
  mainCategorySlug: string | null;
  subCategories: string[];
  subCategorySlugs: string[];
  categories: string[];
  productType: string;
  variations: Variation[];
  sku: string;
};

export type VariationSummary = {
  id: string;
  name: string;
  sku: string;
  variations: Variation[];
};

export type SortKey = "newest" | "priceAsc" | "priceDesc" | "nameAsc";
