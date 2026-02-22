"use client";

import type { Vendor } from "./types";

type VendorListPanelProps = {
  vendors: Vendor[];
  deletedVendors: Vendor[];
  showDeleted: boolean;
  loading: boolean;
  loadingDeleted: boolean;
  deletedLoaded: boolean;
  selectedVendorId: string;
  onRefresh: () => void;
  onToggleShowDeleted: (next: boolean) => void;
  onEditVendor: (vendor: Vendor) => void;
  onSelectVendor: (vendor: Vendor) => void;
  onDeleteVendor: (vendor: Vendor) => void;
  onRestoreVendor: (vendor: Vendor) => void;
};

function VendorRow({
  vendor,
  selectedVendorId,
  showDeleted,
  onEditVendor,
  onSelectVendor,
  onDeleteVendor,
  onRestoreVendor,
}: {
  vendor: Vendor;
  selectedVendorId: string;
  showDeleted: boolean;
  onEditVendor: (vendor: Vendor) => void;
  onSelectVendor: (vendor: Vendor) => void;
  onDeleteVendor: (vendor: Vendor) => void;
  onRestoreVendor: (vendor: Vendor) => void;
}) {
  return (
    <tr className="border-t border-[var(--line)]">
      <td className="px-3 py-2">
        <p className="font-semibold text-[var(--ink)]">{vendor.name}</p>
        <p className="text-xs text-[var(--muted)]">{vendor.slug}</p>
      </td>
      <td className="px-3 py-2 text-xs text-[var(--muted)]">
        <div>{vendor.contactEmail}</div>
        {vendor.contactPersonName && <div>{vendor.contactPersonName}</div>}
      </td>
      <td className="px-3 py-2 text-xs text-[var(--muted)]">
        {vendor.status} • {vendor.active ? "active" : "inactive"}
      </td>
      <td className="px-3 py-2">
        <div className="flex flex-wrap gap-2">
          {showDeleted ? (
            <button
              type="button"
              onClick={() => onRestoreVendor(vendor)}
              className="rounded-md border border-emerald-500/40 px-2 py-1 text-xs"
              style={{ background: "rgba(16,185,129,0.10)", color: "#34d399" }}
            >
              Restore
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={() => onEditVendor(vendor)}
                className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
                style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
              >
                Edit
              </button>
              <button
                type="button"
                onClick={() => onSelectVendor(vendor)}
                className="rounded-md border border-[var(--line)] px-2 py-1 text-xs"
                style={{
                  background: selectedVendorId === vendor.id ? "var(--brand-soft)" : "var(--surface-2)",
                  color: selectedVendorId === vendor.id ? "var(--brand)" : "var(--ink-light)",
                }}
              >
                {selectedVendorId === vendor.id ? "Selected" : "Users"}
              </button>
              <button
                type="button"
                onClick={() => onDeleteVendor(vendor)}
                className="rounded-md border border-red-500/40 px-2 py-1 text-xs"
                style={{ background: "rgba(239,68,68,0.08)", color: "#fca5a5" }}
              >
                Delete
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  );
}

export default function VendorListPanel({
  vendors,
  deletedVendors,
  showDeleted,
  loading,
  loadingDeleted,
  deletedLoaded,
  selectedVendorId,
  onRefresh,
  onToggleShowDeleted,
  onEditVendor,
  onSelectVendor,
  onDeleteVendor,
  onRestoreVendor,
}: VendorListPanelProps) {
  const rows = showDeleted ? deletedVendors : vendors;
  const isLoadingCurrent = showDeleted ? loadingDeleted && !deletedLoaded : loading;
  const emptyMessage = showDeleted ? "No deleted vendors." : "No vendors found.";

  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl text-[var(--ink)]">Vendors</h2>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onToggleShowDeleted(!showDeleted)}
            disabled={loading || loadingDeleted}
            className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
            style={{ background: showDeleted ? "var(--brand-soft)" : "var(--surface-2)", color: showDeleted ? "var(--brand)" : "var(--ink-light)" }}
          >
            {showDeleted ? "Show Active" : "Show Deleted"}
          </button>
          <button
            type="button"
            onClick={onRefresh}
            disabled={loading || (showDeleted && loadingDeleted)}
            className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
            style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
          >
            {loading || (showDeleted && loadingDeleted) ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      <div className="overflow-hidden rounded-2xl border border-[var(--line)]" style={{ background: "var(--surface)" }}>
        <table className="w-full text-left text-sm">
          <thead style={{ background: "var(--surface-2)", color: "var(--ink)" }}>
            <tr>
              <th className="px-3 py-2">Vendor</th>
              <th className="px-3 py-2">Contact</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoadingCurrent && (
              <tr>
                <td colSpan={4} className="px-3 py-5 text-center text-sm text-[var(--muted)]">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoadingCurrent && rows.length === 0 && (
              <tr>
                <td colSpan={4} className="px-3 py-5 text-center text-sm text-[var(--muted)]">
                  {emptyMessage}
                </td>
              </tr>
            )}
            {!isLoadingCurrent &&
              rows.map((vendor) => (
                <VendorRow
                  key={vendor.id}
                  vendor={vendor}
                  selectedVendorId={selectedVendorId}
                  showDeleted={showDeleted}
                  onEditVendor={onEditVendor}
                  onSelectVendor={onSelectVendor}
                  onDeleteVendor={onDeleteVendor}
                  onRestoreVendor={onRestoreVendor}
                />
              ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

