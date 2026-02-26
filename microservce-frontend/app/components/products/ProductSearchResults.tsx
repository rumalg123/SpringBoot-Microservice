"use client";

import Pagination from "../Pagination";
import ProductCard from "../catalog/ProductCard";
import ProductCardSkeleton from "../catalog/ProductCardSkeleton";
import type { ProductSummary } from "../../../lib/types";

type ProductSearchResultsProps = {
  products: ProductSummary[];
  productsLoading: boolean;
  page: number;
  totalPages: number;
  isAuthenticated: boolean;
  isWishlisted: (id: string) => boolean;
  isWishlistBusy: (id: string) => boolean;
  onWishlistToggle: (e: React.MouseEvent<HTMLButtonElement>, id: string) => void;
  onPageChange: (page: number) => void;
  onClearFilters: () => void;
};

export default function ProductSearchResults({
  products,
  productsLoading,
  page,
  totalPages,
  isAuthenticated,
  isWishlisted,
  isWishlistBusy,
  onWishlistToggle,
  onPageChange,
  onClearFilters,
}: ProductSearchResultsProps) {
  return (
    <section>
      {/* Loading skeletons */}
      {productsLoading && (
        <div className="grid gap-4" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))" }}>
          <ProductCardSkeleton count={6} />
        </div>
      )}

      {!productsLoading && (
        <div className="grid gap-4" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))" }}>
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
      )}

      {/* Empty state */}
      {products.length === 0 && !productsLoading && (
        <div className="empty-state mt-6">
          <div className="empty-state-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
          </div>
          <p className="empty-state-title">No products found</p>
          <p className="empty-state-desc">Try adjusting your search or filter criteria</p>
          <button
            disabled={productsLoading}
            onClick={onClearFilters}
            className="btn-primary mt-2"
          >
            Clear All Filters
          </button>
        </div>
      )}

      <Pagination currentPage={page} totalPages={totalPages} onPageChange={onPageChange} disabled={productsLoading} />
    </section>
  );
}
