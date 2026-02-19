"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
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
  categories: string[];
  productType: ProductType;
  variations: Array<{ name: string; value: string }>;
  active: boolean;
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
  imagesCsv: string;
  regularPrice: string;
  discountedPrice: string;
  vendorId: string;
  categoriesCsv: string;
  productType: ProductType;
  variationsCsv: string;
  sku: string;
  active: boolean;
};

const emptyForm: ProductFormState = {
  name: "",
  shortDescription: "",
  description: "",
  imagesCsv: "",
  regularPrice: "",
  discountedPrice: "",
  vendorId: "",
  categoriesCsv: "",
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
  const [type, setType] = useState<ProductType | "">("");
  const [status, setStatus] = useState("Loading admin products...");
  const [showDeleted, setShowDeleted] = useState(false);
  const [form, setForm] = useState<ProductFormState>(emptyForm);
  const [variationParentId, setVariationParentId] = useState("");

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
        await Promise.all([loadActive(0), loadDeleted(0)]);
        setStatus("Admin product catalog loaded.");
      } catch (err) {
        setStatus(err instanceof Error ? err.message : "Failed to load products.");
      }
    };
    void run();
  }, [router, session.status, session.isAuthenticated, session.canViewAdmin, loadActive, loadDeleted]);

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
        imagesCsv: p.images.join(", "),
        regularPrice: String(p.regularPrice),
        discountedPrice: p.discountedPrice === null ? "" : String(p.discountedPrice),
        vendorId: p.vendorId,
        categoriesCsv: p.categories.join(", "),
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

    const payload = {
      name: form.name.trim(),
      shortDescription: form.shortDescription.trim(),
      description: form.description.trim(),
      images: parseCsv(form.imagesCsv),
      regularPrice: Number(form.regularPrice),
      discountedPrice: form.discountedPrice.trim() ? Number(form.discountedPrice) : null,
      vendorId: form.vendorId.trim() ? form.vendorId.trim() : null,
      categories: parseCsv(form.categoriesCsv),
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
    const payload = {
      name: form.name.trim(),
      shortDescription: form.shortDescription.trim(),
      description: form.description.trim(),
      images: parseCsv(form.imagesCsv),
      regularPrice: Number(form.regularPrice),
      discountedPrice: form.discountedPrice.trim() ? Number(form.discountedPrice) : null,
      vendorId: form.vendorId.trim() ? form.vendorId.trim() : null,
      categories: parseCsv(form.categoriesCsv),
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

  if (session.status === "loading" || session.status === "idle") {
    return <main className="mx-auto min-h-screen max-w-7xl px-6 py-10 text-[var(--muted)]">Loading...</main>;
  }

  if (!session.isAuthenticated) {
    return null;
  }

  const rows = showDeleted ? deletedPage?.content || [] : activePage?.content || [];
  const pageInfo = showDeleted ? deletedPage : activePage;

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
                <input value={form.imagesCsv} onChange={(e) => setForm((o) => ({ ...o, imagesCsv: e.target.value }))} placeholder="Images CSV: a.jpg,b.png" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
                <input value={form.categoriesCsv} onChange={(e) => setForm((o) => ({ ...o, categoriesCsv: e.target.value }))} placeholder="Categories CSV" className="rounded-lg border border-[var(--line)] px-3 py-2" required />
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
          </div>
        </div>

        <p className="mt-5 text-xs text-[var(--muted)]">{status}</p>
      </section>
    </main>
  );
}
