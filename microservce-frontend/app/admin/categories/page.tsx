"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import AdminPageShell from "../../components/ui/AdminPageShell";
import ConfirmModal from "../../components/ConfirmModal";
import CategoryOperationsPanel from "../../components/admin/products/CategoryOperationsPanel";

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type CategoryFormState = {
  id?: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string;
};

type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

const emptyCategoryForm: CategoryFormState = {
  name: "",
  slug: "",
  type: "PARENT",
  parentCategoryId: "",
};

function normalizeSlug(value: string): string {
  return value
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "")
    .replace(/-{2,}/g, "-");
}

function extractErrorMessage(error: unknown): string {
  if (error && typeof error === "object") {
    const err = error as Record<string, unknown>;
    const response = err.response as Record<string, unknown> | undefined;
    if (response) {
      const data = response.data as Record<string, unknown> | undefined;
      if (data) {
        if (typeof data.error === "string" && data.error.trim()) return data.error;
        if (typeof data.message === "string" && data.message.trim()) return data.message;
      }
    }
    if (typeof err.message === "string" && err.message.trim()) return err.message;
  }
  if (error instanceof Error) return error.message;
  return "An unexpected error occurred";
}

export default function AdminCategoriesPage() {
  const session = useAuthSession();

  /* ── category lists ── */
  const [categories, setCategories] = useState<Category[]>([]);
  const [deletedCategories, setDeletedCategories] = useState<Category[]>([]);

  /* ── form state ── */
  const [categoryForm, setCategoryForm] = useState<CategoryFormState>(emptyCategoryForm);
  const [categorySlugTouched, setCategorySlugTouched] = useState(false);
  const [categorySlugStatus, setCategorySlugStatus] = useState<SlugStatus>("idle");

  /* ── loading / busy flags ── */
  const [savingCategory, setSavingCategory] = useState(false);
  const [restoringCategoryId, setRestoringCategoryId] = useState<string | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);

  /* ── delete confirm modal ── */
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  /* ── derived ── */
  const categoryMutationBusy = savingCategory || confirmLoading || Boolean(restoringCategoryId);
  const categorySlugBlocked =
    categorySlugStatus === "checking" ||
    categorySlugStatus === "taken" ||
    categorySlugStatus === "invalid";
  const parentCategories = useMemo(
    () => categories.filter((c) => c.type === "PARENT"),
    [categories],
  );

  /* ── data loaders ── */
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

  /* ── initial load ── */
  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated || !session.canManageAdminCategories) return;

    const run = async () => {
      try {
        await Promise.all([loadCategories(), loadDeletedCategories()]);
      } catch (err) {
        toast.error(extractErrorMessage(err));
      } finally {
        setInitialLoading(false);
      }
    };
    void run();
  }, [session.status, session.isAuthenticated, session.canManageAdminCategories, loadCategories, loadDeletedCategories]);

  /* ── auto-generate slug from name ── */
  useEffect(() => {
    if (categorySlugTouched) return;
    const generated = normalizeSlug(categoryForm.name).slice(0, 130);
    setCategoryForm((old) => (old.slug === generated ? old : { ...old, slug: generated }));
  }, [categoryForm.name, categorySlugTouched]);

  /* ── debounced slug availability check ── */
  useEffect(() => {
    const apiClient = session.apiClient;
    if (!apiClient) return;
    const normalized = normalizeSlug(categoryForm.slug).slice(0, 130);
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
    }, 500);
    return () => clearTimeout(timer);
  }, [session.apiClient, categoryForm.slug, categoryForm.id]);

  /* ── CRUD operations ── */
  const saveCategory = async () => {
    if (!session.apiClient) return;
    if (savingCategory) return;
    if (!categoryForm.name.trim()) {
      toast.error("Category name is required");
      return;
    }
    if (!normalizeSlug(categoryForm.slug)) {
      toast.error("Category slug is required");
      return;
    }
    if (categorySlugStatus === "checking") {
      toast.error("Wait until slug availability check completes");
      return;
    }
    if (categorySlugStatus === "taken" || categorySlugStatus === "invalid") {
      toast.error("Choose a unique valid category slug");
      return;
    }
    setSavingCategory(true);
    const payload = {
      name: categoryForm.name.trim(),
      slug: normalizeSlug(categoryForm.slug).slice(0, 130),
      type: categoryForm.type,
      parentCategoryId: categoryForm.type === "SUB" ? categoryForm.parentCategoryId || null : null,
    };
    try {
      if (categoryForm.id) {
        await session.apiClient.put(`/admin/categories/${categoryForm.id}`, payload);
        toast.success("Category updated");
      } else {
        await session.apiClient.post("/admin/categories", payload);
        toast.success("Category created");
      }
      setCategoryForm(emptyCategoryForm);
      setCategorySlugTouched(false);
      setCategorySlugStatus("idle");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
    } catch (err) {
      toast.error(extractErrorMessage(err));
    } finally {
      setSavingCategory(false);
    }
  };

  const deleteCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (confirmLoading) return;
    setConfirmLoading(true);
    try {
      await session.apiClient.delete(`/admin/categories/${id}`);
      toast.success("Category deleted");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
    } catch (err) {
      toast.error(extractErrorMessage(err));
    } finally {
      setConfirmLoading(false);
      setDeleteTarget(null);
      setShowDeleteConfirm(false);
    }
  };

  const restoreCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (restoringCategoryId) return;
    setRestoringCategoryId(id);
    try {
      await session.apiClient.post(`/admin/categories/${id}/restore`);
      toast.success("Category restored");
      await Promise.all([loadCategories(), loadDeletedCategories()]);
    } catch (err) {
      toast.error(extractErrorMessage(err));
    } finally {
      setRestoringCategoryId(null);
    }
  };

  /* ── form handlers ── */
  const handleCategoryFormNameChange = (value: string) => {
    setCategoryForm((o) => ({ ...o, name: value }));
  };

  const handleCategoryFormSlugChange = (value: string) => {
    setCategorySlugTouched(true);
    setCategoryForm((o) => ({ ...o, slug: value }));
  };

  const handleCategoryFormTypeChange = (value: "PARENT" | "SUB") => {
    setCategoryForm((o) => ({
      ...o,
      type: value,
      parentCategoryId: value === "SUB" ? o.parentCategoryId : "",
    }));
  };

  const handleCategoryFormParentChange = (value: string) => {
    setCategoryForm((o) => ({ ...o, parentCategoryId: value }));
  };

  const handleResetCategoryForm = () => {
    setCategoryForm(emptyCategoryForm);
    setCategorySlugTouched(false);
    setCategorySlugStatus("idle");
  };

  const handleEditCategory = (c: Category) => {
    setCategoryForm({
      id: c.id,
      name: c.name,
      slug: c.slug || "",
      type: c.type,
      parentCategoryId: c.parentCategoryId || "",
    });
    setCategorySlugTouched(true);
    setCategorySlugStatus("available");
  };

  const handleDeleteCategoryRequest = (c: Category) => {
    setDeleteTarget(c);
    setShowDeleteConfirm(true);
  };

  /* ── guards ── */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell
        title="Categories"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Categories" },
        ]}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading session...</p>
        </div>
      </AdminPageShell>
    );
  }

  if (!session.canManageAdminCategories) {
    return (
      <AdminPageShell
        title="Categories"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Categories" },
        ]}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            minHeight: 300,
            flexDirection: "column",
            gap: 12,
          }}
        >
          <p
            style={{
              color: "var(--danger, #ef4444)",
              fontSize: "1.1rem",
              fontWeight: 700,
              fontFamily: "var(--font-display, Syne, sans-serif)",
            }}
          >
            Unauthorized
          </p>
          <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>
            You do not have permission to manage categories.
          </p>
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Categories"
      breadcrumbs={[
        { label: "Admin", href: "/admin/orders" },
        { label: "Categories" },
      ]}
    >
      {initialLoading ? (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 300 }}>
          <p style={{ color: "var(--muted)", fontSize: "0.875rem" }}>Loading categories...</p>
        </div>
      ) : (
        <div style={{ maxWidth: 640 }}>
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
            normalizeSlug={(value) => normalizeSlug(value).slice(0, 130)}
            onCategoryFormNameChange={handleCategoryFormNameChange}
            onCategoryFormSlugChange={handleCategoryFormSlugChange}
            onCategoryFormTypeChange={handleCategoryFormTypeChange}
            onCategoryFormParentChange={handleCategoryFormParentChange}
            onSaveCategory={() => saveCategory()}
            onResetCategoryForm={handleResetCategoryForm}
            onEditCategory={handleEditCategory}
            onDeleteCategoryRequest={handleDeleteCategoryRequest}
            onRestoreCategory={(id) => restoreCategory(id)}
          />
        </div>
      )}

      <ConfirmModal
        open={showDeleteConfirm}
        title="Delete Category"
        message={`Are you sure you want to delete "${deleteTarget?.name ?? ""}"? This action can be reversed from the deleted items list.`}
        confirmLabel="Delete"
        cancelLabel="Cancel"
        danger
        loading={confirmLoading}
        onCancel={() => {
          setDeleteTarget(null);
          setShowDeleteConfirm(false);
        }}
        onConfirm={() => {
          if (!deleteTarget) return;
          void deleteCategory(deleteTarget.id);
        }}
      />
    </AdminPageShell>
  );
}
