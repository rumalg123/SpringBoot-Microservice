import { useCallback, useState } from "react";

export type PaginationState = {
  page: number;
  totalPages: number;
  totalElements: number;
};

export type PaginationActions = {
  setPage: (page: number) => void;
  nextPage: () => void;
  prevPage: () => void;
  setTotals: (totalPages: number, totalElements: number) => void;
  reset: () => void;
  isFirst: boolean;
  isLast: boolean;
};

export function usePagination(initialPage = 0): PaginationState & PaginationActions {
  const [page, setPageRaw] = useState(initialPage);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const setPage = useCallback((p: number) => setPageRaw(Math.max(0, p)), []);
  const nextPage = useCallback(() => setPageRaw((p) => Math.min(p + 1, Math.max(0, totalPages - 1))), [totalPages]);
  const prevPage = useCallback(() => setPageRaw((p) => Math.max(0, p - 1)), []);
  const setTotals = useCallback((tp: number, te: number) => { setTotalPages(tp); setTotalElements(te); }, []);
  const reset = useCallback(() => { setPageRaw(initialPage); setTotalPages(0); setTotalElements(0); }, [initialPage]);

  return {
    page, totalPages, totalElements,
    setPage, nextPage, prevPage, setTotals, reset,
    isFirst: page === 0,
    isLast: page >= totalPages - 1,
  };
}
