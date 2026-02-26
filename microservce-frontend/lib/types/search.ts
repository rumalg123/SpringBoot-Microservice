export type SearchHit = {
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
  mainCategory: string | null;
  subCategories: string[];
  brandName: string | null;
  vendorId: string | null;
  variations: { name: string; value: string }[];
  score: number;
};

export type FacetBucket = {
  key: string;
  docCount: number;
};

export type FacetGroup = {
  name: string;
  buckets: FacetBucket[];
};

export type SearchResponse = {
  content: SearchHit[];
  facets: FacetGroup[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  query: string | null;
  tookMs: number;
};

export type AutocompleteSuggestion = {
  text: string;
  type: string;
  id: string | null;
  slug: string | null;
  mainImage: string | null;
};

export type AutocompleteResponse = {
  suggestions: AutocompleteSuggestion[];
  popularSearches: string[];
};
