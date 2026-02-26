import { API_BASE } from "./constants";
import type { PersonalizationProduct, TrackEventPayload } from "./types/personalization";

export type { PersonalizationProduct } from "./types/personalization";

const SESSION_COOKIE = "_ps_sid";

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

function buildHeaders(token?: string | null): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const sessionId = getOrCreateSessionId();
  if (sessionId) headers["X-Session-Id"] = sessionId;
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

type QueuedEvent = { payload: TrackEventPayload; token?: string | null };

let eventQueue: QueuedEvent[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;
const FLUSH_INTERVAL = 2000;

function flushEvents(): void {
  if (eventQueue.length === 0) return;
  const batch = eventQueue.splice(0);
  const token = batch.find((e) => e.token)?.token;
  const payloads = batch.map((e) => e.payload);
  try {
    fetch(`${API_BASE}/personalization/events`, {
      method: "POST",
      headers: buildHeaders(token),
      body: JSON.stringify(payloads),
    }).catch(() => {});
  } catch {}
}

function scheduleFlush(): void {
  if (flushTimer) return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    flushEvents();
  }, FLUSH_INTERVAL);
}

if (typeof window !== "undefined") {
  const onUnload = () => {
    if (eventQueue.length === 0) return;
    const batch = eventQueue.splice(0);
    const token = batch.find((e) => e.token)?.token;
    const payloads = batch.map((e) => e.payload);
    try {
      // Use fetch with keepalive instead of sendBeacon so custom headers
      // (Authorization, X-Session-Id) are actually transmitted.
      fetch(`${API_BASE}/personalization/events`, {
        method: "POST",
        headers: buildHeaders(token),
        body: JSON.stringify(payloads),
        keepalive: true,
      }).catch(() => {});
    } catch {}
  };
  window.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") onUnload();
  });
  window.addEventListener("pagehide", onUnload);
}

export function trackEvent(payload: TrackEventPayload, token?: string | null): void {
  eventQueue.push({ payload, token });
  scheduleFlush();
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

export async function fetchTrending(
  limit: number
): Promise<PersonalizationProduct[]> {
  try {
    const res = await fetch(`${API_BASE}/personalization/trending?limit=${limit}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
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
