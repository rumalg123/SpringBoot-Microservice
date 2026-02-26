"use client";

type Props = {
  customerEmailInput: string;
  filterBusy: boolean;
  onCustomerEmailInputChange: (value: string) => void;
  onSubmit: () => void | Promise<void>;
  onClear: () => void | Promise<void>;
};

export default function OrderFiltersBar({
  customerEmailInput,
  filterBusy,
  onCustomerEmailInputChange,
  onSubmit,
  onClear,
}: Props) {
  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <div className="relative flex min-w-[260px] flex-1 items-center overflow-hidden rounded-md border border-brand-soft bg-brand-soft">
          <span className="shrink-0 px-3 text-muted">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
          </span>
          <input
            value={customerEmailInput}
            onChange={(e) => onCustomerEmailInputChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                void onSubmit();
              }
            }}
            placeholder="Filter by customer email..."
            disabled={filterBusy}
            className="flex-1 border-none bg-transparent py-2.5 text-base text-[#c8c8e8] outline-none"
          />
          {customerEmailInput && (
            <button
              type="button"
              onClick={() => { void onClear(); }}
              disabled={filterBusy}
              className="mr-2.5 grid h-5 w-5 shrink-0 cursor-pointer place-items-center rounded-full border-none bg-[rgba(0,212,255,0.15)] text-xs text-muted"
            >
              x
            </button>
          )}
        </div>
        <button
          type="button"
          onClick={() => { void onSubmit(); }}
          disabled={filterBusy}
          className={`rounded-md border-none px-5 py-2.5 text-sm font-bold text-white ${
            filterBusy ? "cursor-not-allowed bg-[rgba(0,212,255,0.2)]" : "cursor-pointer bg-[linear-gradient(135deg,#00d4ff,#7c3aed)]"
          }`}
        >
          {filterBusy ? "Applying..." : "Apply Filter"}
        </button>
      </div>
      <p className="mb-4 text-[0.68rem] text-muted-2">Use full customer email, e.g. user@example.com</p>
    </>
  );
}
