"use client";

import Pagination from "../../Pagination";

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

type Category = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type PageMeta = {
  totalElements: number;
  totalPages: number;
};

type Props = {
  title: string;
  showDeleted: boolean;
  q: string;
  sku: string;
  category: string;
  type: ProductType | "";
  parentCategories: Category[];
  subCategories: Category[];
  rows: ProductSummary[];
  pageMeta: PageMeta;
  currentPage: number;
  filtersSubmitting: boolean;
  listLoading: boolean;
  productRowActionBusy: boolean;
  loadingProductId: string | null;
  restoringProductId: string | null;
  onShowActive: () => void;
  onShowDeleted: () => void;
  onQChange: (value: string) => void;
  onSkuChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onTypeChange: (value: ProductType | "") => void;
  onApplyFilters: (e: React.FormEvent) => void | Promise<void>;
  onEditProduct: (id: string) => void | Promise<void>;
  onDeleteProductRequest: (product: ProductSummary) => void;
  onRestoreProduct: (id: string) => void | Promise<void>;
  onPageChange: (page: number) => void | Promise<void>;
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

export default function ProductCatalogPanel({
  title,
  showDeleted,
  q,
  sku,
  category,
  type,
  parentCategories,
  subCategories,
  rows,
  pageMeta,
  currentPage,
  filtersSubmitting,
  listLoading,
  productRowActionBusy,
  loadingProductId,
  restoringProductId,
  onShowActive,
  onShowDeleted,
  onQChange,
  onSkuChange,
  onCategoryChange,
  onTypeChange,
  onApplyFilters,
  onEditProduct,
  onDeleteProductRequest,
  onRestoreProduct,
  onPageChange,
}: Props) {
  return (
    <div className="order-2 lg:order-1">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand)]">ADMIN CATALOG</p>
          <h1 className="text-2xl font-bold text-[var(--ink)]">Product Operations</h1>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onShowActive}
            disabled={filtersSubmitting || listLoading}
            className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${!showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
          >
            Active
          </button>
          <button
            type="button"
            onClick={onShowDeleted}
            disabled={filtersSubmitting || listLoading}
            className={`rounded-full px-4 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 ${showDeleted ? "btn-brand" : "border border-[var(--line)] text-[var(--muted)]"}`}
          >
            Deleted
          </button>
        </div>
      </div>

      <form onSubmit={onApplyFilters} className="mb-5 grid gap-3 md:grid-cols-5">
        <input
          value={q}
          onChange={(e) => onQChange(e.target.value)}
          placeholder="Search text"
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        />
        <input
          value={sku}
          onChange={(e) => onSkuChange(e.target.value)}
          placeholder="SKU"
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
        />
        <select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
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
          onChange={(e) => onTypeChange(e.target.value as ProductType | "")}
          className="rounded-xl border border-[var(--line)] px-3 py-2 text-sm"
          style={{ background: "var(--surface-2)", color: "var(--ink)" }}
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
                            void onEditProduct(p.id);
                          }}
                          disabled={productRowActionBusy}
                          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
                          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
                        >
                          {loadingProductId === p.id ? "Loading..." : "Edit"}
                        </button>
                        <button
                          type="button"
                          onClick={() => onDeleteProductRequest(p)}
                          disabled={productRowActionBusy}
                          className="rounded-md border border-red-900/30 px-2 py-1 text-xs text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                          style={{ background: "rgba(239,68,68,0.06)" }}
                        >
                          Delete
                        </button>
                      </>
                    )}
                    {showDeleted && (
                      <button
                        type="button"
                        onClick={() => {
                          void onRestoreProduct(p.id);
                        }}
                        disabled={productRowActionBusy}
                        className="rounded-md border border-emerald-900/30 px-2 py-1 text-xs text-emerald-400 disabled:cursor-not-allowed disabled:opacity-60"
                        style={{ background: "rgba(16,185,129,0.06)" }}
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
        currentPage={currentPage}
        totalPages={pageMeta.totalPages}
        totalElements={pageMeta.totalElements}
        onPageChange={(p) => {
          void onPageChange(p);
        }}
        disabled={listLoading}
      />
    </div>
  );
}
