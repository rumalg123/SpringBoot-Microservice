"use client";

type PaginationProps = {
    currentPage: number;
    totalPages: number;
    totalElements?: number;
    onPageChange: (page: number) => void;
    disabled?: boolean;
};

export default function Pagination({
    currentPage,
    totalPages,
    totalElements,
    onPageChange,
    disabled = false,
}: PaginationProps) {
    const handlePageChange = (page: number) => {
        if (disabled) return;
        window.scrollTo({ top: 0, behavior: "smooth" });
        onPageChange(page);
    };

    if (totalPages <= 1) {
        return totalElements !== undefined ? (
            <div className="mt-4 text-center text-sm text-muted">
                Showing all {totalElements} results
            </div>
        ) : null;
    }

    const getPageNumbers = (): (number | "ellipsis-left" | "ellipsis-right")[] => {
        const pages: (number | "ellipsis-left" | "ellipsis-right")[] = [];
        const maxVisible = 7;
        if (totalPages <= maxVisible) {
            for (let i = 0; i < totalPages; i++) pages.push(i);
            return pages;
        }
        pages.push(0);
        if (currentPage > 2) pages.push("ellipsis-left");
        const start = Math.max(1, currentPage - 1);
        const end = Math.min(totalPages - 2, currentPage + 1);
        for (let i = start; i <= end; i++) pages.push(i);
        if (currentPage < totalPages - 3) pages.push("ellipsis-right");
        pages.push(totalPages - 1);
        return pages;
    };

    const pages = getPageNumbers();

    return (
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
            {/* Results Info */}
            <div className="text-sm text-muted">
                Page <span className="font-bold text-ink">{currentPage + 1}</span> of{" "}
                <span className="font-bold text-ink">{totalPages}</span>
                {totalElements !== undefined && (
                    <span className="ml-1">({totalElements} total result{totalElements !== 1 ? "s" : ""})</span>
                )}
            </div>

            {/* Page Controls */}
            <nav className="flex items-center gap-1" aria-label="Pagination">
                {/* Previous */}
                <button
                    type="button"
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={disabled || currentPage <= 0}
                    className="inline-flex items-center gap-1 rounded-[8px] border border-line-bright bg-brand-soft px-3 py-2 text-sm font-semibold text-ink-light transition-all duration-150 hover:border-brand hover:text-brand disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Go to previous page"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                    <span>Prev</span>
                </button>

                {/* Page Numbers */}
                {pages.map((p, idx) => {
                    if (p === "ellipsis-left" || p === "ellipsis-right") {
                        return (
                            <span key={p} className="px-1 text-sm text-muted">···</span>
                        );
                    }
                    const isActive = p === currentPage;
                    return (
                        <button
                            key={`page-${p}-${idx}`}
                            type="button"
                            onClick={() => handlePageChange(p)}
                            disabled={disabled}
                            className={`min-w-[34px] rounded-[8px] px-2.5 py-[7px] text-sm transition-all duration-150 ${
                                isActive
                                    ? "border-none font-extrabold text-white shadow-[0_0_14px_var(--line-bright)]"
                                    : "border border-line-bright bg-brand-soft font-semibold text-ink-light hover:border-brand hover:text-brand"
                            } ${disabled && !isActive ? "opacity-50" : ""} ${disabled ? "cursor-not-allowed" : "cursor-pointer"}`}
                            style={isActive ? { background: "var(--gradient-brand)" } : undefined}
                            aria-label={`Go to page ${p + 1}`}
                            aria-current={isActive ? "page" : undefined}
                        >
                            {p + 1}
                        </button>
                    );
                })}

                {/* Next */}
                <button
                    type="button"
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={disabled || currentPage + 1 >= totalPages}
                    className="inline-flex items-center gap-1 rounded-[8px] border border-line-bright bg-brand-soft px-3 py-2 text-sm font-semibold text-ink-light transition-all duration-150 hover:border-brand hover:text-brand disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Go to next page"
                >
                    <span>Next</span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
                </button>
            </nav>
        </div>
    );
}
