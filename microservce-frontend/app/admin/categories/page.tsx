"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";
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


export default function AdminCategoriesPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();

  /* ── form state ── */
  const [categoryForm, setCategoryForm] = useState<CategoryFormState>(emptyCategoryForm);
  const [categorySlugTouched, setCategorySlugTouched] = useState(false);
  const [categorySlugStatus, setCategorySlugStatus] = useState<SlugStatus>("idle");

  /* ── loading / busy flags ── */
  const [savingCategory, setSavingCategory] = useState(false);
  const [restoringCategoryId, setRestoringCategoryId] = useState<string | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  /* ── delete confirm modal ── */
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const isReady = session.status === "ready" && session.isAuthenticated && !!session.canManageAdminCategories && !!session.apiClient;

  /* ── queries ── */
  const { data: categories = [], isLoading: categoriesLoading } = useQuery<Category[]>({
    queryKey: ["admin-categories"],
    queryFn: async () => {
      const res = await session.apiClient!.get("/admin/categories");
      return (res.data as Category[]) || [];
    },
    enabled: isReady,
  });

  const { data: deletedCategories = [], isLoading: deletedLoading } = useQuery<Category[]>({
    queryKey: ["admin-categories", "deleted"],
    queryFn: async () => {
      const res = await session.apiClient!.get("/admin/categories/deleted");
      return (res.data as Category[]) || [];
    },
    enabled: isReady,
  });

  const initialLoading = categoriesLoading || deletedLoading;

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

  /* ── save category mutation ── */
  const saveCategoryMutation = useMutation({
    mutationFn: async (payload: { name: string; slug: string; type: string; parentCategoryId: string | null }) => {
      if (categoryForm.id) {
        await session.apiClient!.put(`/admin/categories/${categoryForm.id}`, payload);
      } else {
        await session.apiClient!.post("/admin/categories", payload);
      }
    },
    onSuccess: () => {
      toast.success(categoryForm.id ? "Category updated" : "Category created");
      setCategoryForm(emptyCategoryForm);
      setCategorySlugTouched(false);
      setCategorySlugStatus("idle");
      void queryClient.invalidateQueries({ queryKey: ["admin-categories"] });
    },
    onError: (err) => {
      toast.error(getErrorMessage(err));
    },
    onSettled: () => {
      setSavingCategory(false);
    },
  });

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
    saveCategoryMutation.mutate(payload);
  };

  /* ── delete category mutation ── */
  const deleteCategoryMutation = useMutation({
    mutationFn: async (id: string) => {
      await session.apiClient!.delete(`/admin/categories/${id}`);
    },
    onSuccess: () => {
      toast.success("Category deleted");
      void queryClient.invalidateQueries({ queryKey: ["admin-categories"] });
    },
    onError: (err) => {
      toast.error(getErrorMessage(err));
    },
    onSettled: () => {
      setConfirmLoading(false);
      setDeleteTarget(null);
      setShowDeleteConfirm(false);
    },
  });

  const deleteCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (confirmLoading) return;
    setConfirmLoading(true);
    deleteCategoryMutation.mutate(id);
  };

  /* ── restore category mutation ── */
  const restoreCategoryMutation = useMutation({
    mutationFn: async (id: string) => {
      await session.apiClient!.post(`/admin/categories/${id}/restore`);
    },
    onSuccess: () => {
      toast.success("Category restored");
      void queryClient.invalidateQueries({ queryKey: ["admin-categories"] });
    },
    onError: (err) => {
      toast.error(getErrorMessage(err));
    },
    onSettled: () => {
      setRestoringCategoryId(null);
    },
  });

  const restoreCategory = async (id: string) => {
    if (!session.apiClient) return;
    if (restoringCategoryId) return;
    setRestoringCategoryId(id);
    restoreCategoryMutation.mutate(id);
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
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading session...</p>
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
        <div className="flex items-center justify-center min-h-[300px] flex-col gap-3">
          <p className="text-danger text-[1.1rem] font-bold font-[var(--font-display,Syne,sans-serif)]">
            Unauthorized
          </p>
          <p className="text-muted text-sm">
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
        <div className="flex items-center justify-center min-h-[300px]">
          <p className="text-muted text-base">Loading categories...</p>
        </div>
      ) : (
        <div className="max-w-[640px]">
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
