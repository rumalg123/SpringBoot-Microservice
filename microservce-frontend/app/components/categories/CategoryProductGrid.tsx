"use client";

import Pagination from "../Pagination";
import ProductCard from "../catalog/ProductCard";
import type { ProductSummary } from "../../../lib/types";

type CategoryProductGridProps = {
  products: ProductSummary[];
  productsLoading: boolean;
  busy: boolean;
  page: number;
  totalPages: number;
  isAuthenticated: boolean;
  isWishlisted: (id: string) => boolean;
  isWishlistBusy: (id: string) => boolean;
  onWishlistToggle: (e: React.MouseEvent<HTMLButtonElement>, id: string) => void;
  onPageChange: (page: number) => void;
  onResetFilters: () => void;
};

export default function CategoryProductGrid({
  products,
  productsLoading,
  busy,
  page,
  totalPages,
  isAuthenticated,
  isWishlisted,
  isWishlistBusy,
  onWishlistToggle,
  onPageChange,
  onResetFilters,
}: CategoryProductGridProps) {
  return (
    <section>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3">
        {products.map((p, idx) => (
          <ProductCard
            key={p.id}
            product={p}
            index={idx}
            showWishlist={isAuthenticated}
            isWishlisted={isWishlisted(p.id)}
            wishlistBusy={isWishlistBusy(p.id)}
            onWishlistToggle={(e) => { onWishlistToggle(e, p.id); }}
          />
        ))}
      </div>

      {products.length === 0 && !productsLoading && (
        <div className="empty-state mt-6">
          <div className="empty-state-icon">Search</div>
          <p className="empty-state-title">No products found</p>
          <p className="empty-state-desc">Try adjusting your search and filters</p>
          <button
            disabled={busy}
            onClick={onResetFilters}
            className="btn-primary inline-block px-6 py-2.5 text-sm disabled:cursor-not-allowed disabled:opacity-60"
          >
            Reset Filters
          </button>
        </div>
      )}

      <Pagination
        currentPage={page}
        totalPages={totalPages}
        onPageChange={onPageChange}
        disabled={busy}
      />
    </section>
  );
}
