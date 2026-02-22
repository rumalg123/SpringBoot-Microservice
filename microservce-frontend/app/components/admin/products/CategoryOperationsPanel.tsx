"use client";

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

type Props = {
  categoryForm: CategoryFormState;
  categorySlugStatus: SlugStatus;
  categorySlugBlocked: boolean;
  categoryMutationBusy: boolean;
  savingCategory: boolean;
  restoringCategoryId: string | null;
  categories: Category[];
  deletedCategories: Category[];
  parentCategories: Category[];
  normalizeSlug: (value: string) => string;
  onCategoryFormNameChange: (value: string) => void;
  onCategoryFormSlugChange: (value: string) => void;
  onCategoryFormTypeChange: (value: "PARENT" | "SUB") => void;
  onCategoryFormParentChange: (value: string) => void;
  onSaveCategory: () => void | Promise<void>;
  onResetCategoryForm: () => void;
  onEditCategory: (category: Category) => void;
  onDeleteCategoryRequest: (category: Category) => void;
  onRestoreCategory: (id: string) => void | Promise<void>;
};

export default function CategoryOperationsPanel({
  categoryForm,
  categorySlugStatus,
  categorySlugBlocked,
  categoryMutationBusy,
  savingCategory,
  restoringCategoryId,
  categories,
  deletedCategories,
  parentCategories,
  normalizeSlug,
  onCategoryFormNameChange,
  onCategoryFormSlugChange,
  onCategoryFormTypeChange,
  onCategoryFormParentChange,
  onSaveCategory,
  onResetCategoryForm,
  onEditCategory,
  onDeleteCategoryRequest,
  onRestoreCategory,
}: Props) {
  return (
    <section className="card-surface rounded-2xl p-5">
      <h3 className="text-xl text-[var(--ink)]">Category Operations</h3>
      <p className="mt-1 text-xs text-[var(--muted)]">
        One unique category name, with parent and sub hierarchy.
      </p>

      <div className="mt-3 grid gap-2 text-sm">
        <input
          value={categoryForm.name}
          onChange={(e) => onCategoryFormNameChange(e.target.value)}
          placeholder="Category name"
          className="rounded-lg border border-[var(--line)] px-3 py-2"
          disabled={categoryMutationBusy}
        />
        <div>
          <input
            value={categoryForm.slug}
            onChange={(e) => onCategoryFormSlugChange(normalizeSlug(e.target.value))}
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
          onChange={(e) => onCategoryFormTypeChange(e.target.value as "PARENT" | "SUB")}
          className="rounded-lg border border-[var(--line)] px-3 py-2"
          disabled={categoryMutationBusy}
        >
          <option value="PARENT">PARENT</option>
          <option value="SUB">SUB</option>
        </select>
        {categoryForm.type === "SUB" && (
          <select
            value={categoryForm.parentCategoryId}
            onChange={(e) => onCategoryFormParentChange(e.target.value)}
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
            onClick={() => {
              void onSaveCategory();
            }}
            disabled={categoryMutationBusy || categorySlugBlocked}
            className="btn-brand rounded-lg px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-60"
          >
            {savingCategory ? "Saving..." : categoryForm.id ? "Update Category" : "Create Category"}
          </button>
          {categoryForm.id && (
            <button
              type="button"
              onClick={onResetCategoryForm}
              disabled={categoryMutationBusy}
              className="rounded-lg border border-[var(--line)] px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
              style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
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
                  onClick={() => onEditCategory(c)}
                  disabled={categoryMutationBusy}
                  className="rounded border border-[var(--line)] px-2 py-0.5 text-[10px] disabled:cursor-not-allowed disabled:opacity-60"
                  style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => onDeleteCategoryRequest(c)}
                  disabled={categoryMutationBusy}
                  className="rounded border border-red-900/30 px-2 py-0.5 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                  style={{ background: "rgba(239,68,68,0.06)" }}
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
                  void onRestoreCategory(c.id);
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
  );
}
