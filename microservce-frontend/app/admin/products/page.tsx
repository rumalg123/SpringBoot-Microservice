"use client";

import { ChangeEvent, DragEvent, FormEvent, KeyboardEvent, WheelEvent, useCallback, useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import toast from "react-hot-toast";
import ConfirmModal from "../../components/ConfirmModal";
import { resolveImageUrl } from "../../../lib/image";
import ExportButton from "../../components/ui/ExportButton";
import CategoryOperationsPanel from "../../components/admin/products/CategoryOperationsPanel";
import ProductCatalogPanel from "../../components/admin/products/ProductCatalogPanel";
import ProductEditorPanel from "../../components/admin/products/ProductEditorPanel";
import { useAuthSession } from "../../../lib/authSession";

type BulkOperationResult = {
  totalRequested: number;
  successCount: number;
  failureCount: number;
  errors: string[];
};

type ImportResult = {
  totalRows: number;
  successCount: number;
  failureCount: number;
  errors: string[];
};

type ProductType = "SINGLE" | "PARENT" | "VARIATION";

type ApprovalStatus = "NOT_REQUIRED" | "DRAFT" | "PENDING" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

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
  approvalStatus?: ApprovalStatus;
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
  approvalStatus?: ApprovalStatus;
};

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type VendorSummary = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  active: boolean;
  deleted: boolean;
  status?: string;
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
  const session = useAuthSession();
  const [activePage, setActivePage] = useState<PagedResponse<ProductSummary> | null>(null);
  const [deletedPage, setDeletedPage] = useState<PagedResponse<ProductSummary> | null>(null);
  const [page, setPage] = useState(0);
  const [deletedPageIndex, setDeletedPageIndex] = useState(0);
  const [q, setQ] = useState("");
  const [sku, setSku] = useState("");
  const [category, setCategory] = useState("");
  const [vendorFilterId, setVendorFilterId] = useState("");
  const [vendorFilterSearch, setVendorFilterSearch] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [deletedCategories, setDeletedCategories] = useState<Category[]>([]);
  const [vendors, setVendors] = useState<VendorSummary[]>([]);
  const [type, setType] = useState<ProductType | "">("");
  const [approvalStatusFilter, setApprovalStatusFilter] = useState<ApprovalStatus | "">("");
  const [activeFilter, setActiveFilter] = useState<"" | "true" | "false">("");
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
  const [loadingVendors, setLoadingVendors] = useState(false);

  /* ---- Bulk operations & import/export state ---- */
  const [selectedProductIds, setSelectedProductIds] = useState<string[]>([]);
  const [importingCsv, setImportingCsv] = useState(false);
  const [bulkDeleting, setBulkDeleting] = useState(false);
  const [bulkPriceUpdating, setBulkPriceUpdating] = useState(false);
  const [bulkReassigning, setBulkReassigning] = useState(false);
  const [showBulkPriceModal, setShowBulkPriceModal] = useState(false);
  const [showBulkCategoryModal, setShowBulkCategoryModal] = useState(false);
  const [showBulkDeleteConfirm, setShowBulkDeleteConfirm] = useState(false);
  const [bulkRegularPrice, setBulkRegularPrice] = useState("");
  const [bulkDiscountedPrice, setBulkDiscountedPrice] = useState("");
  const [bulkTargetCategoryId, setBulkTargetCategoryId] = useState("");
  const importFileRef = useRef<HTMLInputElement>(null);

  /* ---- Approval workflow state ---- */
  const [approvingProductId, setApprovingProductId] = useState<string | null>(null);
  const [rejectingProductId, setRejectingProductId] = useState<string | null>(null);
  const [submitForReviewProductId, setSubmitForReviewProductId] = useState<string | null>(null);
  const [showRejectModal, setShowRejectModal] = useState<{ id: string; name: string } | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const canManageCategories = session.canManageAdminCategories;
  const canSelectAnyVendor = session.isSuperAdmin || session.isPlatformStaff;

  const loadCategories = useCallback(async () => {
    if (!session.apiClient) return;
    const res = await session.apiClient.get(canManageCategories ? "/admin/categories" : "/categories");
    setCategories((res.data as Category[]) || []);
  }, [session.apiClient, canManageCategories]);

  const loadDeletedCategories = useCallback(async () => {
    if (!session.apiClient) return;
    if (!canManageCategories) {
      setDeletedCategories([]);
      return;
    }
    const res = await session.apiClient.get("/admin/categories/deleted");
    setDeletedCategories((res.data as Category[]) || []);
  }, [session.apiClient, canManageCategories]);

  const loadVendors = useCallback(async () => {
    if (!session.apiClient) return;
    if (!canSelectAnyVendor) {
      setVendors([]);
      return;
    }
    setLoadingVendors(true);
    try {
      const res = await session.apiClient.get("/admin/vendors");
      const vendorRows = ((res.data as VendorSummary[]) || [])
        .filter((vendor) => vendor && !vendor.deleted)
        .sort((a, b) => a.name.localeCompare(b.name));
      setVendors(vendorRows);
    } catch (err) {
      setVendors([]);
      const message = err instanceof Error ? err.message : "Failed to load vendors";
      toast.error(message);
    } finally {
      setLoadingVendors(false);
    }
  }, [session.apiClient, canSelectAnyVendor]);

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
        if (vendorFilterId.trim()) params.set("vendorId", vendorFilterId.trim());
        if (approvalStatusFilter) {
          const backendStatus = approvalStatusFilter === "PENDING" ? "PENDING_REVIEW" : approvalStatusFilter === "NOT_REQUIRED" ? "DRAFT" : approvalStatusFilter;
          params.set("approvalStatus", backendStatus);
        }
        if (activeFilter) params.set("active", activeFilter);

        const res = await session.apiClient.get(`/admin/products?${params.toString()}`);
        setActivePage(res.data as PagedResponse<ProductSummary>);
        setPage(targetPage);
      } finally {
        setLoadingActiveList(false);
      }
    },
    [session.apiClient, q, sku, category, type, vendorFilterId, approvalStatusFilter, activeFilter]
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
        if (vendorFilterId.trim()) params.set("vendorId", vendorFilterId.trim());

        const res = await session.apiClient.get(`/admin/products/deleted?${params.toString()}`);
        setDeletedPage(res.data as PagedResponse<ProductSummary>);
        setDeletedPageIndex(targetPage);
      } finally {
        setLoadingDeletedList(false);
      }
    },
    [session.apiClient, q, sku, category, type, vendorFilterId]
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
      const res = await session.apiClient.get(`/admin/products?${params.toString()}`);
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

    const run = async () => {
      try {
        const startupTasks: Promise<unknown>[] = [loadActive(0), loadDeleted(0), loadCategories(), loadParentProducts()];
        if (canManageCategories || canSelectAnyVendor) {
          if (canManageCategories) startupTasks.push(loadDeletedCategories());
          if (canSelectAnyVendor) startupTasks.push(loadVendors());
        }
        await Promise.all(startupTasks);
        setStatus("Admin product catalog loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load products.");
      }
    };
    void run();
  }, [session.status, session.canManageAdminProducts, canManageCategories, canSelectAnyVendor, loadActive, loadDeleted, loadCategories, loadDeletedCategories, loadParentProducts, loadVendors]);

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
        vendorId: parent.vendorId || old.vendorId,
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

  /* ---- Selection helpers ---- */
  const toggleProductSelection = (id: string) => {
    setSelectedProductIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const toggleSelectAllCurrentPage = () => {
    const currentRows = showDeleted ? deletedPage?.content || [] : activePage?.content || [];
    const currentIds = currentRows.map((p) => p.id);
    const allSelected = currentIds.length > 0 && currentIds.every((id) => selectedProductIds.includes(id));
    if (allSelected) {
      setSelectedProductIds((prev) => prev.filter((id) => !currentIds.includes(id)));
    } else {
      setSelectedProductIds((prev) => {
        const set = new Set(prev);
        currentIds.forEach((id) => set.add(id));
        return Array.from(set);
      });
    }
  };

  /* ---- CSV Import handler ---- */
  const handleCsvImport = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !session.apiClient) return;
    setImportingCsv(true);
    setStatus("Importing products from CSV...");
    try {
      const formData = new FormData();
      formData.append("file", file);
      const res = await session.apiClient.post("/admin/products/import", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      const result = res.data as ImportResult;
      const successMsg = `Import complete: ${result.successCount}/${result.totalRows} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${successMsg}, ${result.failureCount} failed`);
        if (result.errors?.length) {
          result.errors.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
        }
      } else {
        toast.success(successMsg);
      }
      setStatus(successMsg);
      await Promise.all([loadActive(0), loadDeleted(0), loadParentProducts()]);
    } catch (err) {
      const message = err instanceof Error ? err.message : "CSV import failed";
      setStatus(message);
      toast.error(message);
    } finally {
      setImportingCsv(false);
      if (importFileRef.current) importFileRef.current.value = "";
    }
  };

  /* ---- Bulk Delete handler ---- */
  const handleBulkDelete = async () => {
    if (!session.apiClient || selectedProductIds.length === 0) return;
    setBulkDeleting(true);
    setStatus(`Bulk deleting ${selectedProductIds.length} product(s)...`);
    try {
      const res = await session.apiClient.post("/admin/products/bulk-delete", {
        productIds: selectedProductIds,
      });
      const result = res.data as BulkOperationResult;
      const msg = `Bulk delete: ${result.successCount}/${result.totalRequested} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${msg}, ${result.failureCount} failed`);
        result.errors?.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
      } else {
        toast.success(msg);
      }
      setStatus(msg);
      setSelectedProductIds([]);
      await Promise.all([loadActive(0), loadDeleted(0), loadParentProducts()]);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Bulk delete failed";
      setStatus(message);
      toast.error(message);
    } finally {
      setBulkDeleting(false);
      setShowBulkDeleteConfirm(false);
    }
  };

  /* ---- Bulk Price Update handler ---- */
  const handleBulkPriceUpdate = async () => {
    if (!session.apiClient || selectedProductIds.length === 0) return;
    const regularPrice = Number(bulkRegularPrice);
    if (!Number.isFinite(regularPrice) || regularPrice <= 0) {
      toast.error("Regular price must be greater than 0");
      return;
    }
    const discountedPrice = bulkDiscountedPrice.trim() ? Number(bulkDiscountedPrice) : undefined;
    if (discountedPrice !== undefined && (!Number.isFinite(discountedPrice) || discountedPrice < 0)) {
      toast.error("Discounted price must be 0 or greater");
      return;
    }
    if (discountedPrice !== undefined && discountedPrice > regularPrice) {
      toast.error("Discounted price cannot exceed regular price");
      return;
    }
    setBulkPriceUpdating(true);
    setStatus(`Updating prices for ${selectedProductIds.length} product(s)...`);
    try {
      const items = selectedProductIds.map((productId) => ({
        productId,
        regularPrice,
        ...(discountedPrice !== undefined ? { discountedPrice } : {}),
      }));
      const res = await session.apiClient.post("/admin/products/bulk-price-update", { items });
      const result = res.data as BulkOperationResult;
      const msg = `Price update: ${result.successCount}/${result.totalRequested} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${msg}, ${result.failureCount} failed`);
        result.errors?.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
      } else {
        toast.success(msg);
      }
      setStatus(msg);
      setSelectedProductIds([]);
      setShowBulkPriceModal(false);
      setBulkRegularPrice("");
      setBulkDiscountedPrice("");
      await Promise.all([loadActive(page), loadDeleted(deletedPageIndex)]);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Bulk price update failed";
      setStatus(message);
      toast.error(message);
    } finally {
      setBulkPriceUpdating(false);
    }
  };

  /* ---- Bulk Category Reassign handler ---- */
  const handleBulkCategoryReassign = async () => {
    if (!session.apiClient || selectedProductIds.length === 0) return;
    if (!bulkTargetCategoryId.trim()) {
      toast.error("Select a target category");
      return;
    }
    setBulkReassigning(true);
    setStatus(`Reassigning ${selectedProductIds.length} product(s) to category...`);
    try {
      const res = await session.apiClient.post("/admin/products/bulk-category-reassign", {
        productIds: selectedProductIds,
        targetCategoryId: bulkTargetCategoryId.trim(),
      });
      const result = res.data as BulkOperationResult;
      const msg = `Category reassign: ${result.successCount}/${result.totalRequested} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${msg}, ${result.failureCount} failed`);
        result.errors?.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
      } else {
        toast.success(msg);
      }
      setStatus(msg);
      setSelectedProductIds([]);
      setShowBulkCategoryModal(false);
      setBulkTargetCategoryId("");
      await Promise.all([loadActive(page), loadDeleted(deletedPageIndex)]);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Bulk category reassign failed";
      setStatus(message);
      toast.error(message);
    } finally {
      setBulkReassigning(false);
    }
  };

  /* ---- Approval Workflow handlers ---- */
  const submitForReview = async (productId: string) => {
    if (!session.apiClient || submitForReviewProductId) return;
    setSubmitForReviewProductId(productId);
    try {
      await session.apiClient.post(`/admin/products/${productId}/submit-for-review`);
      toast.success("Product submitted for review");
      await reloadCurrentView();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to submit for review";
      toast.error(message);
    } finally {
      setSubmitForReviewProductId(null);
    }
  };

  const approveProduct = async (productId: string) => {
    if (!session.apiClient || approvingProductId) return;
    setApprovingProductId(productId);
    try {
      await session.apiClient.post(`/admin/products/${productId}/approve`);
      toast.success("Product approved");
      await reloadCurrentView();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to approve product";
      toast.error(message);
    } finally {
      setApprovingProductId(null);
    }
  };

  const rejectProduct = async (productId: string, reason: string) => {
    if (!session.apiClient || rejectingProductId) return;
    setRejectingProductId(productId);
    try {
      await session.apiClient.post(`/admin/products/${productId}/reject`, { reason });
      toast.success("Product rejected");
      setShowRejectModal(null);
      setRejectReason("");
      await reloadCurrentView();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to reject product";
      toast.error(message);
    } finally {
      setRejectingProductId(null);
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
  const bulkOperationBusy = bulkDeleting || bulkPriceUpdating || bulkReassigning || importingCsv;
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
    <>
      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumbs */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">Admin Products</span>
        </nav>

        <section className="animate-rise space-y-4 rounded-xl p-5" style={{ background: "rgba(17,17,40,0.7)", border: "1px solid rgba(0,212,255,0.1)", backdropFilter: "blur(16px)" }}>
          {/* ---- Import / Export Toolbar ---- */}
          <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 10, marginBottom: 4 }}>
            <ExportButton
              apiClient={session.apiClient}
              endpoint="/admin/products/export"
              filename={`products-export-${new Date().toISOString().slice(0, 10)}.csv`}
              label="Export CSV"
              params={{ format: "csv" }}
            />

            <input
              ref={importFileRef}
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => { void handleCsvImport(e); }}
              style={{ display: "none" }}
            />
            <button
              type="button"
              onClick={() => importFileRef.current?.click()}
              disabled={importingCsv || !session.apiClient}
              className="btn-outline"
              style={{ fontSize: "0.78rem", padding: "7px 14px", display: "inline-flex", alignItems: "center", gap: 6 }}
            >
              {importingCsv ? (
                <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid var(--brand)", borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.6s linear infinite" }} />
              ) : (
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              )}
              Import CSV
            </button>
          </div>

          {/* ---- Bulk Actions Bar (when products selected) ---- */}
          {selectedProductIds.length > 0 && !showDeleted && (
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                alignItems: "center",
                gap: 10,
                padding: "10px 14px",
                borderRadius: 12,
                background: "rgba(0,212,255,0.06)",
                border: "1px solid rgba(0,212,255,0.18)",
              }}
            >
              <span style={{ fontSize: "0.8rem", fontWeight: 600, color: "var(--brand, #00d4ff)" }}>
                {selectedProductIds.length} selected
              </span>

              <button
                type="button"
                onClick={() => setShowBulkDeleteConfirm(true)}
                disabled={bulkOperationBusy}
                style={{
                  fontSize: "0.78rem",
                  padding: "6px 12px",
                  borderRadius: 8,
                  border: "1px solid rgba(239,68,68,0.3)",
                  background: "rgba(239,68,68,0.08)",
                  color: "#f87171",
                  cursor: bulkOperationBusy ? "not-allowed" : "pointer",
                  opacity: bulkOperationBusy ? 0.5 : 1,
                }}
              >
                {bulkDeleting ? "Deleting..." : "Bulk Delete"}
              </button>

              <button
                type="button"
                onClick={() => { setBulkRegularPrice(""); setBulkDiscountedPrice(""); setShowBulkPriceModal(true); }}
                disabled={bulkOperationBusy}
                style={{
                  fontSize: "0.78rem",
                  padding: "6px 12px",
                  borderRadius: 8,
                  border: "1px solid rgba(0,212,255,0.2)",
                  background: "rgba(0,212,255,0.06)",
                  color: "var(--ink-light, #dfe7ff)",
                  cursor: bulkOperationBusy ? "not-allowed" : "pointer",
                  opacity: bulkOperationBusy ? 0.5 : 1,
                }}
              >
                {bulkPriceUpdating ? "Updating..." : "Bulk Price Update"}
              </button>

              <button
                type="button"
                onClick={() => { setBulkTargetCategoryId(""); setShowBulkCategoryModal(true); }}
                disabled={bulkOperationBusy}
                style={{
                  fontSize: "0.78rem",
                  padding: "6px 12px",
                  borderRadius: 8,
                  border: "1px solid rgba(124,58,237,0.25)",
                  background: "rgba(124,58,237,0.08)",
                  color: "#a78bfa",
                  cursor: bulkOperationBusy ? "not-allowed" : "pointer",
                  opacity: bulkOperationBusy ? 0.5 : 1,
                }}
              >
                {bulkReassigning ? "Reassigning..." : "Bulk Reassign Category"}
              </button>

              <button
                type="button"
                onClick={() => setSelectedProductIds([])}
                disabled={bulkOperationBusy}
                style={{
                  fontSize: "0.75rem",
                  padding: "5px 10px",
                  borderRadius: 8,
                  border: "1px solid var(--line, rgba(255,255,255,0.1))",
                  background: "transparent",
                  color: "var(--muted)",
                  cursor: "pointer",
                  marginLeft: "auto",
                }}
              >
                Clear selection
              </button>
            </div>
          )}

          <div className="grid gap-6 lg:grid-cols-[1.1fr,0.9fr]">
            <ProductCatalogPanel
              title={title}
              showDeleted={showDeleted}
              q={q}
              sku={sku}
              category={category}
              vendorId={vendorFilterId}
              vendorSearch={vendorFilterSearch}
              type={type}
              parentCategories={parentCategories}
              subCategories={subCategories}
              vendors={vendors}
              loadingVendors={loadingVendors}
              showVendorFilter={canSelectAnyVendor}
              rows={rows}
              pageMeta={pageMeta}
              currentPage={showDeleted ? deletedPageIndex : page}
              filtersSubmitting={filtersSubmitting}
              listLoading={listLoading}
              productRowActionBusy={productRowActionBusy}
              loadingProductId={loadingProductId}
              restoringProductId={restoringProductId}
              selectedProductIds={selectedProductIds}
              onToggleProductSelection={toggleProductSelection}
              onToggleSelectAllCurrentPage={toggleSelectAllCurrentPage}
              onShowActive={() => { setShowDeleted(false); setSelectedProductIds([]); }}
              onShowDeleted={() => { setShowDeleted(true); setSelectedProductIds([]); }}
              onQChange={setQ}
              onSkuChange={setSku}
              onCategoryChange={setCategory}
              onVendorIdChange={setVendorFilterId}
              onVendorSearchChange={setVendorFilterSearch}
              onTypeChange={setType}
              approvalStatus={approvalStatusFilter}
              onApprovalStatusChange={setApprovalStatusFilter}
              activeFilter={activeFilter}
              onActiveFilterChange={setActiveFilter}
              onApplyFilters={applyFilters}
              onEditProduct={(id) => loadToEdit(id)}
              onDeleteProductRequest={(product) => {
                setConfirmAction({ type: "product", id: product.id, name: product.name });
              }}
              onRestoreProduct={(id) => restore(id)}
              onPageChange={(nextPage) => {
                if (showDeleted) {
                  return loadDeleted(nextPage);
                }
                return loadActive(nextPage);
              }}
              /* Approval workflow */
              canApproveReject={canSelectAnyVendor}
              approvingProductId={approvingProductId}
              rejectingProductId={rejectingProductId}
              submitForReviewProductId={submitForReviewProductId}
              onSubmitForReview={(id) => submitForReview(id)}
              onApproveProduct={(id) => approveProduct(id)}
              onRejectProductRequest={(product) => {
                setShowRejectModal({ id: product.id, name: product.name });
                setRejectReason("");
              }}
            />

            <div className="order-1 space-y-4 lg:order-2">
              <ProductEditorPanel
                state={{
                  form,
                  emptyForm,
                  productMutationBusy,
                  isEditingProduct,
                  isEditingVariation,
                  productSlugStatus,
                  parentAttributeNames,
                  newParentAttributeName,
                  uploadingImages,
                  parentCategories,
                  subCategoryOptions,
                  vendors,
                  loadingVendors,
                  priceValidationMessage,
                  parentSearch,
                  loadingParentProducts,
                  parentProducts,
                  filteredParentProducts,
                  variationParentId,
                  selectingVariationParentId,
                  selectedVariationParent,
                  parentVariationAttributes,
                  variationAttributeValues,
                  canQueueVariation,
                  canAddVariationDraft,
                  canCreateQueuedVariations,
                  variationDrafts,
                  creatingQueuedVariationBatch,
                  variationDraftBlockedReason,
                  productSlugBlocked,
                  savingProduct,
                }}
                actions={{
                  submitProduct,
                  setForm,
                  setProductSlugTouched,
                  setProductSlugStatus,
                  setParentAttributeNames,
                  setNewParentAttributeName,
                  setVariationParentId,
                  setSelectedVariationParent,
                  setParentSearch,
                  setParentVariationAttributes,
                  setVariationAttributeValues,
                  setVariationDrafts,
                  uploadImages,
                  setDragImageIndex,
                  onImageDrop,
                  removeImage,
                  addParentAttribute,
                  removeParentAttribute,
                  refreshVendors: loadVendors,
                  refreshVariationParents,
                  onSelectVariationParent,
                  addVariationDraft,
                  createQueuedVariations,
                  loadVariationDraftToForm,
                  removeVariationDraft,
                  updateVariationDraftPayload,
                  updateVariationDraftAttributeValue,
                }}
                helpers={{
                  slugify,
                  resolveImageUrl,
                  MAX_IMAGE_COUNT,
                  canSelectVendor: canSelectAnyVendor,
                  preventNumberInputScroll,
                  preventNumberInputArrows,
                }}
              />

              {canManageCategories && (
                <CategoryOperationsPanel
                  categoryForm={categoryForm}
                  categorySlugStatus={categorySlugStatus}
                  categorySlugBlocked={categorySlugBlocked}
                  categoryMutationBusy={categoryMutationBusy}
                  savingCategory={savingCategory}
                  restoringCategoryId={restoringCategoryId}
                  categories={categories}
                  deletedCategories={deletedCategories}
                  parentCategories={parentCategories}
                  normalizeSlug={(value) => slugify(value).slice(0, 130)}
                  onCategoryFormNameChange={(value) => {
                    setCategoryForm((o) => ({ ...o, name: value }));
                  }}
                  onCategoryFormSlugChange={(value) => {
                    setCategorySlugTouched(true);
                    setCategoryForm((o) => ({ ...o, slug: value }));
                  }}
                  onCategoryFormTypeChange={(value) => {
                    setCategoryForm((o) => ({
                      ...o,
                      type: value,
                      parentCategoryId: value === "SUB" ? o.parentCategoryId : "",
                    }));
                  }}
                  onCategoryFormParentChange={(value) => {
                    setCategoryForm((o) => ({ ...o, parentCategoryId: value }));
                  }}
                  onSaveCategory={() => saveCategory()}
                  onResetCategoryForm={() => {
                    setCategoryForm({ name: "", slug: "", type: "PARENT", parentCategoryId: "" });
                    setCategorySlugTouched(false);
                    setCategorySlugStatus("idle");
                  }}
                  onEditCategory={(c) => {
                    setCategoryForm({
                      id: c.id,
                      name: c.name,
                      slug: c.slug || "",
                      type: c.type,
                      parentCategoryId: c.parentCategoryId || "",
                    });
                    setCategorySlugTouched(true);
                    setCategorySlugStatus("available");
                  }}
                  onDeleteCategoryRequest={(c) => {
                    setConfirmAction({ type: "category", id: c.id, name: c.name });
                  }}
                  onRestoreCategory={(id) => restoreCategory(id)}
                />
              )}
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

      {/* ---- Bulk Delete Confirm Modal ---- */}
      <ConfirmModal
        open={showBulkDeleteConfirm}
        title="Bulk Delete Products"
        message={`Are you sure you want to delete ${selectedProductIds.length} product(s)? This action can be reversed from the deleted items list.`}
        confirmLabel={bulkDeleting ? "Deleting..." : "Delete All"}
        cancelLabel="Cancel"
        danger
        loading={bulkDeleting}
        onCancel={() => setShowBulkDeleteConfirm(false)}
        onConfirm={() => { void handleBulkDelete(); }}
      />

      {/* ---- Bulk Price Update Modal ---- */}
      {showBulkPriceModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 1000,
            display: "grid",
            placeItems: "center",
            background: "rgba(0,0,0,0.6)",
            backdropFilter: "blur(4px)",
          }}
          onClick={() => { if (!bulkPriceUpdating) setShowBulkPriceModal(false); }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: "var(--surface, #16162a)",
              border: "1px solid var(--line, rgba(255,255,255,0.1))",
              borderRadius: 16,
              padding: "24px 28px",
              width: "100%",
              maxWidth: 420,
              color: "var(--ink, #fff)",
            }}
          >
            <h3 style={{ fontSize: "1.1rem", fontWeight: 700, marginBottom: 4, color: "var(--ink, #fff)" }}>
              Bulk Price Update
            </h3>
            <p style={{ fontSize: "0.8rem", color: "var(--muted)", marginBottom: 16 }}>
              Update prices for {selectedProductIds.length} selected product(s).
            </p>

            <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "var(--ink-light, #dfe7ff)", marginBottom: 4 }}>
              Regular Price *
            </label>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={bulkRegularPrice}
              onChange={(e) => setBulkRegularPrice(e.target.value)}
              placeholder="e.g. 29.99"
              style={{
                width: "100%",
                padding: "10px 14px",
                borderRadius: 10,
                border: "1px solid var(--line, rgba(255,255,255,0.12))",
                background: "var(--surface-2, rgba(255,255,255,0.04))",
                color: "var(--ink, #fff)",
                fontSize: "0.9rem",
                marginBottom: 12,
                outline: "none",
              }}
            />

            <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "var(--ink-light, #dfe7ff)", marginBottom: 4 }}>
              Discounted Price (optional)
            </label>
            <input
              type="number"
              min="0"
              step="0.01"
              value={bulkDiscountedPrice}
              onChange={(e) => setBulkDiscountedPrice(e.target.value)}
              placeholder="Leave blank to keep unchanged"
              style={{
                width: "100%",
                padding: "10px 14px",
                borderRadius: 10,
                border: "1px solid var(--line, rgba(255,255,255,0.12))",
                background: "var(--surface-2, rgba(255,255,255,0.04))",
                color: "var(--ink, #fff)",
                fontSize: "0.9rem",
                marginBottom: 20,
                outline: "none",
              }}
            />

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button
                type="button"
                onClick={() => setShowBulkPriceModal(false)}
                disabled={bulkPriceUpdating}
                className="btn-outline"
                style={{ fontSize: "0.82rem", padding: "8px 16px" }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => { void handleBulkPriceUpdate(); }}
                disabled={bulkPriceUpdating || !bulkRegularPrice.trim()}
                className="btn-primary"
                style={{ fontSize: "0.82rem", padding: "8px 16px", display: "inline-flex", alignItems: "center", gap: 6 }}
              >
                {bulkPriceUpdating && (
                  <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid #fff", borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.6s linear infinite" }} />
                )}
                {bulkPriceUpdating ? "Updating..." : "Update Prices"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ---- Bulk Category Reassign Modal ---- */}
      {showBulkCategoryModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 1000,
            display: "grid",
            placeItems: "center",
            background: "rgba(0,0,0,0.6)",
            backdropFilter: "blur(4px)",
          }}
          onClick={() => { if (!bulkReassigning) setShowBulkCategoryModal(false); }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: "var(--surface, #16162a)",
              border: "1px solid var(--line, rgba(255,255,255,0.1))",
              borderRadius: 16,
              padding: "24px 28px",
              width: "100%",
              maxWidth: 420,
              color: "var(--ink, #fff)",
            }}
          >
            <h3 style={{ fontSize: "1.1rem", fontWeight: 700, marginBottom: 4, color: "var(--ink, #fff)" }}>
              Bulk Reassign Category
            </h3>
            <p style={{ fontSize: "0.8rem", color: "var(--muted)", marginBottom: 16 }}>
              Reassign {selectedProductIds.length} selected product(s) to a new category.
            </p>

            <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, color: "var(--ink-light, #dfe7ff)", marginBottom: 4 }}>
              Target Category *
            </label>
            <select
              value={bulkTargetCategoryId}
              onChange={(e) => setBulkTargetCategoryId(e.target.value)}
              style={{
                width: "100%",
                padding: "10px 14px",
                borderRadius: 10,
                border: "1px solid var(--line, rgba(255,255,255,0.12))",
                background: "var(--surface-2, rgba(255,255,255,0.04))",
                color: "var(--ink, #fff)",
                fontSize: "0.9rem",
                marginBottom: 20,
                outline: "none",
              }}
            >
              <option value="">-- Select a category --</option>
              {categories.filter((c) => c.type === "PARENT").length > 0 && (
                <optgroup label="Main Categories">
                  {categories
                    .filter((c) => c.type === "PARENT")
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                </optgroup>
              )}
              {categories.filter((c) => c.type === "SUB").length > 0 && (
                <optgroup label="Sub Categories">
                  {categories
                    .filter((c) => c.type === "SUB")
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                </optgroup>
              )}
            </select>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button
                type="button"
                onClick={() => setShowBulkCategoryModal(false)}
                disabled={bulkReassigning}
                className="btn-outline"
                style={{ fontSize: "0.82rem", padding: "8px 16px" }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => { void handleBulkCategoryReassign(); }}
                disabled={bulkReassigning || !bulkTargetCategoryId.trim()}
                className="btn-primary"
                style={{ fontSize: "0.82rem", padding: "8px 16px", display: "inline-flex", alignItems: "center", gap: 6 }}
              >
                {bulkReassigning && (
                  <span style={{ display: "inline-block", width: 14, height: 14, border: "2px solid #fff", borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.6s linear infinite" }} />
                )}
                {bulkReassigning ? "Reassigning..." : "Reassign Category"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ---- Reject Product Modal ---- */}
      <ConfirmModal
        open={showRejectModal !== null}
        title="Reject Product"
        message={`Are you sure you want to reject "${showRejectModal?.name ?? ""}"? Provide a reason below.`}
        confirmLabel={rejectingProductId ? "Rejecting..." : "Reject"}
        cancelLabel="Cancel"
        danger
        loading={Boolean(rejectingProductId)}
        reasonEnabled
        reasonLabel="Rejection Reason *"
        reasonPlaceholder="Enter a reason for rejecting this product..."
        reasonValue={rejectReason}
        onReasonChange={setRejectReason}
        onCancel={() => {
          setShowRejectModal(null);
          setRejectReason("");
        }}
        onConfirm={() => {
          if (!showRejectModal) return;
          if (!rejectReason.trim()) {
            toast.error("Rejection reason is required");
            return;
          }
          void rejectProduct(showRejectModal.id, rejectReason.trim());
        }}
      />

    </>
  );
}




