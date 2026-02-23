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
            <div style={{ marginTop: "16px", textAlign: "center", fontSize: "0.75rem", color: "var(--muted)" }}>
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

    const navBtnBase: React.CSSProperties = {
        display: "inline-flex", alignItems: "center", gap: "4px",
        padding: "8px 12px", borderRadius: "8px", fontSize: "0.8rem", fontWeight: 600,
        border: "1px solid var(--line-bright)", background: "var(--brand-soft)",
        color: "var(--ink-light)", cursor: "pointer", transition: "all 0.15s",
    };

    return (
        <div style={{ marginTop: "24px", display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: "12px" }}>
            {/* Results Info */}
            <div style={{ fontSize: "0.75rem", color: "var(--muted)" }}>
                Page <span style={{ fontWeight: 700, color: "var(--ink)" }}>{currentPage + 1}</span> of{" "}
                <span style={{ fontWeight: 700, color: "var(--ink)" }}>{totalPages}</span>
                {totalElements !== undefined && (
                    <span style={{ marginLeft: "4px" }}>({totalElements} total result{totalElements !== 1 ? "s" : ""})</span>
                )}
            </div>

            {/* Page Controls */}
            <nav style={{ display: "flex", alignItems: "center", gap: "4px" }} aria-label="Pagination">
                {/* Previous */}
                <button
                    type="button"
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={disabled || currentPage <= 0}
                    style={{
                        ...navBtnBase,
                        opacity: disabled || currentPage <= 0 ? 0.4 : 1,
                        cursor: disabled || currentPage <= 0 ? "not-allowed" : "pointer",
                    }}
                    aria-label="Go to previous page"
                    onMouseEnter={(e) => { if (!(disabled || currentPage <= 0)) { (e.currentTarget as HTMLElement).style.borderColor = "var(--brand)"; (e.currentTarget as HTMLElement).style.color = "var(--brand)"; } }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.borderColor = "var(--line-bright)"; (e.currentTarget as HTMLElement).style.color = "var(--ink-light)"; }}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
                    <span>Prev</span>
                </button>

                {/* Page Numbers */}
                {pages.map((p, idx) => {
                    if (p === "ellipsis-left" || p === "ellipsis-right") {
                        return (
                            <span key={p} style={{ padding: "0 4px", fontSize: "0.8rem", color: "var(--muted)" }}>···</span>
                        );
                    }
                    const isActive = p === currentPage;
                    return (
                        <button
                            key={`page-${p}-${idx}`}
                            type="button"
                            onClick={() => handlePageChange(p)}
                            disabled={disabled}
                            style={{
                                minWidth: "34px", padding: "7px 10px", borderRadius: "8px",
                                fontSize: "0.8rem", fontWeight: isActive ? 800 : 600, cursor: disabled ? "not-allowed" : "pointer",
                                border: isActive ? "none" : "1px solid var(--line-bright)",
                                background: isActive ? "var(--gradient-brand)" : "var(--brand-soft)",
                                color: isActive ? "#fff" : "var(--ink-light)",
                                boxShadow: isActive ? "0 0 14px var(--line-bright)" : "none",
                                opacity: disabled && !isActive ? 0.5 : 1,
                                transition: "all 0.15s",
                            }}
                            aria-label={`Go to page ${p + 1}`}
                            aria-current={isActive ? "page" : undefined}
                            onMouseEnter={(e) => { if (!isActive && !disabled) { (e.currentTarget as HTMLElement).style.borderColor = "var(--brand)"; (e.currentTarget as HTMLElement).style.color = "var(--brand)"; } }}
                            onMouseLeave={(e) => { if (!isActive) { (e.currentTarget as HTMLElement).style.borderColor = "var(--line-bright)"; (e.currentTarget as HTMLElement).style.color = "var(--ink-light)"; } }}
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
                    style={{
                        ...navBtnBase,
                        opacity: disabled || currentPage + 1 >= totalPages ? 0.4 : 1,
                        cursor: disabled || currentPage + 1 >= totalPages ? "not-allowed" : "pointer",
                    }}
                    aria-label="Go to next page"
                    onMouseEnter={(e) => { if (!(disabled || currentPage + 1 >= totalPages)) { (e.currentTarget as HTMLElement).style.borderColor = "var(--brand)"; (e.currentTarget as HTMLElement).style.color = "var(--brand)"; } }}
                    onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.borderColor = "var(--line-bright)"; (e.currentTarget as HTMLElement).style.color = "var(--ink-light)"; }}
                >
                    <span>Next</span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
                </button>
            </nav>
        </div>
    );
}
