export type Placement =
  | "HOME_HERO"
  | "HOME_TOP_STRIP"
  | "HOME_MID_LEFT"
  | "HOME_MID_RIGHT"
  | "HOME_BOTTOM_GRID"
  | "CATEGORY_TOP"
  | "CATEGORY_SIDEBAR"
  | "PRODUCT_DETAIL_SIDE";

export type Size = "HERO" | "WIDE" | "TALL" | "SQUARE" | "STRIP" | "CUSTOM";
export type LinkType = "PRODUCT" | "CATEGORY" | "SEARCH" | "URL" | "NONE";
export type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

export type Poster = {
  id: string;
  name: string;
  slug: string;
  placement: Placement;
  size: Size;
  desktopImage: string;
  mobileImage: string | null;
  linkType: LinkType;
  linkTarget: string | null;
  openInNewTab: boolean;
  title: string | null;
  subtitle: string | null;
  ctaLabel: string | null;
  backgroundColor: string | null;
  sortOrder: number;
  active: boolean;
  deleted: boolean;
  startAt: string | null;
  endAt: string | null;
};

export type PosterAnalytics = {
  id: string;
  name: string;
  slug: string;
  placement: string;
  clickCount: number;
  impressionCount: number;
  clickThroughRate: number;
  lastClickAt: string | null;
  lastImpressionAt: string | null;
  createdAt: string;
};

export type PosterVariant = {
  id: string;
  posterId: string;
  variantName: string;
  weight: number;
  desktopImage: string | null;
  mobileImage: string | null;
  tabletImage: string | null;
  linkUrl: string | null;
  impressions: number;
  clicks: number;
  clickThroughRate: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PosterFormState = {
  id?: string;
  name: string;
  slug: string;
  placement: Placement;
  size: Size;
  desktopImage: string;
  mobileImage: string;
  linkType: LinkType;
  linkTarget: string;
  openInNewTab: boolean;
  title: string;
  subtitle: string;
  ctaLabel: string;
  backgroundColor: string;
  sortOrder: string;
  active: boolean;
  startAt: string;
  endAt: string;
};

export type VariantFormState = {
  variantName: string;
  weight: string;
  desktopImage: string;
  mobileImage: string;
  tabletImage: string;
  linkUrl: string;
  active: boolean;
};

export const placementDefaultSize: Record<Placement, Size> = {
  HOME_HERO: "HERO",
  HOME_TOP_STRIP: "STRIP",
  HOME_MID_LEFT: "SQUARE",
  HOME_MID_RIGHT: "SQUARE",
  HOME_BOTTOM_GRID: "WIDE",
  CATEGORY_TOP: "STRIP",
  CATEGORY_SIDEBAR: "TALL",
  PRODUCT_DETAIL_SIDE: "TALL",
};

export const emptyForm: PosterFormState = {
  name: "",
  slug: "",
  placement: "HOME_TOP_STRIP",
  size: "STRIP",
  desktopImage: "",
  mobileImage: "",
  linkType: "NONE",
  linkTarget: "",
  openInNewTab: false,
  title: "",
  subtitle: "",
  ctaLabel: "",
  backgroundColor: "",
  sortOrder: "0",
  active: true,
  startAt: "",
  endAt: "",
};

export const emptyVariantForm: VariantFormState = {
  variantName: "",
  weight: "50",
  desktopImage: "",
  mobileImage: "",
  tabletImage: "",
  linkUrl: "",
  active: true,
};

export const panelStyle: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: 16,
};

export function slugify(v: string) {
  return v
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

export function toIsoOrNull(v: string) {
  const t = v.trim();
  if (!t) return null;
  const d = new Date(t);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export function toLocalDateTime(v: string | null) {
  if (!v) return "";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function getApiErrorMessage(err: unknown, fallback: string) {
  if (typeof err === "object" && err !== null) {
    const maybe = err as {
      message?: string;
      response?: { data?: { message?: string; error?: string } | string };
    };
    const responseData = maybe.response?.data;
    if (typeof responseData === "string" && responseData.trim()) return responseData.trim();
    if (responseData && typeof responseData === "object") {
      if (typeof responseData.message === "string" && responseData.message.trim()) return responseData.message.trim();
      if (typeof responseData.error === "string" && responseData.error.trim()) return responseData.error.trim();
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) return maybe.message.trim();
  }
  return fallback;
}
