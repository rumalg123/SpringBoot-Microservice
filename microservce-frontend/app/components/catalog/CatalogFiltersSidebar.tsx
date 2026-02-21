"use client";

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
  onApplyPriceFilter: (event: React.FormEvent) => void;
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
  onToggleParentExpanded
}: Props) {
  return (
    <aside className="animate-rise rounded-xl bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-base font-bold text-[var(--ink)]">Filters</h2>

      <form onSubmit={onApplyPriceFilter} className="mb-4 rounded-lg border border-[var(--line)] p-3">
        <p className="mb-2 text-xs font-bold uppercase tracking-[0.12em] text-[var(--muted)]">Price Range</p>
        <div className="grid grid-cols-2 gap-2">
          <input
            type="number"
            min="0"
            step="0.01"
            value={minPriceInput}
            onChange={(event) => onMinPriceChange(event.target.value)}
            placeholder="Min"
            className="rounded-lg border border-[var(--line)] px-2 py-2 text-sm"
          />
          <input
            type="number"
            min="0"
            step="0.01"
            value={maxPriceInput}
            onChange={(event) => onMaxPriceChange(event.target.value)}
            placeholder="Max"
            className="rounded-lg border border-[var(--line)] px-2 py-2 text-sm"
          />
        </div>
        <div className="mt-2 flex gap-2">
          <button
            type="submit"
            disabled={loading}
            className="btn-primary flex-1 px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          >
            Apply
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={onClearPriceFilter}
            className="btn-outline flex-1 px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          >
            Clear
          </button>
        </div>
      </form>

      <div className="rounded-lg border border-[var(--line)] p-3">
        <p className="mb-2 text-xs font-bold uppercase tracking-[0.12em] text-[var(--muted)]">Categories</p>
        {parents.length === 0 && (
          <p className="rounded-lg bg-[#fafafa] px-3 py-2 text-xs text-[var(--muted)]">
            No categories available.
          </p>
        )}

        {parents.map((parent) => {
          const selected = selectedParentNames.includes(parent.name);
          const expanded = Boolean(expandedParentIds[parent.id]);
          const subs = subsByParent.get(parent.id) || [];

          return (
            <div key={parent.id} className="mb-2 rounded-lg border border-[var(--line)]">
              <div className="flex items-center justify-between gap-2 px-2 py-2">
                <label className="flex items-center gap-2 text-sm text-[var(--ink)]">
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() => onToggleParent(parent)}
                  />
                  <span>{parent.name}</span>
                </label>
                <button
                  type="button"
                  onClick={() => onToggleParentExpanded(parent.id)}
                  className="rounded px-1 text-xs text-[var(--muted)] hover:bg-[#f4f4f4]"
                  aria-label={expanded ? "Collapse subcategories" : "Expand subcategories"}
                >
                  {expanded ? "-" : "+"}
                </button>
              </div>
              {expanded && subs.length > 0 && (
                <div className="border-t border-[var(--line)] px-2 py-2">
                  {subs.map((sub) => (
                    <label key={sub.id} className="mb-1 flex items-center gap-2 text-xs text-[var(--ink)]">
                      <input
                        type="checkbox"
                        checked={selectedSubNames.includes(sub.name)}
                        onChange={() => onToggleSub(sub)}
                      />
                      <span>{sub.name}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </aside>
  );
}
