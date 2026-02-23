"use client";

import type { SlugStatus, VendorForm, VendorStatus } from "./types";

type VendorFormPanelProps = {
  form: VendorForm;
  slugStatus: SlugStatus;
  saving: boolean;
  onChange: (updater: (prev: VendorForm) => VendorForm) => void;
  onSlugEdited: () => void;
  onReset: () => void;
  onSubmit: () => void;
};

function slugStatusText(status: SlugStatus) {
  switch (status) {
    case "checking":
      return "Checking slug...";
    case "available":
      return "Slug is available";
    case "taken":
      return "Slug is already taken";
    case "invalid":
      return "Enter a valid slug";
    default:
      return "Vendor URL slug";
  }
}

function slugStatusClass(status: SlugStatus) {
  if (status === "taken" || status === "invalid") return "text-red-600";
  if (status === "available") return "text-emerald-600";
  return "text-[var(--muted)]";
}

export default function VendorFormPanel({
  form,
  slugStatus,
  saving,
  onChange,
  onSlugEdited,
  onReset,
  onSubmit,
}: VendorFormPanelProps) {
  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-2xl text-[var(--ink)]">{form.id ? "Update Vendor" : "Create Vendor"}</h2>
        <button
          type="button"
          onClick={onReset}
          disabled={saving}
          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          Reset
        </button>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSubmit();
        }}
        className="grid gap-3 text-sm"
      >
        <div className="form-group">
          <label className="form-label">Vendor Name</label>
          <input
            value={form.name}
            onChange={(e) => onChange((s) => ({ ...s, name: e.target.value }))}
            className="rounded-lg border border-[var(--line)] px-3 py-2"
            placeholder="e.g. ElectroHub"
            required
            disabled={saving}
          />
        </div>

        <div className="form-group">
          <label className="form-label">Slug</label>
          <input
            value={form.slug}
            onChange={(e) => {
              onSlugEdited();
              onChange((s) => ({ ...s, slug: e.target.value }));
            }}
            className="rounded-lg border border-[var(--line)] px-3 py-2"
            placeholder="electrohub"
            required
            disabled={saving}
          />
          <p className={`mt-1 text-[11px] ${slugStatusClass(slugStatus)}`}>{slugStatusText(slugStatus)}</p>
        </div>

        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          <div className="form-group">
            <label className="form-label">Contact Email</label>
            <input
              type="email"
              value={form.contactEmail}
              onChange={(e) => onChange((s) => ({ ...s, contactEmail: e.target.value }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              placeholder="owner@vendor.com"
              required
              disabled={saving}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Contact Person</label>
            <input
              value={form.contactPersonName}
              onChange={(e) => onChange((s) => ({ ...s, contactPersonName: e.target.value }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              placeholder="Vendor Owner"
              disabled={saving}
            />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
          <div className="form-group">
            <label className="form-label">Status</label>
            <select
              value={form.status}
              onChange={(e) => onChange((s) => ({ ...s, status: e.target.value as VendorStatus }))}
              className="rounded-lg border border-[var(--line)] px-3 py-2"
              disabled={saving}
            >
              <option value="PENDING">PENDING</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="SUSPENDED">SUSPENDED</option>
            </select>
          </div>
          <label className="mt-6 flex items-center gap-2 text-xs text-[var(--muted)]">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => onChange((s) => ({ ...s, active: e.target.checked }))}
              disabled={saving}
            />
            Vendor active
          </label>
        </div>

        <label className="flex items-center gap-2 text-xs text-[var(--muted)]">
          <input
            type="checkbox"
            checked={form.acceptingOrders}
            onChange={(e) => onChange((s) => ({ ...s, acceptingOrders: e.target.checked }))}
            disabled={saving}
          />
          Accepting orders (storefront visible only when active + ACTIVE status + accepting orders)
        </label>

        <button
          type="submit"
          disabled={saving || slugStatus === "checking" || slugStatus === "taken" || slugStatus === "invalid"}
          className="btn-brand rounded-lg px-3 py-2 font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? "Saving..." : form.id ? "Update Vendor" : "Create Vendor"}
        </button>
      </form>
    </section>
  );
}
