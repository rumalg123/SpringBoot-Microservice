export type ProductType = "SINGLE" | "PARENT" | "VARIATION" | "DIGITAL";

export type ApprovalStatus = "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

export type Variation = { name: string; value: string };

export type ProductSpecification = {
  key: string;
  value: string;
  displayOrder: number;
};

export type ProductSummary = {
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
  subCategories: string[];
  categories: string[];
  productType: ProductType;
  approvalStatus: ApprovalStatus;
  vendorId: string;
  viewCount: number;
  soldCount: number;
  active: boolean;
  variations: Variation[];
  createdAt: string;
  updatedAt: string;
  stockAvailable: number | null;
  stockStatus: string | null;
  backorderable: boolean | null;
};

export type ProductDetail = {
  id: string;
  slug: string;
  parentProductId: string | null;
  name: string;
  shortDescription: string;
  description: string;
  brandName: string | null;
  images: string[];
  thumbnailUrl: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  vendorId: string;
  mainCategory: string | null;
  mainCategorySlug: string | null;
  subCategories: string[];
  subCategorySlugs: string[];
  categories: string[];
  categoryIds: string[];
  productType: ProductType;
  digital: boolean;
  variations: Variation[];
  sku: string;
  weightGrams: number | null;
  lengthCm: number | null;
  widthCm: number | null;
  heightCm: number | null;
  metaTitle: string | null;
  metaDescription: string | null;
  approvalStatus: ApprovalStatus;
  rejectionReason: string | null;
  specifications: ProductSpecification[];
  bundledProductIds: string[];
  viewCount: number;
  soldCount: number;
  active: boolean;
  deleted: boolean;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
  stockAvailable: number | null;
  stockStatus: string | null;
  backorderable: boolean | null;
};

export type VariationSummary = {
  id: string;
  name: string;
  sku: string;
  variations: Variation[];
};

export type SortKey = "newest" | "priceAsc" | "priceDesc" | "nameAsc";
