"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { VendorSummary } from "../admin/products/types";

type Props = {
  vendors: VendorSummary[];
  selectedVendorId: string;
  onSelect: (vendorId: string) => void;
  disabled?: boolean;
  loading?: boolean;
  helpText?: string;
};

export default function VendorSearchField({
  vendors,
  selectedVendorId,
  onSelect,
  disabled = false,
  loading = false,
  helpText = "Leave empty for platform warehouse",
}: Props) {
  const [search, setSearch] = useState("");
  const [focused, setFocused] = useState(false);
  const syncedRef = useRef("");

  const selectedVendor = useMemo(
    () => vendors.find((v) => v.id === selectedVendorId) || null,
    [vendors, selectedVendorId]
  );

  useEffect(() => {
    if (selectedVendorId === syncedRef.current) return;
    syncedRef.current = selectedVendorId;
    if (!selectedVendorId) {
      setSearch("");
      return;
    }
    if (selectedVendor) {
      setSearch(selectedVendor.name);
      return;
    }
    setSearch(selectedVendorId);
  }, [selectedVendorId, selectedVendor]);

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    const list = vendors.filter((v) => !v.deleted);
    if (!needle) return list.slice(0, 8);
    return list
      .filter(
        (v) =>
          v.name.toLowerCase().includes(needle) ||
          v.slug.toLowerCase().includes(needle) ||
          (v.contactEmail || "").toLowerCase().includes(needle) ||
          v.id.toLowerCase().includes(needle)
      )
      .slice(0, 8);
  }, [vendors, search]);

  const handleSelect = (vendorId: string, label?: string) => {
    onSelect(vendorId);
    syncedRef.current = vendorId;
    if (typeof label === "string") setSearch(label);
    setFocused(false);
  };

  return (
    <div className="mb-3.5">
      <label className="form-label">Vendor</label>
      <p className="text-[11px] text-[var(--muted)] mb-1.5">{helpText}</p>

      <div className="relative">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => {
            window.setTimeout(() => setFocused(false), 120);
          }}
          placeholder={loading ? "Loading vendors..." : "Search by vendor name, slug, email, or UUID"}
          className="form-input w-full"
          disabled={disabled || loading}
        />

        {focused && filtered.length > 0 && (
          <div className="absolute z-20 mt-1 w-full max-h-52 overflow-auto rounded-lg border border-[var(--line)] bg-[var(--surface-2)] p-1 shadow-lg">
            {filtered.map((v) => {
              const isSelected = v.id === selectedVendorId;
              return (
                <button
                  key={v.id}
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => handleSelect(v.id, v.name)}
                  className={`w-full rounded-md px-2 py-2 text-left text-xs text-ink ${isSelected ? "ring-1 ring-[var(--brand)] bg-brand-soft" : "hover:bg-white/[0.04]"}`}
                  disabled={disabled}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-semibold">{v.name}</span>
                    <span className="rounded bg-white/[0.06] px-1.5 py-0.5 text-[10px] text-ink-light">
                      {v.status || (v.active ? "ACTIVE" : "INACTIVE")}
                    </span>
                  </div>
                  <div className="mt-0.5 text-[11px] text-[var(--muted)]">
                    {v.slug} &bull; {v.contactEmail || "no-email"}
                  </div>
                  <div className="mt-0.5 font-mono text-[10px] text-[var(--muted)]">{v.id}</div>
                </button>
              );
            })}
          </div>
        )}

        {focused && search.trim() && filtered.length === 0 && (
          <div className="absolute z-20 mt-1 w-full rounded-lg border border-[var(--line)] bg-[var(--surface-2)] p-3 shadow-lg">
            <p className="text-xs text-[var(--muted)]">No vendors match your search.</p>
          </div>
        )}
      </div>

      <div className="mt-2 flex gap-2 items-center">
        <input
          value={selectedVendorId}
          readOnly
          placeholder="Auto-filled from vendor search"
          className="form-input w-full font-mono text-xs"
          disabled
        />
        {selectedVendorId && !disabled && (
          <button
            type="button"
            onClick={() => handleSelect("", "")}
            className="rounded-md border border-[var(--line)] bg-surface-2 px-2 py-1 text-xs text-ink-light hover:text-ink"
          >
            Clear
          </button>
        )}
      </div>

      {selectedVendor && (
        <p className="mt-1 text-[11px] text-[var(--muted)]">
          Selected: <span className="text-[var(--ink)]">{selectedVendor.name}</span> ({selectedVendor.slug})
        </p>
      )}
      {!selectedVendor && selectedVendorId.trim() && (
        <p className="mt-1 text-[11px] text-amber-500">
          Vendor UUID is set manually and not found in current vendor list.
        </p>
      )}
    </div>
  );
}
