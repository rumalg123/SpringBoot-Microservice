"use client";

type ProductType = "SINGLE" | "PARENT" | "VARIATION";
type ApprovalStatus = "NOT_REQUIRED" | "DRAFT" | "PENDING" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";

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

type Props = {
  q: string;
  sku: string;
  category: string;
  type: ProductType | "";
  parentCategories: Category[];
  subCategories: Category[];
  vendorId: string;
  vendorSearch: string;
  vendors: VendorSummary[];
  loadingVendors: boolean;
  showVendorFilter?: boolean;
  approvalStatus?: ApprovalStatus | "";
  activeFilter?: "" | "true" | "false";
  filtersSubmitting: boolean;
  listLoading: boolean;
  onQChange: (value: string) => void;
  onSkuChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onTypeChange: (value: ProductType | "") => void;
  onApprovalStatusChange?: (value: ApprovalStatus | "") => void;
  onActiveFilterChange?: (value: "" | "true" | "false") => void;
  onVendorIdChange: (value: string) => void;
  onVendorSearchChange: (value: string) => void;
  onApplyFilters: (e: React.FormEvent) => void | Promise<void>;
};

export default function ProductFilterForm({
  q, sku, category, type, parentCategories, subCategories,
  vendorId, vendorSearch, vendors, loadingVendors, showVendorFilter = true,
  approvalStatus = "", activeFilter = "",
  filtersSubmitting, listLoading,
  onQChange, onSkuChange, onCategoryChange, onTypeChange,
  onApprovalStatusChange, onActiveFilterChange,
  onVendorIdChange, onVendorSearchChange, onApplyFilters,
}: Props) {
  const filteredVendors = vendors
    .filter((vendor) => !vendor.deleted)
    .filter((vendor) => {
      const needle = vendorSearch.trim().toLowerCase();
      if (!needle) return true;
      return vendor.name.toLowerCase().includes(needle)
        || vendor.slug.toLowerCase().includes(needle)
        || (vendor.contactEmail || "").toLowerCase().includes(needle)
        || vendor.id.toLowerCase().includes(needle);
    })
    .sort((a, b) => a.name.localeCompare(b.name))
    .slice(0, 100);

  return (
    <form onSubmit={onApplyFilters} className="mb-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4 2xl:grid-cols-5">
      <input
        value={q}
        onChange={(e) => onQChange(e.target.value)}
        placeholder="Search text"
        className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
      />
      <input
        value={sku}
        onChange={(e) => onSkuChange(e.target.value)}
        placeholder="SKU"
        className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
      />
      <select
        value={category}
        onChange={(e) => onCategoryChange(e.target.value)}
        className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
      >
        <option value="">All Categories</option>
        {parentCategories.length > 0 && (
          <optgroup label="Main Categories">
            {parentCategories
              .slice()
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((item) => (
                <option key={item.id} value={item.name}>{item.name}</option>
              ))}
          </optgroup>
        )}
        {subCategories.length > 0 && (
          <optgroup label="Sub Categories">
            {subCategories
              .slice()
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((item) => (
                <option key={item.id} value={item.name}>{item.name}</option>
              ))}
          </optgroup>
        )}
      </select>
      <select
        value={type}
        onChange={(e) => onTypeChange(e.target.value as ProductType | "")}
        className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
      >
        <option value="">All Types</option>
        <option value="SINGLE">SINGLE</option>
        <option value="PARENT">PARENT</option>
        <option value="VARIATION">VARIATION</option>
      </select>
      {onApprovalStatusChange && (
        <select
          value={approvalStatus}
          onChange={(e) => onApprovalStatusChange(e.target.value as ApprovalStatus | "")}
          className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
        >
          <option value="">All Approval</option>
          <option value="NOT_REQUIRED">DRAFT</option>
          <option value="PENDING">PENDING</option>
          <option value="APPROVED">APPROVED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
      )}
      {onActiveFilterChange && (
        <select
          value={activeFilter}
          onChange={(e) => onActiveFilterChange(e.target.value as "" | "true" | "false")}
          className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
        >
          <option value="">All Status</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      )}
      {showVendorFilter && (
        <>
          <input
            value={vendorSearch}
            onChange={(e) => onVendorSearchChange(e.target.value)}
            placeholder="Vendor search"
            className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
          />
          <select
            value={vendorId}
            onChange={(e) => onVendorIdChange(e.target.value)}
            className="rounded-xl border border-[var(--line)] bg-surface-2 px-3 py-2 text-sm text-ink"
          >
            <option value="">{loadingVendors ? "Loading vendors..." : "All Vendors"}</option>
            {filteredVendors.map((vendor) => (
              <option key={vendor.id} value={vendor.id}>
                {vendor.name} ({vendor.slug})
              </option>
            ))}
          </select>
        </>
      )}
      <button
        type="submit"
        disabled={filtersSubmitting || listLoading}
        className="btn-brand rounded-xl px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60"
      >
        {(filtersSubmitting || listLoading) ? "Applying..." : "Apply Filters"}
      </button>
    </form>
  );
}
