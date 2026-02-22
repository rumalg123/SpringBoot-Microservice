/**
 * Tiny client-side event bus for nav badge real-time updates.
 * Call emitCartUpdate() or emitWishlistUpdate() after any mutation
 * so CartNavWidget / WishlistNavWidget can re-fetch without a page reload.
 */

export function emitCartUpdate() {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("cart-updated"));
    }
}

export function emitWishlistUpdate() {
    if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("wishlist-updated"));
    }
}
