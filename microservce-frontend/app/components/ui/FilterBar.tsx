"use client";

export type FilterDef = {
  key: string;
  label: string;
  type: "text" | "select" | "date" | "dateRange";
  placeholder?: string;
  options?: { label: string; value: string }[];
};

type Props = {
  filters: FilterDef[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  onClear: () => void;
};

export default function FilterBar({ filters, values, onChange, onClear }: Props) {
  const activeCount = Object.values(values).filter((v) => v.trim() !== "").length;
  const hasAnyValue = activeCount > 0;

  return (
    <div className="flex flex-wrap items-end gap-3 py-3.5 px-4 rounded-[12px] bg-surface-2 border border-line mb-4">
      {filters.map((filter) => {
        const currentValue = values[filter.key] || "";

        if (filter.type === "text") {
          return (
            <div key={filter.key} className="flex-[1_1_180px] min-w-[160px]">
              <label className="block mb-1 text-xs font-semibold text-muted uppercase tracking-wide">{filter.label}</label>
              <div className="relative flex items-center">
                <span className="absolute left-2.5 text-muted pointer-events-none flex items-center">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                  </svg>
                </span>
                <input
                  value={currentValue}
                  onChange={(e) => onChange(filter.key, e.target.value)}
                  placeholder={filter.placeholder || `Filter ${filter.label.toLowerCase()}...`}
                  className="w-full py-2 pr-3 pl-[30px] rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 focus:border-brand"
                />
              </div>
            </div>
          );
        }

        if (filter.type === "select") {
          return (
            <div key={filter.key} className="flex-[1_1_160px] min-w-[140px]">
              <label className="block mb-1 text-xs font-semibold text-muted uppercase tracking-wide">{filter.label}</label>
              <select
                value={currentValue}
                onChange={(e) => onChange(filter.key, e.target.value)}
                className="w-full py-2 px-3 pr-[30px] rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none cursor-pointer appearance-none transition-colors duration-150 focus:border-brand"
                style={{
                  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`,
                  backgroundRepeat: "no-repeat",
                  backgroundPosition: "right 10px center",
                }}
              >
                <option value="">{filter.placeholder || `All ${filter.label}`}</option>
                {(filter.options || []).map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          );
        }

        if (filter.type === "date") {
          return (
            <div key={filter.key} className="flex-[1_1_160px] min-w-[140px]">
              <label className="block mb-1 text-xs font-semibold text-muted uppercase tracking-wide">{filter.label}</label>
              <input
                type="date"
                value={currentValue}
                onChange={(e) => onChange(filter.key, e.target.value)}
                className="w-full py-2 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 focus:border-brand"
                style={{ colorScheme: "dark" }}
              />
            </div>
          );
        }

        if (filter.type === "dateRange") {
          const fromKey = `${filter.key}_from`;
          const toKey = `${filter.key}_to`;
          const fromValue = values[fromKey] || "";
          const toValue = values[toKey] || "";
          return (
            <div key={filter.key} className="flex-[2_1_280px] min-w-[240px]">
              <label className="block mb-1 text-xs font-semibold text-muted uppercase tracking-wide">{filter.label}</label>
              <div className="flex gap-1.5 items-center">
                <input
                  type="date"
                  value={fromValue}
                  onChange={(e) => onChange(fromKey, e.target.value)}
                  className="flex-1 py-2 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 focus:border-brand"
                  style={{ colorScheme: "dark" }}
                />
                <span className="text-xs text-muted shrink-0">to</span>
                <input
                  type="date"
                  value={toValue}
                  onChange={(e) => onChange(toKey, e.target.value)}
                  className="flex-1 py-2 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 focus:border-brand"
                  style={{ colorScheme: "dark" }}
                />
              </div>
            </div>
          );
        }

        return null;
      })}

      {/* Clear All button + active filter count */}
      <div className="flex items-center gap-2 pb-px">
        {hasAnyValue && (
          <>
            <span className="inline-flex items-center justify-center min-w-[20px] h-5 rounded-full bg-brand-soft border border-line-bright text-brand text-[0.68rem] font-extrabold px-[5px]">
              {activeCount}
            </span>
            <button
              type="button"
              onClick={onClear}
              className="btn-ghost text-sm py-[7px] px-3.5 inline-flex items-center gap-[5px] text-danger whitespace-nowrap"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
              Clear All
            </button>
          </>
        )}
      </div>
    </div>
  );
}
