/**
 * Re-export all types from lib/types/ for backwards compatibility.
 * New code should import directly from "@/lib/types/..." or "@/lib/types".
 */
export type { ProductSummary, ProductDetail, ProductType, ApprovalStatus, ProductSpecification, Variation, VariationSummary, SortKey } from "./types/product";
export type { SearchHit, FacetBucket, FacetGroup, SearchResponse, AutocompleteSuggestion, AutocompleteResponse } from "./types/search";
export type { PagedResponse } from "./types/pagination";
export { normalizePage } from "./types/pagination";
