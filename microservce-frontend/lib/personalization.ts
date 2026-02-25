const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
const SESSION_COOKIE = "_ps_sid";

export type PersonalizationProduct = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  brandName: string | null;
  mainImage: string | null;
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  sku: string;
  mainCategory: string | null;
  categories: string[] | null;
  vendorId: string | null;
  viewCount: number;
  soldCount: number;
  stockAvailable: number | null;
  stockStatus: string | null;
};

export function getOrCreateSessionId(): string {
  if (typeof document === "undefined") return "";
  const match = document.cookie.match(new RegExp(`(?:^|; )${SESSION_COOKIE}=([^;]*)`));
  if (match) return decodeURIComponent(match[1]);
  const id = crypto.randomUUID();
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${SESSION_COOKIE}=${id}; path=/; max-age=${365 * 24 * 60 * 60}; SameSite=Lax${secure}`;
  return id;
}

export function getSessionId(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp(`(?:^|; )${SESSION_COOKIE}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

type TrackEventPayload = {
  eventType: "PRODUCT_VIEW" | "ADD_TO_CART" | "PURCHASE" | "WISHLIST_ADD" | "SEARCH";
  productId: string;
  categorySlugs?: string;
  vendorId?: string;
  brandName?: string;
  price?: number;
  metadata?: string;
};

function buildHeaders(token?: string | null): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const sessionId = getOrCreateSessionId();
  if (sessionId) headers["X-Session-Id"] = sessionId;
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

export function trackEvent(payload: TrackEventPayload, token?: string | null): void {
  try {
    fetch(`${API_BASE}/personalization/events`, {
      method: "POST",
      headers: buildHeaders(token),
      body: JSON.stringify(payload),
    }).catch(() => {});
  } catch {}
}

export function trackProductView(
  product: { id: string; categories?: string[] | null; vendorId?: string | null; brandName?: string | null; sellingPrice?: number },
  token?: string | null
): void {
  trackEvent(
    {
      eventType: "PRODUCT_VIEW",
      productId: product.id,
      categorySlugs: product.categories?.join(","),
      vendorId: product.vendorId ?? undefined,
      brandName: product.brandName ?? undefined,
      price: product.sellingPrice,
    },
    token
  );
}

export function trackAddToCart(
  product: { id: string; categories?: string[] | null; vendorId?: string | null; brandName?: string | null; sellingPrice?: number },
  token?: string | null
): void {
  trackEvent(
    {
      eventType: "ADD_TO_CART",
      productId: product.id,
      categorySlugs: product.categories?.join(","),
      vendorId: product.vendorId ?? undefined,
      brandName: product.brandName ?? undefined,
      price: product.sellingPrice,
    },
    token
  );
}

export function trackPurchase(
  products: { id: string; price?: number }[],
  token?: string | null
): void {
  for (const p of products) {
    trackEvent({ eventType: "PURCHASE", productId: p.id, price: p.price }, token);
  }
}

export function trackWishlistAdd(
  product: { id: string; categories?: string[] | null; vendorId?: string | null; brandName?: string | null; sellingPrice?: number },
  token?: string | null
): void {
  trackEvent(
    {
      eventType: "WISHLIST_ADD",
      productId: product.id,
      categorySlugs: product.categories?.join(","),
      vendorId: product.vendorId ?? undefined,
      brandName: product.brandName ?? undefined,
      price: product.sellingPrice,
    },
    token
  );
}

export async function mergeSession(token: string): Promise<void> {
  const sessionId = getSessionId();
  if (!sessionId) return;
  const merged = typeof localStorage !== "undefined" && localStorage.getItem("_ps_merged");
  if (merged) return;
  try {
    await fetch(`${API_BASE}/personalization/sessions/merge`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Session-Id": sessionId,
      },
    });
    if (typeof localStorage !== "undefined") {
      localStorage.setItem("_ps_merged", "1");
    }
  } catch {}
}

export async function fetchRecommended(
  limit: number,
  token?: string | null
): Promise<PersonalizationProduct[]> {
  try {
    const res = await fetch(`${API_BASE}/personalization/me/recommended?limit=${limit}`, {
      headers: buildHeaders(token),
      cache: "no-store",
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export async function fetchRecentlyViewed(
  limit: number,
  token?: string | null
): Promise<PersonalizationProduct[]> {
  try {
    const res = await fetch(`${API_BASE}/personalization/me/recently-viewed?limit=${limit}`, {
      headers: buildHeaders(token),
      cache: "no-store",
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export async function fetchSimilarProducts(
  productId: string,
  limit: number
): Promise<PersonalizationProduct[]> {
  try {
    const res = await fetch(
      `${API_BASE}/personalization/products/${productId}/similar?limit=${limit}`,
      { cache: "no-store" }
    );
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export async function fetchBoughtTogether(
  productId: string,
  limit: number
): Promise<PersonalizationProduct[]> {
  try {
    const res = await fetch(
      `${API_BASE}/personalization/products/${productId}/bought-together?limit=${limit}`,
      { cache: "no-store" }
    );
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}
