"use client";

import { ChangeEvent, DragEvent, FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import { useAuthSession } from "../../../lib/authSession";

type ProductType = "SINGLE" | "PARENT" | "VARIATION";

type ProductSummary = {
  id: string;
  name: string;
  shortDescription: string;
  mainImage: string | null;
  sellingPrice: number;
  sku: string;
  productType: ProductType;
  vendorId: string;
  categories: string[];
  active: boolean;
};

type ProductDetail = {
  id: string;
  parentProductId: string | null;
  name: string;
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
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type PagedResponse<T> = {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
};

type ProductFormState = {
  id?: string;
  name: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: string;
  discountedPrice: string;
  vendorId: string;
  mainCategoryName: string;
  subCategoryNames: string[];
  productType: ProductType;
  variationsCsv: string;
  sku: string;
  active: boolean;
};

const emptyForm: ProductFormState = {
  name: "",
  shortDescription: "",
  description: "",
  images: [],
  regularPrice: "",
  discountedPrice: "",
  vendorId: "",
  mainCategoryName: "",
  subCategoryNames: [],
  productType: "SINGLE",
  variationsCsv: "",
  sku: "",
  active: true,
};

function parseCsv(value: string): string[] {
  return value
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);
}

const MAX_IMAGE_COUNT = 5;
const MAX_IMAGE_SIZE_BYTES = 1_048_576;
const MAX_IMAGE_DIMENSION = 540;

function resolveImageUrl(imageName: string): string | null {
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (!base) return null;
  return `${base.replace(/\/+$/, "")}/${imageName.replace(/^\/+/, "")}`;
}

async function validateImageFile(file: File): Promise<void> {
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    throw new Error(`${file.name} exceeds 1MB`);
  }
  const dimensions = await new Promise<{ width: number; height: number }>((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
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
    throw new Error(`${file.name} must be at most 540x540`);
  }
}

function parseVariations(value: string) {
  return parseCsv(value)
    .map((entry) => {
      const [name, ...rest] = entry.split(":");
      const variationName = (name || "").trim();
      const variationValue = rest.join(":").trim();
      if (!variationName || !variationValue) return null;
      return { name: variationName, value: variationValue };
    })
    .filter((v): v is { name: string; value: string } => Boolean(v));
}

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
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
  const [uploadingImages, setUploadingImages] = useState(false);
  const [dragImageIndex, setDragImageIndex] = useState<number | null>(null);
  const [variationParentId, setVariationParentId] = useState("");
  const [categoryForm, setCategoryForm] = useState<{ id?: string; name: string; type: "PARENT" | "SUB"; parentCategoryId: string }>({
    name: "",
    type: "PARENT",
    parentCategoryId: "",
  });

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
      const params = new URLSearchParams();
      params.set("page", String(targetPage));
      params.set("size", "12");
      params.set("sort", "createdAt,DESC");
      if (q.trim()) params.set("q", q.trim());
      if (sku.trim()) params.set("sku", sku.trim());
      if (category.trim()) params.set("category", category.trim());
      if (type) params.set("type", type);

      const res = await session.apiClient.get(`/products?${params.toString()}`);
      setActivePage(res.data as PagedResponse<ProductSummary>);
      setPage(targetPage);
    },
    [session.apiClient, q, sku, category, type]
  );

  const loadDeleted = useCallback(
    async (targetPage: number) => {
      if (!session.apiClient) return;
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
    },
    [session.apiClient, q, sku, category, type]
  );

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
        await Promise.all([loadActive(0), loadDeleted(0), loadCategories(), loadDeletedCategories()]);
        setStatus("Admin product catalog loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load products.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.canViewAdmin, loadActive, loadDeleted, loadCategories, loadDeletedCategories]);

  const applyFilters = async (e: FormEvent) => {
    e.preventDefault();
    setStatus("Applying filters...");
    try {
      await Promise.all([loadActive(0), loadDeleted(0)]);
      setStatus("Filters applied.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to filter.");
    }
  };

  const loadToEdit = async (id: string) => {
    if (!session.apiClient) return;
    setStatus("Loading product into editor...");
    try {
      const res = await session.apiClient.get(`/products/${id}`);
      const p = res.data as ProductDetail;
      setForm({
        id: p.id,
        name: p.name,
        shortDescription: p.shortDescription,
        description: p.description,
        images: p.images,
        regularPrice: String(p.regularPrice),
        discountedPrice: p.discountedPrice === null ? "" : String(p.discountedPrice),
        vendorId: p.vendorId,
        mainCategoryName: p.mainCategory || "",
        subCategoryNames: p.subCategories || [],
        productType: p.productType,
        variationsCsv: p.variations.map((v) => `${v.name}:${v.value}`).join(", "),
        sku: p.sku,
        active: p.active,
      });
      setStatus("Editor loaded.");
      toast.success("Product loaded in editor");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load product details.");
      toast.error(err instanceof Error ? err.message : "Failed to load product details");
    }
  };

  const submitProduct = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient) return;
    if (form.images.length === 0) {
      toast.error("Upload at least one image");
      return;
    }

    const payload = {
      name: form.name.trim(),
      shortDescription: form.shortDescription.trim(),
      description: form.description.trim(),
      images: form.images,
      regularPrice: Number(form.regularPrice),
      discountedPrice: form.discountedPrice.trim() ? Number(form.discountedPrice) : null,
      vendorId: form.vendorId.trim() ? form.vendorId.trim() : null,
      categories: [form.mainCategoryName, ...form.subCategoryNames].filter(Boolean),
      productType: form.productType,
      variations: parseVariations(form.variationsCsv),
      sku: form.sku.trim(),
      active: form.active,
    };

    setStatus(form.id ? "Updating product..." : "Creating product...");
    try {
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
      await Promise.all([loadActive(0), loadDeleted(0)]);
      setShowDeleted(false);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Save failed.");
      toast.error(err instanceof Error ? err.message : "Save failed");
    }
  };

  const createVariation = async () => {
    if (!session.apiClient || !variationParentId.trim()) return;
    if (form.images.length === 0) {
      toast.error("Upload at least one image");
      return;
    }
    const payload = {
      name: form.name.trim(),
      shortDescription: form.shortDescription.trim(),
      description: form.description.trim(),
      images: form.images,
      regularPrice: Number(form.regularPrice),
      discountedPrice: form.discountedPrice.trim() ? Number(form.discountedPrice) : null,
      vendorId: form.vendorId.trim() ? form.vendorId.trim() : null,
      categories: [form.mainCategoryName, ...form.subCategoryNames].filter(Boolean),
      productType: "VARIATION",
      variations: parseVariations(form.variationsCsv),
      sku: form.sku.trim(),
      active: form.active,
    };

    setStatus("Creating variation product...");
    try {
      await session.apiClient.post(`/admin/products/${variationParentId.trim()}/variations`, payload);
      setStatus("Variation created.");
      toast.success("Variation created");
      setVariationParentId("");
      setForm(emptyForm);
      await reloadCurrentView();
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Variation create failed.");
      toast.error(err instanceof Error ? err.message : "Variation create failed");
    }
  };

  const softDelete = async (id: string) => {
    if (!session.apiClient) return;
    setStatus("Deleting product...");
    try {
      await session.apiClient.delete(`/admin/products/${id}`);
      setStatus("Product soft deleted.");
      toast.success("Product moved to deleted list");
      await Promise.all([loadActive(0), loadDeleted(0)]);
      setShowDeleted(true);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Delete failed.");
      toast.error(err instanceof Error ? err.message : "Delete failed");
    }
  };

  const restore = async (id: string) => {
    if (!session.apiClient) return;
    setStatus("Restoring product...");
    try {
      await session.apiClient.post(`/admin/products/${id}/restore`);
      setStatus("Product restored.");
      toast.success("Product restored");
      await Promise.all([loadActive(0), loadDeleted(0)]);
      setShowDeleted(false);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Restore failed.");
      toast.error(err instanceof Error ? err.message : "Restore failed");
    }
  };

  const saveCategory = async () => {
    if (!session.apiClient) return;
    if (!categoryForm.name.trim()) return;
    const payload = {
      name: categoryForm.name.trim(),
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
      setCategoryForm({ name: "", type: "PARENT", parentCategoryId: "" });
      await Promise.all([loadCategories(), loadDeletedCategories()]);
      setStatus("Category operation complete.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Category save failed.");
      toast.error(err instanceof Error ? err.message : "Category save failed");
    }
  };

  const deleteCategory = async (id: string) => {
    if (!session.apiClient) return;
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
    setStatus("Restoring category...");
    try {
      await session.apiClient.post(`/admin/categories/${id}/restore`);
      toast.success("Category restored");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
      setStatus("Category restored.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Restore category failed.");
      toast.error(err instanceof Error ? err.message : "Restore category failed");
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
    return <main className="mx-auto min-h-screen max-w-7xl px-6 py-10 text-[var(--muted)]">Loading...</main>;
  }

  if (!session.isAuthenticated) {
    return null;
  }

  const rows = showDeleted ? deletedPage?.content || [] : activePage?.content || [];
  const pageInfo = showDeleted ? deletedPage : activePage;
  const parentCategories = categories.filter((c) => c.type === "PARENT");
  const selectedParent = parentCategories.find((c) => c.name === form.mainCategoryName) || null;
  const subCategoryOptions = categories.filter(
    (c) => c.type === "SUB" && c.parentCategoryId && selectedParent && c.parentCategoryId === selectedParent.id
  );

  const title = showDeleted ? "Deleted Products" : "Active Products";

  return (
    <main className="mx-auto min-h-screen max-w-7xl px-6 py-8">
      <AppNav
        email={(session.profile?.email as string) || ""}
        canViewAdmin={session.canViewAdmin}
        onLogout={() => {
          void session.logout();
        }}
      />

      <section className="card-surface animate-rise rounded-3xl p-6 md:p-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-xs tracking-[0.22em] text-[var(--muted)]">ADMIN CATALOG</p>
            <h1 className="text-4xl text-[var(--ink)]">Product Operations</h1>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setShowDeleted(false)}
              className={`rounded-full px-4 py-2 text-sm ${!showDeleted ? "btn-brand" : "border border-[var(--line)] bg-white"}`}
            >
              Active
            </button>
            <button
              onClick={() => setShowDeleted(true)}
              className={`rounded-full px-4 py-2 text-sm ${showDeleted ? "btn-brand" : "border border-[var(--line)] bg-white"}`}
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
            className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
          />
          <input
            value={sku}
            onChange={(e) => setSku(e.target.value)}
            placeholder="SKU"
            className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
          />
          <input
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            placeholder="Category"
            className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
          />
          <select
            value={type}
            onChange={(e) => setType(e.target.value as ProductType | "")}
            className="rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm"
          >
            <option value="">All Types</option>
            <option value="SINGLE">SINGLE</option>
            <option value="PARENT">PARENT</option>
            <option value="VARIATION">VARIATION</option>
          </select>
          <button type="submit" className="btn-brand rounded-xl px-3 py-2 text-sm font-semibold">
            Apply Filters
          </button>
        </form>

        <div className="grid gap-6 lg:grid-cols-[1.1fr,0.9fr]">
          <div>
            <h2 className="mb-3 text-2xl text-[var(--ink)]">{title}</h2>
            <div className="overflow-hidden rounded-2xl border border-[var(--line)] bg-white">
              <table className="w-full text-left text-sm">
                <thead className="bg-[#f0e8dd] text-[var(--ink)]">
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
                      <td colSpan={5} className="px-3 py-6 text-center text-[var(--muted)]">
                        No products.
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
                      <td className="px-3 py-2 text-xs text-[var(--muted)]">{p.productType}</td>
                      <td className="px-3 py-2 text-[var(--ink)]">{money(p.sellingPrice)}</td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-2">
                          {!showDeleted && (
                            <>
                              <button
                                onClick={() => {
                                  void loadToEdit(p.id);
                                }}
                                className="rounded-md border border-[var(--line)] bg-white px-2 py-1 text-xs"
                              >
                                Edit
                              </button>
                              <button
                                onClick={() => {
                                  void softDelete(p.id);
                                }}
                                className="rounded-md border border-red-200 bg-red-50 px-2 py-1 text-xs text-red-700"
                              >
                                Delete
                              </button>
                            </>
                          )}
                          {showDeleted && (
                            <button
                              onClick={() => {
                                void restore(p.id);
                              }}
                              className="rounded-md border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs text-emerald-700"
                            >
                              Restore
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-3 flex items-center justify-between">
              <p className="text-xs text-[var(--muted)]">
                {pageInfo ? `${pageInfo.totalElements} total` : "0 total"}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => {
                    if (!pageInfo || pageInfo.first) return;
                    if (showDeleted) {
                      void loadDeleted(Math.max(deletedPageIndex - 1, 0));
                    } else {
                      void loadActive(Math.max(page - 1, 0));
                    }
                  }}
                  disabled={!pageInfo || pageInfo.first}
                  className="rounded-md border border-[var(--line)] bg-white px-3 py-1 text-sm disabled:opacity-50"
                >
                  Prev
                </button>
                <button
                  onClick={() => {
                    if (!pageInfo || pageInfo.last) return;
                    if (showDeleted) {
                      void loadDeleted(deletedPageIndex + 1);
                    } else {
                      void loadActive(page + 1);
                    }
                  }}
                  disabled={!pageInfo || pageInfo.last}
                  className="rounded-md border border-[var(--line)] bg-white px-3 py-1 text-sm disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            </div>
          </div>

          <div className="space-y-4">
            <section className="card-surface rounded-2xl p-5">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-2xl text-[var(--ink)]">{form.id ? "Update Product" : "Create Product"}</h2>
                {form.id && (
                  <button
                    onClick={() => setForm(emptyForm)}
                    className="rounded-md border border-[var(--line)] bg-white px-2 py-1 text-xs"
                  >
                    Reset
                  </button>
                )}
              </div>
              <form onSubmit={submitProduct} className="grid gap-2 text-sm">
                <input value={form.name} onChange={(e) => setForm((o) => ({ ...o, name: e.target.value }))} placeholder="Name" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
                <input value={form.shortDescription} onChange={(e) => setForm((o) => ({ ...o, shortDescription: e.target.value }))} placeholder="Short Description" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
                <textarea value={form.description} onChange={(e) => setForm((o) => ({ ...o, description: e.target.value }))} placeholder="Description" className="rounded-lg border border-[var(--line)] px-3 py-2" rows={3} required />
                <div className="rounded-lg border border-[var(--line)] p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <p className="text-xs text-[var(--muted)]">
                      Product Images ({form.images.length}/{MAX_IMAGE_COUNT})
                    </p>
                    <label className="cursor-pointer rounded-md border border-[var(--line)] bg-white px-2 py-1 text-xs">
                      {uploadingImages ? "Uploading..." : "Upload Images"}
                      <input
                        type="file"
                        accept="image/png,image/jpeg,image/webp"
                        multiple
                        className="hidden"
                        disabled={uploadingImages || form.images.length >= MAX_IMAGE_COUNT}
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
                          className="flex items-center gap-2 rounded-lg border border-[var(--line)] bg-white p-2"
                        >
                          <div className="h-12 w-12 overflow-hidden rounded-md border border-[var(--line)] bg-[#f6f2ea]">
                            {imageUrl ? (
                              <img src={imageUrl} alt={imageName} className="h-full w-full object-cover" />
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
                            className="rounded border border-red-200 bg-red-50 px-2 py-1 text-[10px] text-red-700"
                          >
                            Remove
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </div>
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
                        />
                        {c.name}
                      </label>
                    ))}
                  </div>
                </div>
                <input value={form.sku} onChange={(e) => setForm((o) => ({ ...o, sku: e.target.value }))} placeholder="SKU" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
                <div className="grid grid-cols-2 gap-2">
                  <input type="number" step="0.01" min="0.01" value={form.regularPrice} onChange={(e) => setForm((o) => ({ ...o, regularPrice: e.target.value }))} placeholder="Regular Price" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
                  <input type="number" step="0.01" min="0" value={form.discountedPrice} onChange={(e) => setForm((o) => ({ ...o, discountedPrice: e.target.value }))} placeholder="Discounted Price" className="rounded-lg border border-[var(--line)] px-3 py-2" />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <select value={form.productType} onChange={(e) => setForm((o) => ({ ...o, productType: e.target.value as ProductType }))} className="rounded-lg border border-[var(--line)] px-3 py-2">
                    <option value="SINGLE">SINGLE</option>
                    <option value="PARENT">PARENT</option>
                    <option value="VARIATION">VARIATION</option>
                  </select>
                  <input value={form.vendorId} onChange={(e) => setForm((o) => ({ ...o, vendorId: e.target.value }))} placeholder="Vendor UUID (optional)" className="rounded-lg border border-[var(--line)] px-3 py-2" />
                </div>
                <input value={form.variationsCsv} onChange={(e) => setForm((o) => ({ ...o, variationsCsv: e.target.value }))} placeholder="Variations: color:red,size:XL" className="rounded-lg border border-[var(--line)] px-3 py-2" />
                <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
                  <input type="checkbox" checked={form.active} onChange={(e) => setForm((o) => ({ ...o, active: e.target.checked }))} />
                  Active
                </label>
                <button type="submit" className="btn-brand rounded-lg px-3 py-2 font-semibold">
                  {form.id ? "Update Product" : "Create Product"}
                </button>
              </form>
            </section>

            <section className="card-surface rounded-2xl p-5">
              <h3 className="text-xl text-[var(--ink)]">Create Variation For Parent</h3>
              <p className="mt-1 text-xs text-[var(--muted)]">
                Fill the editor fields above for variation data, then provide parent product ID.
              </p>
              <input
                value={variationParentId}
                onChange={(e) => setVariationParentId(e.target.value)}
                placeholder="Parent Product ID"
                className="mt-3 w-full rounded-lg border border-[var(--line)] px-3 py-2 text-sm"
              />
              <button onClick={() => void createVariation()} className="btn-brand mt-3 rounded-lg px-3 py-2 text-sm font-semibold">
                Create Variation
              </button>
            </section>

            <section className="card-surface rounded-2xl p-5">
              <h3 className="text-xl text-[var(--ink)]">Category Operations</h3>
              <p className="mt-1 text-xs text-[var(--muted)]">
                One unique category name, with parent and sub hierarchy.
              </p>

              <div className="mt-3 grid gap-2 text-sm">
                <input
                  value={categoryForm.name}
                  onChange={(e) => setCategoryForm((o) => ({ ...o, name: e.target.value }))}
                  placeholder="Category name"
                  className="rounded-lg border border-[var(--line)] px-3 py-2"
                />
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
                >
                  <option value="PARENT">PARENT</option>
                  <option value="SUB">SUB</option>
                </select>
                {categoryForm.type === "SUB" && (
                  <select
                    value={categoryForm.parentCategoryId}
                    onChange={(e) => setCategoryForm((o) => ({ ...o, parentCategoryId: e.target.value }))}
                    className="rounded-lg border border-[var(--line)] px-3 py-2"
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
                  <button onClick={() => void saveCategory()} className="btn-brand rounded-lg px-3 py-2 text-xs font-semibold">
                    {categoryForm.id ? "Update Category" : "Create Category"}
                  </button>
                  {categoryForm.id && (
                    <button
                      onClick={() => setCategoryForm({ name: "", type: "PARENT", parentCategoryId: "" })}
                      className="rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-xs"
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
                        {c.name} ({c.type})
                      </span>
                      <div className="flex gap-1">
                        <button
                          onClick={() =>
                            setCategoryForm({
                              id: c.id,
                              name: c.name,
                              type: c.type,
                              parentCategoryId: c.parentCategoryId || "",
                            })
                          }
                          className="rounded border border-[var(--line)] bg-white px-2 py-0.5 text-[10px]"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => {
                            void deleteCategory(c.id);
                          }}
                          className="rounded border border-red-200 bg-red-50 px-2 py-0.5 text-[10px] text-red-700"
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
                        {c.name} ({c.type})
                      </span>
                      <button
                        onClick={() => {
                          void restoreCategory(c.id);
                        }}
                        className="rounded border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] text-emerald-700"
                      >
                        Restore
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
  );
}
