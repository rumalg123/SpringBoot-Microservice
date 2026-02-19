"use client";

type PaginationProps = {
    currentPage: number;
    totalPages: number;
    totalElements?: number;
    onPageChange: (page: number) => void;
};

export default function Pagination({
    currentPage,
    totalPages,
    totalElements,
    onPageChange,
}: PaginationProps) {
    const handlePageChange = (page: number) => {
        window.scrollTo({ top: 0, behavior: "smooth" });
        onPageChange(page);
    };
    if (totalPages <= 1) {
        return totalElements !== undefined ? (
            <div className="mt-4 text-center text-xs text-[var(--muted)]">
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

        // Always show first page
        pages.push(0);

        if (currentPage > 2) {
            pages.push("ellipsis-left");
        }

        // Pages around current
        const start = Math.max(1, currentPage - 1);
        const end = Math.min(totalPages - 2, currentPage + 1);
        for (let i = start; i <= end; i++) {
            pages.push(i);
        }

        if (currentPage < totalPages - 3) {
            pages.push("ellipsis-right");
        }

        // Always show last page
        pages.push(totalPages - 1);

        return pages;
    };

    const pages = getPageNumbers();

    return (
        <div className="mt-6 flex flex-col items-center gap-3 sm:flex-row sm:justify-between">
            {/* Results Info */}
            <div className="text-xs text-[var(--muted)]">
                Page <span className="font-semibold text-[var(--ink)]">{currentPage + 1}</span> of{" "}
                <span className="font-semibold text-[var(--ink)]">{totalPages}</span>
                {totalElements !== undefined && (
                    <span className="ml-1">
                        ({totalElements} total result{totalElements !== 1 ? "s" : ""})
                    </span>
                )}
            </div>

            {/* Page Controls */}
            <nav className="flex items-center gap-1" aria-label="Pagination">
                {/* Previous */}
                <button
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={currentPage <= 0}
                    className="inline-flex items-center gap-1 rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm font-medium text-[var(--ink)] transition hover:border-[var(--brand)] hover:text-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--line)] disabled:hover:text-[var(--ink)]"
                    aria-label="Go to previous page"
                >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                    <span className="hidden sm:inline">Previous</span>
                </button>

                {/* Page Numbers */}
                {pages.map((p, idx) => {
                    if (p === "ellipsis-left" || p === "ellipsis-right") {
                        return (
                            <span key={p} className="px-1 text-sm text-[var(--muted)]">
                                ···
                            </span>
                        );
                    }
                    return (
                        <button
                            key={`page-${p}-${idx}`}
                            onClick={() => handlePageChange(p)}
                            className={`min-w-[36px] rounded-lg px-3 py-2 text-sm font-medium transition ${p === currentPage
                                ? "bg-[var(--brand)] text-white shadow-md shadow-red-200"
                                : "border border-[var(--line)] bg-white text-[var(--ink)] hover:border-[var(--brand)] hover:bg-[var(--brand-soft)] hover:text-[var(--brand)]"
                                }`}
                            aria-label={`Go to page ${p + 1}`}
                            aria-current={p === currentPage ? "page" : undefined}
                        >
                            {p + 1}
                        </button>
                    );
                })}

                {/* Next */}
                <button
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={currentPage + 1 >= totalPages}
                    className="inline-flex items-center gap-1 rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm font-medium text-[var(--ink)] transition hover:border-[var(--brand)] hover:text-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:border-[var(--line)] disabled:hover:text-[var(--ink)]"
                    aria-label="Go to next page"
                >
                    <span className="hidden sm:inline">Next</span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
                </button>
            </nav>
        </div>
    );
}
