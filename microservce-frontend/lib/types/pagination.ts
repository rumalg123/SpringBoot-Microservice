/**
 * Standard Spring Data Page response.
 * The backend returns either flat fields or a nested `page` object.
 * This helper normalises both shapes.
 */
export type PagedResponse<T> = {
  content: T[];
  number?: number;
  totalPages?: number;
  totalElements?: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
  size?: number;
  page?: {
    number?: number;
    totalPages?: number;
    totalElements?: number;
    size?: number;
  };
};

/** Normalise a raw Spring Page response into consistent fields. */
export function normalizePage<T>(raw: PagedResponse<T>) {
  return {
    content: raw.content,
    number: raw.number ?? raw.page?.number ?? 0,
    totalPages: raw.totalPages ?? raw.page?.totalPages ?? 1,
    totalElements: raw.totalElements ?? raw.page?.totalElements ?? raw.content.length,
    size: raw.size ?? raw.page?.size ?? raw.content.length,
    first: raw.first ?? (raw.number ?? raw.page?.number ?? 0) === 0,
    last: raw.last ?? (raw.number ?? raw.page?.number ?? 0) >= (raw.totalPages ?? raw.page?.totalPages ?? 1) - 1,
    empty: raw.empty ?? raw.content.length === 0,
  };
}

export type PaginationParams = {
  page?: number;
  size?: number;
  sort?: string;
};
