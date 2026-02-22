"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { VendorSelectorFieldProps } from "./types";

export default function VendorSelectorField({
  form,
  setForm,
  productMutationBusy,
  canSelectVendor,
  vendors,
  loadingVendors,
  refreshVendors,
  selectedVariationParent,
}: VendorSelectorFieldProps) {
  const [search, setSearch] = useState("");
  const [focused, setFocused] = useState(false);
  const syncedVendorIdRef = useRef<string>("");

  const selectedVendor = useMemo(
    () => vendors.find((vendor) => vendor.id === form.vendorId) || null,
    [vendors, form.vendorId]
  );

  const isVariation = form.productType === "VARIATION";
  const inheritedVendorId = isVariation ? (selectedVariationParent?.vendorId || form.vendorId || "") : "";

  useEffect(() => {
    if (isVariation) {
      return;
    }
    if (form.vendorId === syncedVendorIdRef.current) {
      return;
    }
    syncedVendorIdRef.current = form.vendorId;
    if (!form.vendorId) {
      setSearch("");
      return;
    }
    if (selectedVendor) {
      setSearch(selectedVendor.name);
      return;
    }
    setSearch(form.vendorId);
  }, [isVariation, form.vendorId, selectedVendor]);

  const filteredVendors = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const list = vendors.filter((vendor) => !vendor.deleted);
    if (!needle) {
      return list.slice(0, 8);
    }
    return list
      .filter((vendor) => {
        return vendor.name.toLowerCase().includes(needle)
          || vendor.slug.toLowerCase().includes(needle)
          || (vendor.contactEmail || "").toLowerCase().includes(needle)
          || vendor.id.toLowerCase().includes(needle);
      })
      .slice(0, 8);
  }, [vendors, search]);

  const disabled = productMutationBusy || isVariation || !canSelectVendor;

  const selectVendor = (vendorId: string, label?: string) => {
    setForm((old) => ({ ...old, vendorId }));
    syncedVendorIdRef.current = vendorId;
    if (typeof label === "string") {
      setSearch(label);
    }
    setFocused(false);
  };

  return (
    <div className="rounded-lg border border-[var(--line)] p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div>
          <p className="text-xs font-semibold text-[var(--ink)]">Vendor</p>
          <p className="text-[11px] text-[var(--muted)]">
            {canSelectVendor
              ? "Super admin can assign product ownership to a vendor. Variations inherit vendor from parent."
              : "Vendor ownership is scoped by your account. Variations inherit vendor from parent."}
          </p>
        </div>
        <button
          type="button"
          onClick={() => void refreshVendors()}
          disabled={productMutationBusy || loadingVendors || !canSelectVendor}
          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          {loadingVendors ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {isVariation ? (
        <div className="rounded-md border border-[var(--line)] bg-[var(--surface-2)] px-3 py-2 text-xs text-[var(--ink)]">
          <p className="font-semibold">
            {selectedVariationParent ? "Inherited from parent product" : "Select a parent product to inherit vendor"}
          </p>
          {selectedVariationParent && (
            <p className="mt-1 text-[var(--muted)]">
              {selectedVariationParent.name} ({selectedVariationParent.sku}) - Vendor ID:{" "}
              <span className="font-mono text-[var(--ink)]">{inheritedVendorId || "N/A"}</span>
            </p>
          )}
        </div>
      ) : (
        <>
          <label className="form-label">Search Vendor</label>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={() => {
              // Let option clicks finish first.
              window.setTimeout(() => setFocused(false), 120);
            }}
            placeholder="Search by vendor name, slug, email, or UUID"
            className="w-full rounded-lg border border-[var(--line)] px-3 py-2"
            disabled={productMutationBusy || !canSelectVendor}
          />

          {canSelectVendor && focused && filteredVendors.length > 0 && (
            <div className="mt-2 max-h-52 overflow-auto rounded-lg border border-[var(--line)] bg-[var(--surface-2)] p-1">
              {filteredVendors.map((vendor) => {
                const isSelected = vendor.id === form.vendorId;
                return (
                  <button
                    key={vendor.id}
                    type="button"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => selectVendor(vendor.id, vendor.name)}
                    className={`w-full rounded-md px-2 py-2 text-left text-xs ${isSelected ? "ring-1 ring-[var(--brand)]" : ""}`}
                    style={{
                      background: isSelected ? "var(--brand-soft)" : "transparent",
                      color: "var(--ink)",
                    }}
                    disabled={productMutationBusy}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{vendor.name}</span>
                      <span className="rounded px-1.5 py-0.5 text-[10px]" style={{ background: "rgba(255,255,255,0.06)", color: "var(--ink-light)" }}>
                        {vendor.status || (vendor.active ? "ACTIVE" : "INACTIVE")}
                      </span>
                    </div>
                    <div className="mt-0.5 text-[11px] text-[var(--muted)]">
                      {vendor.slug} • {vendor.contactEmail || "no-email"}
                    </div>
                    <div className="mt-0.5 font-mono text-[10px] text-[var(--muted)]">{vendor.id}</div>
                  </button>
                );
              })}
            </div>
          )}

          {canSelectVendor && search.trim() && focused && filteredVendors.length === 0 && (
            <p className="mt-2 text-xs text-[var(--muted)]">No vendors match your search.</p>
          )}
          {!canSelectVendor && (
            <p className="mt-2 text-xs text-[var(--muted)]">
              Vendor admins cannot change vendor ownership. Backend applies your scoped vendor automatically.
            </p>
          )}
        </>
      )}

      <div className="mt-3 grid gap-2">
        <label className="form-label">{isVariation ? "Vendor ID (inherited)" : "Vendor ID"}</label>
        <div className="flex gap-2">
          <input
            value={form.vendorId}
            onChange={(e) => {
              if (isVariation) return;
              syncedVendorIdRef.current = e.target.value;
              setForm((old) => ({ ...old, vendorId: e.target.value }));
            }}
            placeholder={isVariation ? "Inherited from selected parent" : "Auto-filled from vendor search (manual override supported)"}
            className="w-full rounded-lg border border-[var(--line)] px-3 py-2 font-mono text-xs"
            disabled={disabled}
            readOnly={isVariation}
          />
          {!isVariation && canSelectVendor && (
            <button
              type="button"
              onClick={() => selectVendor("", "")}
              disabled={productMutationBusy || !form.vendorId}
              className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
              style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
            >
              Clear
            </button>
          )}
        </div>

        {!isVariation && selectedVendor && (
          <p className="text-[11px] text-[var(--muted)]">
            Selected vendor: <span className="text-[var(--ink)]">{selectedVendor.name}</span> ({selectedVendor.slug})
          </p>
        )}
        {!isVariation && canSelectVendor && !selectedVendor && form.vendorId.trim() && (
          <p className="text-[11px] text-amber-500">
            Vendor UUID is set manually and not found in current vendor list.
          </p>
        )}
        {!isVariation && canSelectVendor && !form.vendorId.trim() && (
          <p className="text-[11px] text-[var(--muted)]">
            Optional for now. If empty, backend may use the platform fallback vendor.
          </p>
        )}
      </div>
    </div>
  );
}
