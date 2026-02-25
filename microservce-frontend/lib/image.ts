export function resolveImageUrl(imageName: string | null): string | null {
  if (!imageName) return null;
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map((s) => encodeURIComponent(s)).join("/");
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();

  // CDN path for both product and review images (same bucket)
  if (base) {
    if (normalized.startsWith("products/") || normalized.startsWith("reviews/")) {
      return `${base.replace(/\/+$/, "")}/${normalized}`;
    }
  }

  // API proxy fallback: route to the correct service
  if (normalized.startsWith("reviews/")) {
    return `${apiBase.replace(/\/+$/, "")}/reviews/images/${encoded}`;
  }
  return `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
}
