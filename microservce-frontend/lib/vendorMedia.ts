import { API_BASE } from "./constants";

export function resolveVendorMediaUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const normalized = value.trim();
  if (!normalized) {
    return null;
  }

  if (/^https?:\/\//i.test(normalized)) {
    return normalized;
  }

  const encoded = normalized
    .replace(/^\/+/, "")
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");

  const cdnBase = (process.env.NEXT_PUBLIC_VENDOR_MEDIA_BASE_URL || "").trim().replace(/\/+$/, "");
  if (cdnBase) {
    return `${cdnBase}/${encoded}`;
  }

  return `${API_BASE}/vendors/media/${encoded}`;
}
