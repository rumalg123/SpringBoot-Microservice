import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import { API_BASE, PAGE_SIZE_SMALL as PAGE_SIZE, AGGREGATE_PAGE_SIZE, AGGREGATE_MAX_PAGES } from "../constants";
import type { ProductSummary, PagedResponse, SearchResponse } from "../types";
import type { Category } from "../types/category";
import type { SortKey } from "../types/product";

/* ── Pure helpers ── */

function parsePrice(value: string): number | null {
  const normalized = value.trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  return Number(parsed.toFixed(2));
}

function sortParams(sortBy: SortKey): { field: string; direction: "ASC" | "DESC" } {
  switch (sortBy) {
    case "priceAsc":
      return { field: "regularPrice", direction: "ASC" };
    case "priceDesc":
      return { field: "regularPrice", direction: "DESC" };
    case "nameAsc":
      return { field: "name", direction: "ASC" };
    case "newest":
    default:
      return { field: "createdAt", direction: "DESC" };
  }
}

function normalizeLower(values: string[]): string[] {
  return values.map((v) => v.trim().toLowerCase()).filter(Boolean);
}

function matchesCategorySelection(
  product: ProductSummary,
  selectedParentNames: string[],
  selectedSubNames: string[],
): boolean {
  const categories = new Set(normalizeLower(product.categories || []));
  const selectedParents = normalizeLower(selectedParentNames);
  const selectedSubs = normalizeLower(selectedSubNames);
  const parentMatch = selectedParents.length === 0 || selectedParents.some((n) => categories.has(n));
  const subMatch = selectedSubs.length === 0 || selectedSubs.some((n) => categories.has(n));
  return parentMatch && subMatch;
}

function sortProductsClient(products: ProductSummary[], sortBy: SortKey): ProductSummary[] {
  const copy = [...products];
  if (sortBy === "priceAsc") copy.sort((a, b) => a.sellingPrice - b.sellingPrice);
  else if (sortBy === "priceDesc") copy.sort((a, b) => b.sellingPrice - a.sellingPrice);
  else if (sortBy === "nameAsc") copy.sort((a, b) => a.name.localeCompare(b.name));
  return copy;
}

const SEARCH_SORT_MAP: Record<SortKey, string> = {
  newest: "newest",
  priceAsc: "price-low",
  priceDesc: "price-high",
  nameAsc: "relevance",
};

/* ── Types ── */

export type UseProductListingOptions = {
  /** Initial parent-category names to pre-select (e.g. from URL params or route). */
  initialParentNames?: string[];
  /** Initial sub-category names to pre-select. */
  initialSubNames?: string[];
  /** Initial text search query. */
  initialSearch?: string;
  /**
   * When true, the hook uses the `/search/products` endpoint for text queries.
   * When false (category page), text queries are passed as `?q=` to `/products`.
   * @default true
   */
  useSearchService?: boolean;
  /**
   * When true, the product-fetching effect is suspended.
   * Useful when the calling page needs to resolve a route before fetching.
   * @default false
   */
  suspended?: boolean;
  /**
   * Pre-loaded categories list. When provided the hook skips its own
   * `/categories` fetch and uses these directly.
   */
  preloadedCategories?: Category[];
};

export type UseProductListingReturn = {
  /* ── Data ── */
  products: ProductSummary[];
  allCategories: Category[];
  status: string;

  /* ── Loading ── */
  productsLoading: boolean;

  /* ── Pagination ── */
  page: number;
  totalPages: number;
  setPage: (page: number) => void;

  /* ── Sort ── */
  sortBy: SortKey;
  setSortBy: (key: SortKey) => void;

  /* ── Text search ── */
  query: string;
  search: string;
  setQuery: (value: string) => void;
  onSearch: (e: FormEvent) => void;
  clearSearch: () => void;

  /* ── Category filters ── */
  parents: Category[];
  subsByParent: Map<string, Category[]>;
  selectedParentNames: string[];
  selectedSubNames: string[];
  expandedParentIds: Record<string, boolean>;
  parentBySubName: Map<string, string>;
  toggleParentSelection: (parent: Category) => void;
  toggleSubSelection: (sub: Category) => void;
  toggleParentExpanded: (parentId: string) => void;
  setSelectedParentNames: React.Dispatch<React.SetStateAction<string[]>>;
  setSelectedSubNames: React.Dispatch<React.SetStateAction<string[]>>;

  /* ── Price filters ── */
  minPriceInput: string;
  maxPriceInput: string;
  appliedMinPrice: number | null;
  appliedMaxPrice: number | null;
  setMinPriceInput: (value: string) => void;
  setMaxPriceInput: (value: string) => void;
  applyPriceFilter: (e: FormEvent) => void;
  clearPriceFilter: () => void;

  /* ── Combined ── */
  activeFilterLabel: string;
  clearAllFilters: () => void;
};

/* ── Hook ── */

export function useProductListing(options: UseProductListingOptions = {}): UseProductListingReturn {
  const {
    initialParentNames = [],
    initialSubNames = [],
    initialSearch = "",
    useSearchService = true,
    suspended = false,
    preloadedCategories,
  } = options;

  /* ── Core state ── */
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [allCategories, setAllCategories] = useState<Category[]>(preloadedCategories ?? []);
  const [query, setQuery] = useState(initialSearch);
  const [search, setSearch] = useState(initialSearch);
  const [selectedParentNames, setSelectedParentNames] = useState<string[]>(initialParentNames);
  const [selectedSubNames, setSelectedSubNames] = useState<string[]>(initialSubNames);
  const [expandedParentIds, setExpandedParentIds] = useState<Record<string, boolean>>({});
  const [minPriceInput, setMinPriceInput] = useState("");
  const [maxPriceInput, setMaxPriceInput] = useState("");
  const [appliedMinPrice, setAppliedMinPrice] = useState<number | null>(null);
  const [appliedMaxPrice, setAppliedMaxPrice] = useState<number | null>(null);
  const [sortBy, setSortByRaw] = useState<SortKey>("newest");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [productsLoading, setProductsLoading] = useState(false);
  const [status, setStatus] = useState("Loading products...");

  /* ── Fetch categories (skipped when preloaded) ── */
  useEffect(() => {
    if (preloadedCategories) return;
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/categories`, { cache: "no-store" });
        if (!res.ok) return;
        const data = (await res.json()) as Category[];
        setAllCategories(data || []);
      } catch {
        setAllCategories([]);
      }
    };
    void run();
  }, [preloadedCategories]);

  /* ── Sync preloaded categories if they change ── */
  useEffect(() => {
    if (preloadedCategories) setAllCategories(preloadedCategories);
  }, [preloadedCategories]);

  /* ── Derived category data ── */
  const parents = useMemo(
    () =>
      allCategories
        .filter((c) => c.type === "PARENT")
        .sort((a, b) => a.name.localeCompare(b.name)),
    [allCategories],
  );

  const subsByParent = useMemo(() => {
    const map = new Map<string, Category[]>();
    for (const sub of allCategories.filter((c) => c.type === "SUB" && c.parentCategoryId)) {
      const key = sub.parentCategoryId as string;
      const existing = map.get(key) || [];
      existing.push(sub);
      map.set(key, existing);
    }
    for (const [, list] of map) list.sort((a, b) => a.name.localeCompare(b.name));
    return map;
  }, [allCategories]);

  const parentNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of parents) map.set(p.id, p.name);
    return map;
  }, [parents]);

  const parentBySubName = useMemo(() => {
    const map = new Map<string, string>();
    for (const sub of allCategories.filter((c) => c.type === "SUB" && c.parentCategoryId)) {
      const parentName = parentNameById.get(sub.parentCategoryId || "");
      if (parentName) map.set(sub.name.toLowerCase(), parentName);
    }
    return map;
  }, [allCategories, parentNameById]);

  /* ── Auto-expand selected parents in sidebar ── */
  useEffect(() => {
    if (parents.length === 0) return;
    setExpandedParentIds((prev) => {
      const next = { ...prev };
      for (const p of parents) {
        if (selectedParentNames.includes(p.name)) next[p.id] = true;
      }
      return next;
    });
  }, [parents, selectedParentNames]);

  /* ── Product fetching effect ── */
  useEffect(() => {
    if (suspended) return;

    const controller = new AbortController();
    const { signal } = controller;

    const run = async () => {
      setProductsLoading(true);
      try {
        const sort = sortParams(sortBy);

        // ──────────────────────────────────────────────
        // Strategy 1: Search service (only when enabled and a text query exists)
        // ──────────────────────────────────────────────
        if (useSearchService && search.trim()) {
          const params = new URLSearchParams();
          params.set("q", search.trim());
          params.set("page", String(page));
          params.set("size", String(PAGE_SIZE));
          params.set("sortBy", SEARCH_SORT_MAP[sortBy] || "relevance");
          if (appliedMinPrice !== null) params.set("minPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) params.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) params.set("subCategory", selectedSubNames[0]);

          const res = await fetch(`${API_BASE}/search/products?${params.toString()}`, {
            cache: "no-store",
            signal,
          });
          if (!res.ok) throw new Error("Failed to fetch search results");
          const data = (await res.json()) as SearchResponse;
          if (signal.aborted) return;

          const mapped: ProductSummary[] = (data.content || []).map((hit) => ({
            id: hit.id,
            slug: hit.slug,
            name: hit.name,
            shortDescription: hit.shortDescription,
            brandName: hit.brandName,
            mainImage: hit.mainImage,
            regularPrice: hit.regularPrice,
            discountedPrice: hit.discountedPrice,
            sellingPrice: hit.sellingPrice,
            sku: hit.sku,
            mainCategory: hit.mainCategory,
            subCategories: hit.subCategories,
            categories: hit.categories,
            vendorId: hit.vendorId ?? "",
            variations: hit.variations,
          } as ProductSummary));

          setProducts(mapped);
          setTotalPages(Math.max(data.totalPages || 1, 1));
          setStatus(
            `Showing ${mapped.length} of ${data.totalElements} results for "${search.trim()}"${data.tookMs ? ` (${data.tookMs}ms)` : ""}`,
          );
          return;
        }

        // ──────────────────────────────────────────────
        // Strategy 2: Direct product-service query (single category)
        // ──────────────────────────────────────────────
        const canUseDirectCategoryQuery =
          selectedParentNames.length <= 1 && selectedSubNames.length <= 1;

        if (canUseDirectCategoryQuery) {
          const params = new URLSearchParams();
          params.set("page", String(page));
          params.set("size", String(PAGE_SIZE));
          params.set("sort", `${sort.field},${sort.direction}`);
          if (!useSearchService && search.trim()) params.set("q", search.trim());
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());
          if (selectedParentNames.length === 1) params.set("mainCategory", selectedParentNames[0]);
          if (selectedSubNames.length === 1) params.set("subCategory", selectedSubNames[0]);

          const res = await fetch(`${API_BASE}/products?${params.toString()}`, {
            cache: "no-store",
            signal,
          });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as PagedResponse<ProductSummary>;
          if (signal.aborted) return;

          setProducts(data.content || []);
          setTotalPages(Math.max(data.totalPages ?? data.page?.totalPages ?? 1, 1));
          setStatus(`Showing ${data.content?.length || 0} products`);
          return;
        }

        // ──────────────────────────────────────────────
        // Strategy 3: Aggregate + client-side filter (multi-category)
        // ──────────────────────────────────────────────
        const aggregated: ProductSummary[] = [];
        let totalServerPages = 1;
        let currentPage = 0;

        while (currentPage < totalServerPages && currentPage < AGGREGATE_MAX_PAGES) {
          const params = new URLSearchParams();
          params.set("page", String(currentPage));
          params.set("size", String(AGGREGATE_PAGE_SIZE));
          params.set("sort", `${sort.field},${sort.direction}`);
          if (!useSearchService && search.trim()) params.set("q", search.trim());
          if (appliedMinPrice !== null) params.set("minSellingPrice", appliedMinPrice.toString());
          if (appliedMaxPrice !== null) params.set("maxSellingPrice", appliedMaxPrice.toString());

          const res = await fetch(`${API_BASE}/products?${params.toString()}`, {
            cache: "no-store",
            signal,
          });
          if (!res.ok) throw new Error("Failed to fetch products");
          const data = (await res.json()) as PagedResponse<ProductSummary>;
          if (signal.aborted) return;

          aggregated.push(...(data.content || []));
          totalServerPages = Math.max(data.totalPages ?? data.page?.totalPages ?? 1, 1);
          currentPage += 1;
        }

        // Deduplicate by product ID
        const dedupedMap = new Map<string, ProductSummary>();
        for (const p of aggregated) dedupedMap.set(p.id, p);
        const deduped = Array.from(dedupedMap.values());

        // Client-side category filter + sort
        const filtered = sortProductsClient(
          deduped.filter((p) => matchesCategorySelection(p, selectedParentNames, selectedSubNames)),
          sortBy,
        );

        // Client-side pagination
        const computedTotalPages = Math.max(Math.ceil(filtered.length / PAGE_SIZE), 1);
        const boundedPage = Math.min(page, computedTotalPages - 1);
        const start = boundedPage * PAGE_SIZE;
        const slice = filtered.slice(start, start + PAGE_SIZE);
        if (boundedPage !== page) setPage(boundedPage);

        setProducts(slice);
        setTotalPages(computedTotalPages);
        setStatus(`Showing ${slice.length} of ${filtered.length} products`);
      } catch (err) {
        if (signal.aborted) return;
        setProducts([]);
        setTotalPages(1);
        setStatus("Failed to load products.");
      } finally {
        if (!signal.aborted) setProductsLoading(false);
      }
    };

    void run();
    return () => controller.abort();
  }, [
    suspended,
    useSearchService,
    search,
    selectedParentNames,
    selectedSubNames,
    appliedMinPrice,
    appliedMaxPrice,
    sortBy,
    page,
  ]);

  /* ── Handlers ── */

  const setSortBy = useCallback(
    (key: SortKey) => {
      setSortByRaw(key);
      setPage(0);
    },
    [],
  );

  const onSearch = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      if (productsLoading) return;
      setPage(0);
      setSearch(query);
    },
    [query, productsLoading],
  );

  const clearSearch = useCallback(() => {
    setQuery("");
    setSearch("");
    setPage(0);
  }, []);

  const applyPriceFilter = useCallback(
    (e: FormEvent) => {
      e.preventDefault();
      const min = parsePrice(minPriceInput);
      const max = parsePrice(maxPriceInput);
      if (min !== null && max !== null && min > max) {
        toast.error("Min price must be lower than max price");
        return;
      }
      setAppliedMinPrice(min);
      setAppliedMaxPrice(max);
      setPage(0);
    },
    [minPriceInput, maxPriceInput],
  );

  const clearPriceFilter = useCallback(() => {
    setMinPriceInput("");
    setMaxPriceInput("");
    setAppliedMinPrice(null);
    setAppliedMaxPrice(null);
    setPage(0);
  }, []);

  const toggleParentSelection = useCallback(
    (parent: Category) => {
      setSelectedParentNames((prev) => {
        const exists = prev.includes(parent.name);
        if (exists) {
          const subs = (subsByParent.get(parent.id) || []).map((s) => s.name);
          setSelectedSubNames((old) => old.filter((s) => !subs.includes(s)));
          return prev.filter((n) => n !== parent.name);
        }
        return [...prev, parent.name];
      });
      setPage(0);
    },
    [subsByParent],
  );

  const toggleSubSelection = useCallback(
    (sub: Category) => {
      setSelectedSubNames((prev) => {
        const exists = prev.includes(sub.name);
        if (exists) return prev.filter((n) => n !== sub.name);
        return [...prev, sub.name];
      });
      const parentName = parentBySubName.get(sub.name.toLowerCase());
      if (parentName) {
        setSelectedParentNames((prev) =>
          prev.includes(parentName) ? prev : [...prev, parentName],
        );
      }
      setPage(0);
    },
    [parentBySubName],
  );

  const toggleParentExpanded = useCallback((parentId: string) => {
    setExpandedParentIds((prev) => ({ ...prev, [parentId]: !prev[parentId] }));
  }, []);

  const clearAllFilters = useCallback(() => {
    setQuery("");
    setSearch("");
    setSelectedParentNames([]);
    setSelectedSubNames([]);
    setMinPriceInput("");
    setMaxPriceInput("");
    setAppliedMinPrice(null);
    setAppliedMaxPrice(null);
    setSortByRaw("newest");
    setPage(0);
  }, []);

  /* ── Derived display ── */

  const activeFilterLabel = useMemo(() => {
    const parts: string[] = [];
    if (selectedParentNames.length > 0) parts.push(`Categories: ${selectedParentNames.join(", ")}`);
    if (selectedSubNames.length > 0) parts.push(`Subcategories: ${selectedSubNames.join(", ")}`);
    if (search.trim()) parts.push(`Search: "${search.trim()}"`);
    if (appliedMinPrice !== null || appliedMaxPrice !== null) {
      parts.push(`Price: ${appliedMinPrice ?? 0} - ${appliedMaxPrice ?? "Any"}`);
    }
    return parts.length === 0 ? "All Products" : parts.join(" | ");
  }, [selectedParentNames, selectedSubNames, search, appliedMinPrice, appliedMaxPrice]);

  return {
    products,
    allCategories,
    status,
    productsLoading,
    page,
    totalPages,
    setPage,
    sortBy,
    setSortBy,
    query,
    search,
    setQuery,
    onSearch,
    clearSearch,
    parents,
    subsByParent,
    selectedParentNames,
    selectedSubNames,
    expandedParentIds,
    parentBySubName,
    toggleParentSelection,
    toggleSubSelection,
    toggleParentExpanded,
    setSelectedParentNames,
    setSelectedSubNames,
    minPriceInput,
    maxPriceInput,
    appliedMinPrice,
    appliedMaxPrice,
    setMinPriceInput,
    setMaxPriceInput,
    applyPriceFilter,
    clearPriceFilter,
    activeFilterLabel,
    clearAllFilters,
  };
}
