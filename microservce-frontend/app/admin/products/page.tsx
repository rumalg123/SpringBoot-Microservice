"use client";

import { ChangeEvent, DragEvent, FormEvent, KeyboardEvent, WheelEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import Pagination from "../../components/Pagination";
import ConfirmModal from "../../components/ConfirmModal";
import { useAuthSession } from "../../../lib/authSession";

type ProductType = "SINGLE" | "PARENT" | "VARIATION";

type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  sellingPrice: number;
  sku: string;
  productType: ProductType;
  vendorId: string;
  categories: string[];
  active: boolean;
  variations?: Array<{ name: string; value: string }>;
};

type ProductDetail = {
  id: string;
  parentProductId: string | null;
  name: string;
  slug: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: number;
  discountedPrice: number | null;
  sku: string;
  vendorId: string;
  mainCategory: string | null;
  subCategories: string[];
  categories: string[];
  productType: ProductType;
  variations: Array<{ name: string; value: string }>;
  active: boolean;
};

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type PagedResponse<T> = {
  content: T[];
  number?: number;
  totalPages?: number;
  totalElements?: number;
  first?: boolean;
  last?: boolean;
  page?: {
    number?: number;
    totalPages?: number;
    totalElements?: number;
  };
};

type ProductFormState = {
  id?: string;
  name: string;
  slug: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: string;
  discountedPrice: string;
  vendorId: string;
  mainCategoryName: string;
  subCategoryNames: string[];
  productType: ProductType;
  sku: string;
  active: boolean;
};

type VariationDraft = {
  id: string;
  parentId: string;
  parentLabel: string;
  mainCategoryName: string;
  subCategoryNames: string[];
  payload: {
    name: string;
    slug: string;
    shortDescription: string;
    description: string;
    images: string[];
    regularPrice: number;
    discountedPrice: number | null;
    vendorId: string | null;
    categories: string[];
    productType: "VARIATION";
    variations: Array<{ name: string; value: string }>;
    sku: string;
    active: boolean;
  };
};

const emptyForm: ProductFormState = {
  name: "",
  slug: "",
  shortDescription: "",
  description: "",
  images: [],
  regularPrice: "",
  discountedPrice: "",
  vendorId: "",
  mainCategoryName: "",
  subCategoryNames: [],
  productType: "SINGLE",
  sku: "",
  active: true,
};

const MAX_IMAGE_COUNT = 5;
const MAX_IMAGE_SIZE_BYTES = 1_048_576;
const MAX_IMAGE_DIMENSION = 1200;
type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

function normalizeVariationAttributeName(value: string): string {
  return value.trim().toLowerCase();
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

function dedupeVariationAttributeNames(values: string[]): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const raw of values) {
    const normalized = normalizeVariationAttributeName(raw);
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    out.push(normalized);
  }
  return out;
}

function resolveImageUrl(imageName: string): string | null {
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (!base) {
    return apiUrl;
  }
  if (normalized.startsWith("products/")) {
    return `${base.replace(/\/+$/, "")}/${normalized}`;
  }
  return apiUrl;
}

async function validateImageFile(file: File): Promise<void> {
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    throw new Error(`${file.name} exceeds 1MB`);
  }
  const dimensions = await new Promise<{ width: number; height: number }>((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new window.Image();
    image.onload = () => {
      resolve({ width: image.width, height: image.height });
      URL.revokeObjectURL(url);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error(`${file.name} is not a valid image`));
    };
    image.src = url;
  });
  if (dimensions.width > MAX_IMAGE_DIMENSION || dimensions.height > MAX_IMAGE_DIMENSION) {
    throw new Error(`${file.name} must be at most ${MAX_IMAGE_DIMENSION}x${MAX_IMAGE_DIMENSION}`);
  }
}

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function parseNumber(value: string): number | null {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function preventNumberInputScroll(e: WheelEvent<HTMLInputElement>) {
  e.currentTarget.blur();
}

function preventNumberInputArrows(e: KeyboardEvent<HTMLInputElement>) {
  if (e.key === "ArrowUp" || e.key === "ArrowDown") {
    e.preventDefault();
  }
}

function getPageMeta<T>(pageInfo: PagedResponse<T> | null) {
  if (!pageInfo) {
    return { totalElements: 0, totalPages: 0, first: true, last: true };
  }
  const pageNumber = pageInfo.number ?? pageInfo.page?.number ?? 0;
  const totalPages = pageInfo.totalPages ?? pageInfo.page?.totalPages ?? 1;
  const totalElements = pageInfo.totalElements ?? pageInfo.page?.totalElements ?? pageInfo.content.length;
  const first = pageInfo.first ?? pageNumber <= 0;
  const last = pageInfo.last ?? pageNumber >= Math.max(totalPages - 1, 0);
  return { totalElements, totalPages, first, last };
}

export default function AdminProductsPage() {
  const router = useRouter();
  const session = useAuthSession();
  const [activePage, setActivePage] = useState<PagedResponse<ProductSummary> | null>(null);
  const [deletedPage, setDeletedPage] = useState<PagedResponse<ProductSummary> | null>(null);
  const [page, setPage] = useState(0);
  const [deletedPageIndex, setDeletedPageIndex] = useState(0);
  const [q, setQ] = useState("");
  const [sku, setSku] = useState("");
  const [category, setCategory] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [deletedCategories, setDeletedCategories] = useState<Category[]>([]);
  const [type, setType] = useState<ProductType | "">("");
  const [status, setStatus] = useState("Loading admin products...");
  const [showDeleted, setShowDeleted] = useState(false);
  const [form, setForm] = useState<ProductFormState>(emptyForm);
  const [parentAttributeNames, setParentAttributeNames] = useState<string[]>([]);
  const [newParentAttributeName, setNewParentAttributeName] = useState("");
  const [uploadingImages, setUploadingImages] = useState(false);
  const [dragImageIndex, setDragImageIndex] = useState<number | null>(null);
  const [parentProducts, setParentProducts] = useState<ProductSummary[]>([]);
  const [loadingParentProducts, setLoadingParentProducts] = useState(false);
  const [variationParentId, setVariationParentId] = useState("");
  const [selectedVariationParent, setSelectedVariationParent] = useState<ProductSummary | null>(null);
  const [parentSearch, setParentSearch] = useState("");
  const [parentVariationAttributes, setParentVariationAttributes] = useState<string[]>([]);
  const [variationAttributeValues, setVariationAttributeValues] = useState<Record<string, string>>({});
  const [variationDrafts, setVariationDrafts] = useState<VariationDraft[]>([]);
  const [categoryForm, setCategoryForm] = useState<{ id?: string; name: string; slug: string; type: "PARENT" | "SUB"; parentCategoryId: string }>({
    name: "",
    slug: "",
    type: "PARENT",
    parentCategoryId: "",
  });
  const [productSlugTouched, setProductSlugTouched] = useState(false);
  const [categorySlugTouched, setCategorySlugTouched] = useState(false);
  const [productSlugStatus, setProductSlugStatus] = useState<SlugStatus>("idle");
  const [categorySlugStatus, setCategorySlugStatus] = useState<SlugStatus>("idle");
  const [confirmAction, setConfirmAction] = useState<{ type: "product" | "category"; id: string; name: string } | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [filtersSubmitting, setFiltersSubmitting] = useState(false);
  const [loadingProductId, setLoadingProductId] = useState<string | null>(null);
  const [savingProduct, setSavingProduct] = useState(false);
  const [creatingQueuedVariationBatch, setCreatingQueuedVariationBatch] = useState(false);
  const [selectingVariationParentId, setSelectingVariationParentId] = useState<string | null>(null);
  const [restoringProductId, setRestoringProductId] = useState<string | null>(null);
  const [savingCategory, setSavingCategory] = useState(false);
  const [restoringCategoryId, setRestoringCategoryId] = useState<string | null>(null);
  const [loadingActiveList, setLoadingActiveList] = useState(false);
  const [loadingDeletedList, setLoadingDeletedList] = useState(false);

  const loadCategories = useCallback(async () => {
    if (!session.apiClient) return;
    const res = await session.apiClient.get("/admin/categories");
    setCategories((res.data as Category[]) || []);
  }, [session.apiClient]);

  const loadDeletedCategories = useCallback(async () => {
    if (!session.apiClient) return;
    const res = await session.apiClient.get("/admin/categories/deleted");
    setDeletedCategories((res.data as Category[]) || []);
  }, [session.apiClient]);

  const loadActive = useCallback(
    async (targetPage: number) => {
      if (!session.apiClient) return;
      setLoadingActiveList(true);
      try {
        const params = new URLSearchParams();
        params.set("page", String(targetPage));
        params.set("size", "12");
        params.set("sort", "createdAt,DESC");
        params.set("includeOrphanParents", "true");
        if (q.trim()) params.set("q", q.trim());
        if (sku.trim()) params.set("sku", sku.trim());
        if (category.trim()) params.set("category", category.trim());
        if (type) params.set("type", type);

        const res = await session.apiClient.get(`/products?${params.toString()}`);
        setActivePage(res.data as PagedResponse<ProductSummary>);
        setPage(targetPage);
      } finally {
        setLoadingActiveList(false);
      }
    },
    [session.apiClient, q, sku, category, type]
  );

  const loadDeleted = useCallback(
    async (targetPage: number) => {
      if (!session.apiClient) return;
      setLoadingDeletedList(true);
      try {
        const params = new URLSearchParams();
        params.set("page", String(targetPage));
        params.set("size", "12");
        params.set("sort", "updatedAt,DESC");
        if (q.trim()) params.set("q", q.trim());
        if (sku.trim()) params.set("sku", sku.trim());
        if (category.trim()) params.set("category", category.trim());
        if (type) params.set("type", type);

        const res = await session.apiClient.get(`/admin/products/deleted?${params.toString()}`);
        setDeletedPage(res.data as PagedResponse<ProductSummary>);
        setDeletedPageIndex(targetPage);
      } finally {
        setLoadingDeletedList(false);
      }
    },
    [session.apiClient, q, sku, category, type]
  );

  const loadParentProducts = useCallback(async () => {
    if (!session.apiClient) return;
    setLoadingParentProducts(true);
    try {
      const params = new URLSearchParams();
      params.set("page", "0");
      params.set("size", "1000");
      params.set("sort", "name,ASC");
      params.set("type", "PARENT");
      params.set("includeOrphanParents", "true");
      const res = await session.apiClient.get(`/products?${params.toString()}`);
      const pageData = res.data as PagedResponse<ProductSummary>;
      setParentProducts((pageData.content || []).filter((p) => p.active));
    } finally {
      setLoadingParentProducts(false);
    }
  }, [session.apiClient]);

  const reloadCurrentView = useCallback(async () => {
    if (showDeleted) {
      await loadDeleted(deletedPageIndex);
    } else {
      await loadActive(page);
    }
  }, [showDeleted, loadDeleted, deletedPageIndex, loadActive, page]);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.canViewAdmin) {
      router.replace("/products");
      return;
    }

    const run = async () => {
      try {
        await Promise.all([loadActive(0), loadDeleted(0), loadCategories(), loadDeletedCategories(), loadParentProducts()]);
        setStatus("Admin product catalog loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load products.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.canViewAdmin, loadActive, loadDeleted, loadCategories, loadDeletedCategories, loadParentProducts]);

  useEffect(() => {
    if (productSlugTouched) return;
    const generated = slugify(form.name).slice(0, 180);
    setForm((old) => (old.slug === generated ? old : { ...old, slug: generated }));
  }, [form.name, productSlugTouched]);

  useEffect(() => {
    if (categorySlugTouched) return;
    const generated = slugify(categoryForm.name).slice(0, 130);
    setCategoryForm((old) => (old.slug === generated ? old : { ...old, slug: generated }));
  }, [categoryForm.name, categorySlugTouched]);

  useEffect(() => {
    const apiClient = session.apiClient;
    if (!apiClient) return;
    const normalized = slugify(form.slug).slice(0, 180);
    if (!normalized) {
      setProductSlugStatus("invalid");
      return;
    }
    setProductSlugStatus("checking");
    const timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams();
        params.set("slug", normalized);
        if (form.id) params.set("excludeId", form.id);
        const res = await apiClient.get(`/products/slug-available?${params.toString()}`);
        const available = Boolean((res.data as { available?: boolean })?.available);
        setProductSlugStatus(available ? "available" : "taken");
      } catch {
        setProductSlugStatus("idle");
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [session.apiClient, form.slug, form.id]);

  useEffect(() => {
    const apiClient = session.apiClient;
    if (!apiClient) return;
    const normalized = slugify(categoryForm.slug).slice(0, 130);
    if (!normalized) {
      setCategorySlugStatus("invalid");
      return;
    }
    setCategorySlugStatus("checking");
    const timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams();
        params.set("slug", normalized);
        if (categoryForm.id) params.set("excludeId", categoryForm.id);
        const res = await apiClient.get(`/categories/slug-available?${params.toString()}`);
        const available = Boolean((res.data as { available?: boolean })?.available);
        setCategorySlugStatus(available ? "available" : "taken");
      } catch {
        setCategorySlugStatus("idle");
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [session.apiClient, categoryForm.slug, categoryForm.id]);

  useEffect(() => {
    if (form.productType !== "VARIATION") return;
    void loadParentProducts();
  }, [form.productType, loadParentProducts]);

  useEffect(() => {
    if (form.productType !== "VARIATION") return;
    if (!variationParentId) return;
    const selected = parentProducts.find((candidate) => candidate.id === variationParentId) || null;
    setSelectedVariationParent(selected);
  }, [form.productType, variationParentId, parentProducts]);

  const applyFilters = async (e: FormEvent) => {
    e.preventDefault();
    if (filtersSubmitting) return;
    setFiltersSubmitting(true);
    setStatus("Applying filters...");
    try {
      await Promise.all([loadActive(0), loadDeleted(0)]);
      setStatus("Filters applied.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to filter.");
    } finally {
      setFiltersSubmitting(false);
    }
  };

  const loadToEdit = async (id: string) => {
    if (!session.apiClient) return;
    if (loadingProductId) return;
    setLoadingProductId(id);
    setStatus("Loading product into editor...");
    try {
      const res = await session.apiClient.get(`/products/${id}`);
      const p = res.data as ProductDetail;
      setForm({
        id: p.id,
        name: p.name,
        slug: p.slug || "",
        shortDescription: p.shortDescription,
        description: p.description,
        images: p.images,
        regularPrice: String(p.regularPrice),
        discountedPrice: p.discountedPrice === null ? "" : String(p.discountedPrice),
        vendorId: p.vendorId,
        mainCategoryName: p.mainCategory || "",
        subCategoryNames: p.subCategories || [],
        productType: p.productType,
        sku: p.sku,
        active: p.active,
      });
      setProductSlugTouched(true);
      setProductSlugStatus("available");
      if (p.productType === "PARENT") {
        setParentAttributeNames(dedupeVariationAttributeNames((p.variations || []).map((v) => v.name)));
        setVariationParentId("");
        setSelectedVariationParent(null);
        setParentSearch("");
        setParentVariationAttributes([]);
        setVariationAttributeValues({});
        setVariationDrafts([]);
      } else if (p.productType === "VARIATION") {
        const attributes = dedupeVariationAttributeNames((p.variations || []).map((v) => v.name));
        const values: Record<string, string> = {};
        for (const v of p.variations || []) {
          values[normalizeVariationAttributeName(v.name)] = (v.value || "").trim();
        }
        const selectedParent = parentProducts.find((candidate) => candidate.id === p.parentProductId) || null;
        setParentAttributeNames([]);
        setVariationParentId(p.parentProductId || "");
        setSelectedVariationParent(selectedParent);
        setParentSearch(selectedParent ? selectedParent.name : "");
        setParentVariationAttributes(attributes);
        setVariationAttributeValues(values);
        setVariationDrafts([]);
      } else {
        setParentAttributeNames([]);
        setVariationParentId("");
        setSelectedVariationParent(null);
        setParentSearch("");
        setParentVariationAttributes([]);
        setVariationAttributeValues({});
        setVariationDrafts([]);
      }
      setNewParentAttributeName("");
      setStatus("Editor loaded.");
      toast.success("Product loaded in editor");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load product details.");
      toast.error(err instanceof Error ? err.message : "Failed to load product details");
    } finally {
      setLoadingProductId(null);
    }
  };

  const buildCommonPayload = () => ({
    name: form.name.trim(),
    slug: slugify(form.slug).slice(0, 180),
    shortDescription: form.shortDescription.trim(),
    description: form.description.trim(),
    images: form.images,
    regularPrice: Number(form.regularPrice),
    discountedPrice: form.discountedPrice.trim() ? Number(form.discountedPrice) : null,
    vendorId: form.vendorId.trim() ? form.vendorId.trim() : null,
    categories: [form.mainCategoryName, ...form.subCategoryNames].filter(Boolean),
    sku: form.sku.trim(),
    active: form.active,
  });

  const buildVariationPairs = () => {
    const pairs = parentVariationAttributes.map((name) => ({
      name,
      value: (variationAttributeValues[name] || "").trim(),
    }));
    if (pairs.length === 0) {
      throw new Error("Selected parent has no variation attributes");
    }
    const missingAttribute = pairs.find((pair) => !pair.value);
    if (missingAttribute) {
      throw new Error(`Enter value for ${missingAttribute.name}`);
    }
    return pairs;
  };

  const variationSignature = (pairs: Array<{ name: string; value: string }>) =>
    pairs
      .map((pair) => `${normalizeVariationAttributeName(pair.name)}=${(pair.value || "").trim().toLowerCase()}`)
      .sort()
      .join("|");

  const resetVariationInputsForNextChild = () => {
    const emptyValues: Record<string, string> = {};
    parentVariationAttributes.forEach((name) => {
      emptyValues[name] = "";
    });
    setVariationAttributeValues(emptyValues);
    setForm((old) => ({
      ...old,
      id: undefined,
      slug: "",
      sku: "",
    }));
    setProductSlugTouched(false);
    setProductSlugStatus("idle");
  };

  const addVariationDraft = () => {
    if (!variationParentId.trim()) {
      toast.error("Select a parent product");
      return;
    }
    if (priceValidationMessage) {
      toast.error(priceValidationMessage);
      return;
    }
    if (form.images.length === 0) {
      toast.error("Upload at least one image");
      return;
    }
    if (!slugify(form.slug)) {
      toast.error("Slug is required");
      return;
    }
    if (productSlugStatus === "checking") {
      toast.error("Wait until slug availability check completes");
      return;
    }
    if (productSlugStatus === "taken" || productSlugStatus === "invalid") {
      toast.error("Choose a unique valid slug");
      return;
    }
    try {
      const pairs = buildVariationPairs();
      const normalizedSlug = slugify(form.slug).slice(0, 180);
      const duplicateSlugInQueue = variationDrafts.some(
        (draft) => slugify(draft.payload.slug).slice(0, 180) === normalizedSlug
      );
      if (duplicateSlugInQueue) {
        toast.error("This slug is already in the variation queue");
        return;
      }
      const signature = variationSignature(pairs);
      const duplicateInQueue = variationDrafts.some(
        (draft) => draft.parentId === variationParentId.trim() && variationSignature(draft.payload.variations) === signature
      );
      if (duplicateInQueue) {
        toast.error("Same attribute combination is already in the queue");
        return;
      }

      const payload = {
        ...buildCommonPayload(),
        productType: "VARIATION" as const,
        variations: pairs,
      };
      const parentLabel = selectedVariationParent
        ? `${selectedVariationParent.name} (${selectedVariationParent.sku})`
        : variationParentId.trim();
      setVariationDrafts((old) => [
        ...old,
        {
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          parentId: variationParentId.trim(),
          parentLabel,
          mainCategoryName: form.mainCategoryName,
          subCategoryNames: [...form.subCategoryNames],
          payload,
        },
      ]);
      setStatus("Variation added to queue.");
      toast.success("Child variation added");
      resetVariationInputsForNextChild();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to add variation";
      setStatus(message);
      toast.error(message);
    }
  };

  const updateVariationDraftPayload = (draftId: string, patch: Partial<VariationDraft["payload"]>) => {
    setVariationDrafts((old) =>
      old.map((draft) =>
        draft.id === draftId
          ? {
            ...draft,
            payload: {
              ...draft.payload,
              ...patch,
            },
          }
          : draft
      )
    );
  };

  const updateVariationDraftAttributeValue = (draftId: string, attributeName: string, value: string) => {
    setVariationDrafts((old) =>
      old.map((draft) => {
        if (draft.id !== draftId) return draft;
        return {
          ...draft,
          payload: {
            ...draft.payload,
            variations: draft.payload.variations.map((pair) =>
              normalizeVariationAttributeName(pair.name) === normalizeVariationAttributeName(attributeName)
                ? { ...pair, value }
                : pair
            ),
          },
        };
      })
    );
  };

  const removeVariationDraft = (draftId: string) => {
    setVariationDrafts((old) => old.filter((draft) => draft.id !== draftId));
  };

  const loadVariationDraftToForm = (draftId: string) => {
    const draft = variationDrafts.find((item) => item.id === draftId);
    if (!draft) return;
    const selectedParent = parentProducts.find((candidate) => candidate.id === draft.parentId) || null;
    setVariationParentId(draft.parentId);
    setSelectedVariationParent(selectedParent);
    setParentSearch(selectedParent ? selectedParent.name : draft.parentLabel);
    const attributes = dedupeVariationAttributeNames(draft.payload.variations.map((pair) => pair.name));
    const values: Record<string, string> = {};
    for (const pair of draft.payload.variations) {
      values[normalizeVariationAttributeName(pair.name)] = pair.value || "";
    }
    setParentVariationAttributes(attributes);
    setVariationAttributeValues(values);
    setForm({
      id: undefined,
      name: draft.payload.name,
      slug: draft.payload.slug,
      shortDescription: draft.payload.shortDescription,
      description: draft.payload.description,
      images: [...draft.payload.images],
      regularPrice: String(draft.payload.regularPrice),
      discountedPrice: draft.payload.discountedPrice === null ? "" : String(draft.payload.discountedPrice),
      vendorId: draft.payload.vendorId || "",
      mainCategoryName: draft.mainCategoryName,
      subCategoryNames: [...draft.subCategoryNames],
      productType: "VARIATION",
      sku: draft.payload.sku,
      active: draft.payload.active,
    });
    setProductSlugTouched(true);
    setProductSlugStatus("available");
    setVariationDrafts((old) => old.filter((item) => item.id !== draftId));
    setStatus("Queued variation loaded into editor.");
    toast.success("Queued variation loaded for editing");
  };

  const validateVariationDraft = (draft: VariationDraft): string | null => {
    if (!draft.parentId.trim()) return "Parent product is missing";
    if (!draft.payload.name.trim()) return "Product name is required";
    if (!slugify(draft.payload.slug)) return "Slug is required";
    if (!draft.payload.shortDescription.trim()) return "Short description is required";
    if (!draft.payload.description.trim()) return "Description is required";
    if (!draft.payload.sku.trim()) return "SKU is required";
    if (!draft.payload.images || draft.payload.images.length === 0) return "At least one image is required";
    if (!Number.isFinite(draft.payload.regularPrice) || draft.payload.regularPrice <= 0) return "Regular price must be greater than 0";
    if (
      draft.payload.discountedPrice !== null
      && (!Number.isFinite(draft.payload.discountedPrice) || draft.payload.discountedPrice < 0)
    ) {
      return "Discounted price must be 0 or greater";
    }
    if (
      draft.payload.discountedPrice !== null
      && draft.payload.discountedPrice > draft.payload.regularPrice
    ) {
      return "Discounted price cannot be greater than regular price";
    }
    if (!draft.payload.categories || draft.payload.categories.length === 0) return "Parent categories are required";
    if (!draft.payload.variations || draft.payload.variations.length === 0) return "Variation attributes are required";
    if (draft.payload.variations.some((pair) => !(pair.value || "").trim())) {
      return "All attribute values are required";
    }
    return null;
  };

  const createQueuedVariations = async () => {
    if (!session.apiClient) return;
    if (creatingQueuedVariationBatch) return;
    if (variationDrafts.length === 0) {
      toast.error("Add at least one child variation");
      return;
    }
    const queueSignatures = new Set<string>();
    const queueSlugs = new Set<string>();
    for (let i = 0; i < variationDrafts.length; i++) {
      const draft = variationDrafts[i];
      const error = validateVariationDraft(draft);
      if (error) {
        toast.error(`Queue item ${i + 1}: ${error}`);
        return;
      }
      const normalizedSlug = slugify(draft.payload.slug).slice(0, 180);
      if (queueSlugs.has(normalizedSlug)) {
        toast.error(`Queue item ${i + 1}: duplicate slug in queue`);
        return;
      }
      queueSlugs.add(normalizedSlug);
      const normalizedPairs = draft.payload.variations.map((pair) => ({
        name: normalizeVariationAttributeName(pair.name),
        value: (pair.value || "").trim(),
      }));
      const signatureKey = `${draft.parentId}::${variationSignature(normalizedPairs)}`;
      if (queueSignatures.has(signatureKey)) {
        toast.error(`Queue item ${i + 1}: duplicate attribute combination for selected parent`);
        return;
      }
      queueSignatures.add(signatureKey);
    }
    setCreatingQueuedVariationBatch(true);
    setStatus(`Creating ${variationDrafts.length} variation product(s)...`);
    try {
      for (const draft of variationDrafts) {
        const requestPayload = {
          ...draft.payload,
          name: draft.payload.name.trim(),
          slug: slugify(draft.payload.slug).slice(0, 180),
          shortDescription: draft.payload.shortDescription.trim(),
          description: draft.payload.description.trim(),
          regularPrice: Number(draft.payload.regularPrice),
          discountedPrice: draft.payload.discountedPrice === null ? null : Number(draft.payload.discountedPrice),
          vendorId: draft.payload.vendorId && draft.payload.vendorId.trim() ? draft.payload.vendorId.trim() : null,
          categories: (draft.payload.categories || []).map((name) => name.trim()).filter(Boolean),
          sku: draft.payload.sku.trim(),
          variations: draft.payload.variations.map((pair) => ({
            name: normalizeVariationAttributeName(pair.name),
            value: (pair.value || "").trim(),
          })),
        };
        await session.apiClient.post(`/admin/products/${draft.parentId}/variations`, requestPayload);
      }
      setStatus(`${variationDrafts.length} variation product(s) created.`);
      toast.success(`${variationDrafts.length} variation product(s) created`);
      setVariationDrafts([]);
      setVariationParentId("");
      setSelectedVariationParent(null);
      setParentSearch("");
      setParentVariationAttributes([]);
      setVariationAttributeValues({});
      setForm((old) => ({
        ...emptyForm,
        productType: "VARIATION",
        mainCategoryName: old.mainCategoryName,
        subCategoryNames: old.subCategoryNames,
      }));
      setProductSlugTouched(false);
      setProductSlugStatus("idle");
      await Promise.all([reloadCurrentView(), loadParentProducts()]);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Variation create failed.");
      toast.error(err instanceof Error ? err.message : "Variation create failed");
    } finally {
      setCreatingQueuedVariationBatch(false);
    }
  };

  const submitProduct = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient) return;
    if (savingProduct) return;
    if (priceValidationMessage) {
      toast.error(priceValidationMessage);
      return;
    }
    if (form.images.length === 0) {
      toast.error("Upload at least one image");
      return;
    }
    if (!slugify(form.slug)) {
      toast.error("Slug is required");
      return;
    }
    if (productSlugStatus === "checking") {
      toast.error("Wait until slug availability check completes");
      return;
    }
    if (productSlugStatus === "taken" || productSlugStatus === "invalid") {
      toast.error("Choose a unique valid slug");
      return;
    }

    if (form.productType === "VARIATION" && !form.id) {
      addVariationDraft();
      return;
    }

    setSavingProduct(true);
    try {
      let payload: {
        name: string;
        slug: string;
        shortDescription: string;
        description: string;
        images: string[];
        regularPrice: number;
        discountedPrice: number | null;
        vendorId: string | null;
        categories: string[];
        productType: ProductType;
        variations: Array<{ name: string; value: string }>;
        sku: string;
        active: boolean;
      };
      if (form.productType === "PARENT") {
        const attributes = dedupeVariationAttributeNames(parentAttributeNames);
        if (attributes.length === 0) {
          toast.error("Add at least one parent attribute");
          return;
        }
        payload = {
          ...buildCommonPayload(),
          productType: "PARENT",
          variations: attributes.map((name) => ({ name, value: "" })),
        };
      } else if (form.productType === "VARIATION") {
        payload = {
          ...buildCommonPayload(),
          productType: "VARIATION",
          variations: buildVariationPairs(),
        };
      } else {
        payload = {
          ...buildCommonPayload(),
          productType: "SINGLE",
          variations: [],
        };
      }

      setStatus(form.id ? "Updating product..." : "Creating product...");
      if (form.id) {
        await session.apiClient.put(`/admin/products/${form.id}`, payload);
        setStatus("Product updated.");
        toast.success("Product updated");
      } else {
        await session.apiClient.post("/admin/products", payload);
        setStatus("Product created.");
        toast.success("Product created");
      }
      setForm(emptyForm);
      setProductSlugTouched(false);
      setProductSlugStatus("idle");
      setParentAttributeNames([]);
      setNewParentAttributeName("");
      setVariationParentId("");
      setSelectedVariationParent(null);
      setParentSearch("");
      setParentVariationAttributes([]);
      setVariationAttributeValues({});
      setVariationDrafts([]);
      await Promise.all([loadActive(0), loadDeleted(0), loadParentProducts()]);
      setShowDeleted(false);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Save failed.");
      toast.error(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSavingProduct(false);
    }
  };

  const onSelectVariationParent = async (parentId: string) => {
    if (selectingVariationParentId) return;
    if (form.id) return;
    if (variationDrafts.length > 0 && variationDrafts.some((draft) => draft.parentId !== parentId)) {
      setVariationDrafts([]);
      toast("Variation queue cleared because parent product changed.");
    }
    setVariationParentId(parentId);
    const selected = parentProducts.find((candidate) => candidate.id === parentId) || null;
    setSelectedVariationParent(selected);
    if (selected) {
      setParentSearch(selected.name);
    }
    setParentVariationAttributes([]);
    setVariationAttributeValues({});
    if (!session.apiClient || !parentId) return;
    setSelectingVariationParentId(parentId);
    try {
      const res = await session.apiClient.get(`/products/${parentId}`);
      const parent = res.data as ProductDetail;
      const attrs = dedupeVariationAttributeNames((parent.variations || []).map((v) => v.name).filter(Boolean));
      setParentVariationAttributes(attrs);
      const initial: Record<string, string> = {};
      attrs.forEach((name) => {
        initial[name] = "";
      });
      setVariationAttributeValues(initial);
      setForm((old) => ({
        ...old,
        productType: "VARIATION",
        mainCategoryName: parent.mainCategory || old.mainCategoryName,
        subCategoryNames: parent.subCategories || old.subCategoryNames,
      }));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to load parent product");
    } finally {
      setSelectingVariationParentId(null);
    }
  };

  const refreshVariationParents = async () => {
    try {
      setStatus("Refreshing parent products...");
      await loadParentProducts();
      setStatus("Parent products refreshed.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to refresh parent products";
      setStatus(message);
      toast.error(message);
    }
  };

  const addParentAttribute = () => {
    const normalized = normalizeVariationAttributeName(newParentAttributeName);
    if (!normalized) return;
    setParentAttributeNames((old) => {
      if (old.includes(normalized)) return old;
      return [...old, normalized];
    });
    setNewParentAttributeName("");
  };

  const removeParentAttribute = (name: string) => {
    setParentAttributeNames((old) => old.filter((candidate) => candidate !== name));
  };

  const softDelete = async (id: string) => {
    if (!session.apiClient) return;
    if (confirmLoading) return;
    setStatus("Deleting product...");
    try {
      await session.apiClient.delete(`/admin/products/${id}`);
      setStatus("Product soft deleted.");
      toast.success("Product moved to deleted list");
      await Promise.all([loadActive(0), loadDeleted(0), loadParentProducts()]);
      setShowDeleted(true);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Delete failed.");
      toast.error(err instanceof Error ? err.message : "Delete failed");
    }
  };

  const restore = async (id: string) => {
    if (!session.apiClient) return;
    if (restoringProductId) return;
    setRestoringProductId(id);
    setStatus("Restoring product...");
    try {
      await session.apiClient.post(`/admin/products/${id}/restore`);
      setStatus("Product restored.");
      toast.success("Product restored");
      await Promise.all([loadActive(0), loadDeleted(0), loadParentProducts()]);
      setShowDeleted(false);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Restore failed.");
      toast.error(err instanceof Error ? err.message : "Restore failed");
    } finally {
      setRestoringProductId(null);
    }
  };

  const saveCategory = async () => {
    if (!session.apiClient) return;
    if (savingCategory) return;
    if (!categoryForm.name.trim()) return;
    if (!slugify(categoryForm.slug)) {
      toast.error("Category slug is required");
      return;
    }
    if (categorySlugStatus === "checking") {
      toast.error("Wait until category slug availability check completes");
      return;
    }
    if (categorySlugStatus === "taken" || categorySlugStatus === "invalid") {
      toast.error("Choose a unique valid category slug");
      return;
    }
    setSavingCategory(true);
    const payload = {
      name: categoryForm.name.trim(),
      slug: slugify(categoryForm.slug).slice(0, 130),
      type: categoryForm.type,
      parentCategoryId: categoryForm.type === "SUB" ? categoryForm.parentCategoryId || null : null,
    };
    setStatus(categoryForm.id ? "Updating category..." : "Creating category...");
    try {
      if (categoryForm.id) {
        await session.apiClient.put(`/admin/categories/${categoryForm.id}`, payload);
        toast.success("Category updated");
      } else {
        await session.apiClient.post("/admin/categories", payload);
        toast.success("Category created");
      }
      setCategoryForm({ name: "", slug: "", type: "PARENT", parentCategoryId: "" });
      setCategorySlugTouched(false);
      setCategorySlugStatus("idle");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
      setStatus("Category operation complete.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Category save failed.");
      toast.error(err instanceof Error ? err.message : "Category save failed");
    } finally {
      setSavingCategory(false);
    }
  };

  const deleteCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (confirmLoading) return;
    setStatus("Deleting category...");
    try {
      await session.apiClient.delete(`/admin/categories/${id}`);
      toast.success("Category deleted");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
      setStatus("Category deleted.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Delete category failed.");
      toast.error(err instanceof Error ? err.message : "Delete category failed");
    }
  };

  const restoreCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (restoringCategoryId) return;
    setRestoringCategoryId(id);
    setStatus("Restoring category...");
    try {
      await session.apiClient.post(`/admin/categories/${id}/restore`);
      toast.success("Category restored");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
      setStatus("Category restored.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Restore category failed.");
      toast.error(err instanceof Error ? err.message : "Restore category failed");
    } finally {
      setRestoringCategoryId(null);
    }
  };

  const uploadImages = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files ? Array.from(e.target.files) : [];
    if (!session.apiClient || files.length === 0) return;
    if (form.images.length + files.length > MAX_IMAGE_COUNT) {
      toast.error("You can add up to 5 images");
      e.target.value = "";
      return;
    }

    try {
      setUploadingImages(true);
      for (const file of files) {
        await validateImageFile(file);
      }

      const namesRes = await session.apiClient.post("/admin/products/images/names", {
        fileNames: files.map((file) => file.name),
      });
      const preparedKeys = ((namesRes.data as { images?: string[] })?.images || []).filter(Boolean);
      if (preparedKeys.length !== files.length) {
        throw new Error("Failed to prepare image names");
      }

      const data = new FormData();
      files.forEach((file, index) => {
        data.append("files", file);
        data.append("keys", preparedKeys[index]);
      });

      const res = await session.apiClient.post("/admin/products/images", data, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      });
      const uploaded = ((res.data as { images?: string[] })?.images || []).filter(Boolean);
      setForm((old) => ({ ...old, images: [...old.images, ...uploaded].slice(0, MAX_IMAGE_COUNT) }));
      toast.success("Images uploaded");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Image upload failed");
    } finally {
      setUploadingImages(false);
      e.target.value = "";
    }
  };

  const removeImage = (index: number) => {
    setForm((old) => ({
      ...old,
      images: old.images.filter((_, i) => i !== index),
    }));
  };

  const onImageDrop = (targetIndex: number) => {
    if (dragImageIndex === null || dragImageIndex === targetIndex) return;
    setForm((old) => {
      const next = [...old.images];
      const [dragged] = next.splice(dragImageIndex, 1);
      next.splice(targetIndex, 0, dragged);
      return { ...old, images: next };
    });
    setDragImageIndex(null);
  };

  if (session.status === "loading" || session.status === "idle") {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        <div className="mx-auto max-w-7xl px-4 py-10 text-center text-[var(--muted)]">
          <div className="mx-auto w-12 h-12 animate-spin rounded-full border-4 border-[var(--line)] border-t-[var(--brand)]" />
          <p className="mt-4">Loading...</p>
        </div>
      </div>
    );
  }

  if (!session.isAuthenticated) {
    return null;
  }

  const rows = showDeleted ? deletedPage?.content || [] : activePage?.content || [];
  const pageInfo = showDeleted ? deletedPage : activePage;
  const parentCategories = categories.filter((c) => c.type === "PARENT");
  const subCategories = categories.filter((c) => c.type === "SUB");
  const selectedParent = parentCategories.find((c) => c.name === form.mainCategoryName) || null;
  const subCategoryOptions = categories.filter(
    (c) => c.type === "SUB" && c.parentCategoryId && selectedParent && c.parentCategoryId === selectedParent.id
  );

  const title = showDeleted ? "Deleted Products" : "Active Products";
  const pageMeta = getPageMeta(pageInfo);
  const filteredParentProducts = parentProducts.filter((p) => {
    const needle = parentSearch.trim().toLowerCase();
    if (!needle) return true;
    return p.name.toLowerCase().includes(needle)
      || p.sku.toLowerCase().includes(needle)
      || (p.slug || "").toLowerCase().includes(needle);
  });
  const regularPriceValue = parseNumber(form.regularPrice);
  const discountedPriceValue = form.discountedPrice.trim() ? parseNumber(form.discountedPrice) : null;
  const priceValidationMessage =
    regularPriceValue !== null && discountedPriceValue !== null && discountedPriceValue > regularPriceValue
      ? "Discounted price cannot be greater than regular price."
      : null;
  const listLoading = showDeleted ? loadingDeletedList : loadingActiveList;
  const productMutationBusy = savingProduct || creatingQueuedVariationBatch || uploadingImages;
  const productRowActionBusy = Boolean(loadingProductId) || confirmLoading || Boolean(restoringProductId) || listLoading;
  const categoryMutationBusy = savingCategory || confirmLoading || Boolean(restoringCategoryId);
  const productSlugBlocked = productSlugStatus === "checking" || productSlugStatus === "taken" || productSlugStatus === "invalid";
  const categorySlugBlocked = categorySlugStatus === "checking" || categorySlugStatus === "taken" || categorySlugStatus === "invalid";
  const isEditingProduct = Boolean(form.id);
  const isEditingVariation = isEditingProduct && form.productType === "VARIATION";
  const canQueueVariation = !isEditingVariation;
  const variationDraftBlockedReason =
    !canQueueVariation
      ? "Queue mode is disabled while editing an existing variation."
      : Boolean(selectingVariationParentId)
        ? "Wait until parent attributes finish loading."
        : !variationParentId.trim()
          ? "Select a parent product first."
          : parentVariationAttributes.length === 0
            ? "Selected parent has no variation attributes."
            : productSlugStatus === "checking"
              ? "Wait until slug availability check completes."
              : productSlugStatus === "taken" || productSlugStatus === "invalid"
                ? "Choose a unique valid slug."
                : priceValidationMessage;
  const canAddVariationDraft =
    canQueueVariation
    && !variationDraftBlockedReason
    && !productMutationBusy
    && !loadingParentProducts;
  const canCreateQueuedVariations =
    canQueueVariation
    && variationDrafts.length > 0
    && !creatingQueuedVariationBatch
    && !savingProduct;

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => {
          void session.logout();
        }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">Admin Products</span>
        </nav>

        <section className="animate-rise space-y-4 rounded-xl p-5" style={{ background: "rgba(17,17,40,0.7)", border: "1px solid rgba(0,212,255,0.1)", backdropFilter: "blur(16px)" }}>
          <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN CATALOG</p>
              <h1 className="text-2xl font-bold text-[var(--ink)]">Product Operations</h1>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setShowDeleted(false)}
                disabled={filtersSubmitting || listLoading}
                className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${!showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
              >
                Active
              </button>
              <button
                type="button"
                onClick={() => setShowDeleted(true)}
                disabled={filtersSubmitting || listLoading}
                className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
              >
                Deleted
              </button>
            </div>
          </div>

          <form onSubmit={applyFilters} className="mb-5 grid gap-3 md:grid-cols-5">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search text"
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm" style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            />
            <input
              value={sku}
              onChange={(e) => setSku(e.target.value)}
              placeholder="SKU"
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm" style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            />
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm" style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            >
              <option value="">All Categories</option>
              {parentCategories.length > 0 && (
                <optgroup label="Main Categories">
                  {parentCategories
                    .slice()
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((item) => (
                      <option key={item.id} value={item.name}>
                        {item.name}
                      </option>
                    ))}
                </optgroup>
              )}
              {subCategories.length > 0 && (
                <optgroup label="Sub Categories">
                  {subCategories
                    .slice()
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((item) => (
                      <option key={item.id} value={item.name}>
                        {item.name}
                      </option>
                    ))}
                </optgroup>
              )}
            </select>
            <select
              value={type}
              onChange={(e) => setType(e.target.value as ProductType | "")}
              className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm" style={{ background: "var(--surface-2)", color: "var(--ink)" }}
            >
              <option value="">All Types</option>
              <option value="SINGLE">SINGLE</option>
              <option value="PARENT">PARENT</option>
              <option value="VARIATION">VARIATION</option>
            </select>
            <button
              type="submit"
              disabled={filtersSubmitting || listLoading}
              className="btn-brand rounded-xl px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60"
            >
              {(filtersSubmitting || listLoading) ? "Applying..." : "Apply Filters"}
            </button>
          </form>

          <div className="grid gap-6 lg:grid-cols-[1.1fr,0.9fr]">
            <div className="order-2 lg:order-1">
              <h2 className="mb-3 text-2xl text-[var(--ink)]">{title}</h2>
              <div className="overflow-hidden rounded-2xl border border-[var(--line)]" style={{ background: "var(--surface)" }}>
                <table className="w-full text-left text-sm">
                  <thead style={{ background: "var(--surface-2)", color: "var(--ink)" }}>
                    <tr>
                      <th className="px-3 py-2">Name</th>
                      <th className="px-3 py-2">SKU</th>
                      <th className="px-3 py-2">Type</th>
                      <th className="px-3 py-2">Price</th>
                      <th className="px-3 py-2">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 && (
                      <tr>
                        <td colSpan={5}>
                          <div className="empty-state">
                            <div className="empty-state-icon">Items</div>
                            <p className="empty-state-title">No products</p>
                            <p className="empty-state-desc">{showDeleted ? "No deleted products" : "Create your first product to get started"}</p>
                          </div>
                        </td>
                      </tr>
                    )}
                    {rows.map((p) => (
                      <tr key={p.id} className="border-t border-[var(--line)]">
                        <td className="px-3 py-2">
                          <p className="font-semibold text-[var(--ink)]">{p.name}</p>
                          <p className="line-clamp-1 text-xs text-[var(--muted)]">{p.shortDescription}</p>
                        </td>
                        <td className="px-3 py-2 font-mono text-xs text-[var(--muted)]">{p.sku}</td>
                        <td className="text-xs text-[var(--muted)]">
                          <span className={`type-badge type-badge--${p.productType.toLowerCase()}`}>{p.productType}</span>
                        </td>
                        <td className="px-3 py-2 text-[var(--ink)]">{money(p.sellingPrice)}</td>
                        <td className="px-3 py-2">
                          <div className="flex flex-wrap gap-2">
                            {!showDeleted && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => {
                                    void loadToEdit(p.id);
                                  }}
                                  disabled={productRowActionBusy}
                                  className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                                >
                                  {loadingProductId === p.id ? "Loading..." : "Edit"}
                                </button>
                                <button
                                  type="button"
                                  onClick={() => {
                                    setConfirmAction({ type: "product", id: p.id, name: p.name });
                                  }}
                                  disabled={productRowActionBusy}
                                  className="rounded-md border border-red-900/30 px-2 py-1 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(239,68,68,0.06)" }}
                                >
                                  Delete
                                </button>
                              </>
                            )}
                            {showDeleted && (
                              <button
                                type="button"
                                onClick={() => {
                                  void restore(p.id);
                                }}
                                disabled={productRowActionBusy}
                                className="rounded-md border border-emerald-900/30 px-2 py-1 text-xs text-emerald-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(16,185,129,0.06)" }}
                              >
                                {restoringProductId === p.id ? "Restoring..." : "Restore"}
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination
                currentPage={showDeleted ? deletedPageIndex : page}
                totalPages={pageMeta.totalPages}
                totalElements={pageMeta.totalElements}
                onPageChange={(p) => {
                  if (showDeleted) {
                    void loadDeleted(p);
                  } else {
                    void loadActive(p);
                  }
                }}
                disabled={listLoading}
              />
            </div>

            <div className="order-1 space-y-4 lg:order-2">
              <section className="card-surface rounded-2xl p-5">
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-2xl text-[var(--ink)]">
                    {form.id
                      ? form.productType === "VARIATION"
                        ? "Update Variation"
                        : "Update Product"
                      : form.productType === "VARIATION"
                        ? "Create Child Variations"
                        : "Create Product"}
                  </h2>
                  {form.id && (
                    <button
                      type="button"
                      onClick={() => {
                        setForm(emptyForm);
                        setProductSlugTouched(false);
                        setProductSlugStatus("idle");
                        setParentAttributeNames([]);
                        setNewParentAttributeName("");
                        setVariationParentId("");
                        setSelectedVariationParent(null);
                        setParentSearch("");
                        setParentVariationAttributes([]);
                        setVariationAttributeValues({});
                        setVariationDrafts([]);
                      }}
                      disabled={productMutationBusy}
                      className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                    >
                      Reset
                    </button>
                  )}
                </div>
                <form onSubmit={submitProduct} className="grid gap-3 text-sm">
                  <div className="form-group">
                    <label className="form-label">Product Name</label>
                    <input
                      value={form.name}
                      onChange={(e) => {
                        const value = e.target.value;
                        setForm((o) => ({ ...o, name: value }));
                      }}
                      placeholder="e.g. Nike Air Max 90"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Slug</label>
                    <input
                      value={form.slug}
                      onChange={(e) => {
                        setProductSlugTouched(true);
                        setForm((o) => ({ ...o, slug: slugify(e.target.value).slice(0, 180) }));
                      }}
                      placeholder="product-url-slug"
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy}
                      required
                    />
                    <p
                      className={`mt-1 text-[11px] ${productSlugStatus === "taken" || productSlugStatus === "invalid"
                        ? "text-red-600"
                        : productSlugStatus === "available"
                          ? "text-emerald-600"
                          : "text-[var(--muted)]"
                        }`}
                    >
                      {productSlugStatus === "checking" && "Checking slug..."}
                      {productSlugStatus === "available" && "Slug is available"}
                      {productSlugStatus === "taken" && "Slug is already taken"}
                      {productSlugStatus === "invalid" && "Enter a valid slug"}
                      {productSlugStatus === "idle" && "Slug is used in product URL"}
                    </p>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Short Description</label>
                    <input value={form.shortDescription} onChange={(e) => setForm((o) => ({ ...o, shortDescription: e.target.value }))} placeholder="Brief product summary" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} required />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Description</label>
                    <textarea value={form.description} onChange={(e) => setForm((o) => ({ ...o, description: e.target.value }))} placeholder="Full product description" className="rounded-lg border border-[var(--line)] px-3 py-2" rows={3} disabled={productMutationBusy} required />
                  </div>
                  <div className="rounded-lg border border-[var(--line)] p-3">
                    <div className="mb-2 flex items-center justify-between">
                      <p className="text-xs text-[var(--muted)]">
                        Product Images ({form.images.length}/{MAX_IMAGE_COUNT})
                      </p>
                      <label className="cursor-pointer rounded-md border border-[var(--line)] px-2 py-1 text-xs" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}>
                        {uploadingImages ? "Uploading..." : "Upload Images"}
                        <input
                          type="file"
                          accept="image/png,image/jpeg,image/webp"
                          multiple
                          className="hidden"
                          disabled={productMutationBusy || form.images.length >= MAX_IMAGE_COUNT}
                          onChange={(e) => {
                            void uploadImages(e);
                          }}
                        />
                      </label>
                    </div>
                    <p className="mb-2 text-[11px] text-[var(--muted)]">Max 5 images, 1MB each, 540x540 max. Drag to reorder. First image is main.</p>
                    {form.images.length === 0 && <p className="text-xs text-[var(--muted)]">No images uploaded.</p>}
                    <div className="grid gap-2">
                      {form.images.map((imageName, index) => {
                        const imageUrl = resolveImageUrl(imageName);
                        return (
                          <div
                            key={`${imageName}-${index}`}
                            draggable
                            onDragStart={() => setDragImageIndex(index)}
                            onDragOver={(e: DragEvent<HTMLDivElement>) => e.preventDefault()}
                            onDrop={() => onImageDrop(index)}
                            className="flex items-center gap-2 rounded-lg border border-[var(--line)] p-2" style={{ background: "var(--surface-2)" }}
                          >
                            <div className="h-12 w-12 overflow-hidden rounded-md border border-[var(--line)]" style={{ background: "var(--surface-3)" }}>
                              {imageUrl ? (
                                <Image src={imageUrl} alt={imageName} width={48} height={48} className="h-full w-full object-cover" unoptimized />
                              ) : (
                                <div className="grid h-full w-full place-items-center text-[10px] text-[var(--muted)]">IMG</div>
                              )}
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-xs text-[var(--ink)]">{imageName}</p>
                              <p className="text-[10px] text-[var(--muted)]">{index === 0 ? "Main image" : `Position ${index + 1}`}</p>
                            </div>
                            <button
                              type="button"
                              onClick={() => removeImage(index)}
                              disabled={productMutationBusy}
                              className="rounded border border-red-900/30 px-2 py-1 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(239,68,68,0.06)" }}
                            >
                              Remove
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Product Type</label>
                    <select
                      value={form.productType}
                      onChange={(e) => {
                        const nextType = e.target.value as ProductType;
                        setForm((o) => ({
                          ...o,
                          productType: nextType,
                          mainCategoryName: nextType === "VARIATION" ? "" : o.mainCategoryName,
                          subCategoryNames: nextType === "VARIATION" ? [] : o.subCategoryNames,
                        }));
                        if (nextType !== "PARENT") {
                          setParentAttributeNames([]);
                          setNewParentAttributeName("");
                        }
                        if (nextType !== "VARIATION") {
                          setVariationParentId("");
                          setSelectedVariationParent(null);
                          setParentSearch("");
                          setParentVariationAttributes([]);
                          setVariationAttributeValues({});
                          setVariationDrafts([]);
                        }
                      }}
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={productMutationBusy || isEditingProduct}
                    >
                      <option value="SINGLE">SINGLE</option>
                      <option value="PARENT">PARENT</option>
                      <option value="VARIATION">VARIATION</option>
                    </select>
                    <p className="mt-1 text-[11px] text-[var(--muted)]">
                      {isEditingProduct
                        ? "Product type is locked while editing."
                        : "Select product type first. Variation products inherit categories from their parent."}
                    </p>
                  </div>
                  {form.productType !== "VARIATION" && (
                    <>
                      <select
                        value={form.mainCategoryName}
                        onChange={(e) =>
                          setForm((o) => ({
                            ...o,
                            mainCategoryName: e.target.value,
                            subCategoryNames: [],
                          }))
                        }
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                        required
                      >
                        <option value="">Select Main Category</option>
                        {parentCategories.map((c) => (
                          <option key={c.id} value={c.name}>
                            {c.name}
                          </option>
                        ))}
                      </select>
                      <div className="rounded-lg border border-[var(--line)] p-2">
                        <p className="mb-1 text-xs text-[var(--muted)]">Sub Categories (multiple)</p>
                        <div className="grid gap-1">
                          {subCategoryOptions.length === 0 && (
                            <p className="text-xs text-[var(--muted)]">No sub categories for selected main category.</p>
                          )}
                          {subCategoryOptions.map((c) => (
                            <label key={c.id} className="flex items-center gap-2 text-xs text-[var(--ink)]">
                              <input
                                type="checkbox"
                                checked={form.subCategoryNames.includes(c.name)}
                                onChange={(e) =>
                                  setForm((o) => ({
                                    ...o,
                                    subCategoryNames: e.target.checked
                                      ? [...o.subCategoryNames, c.name]
                                      : o.subCategoryNames.filter((n) => n !== c.name),
                                  }))
                                }
                                disabled={productMutationBusy}
                              />
                              {c.name}
                            </label>
                          ))}
                        </div>
                      </div>
                    </>
                  )}
                  <div className="form-group">
                    <label className="form-label">SKU</label>
                    <input value={form.sku} onChange={(e) => setForm((o) => ({ ...o, sku: e.target.value }))} placeholder="e.g. NK-AM90-BLK-42" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} required />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="form-group">
                      <label className="form-label">Regular Price</label>
                      <input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        min="0.01"
                        value={form.regularPrice}
                        onChange={(e) => setForm((o) => ({ ...o, regularPrice: e.target.value }))}
                        onWheel={preventNumberInputScroll}
                        onKeyDown={preventNumberInputArrows}
                        placeholder="0.00"
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Discounted Price</label>
                      <input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        min="0"
                        value={form.discountedPrice}
                        onChange={(e) => setForm((o) => ({ ...o, discountedPrice: e.target.value }))}
                        onWheel={preventNumberInputScroll}
                        onKeyDown={preventNumberInputArrows}
                        placeholder="Optional"
                        className="rounded-lg border border-[var(--line)] px-3 py-2"
                        disabled={productMutationBusy}
                      />
                    </div>
                  </div>
                  {priceValidationMessage && (
                    <p className="text-xs font-semibold text-red-600">{priceValidationMessage}</p>
                  )}
                  <input value={form.vendorId} onChange={(e) => setForm((o) => ({ ...o, vendorId: e.target.value }))} placeholder="Vendor UUID (optional)" className="rounded-lg border border-[var(--line)] px-3 py-2" disabled={productMutationBusy} />
                  {form.productType === "PARENT" && (
                    <div className="rounded-lg border border-[var(--line)] p-3">
                      <p className="text-xs font-semibold text-[var(--ink)]">Variation Attributes</p>
                      <p className="mt-1 text-[11px] text-[var(--muted)]">
                        Add attribute names for this parent product (example: color, size, material).
                      </p>
                      <div className="mt-2 flex gap-2">
                        <input
                          value={newParentAttributeName}
                          onChange={(e) => setNewParentAttributeName(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.preventDefault();
                              addParentAttribute();
                            }
                          }}
                          placeholder="Attribute name"
                          className="flex-1 rounded-lg border border-[var(--line)] px-3 py-2"
                          disabled={productMutationBusy}
                        />
                        <button
                          type="button"
                          onClick={addParentAttribute}
                          disabled={productMutationBusy}
                          className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                        >
                          Add
                        </button>
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {parentAttributeNames.length === 0 && (
                          <p className="text-xs text-[var(--muted)]">No attributes added yet.</p>
                        )}
                        {parentAttributeNames.map((name) => (
                          <span
                            key={name}
                            className="inline-flex items-center gap-2 rounded-full border border-[var(--line)] bg-[var(--bg)] px-3 py-1 text-xs text-[var(--ink)]"
                          >
                            {name}
                            <button
                              type="button"
                              onClick={() => removeParentAttribute(name)}
                              disabled={productMutationBusy}
                              className="rounded-full border border-[var(--line)] px-1.5 text-[10px] leading-4 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--muted)" }}
                            >
                              x
                            </button>
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {form.productType === "VARIATION" && (
                    <div className="rounded-lg border border-[var(--line)] p-3">
                      <p className="text-xs font-semibold text-[var(--ink)]">Child Variation Setup</p>
                      <p className="mt-1 text-[11px] text-[var(--muted)]">
                        {isEditingVariation
                          ? "Editing mode: parent selection is locked. Categories remain inherited from the existing parent."
                          : "Select a parent product first. Categories are inherited automatically."}
                      </p>
                      <div className="mt-2 flex gap-2">
                        <input
                          value={parentSearch}
                          onChange={(e) => setParentSearch(e.target.value)}
                          placeholder="Search parent by name, SKU, or slug"
                          className="w-full rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
                          disabled={isEditingVariation || loadingParentProducts || productMutationBusy}
                        />
                        <button
                          type="button"
                          onClick={() => {
                            void refreshVariationParents();
                          }}
                          disabled={isEditingVariation || loadingParentProducts || productMutationBusy || Boolean(selectingVariationParentId)}
                          className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                        >
                          {loadingParentProducts ? "Refreshing..." : "Refresh"}
                        </button>
                      </div>
                      {!loadingParentProducts && parentProducts.length > 0 && (
                        <p className="mt-1 text-[11px] text-[var(--muted)]">
                          Showing {Math.min(filteredParentProducts.length, 20)} of {parentProducts.length} parent products
                        </p>
                      )}
                      <div className="mt-2 max-h-40 overflow-auto rounded-lg border border-[var(--line)]" style={{ background: "var(--surface)" }}>
                        {loadingParentProducts && (
                          <p className="px-3 py-2 text-xs text-[var(--muted)]">Loading parent products...</p>
                        )}
                        {!loadingParentProducts && parentProducts.length === 0 && (
                          <p className="px-3 py-2 text-xs text-[var(--muted)]">No active parent products found.</p>
                        )}
                        {!loadingParentProducts && parentProducts.length > 0 && filteredParentProducts.length === 0 && (
                          <p className="px-3 py-2 text-xs text-[var(--muted)]">No matching parent products.</p>
                        )}
                        {filteredParentProducts.slice(0, 20).map((p) => (
                          <button
                            key={p.id}
                            type="button"
                            onClick={() => {
                              void onSelectVariationParent(p.id);
                            }}
                            disabled={isEditingVariation || Boolean(selectingVariationParentId) || productMutationBusy}
                            className={`flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-xs transition hover:bg-[var(--brand-soft)] ${variationParentId === p.id ? "bg-[var(--brand-soft)]" : ""
                              } disabled:cursor-not-allowed disabled:opacity-60`}
                          >
                            <span className="min-w-0">
                              <span className="block truncate text-[var(--ink)]">{p.name}</span>
                              <span className="block truncate text-[10px] text-[var(--muted)]">/{p.slug || "no-slug"}</span>
                            </span>
                            <span className="shrink-0 text-[var(--muted)]">{p.sku}</span>
                          </button>
                        ))}
                      </div>
                      {selectedVariationParent && (
                        <div className="mt-2 rounded-md border border-[var(--line)] bg-[var(--bg)] px-2 py-1.5 text-xs text-[var(--muted)]">
                          <p>
                            Selected parent: <span className="font-semibold text-[var(--ink)]">{selectedVariationParent.name}</span> ({selectedVariationParent.sku})
                          </p>
                          {(form.mainCategoryName || form.subCategoryNames.length > 0) && (
                            <div className="mt-1 flex flex-wrap gap-1">
                              {form.mainCategoryName && (
                                <span className="rounded-full bg-[var(--brand)] px-2 py-0.5 text-[10px] font-semibold text-white">
                                  {form.mainCategoryName}
                                </span>
                              )}
                              {form.subCategoryNames.map((name) => (
                                <span
                                  key={`variation-sub-${name}`}
                                  className="rounded-full border border-[var(--line)] px-2 py-0.5 text-[10px] text-[var(--ink)]" style={{ background: "var(--surface-2)" }}
                                >
                                  {name}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                      {selectingVariationParentId && (
                        <p className="mt-2 text-xs text-[var(--muted)]">Loading parent attributes...</p>
                      )}
                      {parentVariationAttributes.length > 0 && (
                        <div className="mt-3 grid gap-2 sm:grid-cols-2">
                          {parentVariationAttributes.map((attributeName) => (
                            <input
                              key={attributeName}
                              value={variationAttributeValues[attributeName] || ""}
                              onChange={(e) =>
                                setVariationAttributeValues((old) => ({
                                  ...old,
                                  [attributeName]: e.target.value,
                                }))
                              }
                              placeholder={`${attributeName} (required)`}
                              className="rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
                              disabled={productMutationBusy}
                              required
                            />
                          ))}
                        </div>
                      )}
                      {parentVariationAttributes.length === 0 && (
                        <p className="mt-2 text-xs text-[var(--muted)]">
                          Select a parent product to load variation attributes.
                        </p>
                      )}
                      <div className="mt-3 flex flex-wrap gap-2">
                        {canQueueVariation && (
                          <>
                            <button
                              type="button"
                              onClick={addVariationDraft}
                              disabled={!canAddVariationDraft}
                              className="btn-brand rounded-lg px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              Add Another Variation
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                void createQueuedVariations();
                              }}
                              disabled={!canCreateQueuedVariations}
                              className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-50" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                            >
                              {creatingQueuedVariationBatch
                                ? `Creating... (${variationDrafts.length})`
                                : `Create Queued Variations (${variationDrafts.length})`}
                            </button>
                            {variationDrafts.length > 0 && (
                              <button
                                type="button"
                                onClick={() => setVariationDrafts([])}
                                disabled={creatingQueuedVariationBatch}
                                className="rounded-lg border border-red-900/30 px-3 py-2 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(239,68,68,0.06)" }}
                              >
                                Clear Queue
                              </button>
                            )}
                          </>
                        )}
                      </div>
                      {canQueueVariation && variationDraftBlockedReason && (
                        <p className="mt-2 text-xs font-medium text-amber-700">{variationDraftBlockedReason}</p>
                      )}
                      {canQueueVariation && variationDrafts.length > 0 && (
                        <div className="mt-3 rounded-lg border border-[var(--line)] bg-[var(--bg)] p-2">
                          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.1em] text-[var(--muted)]">
                            Queued Variations
                          </p>
                          <div className="grid gap-2">
                            {variationDrafts.map((draft) => (
                              <div key={draft.id} className="rounded-md border border-[var(--line)] p-2 text-xs" style={{ background: "var(--surface-2)" }}>
                                <div className="flex items-center justify-between gap-2">
                                  <p className="truncate text-[var(--ink)]">
                                    Parent: <span className="font-semibold">{draft.parentLabel}</span>
                                  </p>
                                  <div className="flex gap-1">
                                    <button
                                      type="button"
                                      onClick={() => loadVariationDraftToForm(draft.id)}
                                      disabled={creatingQueuedVariationBatch || savingProduct}
                                      className="rounded border border-[var(--line)] px-2 py-0.5 text-[10px] disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-3)", color: "var(--ink-light)" }}
                                    >
                                      Load To Form
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => removeVariationDraft(draft.id)}
                                      disabled={creatingQueuedVariationBatch || savingProduct}
                                      className="rounded border border-red-900/30 px-2 py-0.5 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(239,68,68,0.06)" }}
                                    >
                                      Remove
                                    </button>
                                  </div>
                                </div>
                                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                                  <input
                                    value={draft.payload.name}
                                    onChange={(e) => updateVariationDraftPayload(draft.id, { name: e.target.value })}
                                    placeholder="Variation name"
                                    className="rounded border border-[var(--line)] px-2 py-1.5"
                                  />
                                  <input
                                    value={draft.payload.slug}
                                    onChange={(e) =>
                                      updateVariationDraftPayload(draft.id, { slug: slugify(e.target.value).slice(0, 180) })
                                    }
                                    placeholder="Variation slug"
                                    className="rounded border border-[var(--line)] px-2 py-1.5"
                                  />
                                  <input
                                    value={draft.payload.sku}
                                    onChange={(e) => updateVariationDraftPayload(draft.id, { sku: e.target.value })}
                                    placeholder="Variation SKU"
                                    className="rounded border border-[var(--line)] px-2 py-1.5"
                                  />
                                  <input
                                    type="number"
                                    inputMode="decimal"
                                    step="0.01"
                                    min="0.01"
                                    value={draft.payload.regularPrice}
                                    onChange={(e) => {
                                      const next = Number(e.target.value);
                                      updateVariationDraftPayload(draft.id, { regularPrice: Number.isFinite(next) ? next : 0 });
                                    }}
                                    onWheel={preventNumberInputScroll}
                                    onKeyDown={preventNumberInputArrows}
                                    placeholder="Regular price"
                                    className="rounded border border-[var(--line)] px-2 py-1.5"
                                  />
                                  <input
                                    type="number"
                                    inputMode="decimal"
                                    step="0.01"
                                    min="0"
                                    value={draft.payload.discountedPrice === null ? "" : draft.payload.discountedPrice}
                                    onChange={(e) => {
                                      const raw = e.target.value.trim();
                                      if (!raw) {
                                        updateVariationDraftPayload(draft.id, { discountedPrice: null });
                                        return;
                                      }
                                      const next = Number(raw);
                                      updateVariationDraftPayload(draft.id, { discountedPrice: Number.isFinite(next) ? next : null });
                                    }}
                                    onWheel={preventNumberInputScroll}
                                    onKeyDown={preventNumberInputArrows}
                                    placeholder="Discounted price"
                                    className="rounded border border-[var(--line)] px-2 py-1.5"
                                  />
                                </div>
                                <label className="mt-2 inline-flex items-center gap-2 text-[11px] text-[var(--muted)]">
                                  <input
                                    type="checkbox"
                                    checked={draft.payload.active}
                                    onChange={(e) => updateVariationDraftPayload(draft.id, { active: e.target.checked })}
                                  />
                                  Active
                                </label>
                                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                                  {draft.payload.variations.map((pair) => (
                                    <input
                                      key={`${draft.id}-${pair.name}`}
                                      value={pair.value}
                                      onChange={(e) => updateVariationDraftAttributeValue(draft.id, pair.name, e.target.value)}
                                      placeholder={`${pair.name} (required)`}
                                      className="rounded border border-[var(--line)] px-2 py-1.5"
                                    />
                                  ))}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                  <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
                    <input type="checkbox" checked={form.active} onChange={(e) => setForm((o) => ({ ...o, active: e.target.checked }))} disabled={productMutationBusy} />
                    Active
                  </label>
                  <button
                    type="submit"
                    disabled={Boolean(priceValidationMessage) || savingProduct || creatingQueuedVariationBatch || uploadingImages || Boolean(selectingVariationParentId) || productSlugBlocked}
                    className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {savingProduct
                      ? "Saving..."
                      : form.productType === "VARIATION" && !form.id
                        ? "Add Child Variation"
                        : form.productType === "VARIATION" && form.id
                          ? "Update Variation"
                          : form.id
                            ? "Update Product"
                            : "Create Product"}
                  </button>
                </form>
              </section>

              <section className="card-surface rounded-2xl p-5">
                <h3 className="text-xl text-[var(--ink)]">Category Operations</h3>
                <p className="mt-1 text-xs text-[var(--muted)]">
                  One unique category name, with parent and sub hierarchy.
                </p>

                <div className="mt-3 grid gap-2 text-sm">
                  <input
                    value={categoryForm.name}
                    onChange={(e) => {
                      const value = e.target.value;
                      setCategoryForm((o) => ({ ...o, name: value }));
                    }}
                    placeholder="Category name"
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={categoryMutationBusy}
                  />
                  <div>
                    <input
                      value={categoryForm.slug}
                      onChange={(e) => {
                        const value = slugify(e.target.value).slice(0, 130);
                        setCategorySlugTouched(true);
                        setCategoryForm((o) => ({ ...o, slug: value }));
                      }}
                      placeholder="Category slug"
                      className="w-full rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={categoryMutationBusy}
                    />
                    <p
                      className={`mt-1 text-[11px] ${categorySlugStatus === "taken" || categorySlugStatus === "invalid"
                        ? "text-red-600"
                        : categorySlugStatus === "available"
                          ? "text-emerald-600"
                          : "text-[var(--muted)]"
                        }`}
                    >
                      {categorySlugStatus === "checking" && "Checking slug..."}
                      {categorySlugStatus === "available" && "Slug is available"}
                      {categorySlugStatus === "taken" && "Slug is already taken"}
                      {categorySlugStatus === "invalid" && "Enter a valid slug"}
                      {categorySlugStatus === "idle" && "Slug will be used in category URL"}
                    </p>
                  </div>
                  <select
                    value={categoryForm.type}
                    onChange={(e) =>
                      setCategoryForm((o) => ({
                        ...o,
                        type: e.target.value as "PARENT" | "SUB",
                        parentCategoryId: e.target.value === "SUB" ? o.parentCategoryId : "",
                      }))
                    }
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
                    disabled={categoryMutationBusy}
                  >
                    <option value="PARENT">PARENT</option>
                    <option value="SUB">SUB</option>
                  </select>
                  {categoryForm.type === "SUB" && (
                    <select
                      value={categoryForm.parentCategoryId}
                      onChange={(e) => setCategoryForm((o) => ({ ...o, parentCategoryId: e.target.value }))}
                      className="rounded-lg border border-[var(--line)] px-3 py-2"
                      disabled={categoryMutationBusy}
                    >
                      <option value="">Select parent category</option>
                      {parentCategories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                    </select>
                  )}
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => void saveCategory()}
                      disabled={categoryMutationBusy || categorySlugBlocked}
                      className="btn-brand rounded-lg px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {savingCategory ? "Saving..." : categoryForm.id ? "Update Category" : "Create Category"}
                    </button>
                    {categoryForm.id && (
                      <button
                        type="button"
                        onClick={() => {
                          setCategoryForm({ name: "", slug: "", type: "PARENT", parentCategoryId: "" });
                          setCategorySlugTouched(false);
                          setCategorySlugStatus("idle");
                        }}
                        disabled={categoryMutationBusy}
                        className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                      >
                        Reset
                      </button>
                    )}
                  </div>
                </div>

                <div className="mt-4">
                  <p className="text-xs tracking-[0.2em] text-[var(--muted)]">ACTIVE CATEGORIES</p>
                  <div className="mt-2 max-h-40 overflow-auto rounded-lg border border-[var(--line)] p-2">
                    {categories.map((c) => (
                      <div key={c.id} className="mb-1 flex items-center justify-between rounded-md px-2 py-1 hover:bg-[var(--brand-soft)]">
                        <span className="text-xs text-[var(--ink)]">
                          {c.name} ({c.type}) - /{c.slug}
                        </span>
                        <div className="flex gap-1">
                          <button
                            type="button"
                            onClick={() => {
                              setCategoryForm({
                                id: c.id,
                                name: c.name,
                                slug: c.slug || "",
                                type: c.type,
                                parentCategoryId: c.parentCategoryId || "",
                              });
                              setCategorySlugTouched(true);
                              setCategorySlugStatus("available");
                            }
                            }
                            disabled={categoryMutationBusy}
                            className="rounded border border-[var(--line)] px-2 py-0.5 text-[10px] disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setConfirmAction({ type: "category", id: c.id, name: c.name });
                            }}
                            disabled={categoryMutationBusy}
                            className="rounded border border-red-900/30 px-2 py-0.5 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60" style={{ background: "rgba(239,68,68,0.06)" }}
                          >
                            Delete
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-4">
                  <p className="text-xs tracking-[0.2em] text-[var(--muted)]">DELETED CATEGORIES</p>
                  <div className="mt-2 max-h-32 overflow-auto rounded-lg border border-[var(--line)] p-2">
                    {deletedCategories.length === 0 && <p className="text-xs text-[var(--muted)]">No deleted categories.</p>}
                    {deletedCategories.map((c) => (
                      <div key={c.id} className="mb-1 flex items-center justify-between rounded-md px-2 py-1">
                        <span className="text-xs text-[var(--ink)]">
                          {c.name} ({c.type}) - /{c.slug}
                        </span>
                        <button
                          type="button"
                          onClick={() => {
                            void restoreCategory(c.id);
                          }}
                          disabled={categoryMutationBusy}
                          className="rounded border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {restoringCategoryId === c.id ? "Restoring..." : "Restore"}
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
            </div>
          </div>

          <p className="mt-5 text-xs text-[var(--muted)]">{status}</p>
        </section>
      </main>

      <ConfirmModal
        open={confirmAction !== null}
        title={confirmAction?.type === "product" ? "Delete Product" : "Delete Category"}
        message={`Are you sure you want to delete "${confirmAction?.name ?? ""}"? This action can be reversed from the deleted items list.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        danger
        loading={confirmLoading}
        onCancel={() => setConfirmAction(null)}
        onConfirm={async () => {
          if (!confirmAction) return;
          setConfirmLoading(true);
          try {
            if (confirmAction.type === "product") {
              await softDelete(confirmAction.id);
            } else {
              await deleteCategory(confirmAction.id);
            }
          } finally {
            setConfirmLoading(false);
            setConfirmAction(null);
          }
        }}
      />

      <Footer />
    </div>
  );
}
