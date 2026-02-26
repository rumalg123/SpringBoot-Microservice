"use client";

import { FormEvent } from "react";

type Category = {
  id: string;
  name: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

type Props = {
  parents: Category[];
  subsByParent: Map<string, Category[]>;
  selectedParentNames: string[];
  selectedSubNames: string[];
  expandedParentIds: Record<string, boolean>;
  minPriceInput: string;
  maxPriceInput: string;
  loading: boolean;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onApplyPriceFilter: (event: FormEvent) => void;
  onClearPriceFilter: () => void;
  onToggleParent: (parent: Category) => void;
  onToggleSub: (sub: Category) => void;
  onToggleParentExpanded: (parentId: string) => void;
};

export default function CatalogFiltersSidebar({
  parents,
  subsByParent,
  selectedParentNames,
  selectedSubNames,
  expandedParentIds,
  minPriceInput,
  maxPriceInput,
  loading,
  onMinPriceChange,
  onMaxPriceChange,
  onApplyPriceFilter,
  onClearPriceFilter,
  onToggleParent,
  onToggleSub,
  onToggleParentExpanded,
}: Props) {
  const activeCount =
    selectedParentNames.length + selectedSubNames.length + (minPriceInput || maxPriceInput ? 1 : 0);

  return (
    <aside
      className="filter-sidebar animate-rise p-0 border border-brand/10"
    >
      {/* Sidebar Header */}
      <div className="px-[18px] py-4 border-b border-brand/[0.08] bg-brand/[0.03] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00d4ff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
          </svg>
          <span className="font-[Syne,sans-serif] font-extrabold text-[0.9rem] text-white">
            Filters
          </span>
          {activeCount > 0 && (
            <span className="bg-[image:linear-gradient(135deg,#00d4ff,#7c3aed)] text-white text-[0.6rem] font-extrabold px-[7px] py-[2px] rounded-full leading-[1.4]">
              {activeCount}
            </span>
          )}
        </div>
      </div>

      <div className="px-[18px] py-4 flex flex-col gap-5">
        {/* Price Range */}
        <div>
          <p className="filter-section-title mb-3">Price Range</p>
          <form onSubmit={onApplyPriceFilter}>
            <div className="grid grid-cols-2 gap-2 mb-2.5">
              <div className="relative">
                <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-brand/50 text-[0.75rem] font-bold pointer-events-none">
                  $
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={minPriceInput}
                  onChange={(e) => onMinPriceChange(e.target.value)}
                  placeholder="Min"
                  aria-label="Minimum price"
                  className="w-full py-[9px] pr-2.5 pl-[22px] rounded-lg border border-brand/15 bg-brand/[0.04] text-white text-sm outline-none"
                />
              </div>
              <div className="relative">
                <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-brand/50 text-[0.75rem] font-bold pointer-events-none">
                  $
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={maxPriceInput}
                  onChange={(e) => onMaxPriceChange(e.target.value)}
                  placeholder="Max"
                  aria-label="Maximum price"
                  className="w-full py-[9px] pr-2.5 pl-[22px] rounded-lg border border-brand/15 bg-brand/[0.04] text-white text-sm outline-none"
                />
              </div>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={loading}
                className={`flex-1 py-2 rounded-lg border-none bg-[image:linear-gradient(135deg,#00d4ff,#7c3aed)] text-white text-[0.75rem] font-bold transition-all duration-200 ${loading ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
              >
                Apply
              </button>
              <button
                type="button"
                disabled={loading}
                onClick={onClearPriceFilter}
                className={`flex-1 py-2 rounded-lg border border-brand/20 bg-transparent text-brand text-[0.75rem] font-bold transition-all duration-200 ${loading ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
              >
                Clear
              </button>
            </div>
          </form>
        </div>

        {/* Divider */}
        <div className="neon-divider" />

        {/* Categories */}
        <div>
          <p className="filter-section-title mb-3">Categories</p>

          {parents.length === 0 && (
            <p className="text-[0.78rem] text-[#4a4a70] px-2.5 py-2 bg-brand/[0.03] rounded-lg">
              No categories available.
            </p>
          )}

          <div className="flex flex-col gap-1.5">
            {parents.map((parent) => {
              const selected = selectedParentNames.includes(parent.name);
              const expanded = Boolean(expandedParentIds[parent.id]);
              const subs = subsByParent.get(parent.id) || [];

              return (
                <div
                  key={parent.id}
                  className={`rounded-md overflow-hidden transition-all duration-200 ${selected ? "border border-brand/35 bg-brand/[0.05]" : "border border-brand/[0.08] bg-transparent"}`}
                >
                  <div className="flex items-center justify-between px-3 py-[9px] gap-2">
                    <label
                      className={`flex items-center gap-2.5 text-[0.82rem] cursor-pointer flex-1 ${selected ? "text-brand font-bold" : "text-[#c8c8e8] font-medium"}`}
                    >
                      <input
                        type="checkbox"
                        checked={selected}
                        onChange={() => onToggleParent(parent)}
                        className="accent-brand w-3.5 h-3.5"
                      />
                      {parent.name}
                      {selected && (
                        <span className="text-[0.6rem] bg-brand/15 text-brand px-1.5 py-px rounded-md font-extrabold">
                          ✓
                        </span>
                      )}
                    </label>
                    {subs.length > 0 && (
                      <button
                        type="button"
                        onClick={() => onToggleParentExpanded(parent.id)}
                        className="w-[22px] h-[22px] rounded-sm border border-brand/15 bg-brand/[0.05] text-brand text-xs font-extrabold cursor-pointer flex items-center justify-center shrink-0 transition-all duration-200"
                        aria-label={expanded ? "Collapse" : "Expand"}
                      >
                        {expanded ? "−" : "+"}
                      </button>
                    )}
                  </div>

                  {expanded && subs.length > 0 && (
                    <div className="border-t border-brand/[0.08] py-2 pr-3 pl-8 flex flex-col gap-[7px] bg-black/20">
                      {subs.map((sub) => {
                        const subSelected = selectedSubNames.includes(sub.name);
                        return (
                          <label
                            key={sub.id}
                            className={`flex items-center gap-2 text-[0.78rem] cursor-pointer transition-colors duration-150 ${subSelected ? "text-brand font-semibold" : "text-[#6868a0] font-normal"}`}
                          >
                            <input
                              type="checkbox"
                              checked={subSelected}
                              onChange={() => onToggleSub(sub)}
                              className="accent-brand w-3 h-3"
                            />
                            {sub.name}
                          </label>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </aside>
  );
}
