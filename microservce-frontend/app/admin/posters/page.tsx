"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import ConfirmModal from "../../components/ConfirmModal";
import PosterFormField from "../../components/posters/admin/PosterFormField";
import PosterLinkTargetEditor from "../../components/posters/admin/PosterLinkTargetEditor";
import StatusBadge, { ACTIVE_INACTIVE_COLORS } from "../../components/ui/StatusBadge";
import { useAuthSession } from "../../../lib/authSession";

type Placement =
  | "HOME_HERO"
  | "HOME_TOP_STRIP"
  | "HOME_MID_LEFT"
  | "HOME_MID_RIGHT"
  | "HOME_BOTTOM_GRID"
  | "CATEGORY_TOP"
  | "CATEGORY_SIDEBAR"
  | "PRODUCT_DETAIL_SIDE";
type Size = "HERO" | "WIDE" | "TALL" | "SQUARE" | "STRIP" | "CUSTOM";
type LinkType = "PRODUCT" | "CATEGORY" | "SEARCH" | "URL" | "NONE";
type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

type Poster = {
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

type PosterAnalytics = {
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

type PosterVariant = {
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

type FormState = {
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

type VariantFormState = {
  variantName: string;
  weight: string;
  desktopImage: string;
  mobileImage: string;
  tabletImage: string;
  linkUrl: string;
  active: boolean;
};

const placementDefaultSize: Record<Placement, Size> = {
  HOME_HERO: "HERO",
  HOME_TOP_STRIP: "STRIP",
  HOME_MID_LEFT: "SQUARE",
  HOME_MID_RIGHT: "SQUARE",
  HOME_BOTTOM_GRID: "WIDE",
  CATEGORY_TOP: "STRIP",
  CATEGORY_SIDEBAR: "TALL",
  PRODUCT_DETAIL_SIDE: "TALL",
};

const emptyForm: FormState = {
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

const emptyVariantForm: VariantFormState = {
  variantName: "",
  weight: "50",
  desktopImage: "",
  mobileImage: "",
  tabletImage: "",
  linkUrl: "",
  active: true,
};

const fieldBaseStyle: React.CSSProperties = {
  background: "var(--surface-2)",
  borderColor: "var(--line)",
  color: "var(--ink)",
};

const panelStyle: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)",
  border: "1px solid var(--line)",
  borderRadius: 16,
  padding: 16,
};

function slugify(v: string) {
  return v.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}

function toIsoOrNull(v: string) {
  const t = v.trim();
  if (!t) return null;
  const d = new Date(t);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

function toLocalDateTime(v: string | null) {
  if (!v) return "";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function posterImageSources(key: string | null): { primary: string | null; fallback: string | null } {
  if (!key) return { primary: null, fallback: null };
  const normalized = key.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map(encodeURIComponent).join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/posters/images/${encoded}`;
  const cdnBase = (process.env.NEXT_PUBLIC_POSTER_IMAGE_BASE_URL || "").trim();
  if (!cdnBase) return { primary: apiUrl, fallback: null };
  if (normalized.startsWith("posters/")) {
    return {
      primary: `${cdnBase.replace(/\/+$/, "")}/${normalized}`,
      fallback: apiUrl,
    };
  }
  return { primary: apiUrl, fallback: null };
}

function PosterImageFill({ imageKey, alt }: { imageKey: string | null; alt: string }) {
  const sources = useMemo(() => posterImageSources(imageKey), [imageKey]);
  const [src, setSrc] = useState<string | null>(sources.primary);

  useEffect(() => {
    setSrc(sources.primary);
  }, [sources.primary]);

  if (!src) return null;

  return (
    <Image
      src={src}
      alt={alt}
      fill
      unoptimized
      style={{ objectFit: "cover" }}
      onError={() => {
        if (sources.fallback && src !== sources.fallback) {
          setSrc(sources.fallback);
          return;
        }
        setSrc(null);
      }}
    />
  );
}

/* badgeStyle replaced by StatusBadge + ACTIVE_INACTIVE_COLORS from components/ui/StatusBadge */

function getApiErrorMessage(err: unknown, fallback: string) {
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

const statCardStyle: React.CSSProperties = {
  background: "rgba(255,255,255,0.04)",
  border: "1px solid var(--line)",
  borderRadius: 12,
  padding: "14px 18px",
  textAlign: "center",
};

const variantTableCellStyle: React.CSSProperties = {
  padding: "8px 10px",
  borderBottom: "1px solid var(--line)",
  fontSize: "0.8rem",
  color: "var(--ink-light)",
};

export default function AdminPostersPage() {
  const router = useRouter();
  const session = useAuthSession();
  const [items, setItems] = useState<Poster[]>([]);
  const [deletedItems, setDeletedItems] = useState<Poster[]>([]);
  const [deletedLoaded, setDeletedLoaded] = useState(false);
  const [showDeleted, setShowDeleted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState("Loading posters...");
  const [form, setForm] = useState<FormState>(emptyForm);
  const [slugEdited, setSlugEdited] = useState(false);
  const [slugStatus, setSlugStatus] = useState<SlugStatus>("idle");
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Poster | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [restoreId, setRestoreId] = useState<string | null>(null);

  // Analytics state
  const [analytics, setAnalytics] = useState<PosterAnalytics[]>([]);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [showAnalytics, setShowAnalytics] = useState(false);

  // Variants state
  const [variants, setVariants] = useState<PosterVariant[]>([]);
  const [variantsLoading, setVariantsLoading] = useState(false);
  const [variantForm, setVariantForm] = useState<VariantFormState>(emptyVariantForm);
  const [showVariantForm, setShowVariantForm] = useState(false);
  const [editingVariantId, setEditingVariantId] = useState<string | null>(null);
  const [savingVariant, setSavingVariant] = useState(false);
  const [deleteVariantTarget, setDeleteVariantTarget] = useState<PosterVariant | null>(null);
  const [deletingVariant, setDeletingVariant] = useState(false);

  const list = showDeleted ? deletedItems : items;
  const grouped = useMemo(() => {
    const m = new Map<string, Poster[]>();
    for (const p of list) {
      const arr = m.get(p.placement) || [];
      arr.push(p);
      m.set(p.placement, arr);
    }
    return m;
  }, [list]);

  // Analytics summary computed from fetched data
  const analyticsSummary = useMemo(() => {
    if (analytics.length === 0) return { totalImpressions: 0, totalClicks: 0, avgCtr: 0 };
    const totalImpressions = analytics.reduce((sum, a) => sum + a.impressionCount, 0);
    const totalClicks = analytics.reduce((sum, a) => sum + a.clickCount, 0);
    const avgCtr = analytics.reduce((sum, a) => sum + a.clickThroughRate, 0) / analytics.length;
    return { totalImpressions, totalClicks, avgCtr };
  }, [analytics]);

  // Build a lookup map from poster id -> analytics
  const analyticsMap = useMemo(() => {
    const m = new Map<string, PosterAnalytics>();
    for (const a of analytics) m.set(a.id, a);
    return m;
  }, [analytics]);

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((s) => ({ ...s, [key]: value }));
  const setVariantField = <K extends keyof VariantFormState>(key: K, value: VariantFormState[K]) => setVariantForm((s) => ({ ...s, [key]: value }));

  const load = async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const a = await session.apiClient.get("/admin/posters");
      const raw = a.data as { content?: Poster[] };
      setItems(raw.content || []);
      setStatus("Posters loaded.");
    } catch (e) {
      setStatus(getApiErrorMessage(e, "Failed to load posters."));
    } finally {
      setLoading(false);
    }
  };

  const loadDeleted = async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const d = await session.apiClient.get("/admin/posters/deleted");
      const raw = d.data as { content?: Poster[] };
      setDeletedItems(raw.content || []);
      setDeletedLoaded(true);
      setStatus("Deleted posters loaded.");
    } catch (e) {
      setStatus(getApiErrorMessage(e, "Failed to load deleted posters."));
    } finally {
      setLoading(false);
    }
  };

  const loadAnalytics = async () => {
    if (!session.apiClient) return;
    setAnalyticsLoading(true);
    try {
      const res = await session.apiClient.get("/admin/posters/analytics");
      setAnalytics((res.data as PosterAnalytics[]) || []);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to load analytics."));
    } finally {
      setAnalyticsLoading(false);
    }
  };

  const loadVariants = async (posterId: string) => {
    if (!session.apiClient) return;
    setVariantsLoading(true);
    try {
      const res = await session.apiClient.get(`/admin/posters/${posterId}/variants`);
      setVariants((res.data as PosterVariant[]) || []);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to load variants."));
    } finally {
      setVariantsLoading(false);
    }
  };

  const saveVariant = async () => {
    if (!session.apiClient || !form.id || savingVariant) return;
    if (!variantForm.variantName.trim()) {
      toast.error("Variant name is required");
      return;
    }
    const weight = Number(variantForm.weight);
    if (!Number.isFinite(weight) || weight < 1 || weight > 100) {
      toast.error("Weight must be between 1 and 100");
      return;
    }
    setSavingVariant(true);
    try {
      const payload = {
        variantName: variantForm.variantName.trim(),
        weight,
        desktopImage: variantForm.desktopImage.trim() || undefined,
        mobileImage: variantForm.mobileImage.trim() || undefined,
        tabletImage: variantForm.tabletImage.trim() || undefined,
        linkUrl: variantForm.linkUrl.trim() || undefined,
        active: variantForm.active,
      };
      if (editingVariantId) {
        await session.apiClient.put(`/admin/posters/${form.id}/variants/${editingVariantId}`, payload);
        toast.success("Variant updated");
      } else {
        await session.apiClient.post(`/admin/posters/${form.id}/variants`, payload);
        toast.success("Variant created");
      }
      setVariantForm(emptyVariantForm);
      setShowVariantForm(false);
      setEditingVariantId(null);
      await loadVariants(form.id);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to save variant"));
    } finally {
      setSavingVariant(false);
    }
  };

  const deleteVariant = async () => {
    if (!session.apiClient || !form.id || !deleteVariantTarget || deletingVariant) return;
    setDeletingVariant(true);
    try {
      await session.apiClient.delete(`/admin/posters/${form.id}/variants/${deleteVariantTarget.id}`);
      toast.success("Variant deleted");
      setDeleteVariantTarget(null);
      await loadVariants(form.id);
    } catch (e) {
      toast.error(getApiErrorMessage(e, "Failed to delete variant"));
    } finally {
      setDeletingVariant(false);
    }
  };

  const editVariant = (v: PosterVariant) => {
    setVariantForm({
      variantName: v.variantName,
      weight: String(v.weight),
      desktopImage: v.desktopImage || "",
      mobileImage: v.mobileImage || "",
      tabletImage: v.tabletImage || "",
      linkUrl: v.linkUrl || "",
      active: v.active,
    });
    setEditingVariantId(v.id);
    setShowVariantForm(true);
  };

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) {
      router.replace("/");
      return;
    }
    if (!session.canManageAdminPosters) {
      router.replace("/products");
      return;
    }
    void load();
  }, [session.status, session.isAuthenticated, session.canManageAdminPosters, router]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!showDeleted || deletedLoaded) return;
    if (!session.apiClient) return;
    void loadDeleted();
  }, [showDeleted, deletedLoaded, session.apiClient]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load analytics when toggled on
  useEffect(() => {
    if (!showAnalytics || !session.apiClient) return;
    void loadAnalytics();
  }, [showAnalytics, session.apiClient]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load variants when editing a poster
  useEffect(() => {
    if (!form.id || !session.apiClient) {
      setVariants([]);
      setShowVariantForm(false);
      setEditingVariantId(null);
      setVariantForm(emptyVariantForm);
      return;
    }
    void loadVariants(form.id);
  }, [form.id, session.apiClient]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (slugEdited) return;
    setForm((s) => ({ ...s, slug: slugify(s.name) }));
  }, [form.name, slugEdited]);

  useEffect(() => {
    if (!session.apiClient) return;
    const slug = slugify(form.slug);
    if (!slug) {
      setSlugStatus("invalid");
      return;
    }
    let cancelled = false;
    const t = window.setTimeout(async () => {
      setSlugStatus("checking");
      try {
        const params = new URLSearchParams({ slug });
        if (form.id) params.set("excludeId", form.id);
        const res = await session.apiClient!.get(`/posters/slug-available?${params.toString()}`);
        if (cancelled) return;
        setSlugStatus((res.data as { available?: boolean }).available ? "available" : "taken");
      } catch {
        if (!cancelled) setSlugStatus("idle");
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(t);
    };
  }, [form.slug, form.id, session.apiClient]);

  const edit = (p: Poster) => {
    setForm({
      id: p.id,
      name: p.name,
      slug: p.slug,
      placement: p.placement,
      size: p.size,
      desktopImage: p.desktopImage || "",
      mobileImage: p.mobileImage || "",
      linkType: p.linkType,
      linkTarget: p.linkTarget || "",
      openInNewTab: p.openInNewTab,
      title: p.title || "",
      subtitle: p.subtitle || "",
      ctaLabel: p.ctaLabel || "",
      backgroundColor: p.backgroundColor || "",
      sortOrder: String(p.sortOrder ?? 0),
      active: p.active,
      startAt: toLocalDateTime(p.startAt),
      endAt: toLocalDateTime(p.endAt),
    });
    setSlugEdited(true);
    setShowDeleted(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const duplicate = (p: Poster) => {
    setForm({
      ...emptyForm,
      name: `${p.name} Copy`,
      placement: p.placement,
      size: p.size,
      desktopImage: p.desktopImage || "",
      mobileImage: p.mobileImage || "",
      linkType: p.linkType,
      linkTarget: p.linkTarget || "",
      openInNewTab: p.openInNewTab,
      title: p.title || "",
      subtitle: p.subtitle || "",
      ctaLabel: p.ctaLabel || "",
      backgroundColor: p.backgroundColor || "",
      sortOrder: String(p.sortOrder ?? 0),
      active: p.active,
      startAt: toLocalDateTime(p.startAt),
      endAt: toLocalDateTime(p.endAt),
    });
    setSlugEdited(false);
    setShowDeleted(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const applyPreset = (placement: Placement) => {
    setForm((s) => ({
      ...s,
      placement,
      size: placementDefaultSize[placement],
      ctaLabel: s.ctaLabel || (placement === "HOME_HERO" ? "Shop Now" : ""),
    }));
  };

  const uploadImage = async (e: ChangeEvent<HTMLInputElement>, target: "desktopImage" | "mobileImage") => {
    const files = Array.from(e.target.files || []);
    e.target.value = "";
    if (!files.length || !session.apiClient) return;
    setUploading(true);
    try {
      const namesRes = await session.apiClient.post("/admin/posters/images/names", { fileNames: [files[0].name] });
      const key = ((namesRes.data as { images?: string[] }).images || [])[0];
      if (!key) throw new Error("Could not prepare image name");
      const fd = new FormData();
      fd.append("files", files[0]);
      fd.append("keys", key);
      const upRes = await session.apiClient.post("/admin/posters/images", fd, { headers: { "Content-Type": "multipart/form-data" } });
      const uploaded = ((upRes.data as { images?: string[] }).images || [])[0];
      if (!uploaded) throw new Error("Upload failed");
      setField(target, uploaded as FormState[typeof target]);
      toast.success("Image uploaded");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Upload failed"));
    } finally {
      setUploading(false);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!session.apiClient || submitting) return;
    setSubmitting(true);
    try {
      const slug = slugify(form.slug || form.name);
      if (!form.name.trim()) throw new Error("Name is required");
      if (!slug) throw new Error("Slug is required");
      if (!form.desktopImage.trim()) throw new Error("Desktop image is required");
      if (slugStatus === "taken") throw new Error("Slug already taken");
      const sortOrder = Number(form.sortOrder || "0");
      if (!Number.isFinite(sortOrder)) throw new Error("Sort order must be a number");

      const payload = {
        name: form.name.trim(),
        slug,
        placement: form.placement,
        size: form.size,
        desktopImage: form.desktopImage.trim(),
        mobileImage: form.mobileImage.trim() || null,
        linkType: form.linkType,
        linkTarget: form.linkType === "NONE" ? null : (form.linkTarget.trim() || null),
        openInNewTab: form.openInNewTab,
        title: form.title.trim() || null,
        subtitle: form.subtitle.trim() || null,
        ctaLabel: form.ctaLabel.trim() || null,
        backgroundColor: form.backgroundColor.trim() || null,
        sortOrder,
        active: form.active,
        startAt: toIsoOrNull(form.startAt),
        endAt: toIsoOrNull(form.endAt),
      };

      if (form.id) await session.apiClient.put(`/admin/posters/${form.id}`, payload);
      else await session.apiClient.post("/admin/posters", payload);

      toast.success(form.id ? "Poster updated" : "Poster created");
      setForm(emptyForm);
      setSlugEdited(false);
      await load();
      if (deletedLoaded) await loadDeleted();
      if (showAnalytics) await loadAnalytics();
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Save failed"));
    } finally {
      setSubmitting(false);
    }
  };

  const deletePoster = async () => {
    if (!session.apiClient || !deleteTarget || deleteBusy) return;
    setDeleteBusy(true);
    try {
      await session.apiClient.delete(`/admin/posters/${deleteTarget.id}`);
      toast.success("Poster deleted");
      setDeleteTarget(null);
      if (form.id === deleteTarget.id) {
        setForm(emptyForm);
        setSlugEdited(false);
      }
      await load();
      if (deletedLoaded) await loadDeleted();
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Delete failed"));
    } finally {
      setDeleteBusy(false);
    }
  };

  const restore = async (id: string) => {
    if (!session.apiClient || restoreId) return;
    setRestoreId(id);
    try {
      await session.apiClient.post(`/admin/posters/${id}/restore`);
      toast.success("Poster restored");
      await load();
      if (deletedLoaded) await loadDeleted();
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Restore failed"));
    } finally {
      setRestoreId(null);
    }
  };

  if (session.status === "loading" || session.status === "idle") {
    return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><div className="spinner-lg" /></div>;
  }
  if (!session.isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav
        email={(session.profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={session.canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={session.apiClient}
        emailVerified={session.emailVerified}
        onLogout={() => { void session.logout(); }}
      />
      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link><span className="breadcrumb-sep">&gt;</span>
          <Link href="/admin/products">Admin</Link><span className="breadcrumb-sep">&gt;</span>
          <span className="breadcrumb-current">Posters</span>
        </nav>

        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 style={{ margin: 0, color: "var(--ink)", fontFamily: "'Syne', sans-serif", fontWeight: 800 }}>Admin Posters</h1>
            <p style={{ margin: "4px 0 0", color: "var(--muted)", fontSize: "0.85rem" }}>Placement presets, image uploads, auto slug, scheduling, and duplicate flow.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={() => setShowAnalytics((v) => !v)} disabled={analyticsLoading} style={{ padding: "8px 12px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: showAnalytics ? "rgba(59,130,246,0.12)" : "var(--brand-soft)", color: showAnalytics ? "#60a5fa" : "var(--brand)", fontWeight: 700, opacity: analyticsLoading ? 0.7 : 1, cursor: analyticsLoading ? "not-allowed" : "pointer" }}>
              {analyticsLoading ? "Loading..." : showAnalytics ? "Hide Analytics" : "Show Analytics"}
            </button>
            <button type="button" onClick={() => setShowDeleted((v) => !v)} disabled={loading} style={{ padding: "8px 12px", borderRadius: "10px", border: "1px solid var(--line-bright)", background: showDeleted ? "rgba(239,68,68,0.1)" : "var(--brand-soft)", color: showDeleted ? "#f87171" : "var(--brand)", fontWeight: 700, opacity: loading ? 0.7 : 1, cursor: loading ? "not-allowed" : "pointer" }}>
              {showDeleted ? "Showing Deleted" : "Show Deleted"}
            </button>
          </div>
        </div>

        {/* Analytics Summary */}
        {showAnalytics && (
          <section className="mb-5" style={panelStyle}>
            <h2 style={{ margin: "0 0 12px", color: "var(--ink)", fontSize: "1.05rem" }}>Analytics Overview</h2>
            {analyticsLoading ? (
              <div className="skeleton" style={{ height: 80, borderRadius: 12 }} />
            ) : analytics.length === 0 ? (
              <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>No analytics data available yet.</p>
            ) : (
              <>
                <div className="grid gap-3" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))" }}>
                  <div style={statCardStyle}>
                    <div style={{ fontSize: "1.5rem", fontWeight: 800, color: "#60a5fa" }}>{analyticsSummary.totalImpressions.toLocaleString()}</div>
                    <div style={{ fontSize: "0.75rem", color: "var(--muted)", marginTop: 2 }}>Total Impressions</div>
                  </div>
                  <div style={statCardStyle}>
                    <div style={{ fontSize: "1.5rem", fontWeight: 800, color: "#34d399" }}>{analyticsSummary.totalClicks.toLocaleString()}</div>
                    <div style={{ fontSize: "0.75rem", color: "var(--muted)", marginTop: 2 }}>Total Clicks</div>
                  </div>
                  <div style={statCardStyle}>
                    <div style={{ fontSize: "1.5rem", fontWeight: 800, color: "#c084fc" }}>{analyticsSummary.avgCtr.toFixed(2)}%</div>
                    <div style={{ fontSize: "0.75rem", color: "var(--muted)", marginTop: 2 }}>Average CTR</div>
                  </div>
                </div>

                {/* Per-poster analytics table */}
                <div style={{ marginTop: 16, overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr style={{ borderBottom: "2px solid var(--line)" }}>
                        {["Poster", "Placement", "Impressions", "Clicks", "CTR", "Last Click", "Last Impression"].map((h) => (
                          <th key={h} style={{ padding: "8px 10px", textAlign: "left", fontSize: "0.72rem", color: "var(--muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {analytics.map((a) => (
                        <tr key={a.id} style={{ borderBottom: "1px solid var(--line)" }}>
                          <td style={{ padding: "8px 10px", fontSize: "0.82rem", color: "var(--ink)" }}>{a.name}</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.78rem", color: "var(--ink-light)" }}>{a.placement}</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.82rem", color: "#60a5fa", fontWeight: 600 }}>{a.impressionCount.toLocaleString()}</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.82rem", color: "#34d399", fontWeight: 600 }}>{a.clickCount.toLocaleString()}</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.82rem", color: "#c084fc", fontWeight: 600 }}>{a.clickThroughRate.toFixed(2)}%</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.75rem", color: "var(--muted)" }}>{a.lastClickAt ? new Date(a.lastClickAt).toLocaleDateString() : "--"}</td>
                          <td style={{ padding: "8px 10px", fontSize: "0.75rem", color: "var(--muted)" }}>{a.lastImpressionAt ? new Date(a.lastImpressionAt).toLocaleDateString() : "--"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </section>
        )}

        <section className="mb-5" style={panelStyle}>
          <form onSubmit={(e) => { void submit(e); }} className="grid gap-3">
            <div className="grid gap-3 md:grid-cols-2">
              <PosterFormField label="Poster Name">
                <input value={form.name} onChange={(e) => setField("name", e.target.value)} placeholder="Summer Sale Hero Banner" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
              </PosterFormField>
              <PosterFormField label="Slug" hint="Auto-fills from poster name. You can edit it.">
                <div className="flex gap-2">
                  <input value={form.slug} onChange={(e) => { setSlugEdited(true); setField("slug", slugify(e.target.value)); }} placeholder="summer-sale-hero-banner" className="w-full rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                  <button type="button" onClick={() => { setSlugEdited(false); setField("slug", slugify(form.name)); }} style={{ padding: "0 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700 }}>Auto</button>
                </div>
              </PosterFormField>
            </div>

            <div style={{ fontSize: "0.72rem", color: slugStatus === "taken" ? "#f87171" : slugStatus === "available" ? "#34d399" : "var(--muted-2)" }}>
              {slugStatus === "taken" ? "Slug taken" : slugStatus === "available" ? "Slug available" : slugStatus === "checking" ? "Checking slug..." : "Slug auto-fills from name"}
            </div>

            <div className="grid gap-3 md:grid-cols-4">
              <PosterFormField label="Placement">
                <select value={form.placement} onChange={(e) => applyPreset(e.target.value as Placement)} className="poster-select rounded-lg border px-3 py-2.5" style={fieldBaseStyle}>
                  {Object.keys(placementDefaultSize).map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </PosterFormField>
              <PosterFormField label="Size" hint="Placement can auto-suggest size.">
                <select value={form.size} onChange={(e) => setField("size", e.target.value as Size)} className="poster-select rounded-lg border px-3 py-2.5" style={fieldBaseStyle}>
                  {["HERO", "WIDE", "TALL", "SQUARE", "STRIP", "CUSTOM"].map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </PosterFormField>
              <PosterFormField label="Sort Order">
                <input value={form.sortOrder} onChange={(e) => setField("sortOrder", e.target.value)} placeholder="0" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
              </PosterFormField>
              <PosterFormField label="Status">
                <label className="flex items-center gap-2 rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, display: "flex" }}><input type="checkbox" checked={form.active} onChange={(e) => setField("active", e.target.checked)} />Active poster</label>
              </PosterFormField>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <PosterFormField label="Desktop Image" hint="Required. Upload or paste object key.">
                <div className="grid gap-2">
                  <div className="flex gap-2">
                    <input value={form.desktopImage} onChange={(e) => setField("desktopImage", e.target.value)} placeholder="posters/uuid.jpg" className="w-full rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    <label style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700, cursor: uploading ? "not-allowed" : "pointer" }}>
                      {uploading ? "..." : "Upload"}
                      <input hidden type="file" accept="image/*" onChange={(e) => { void uploadImage(e, "desktopImage"); }} />
                    </label>
                  </div>
                  <div style={{ position: "relative", height: 66, borderRadius: 8, overflow: "hidden", border: "1px solid var(--line)", background: "var(--surface-3)" }}>
                    {form.desktopImage
                      ? <PosterImageFill imageKey={form.desktopImage} alt="Desktop poster preview" />
                      : <div style={{ height: "100%", display: "grid", placeItems: "center", color: "var(--muted)", fontSize: "0.75rem" }}>Desktop preview</div>}
                  </div>
                </div>
              </PosterFormField>
              <PosterFormField label="Mobile Image (Optional)" hint="Leave empty if frontend should use desktop image.">
                <div className="grid gap-2">
                  <div className="flex gap-2">
                    <input value={form.mobileImage} onChange={(e) => setField("mobileImage", e.target.value)} placeholder="posters/uuid-mobile.jpg" className="w-full rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    <button type="button" onClick={() => setField("mobileImage", form.desktopImage)} style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700 }}>Copy</button>
                    <label style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700, cursor: uploading ? "not-allowed" : "pointer" }}>
                      {uploading ? "..." : "Upload"}
                      <input hidden type="file" accept="image/*" onChange={(e) => { void uploadImage(e, "mobileImage"); }} />
                    </label>
                  </div>
                  <div style={{ position: "relative", height: 66, borderRadius: 8, overflow: "hidden", border: "1px solid var(--line)", background: "var(--surface-3)" }}>
                    {form.mobileImage
                      ? <PosterImageFill imageKey={form.mobileImage || null} alt="Mobile poster preview" />
                      : <div style={{ height: "100%", display: "grid", placeItems: "center", color: "var(--muted)", fontSize: "0.75rem" }}>Mobile preview</div>}
                  </div>
                </div>
              </PosterFormField>
            </div>

            <PosterLinkTargetEditor
              apiClient={session.apiClient}
              linkType={form.linkType}
              linkTarget={form.linkTarget}
              openInNewTab={form.openInNewTab}
              fieldBaseStyle={fieldBaseStyle}
              disabled={submitting}
              onLinkTypeChange={(value) => setField("linkType", value)}
              onLinkTargetChange={(value) => setField("linkTarget", value)}
              onOpenInNewTabChange={(value) => setField("openInNewTab", value)}
            />

            <div className="grid gap-3 md:grid-cols-2">
              <PosterFormField label="Title (Optional)">
                <input value={form.title} onChange={(e) => setField("title", e.target.value)} placeholder="Main headline" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
              </PosterFormField>
              <PosterFormField label="CTA Label (Optional)">
                <div className="flex gap-2">
                  <input value={form.ctaLabel} onChange={(e) => setField("ctaLabel", e.target.value)} placeholder="Shop Now" className="w-full rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                  <button type="button" onClick={() => { setField("title", form.title || form.name); setField("ctaLabel", form.ctaLabel || "Shop Now"); }} style={{ padding: "0 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700 }}>Autofill</button>
                </div>
              </PosterFormField>
            </div>

            <PosterFormField label="Subtitle (Optional)">
              <textarea value={form.subtitle} onChange={(e) => setField("subtitle", e.target.value)} rows={2} placeholder="Supporting text for the campaign banner" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
            </PosterFormField>

            <div className="grid gap-3 md:grid-cols-3">
              <PosterFormField label="Background Color / CSS (Optional)" hint="Use a hex color or CSS gradient string.">
                <div className="flex gap-2">
                  <input value={form.backgroundColor} onChange={(e) => setField("backgroundColor", e.target.value)} placeholder="#0f172a or linear-gradient(...)" className="w-full rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                  <div style={{ width: 44, borderRadius: 8, border: "1px solid var(--line)", background: form.backgroundColor || "var(--surface-3)" }} />
                </div>
              </PosterFormField>
              <PosterFormField label="Start At (Optional)" hint="Local time input; backend stores UTC.">
                <input type="datetime-local" value={form.startAt} onChange={(e) => setField("startAt", e.target.value)} className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
              </PosterFormField>
              <PosterFormField label="End At (Optional)" hint="Must be after start time.">
                <input type="datetime-local" value={form.endAt} onChange={(e) => setField("endAt", e.target.value)} className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
              </PosterFormField>
            </div>

            <div className="flex flex-wrap gap-2">
              <button type="submit" disabled={submitting || slugStatus === "taken"} className="btn-primary" style={{ padding: "10px 14px", borderRadius: 10, fontWeight: 800 }}>
                {submitting ? (form.id ? "Saving..." : "Creating...") : (form.id ? "Save Poster" : "Create Poster")}
              </button>
              {form.id && <button type="button" onClick={() => { setForm(emptyForm); setSlugEdited(false); }} style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid var(--line)", background: "var(--surface-2)", color: "var(--ink-light)", fontWeight: 700 }}>Cancel Edit</button>}
            </div>
          </form>

          {/* A/B Variants Section - shown only when editing a poster */}
          {form.id && (
            <div style={{ marginTop: 24, borderTop: "1px solid var(--line)", paddingTop: 20 }}>
              <div className="mb-3 flex items-center justify-between">
                <h3 style={{ margin: 0, color: "var(--ink)", fontSize: "1rem" }}>A/B Variants</h3>
                {!showVariantForm && (
                  <button type="button" onClick={() => { setVariantForm(emptyVariantForm); setEditingVariantId(null); setShowVariantForm(true); }} style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700, fontSize: "0.8rem" }}>
                    + Add Variant
                  </button>
                )}
              </div>

              {/* Variant Form */}
              {showVariantForm && (
                <div style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", borderRadius: 12, padding: 14, marginBottom: 16 }}>
                  <h4 style={{ margin: "0 0 12px", color: "var(--ink-light)", fontSize: "0.88rem" }}>
                    {editingVariantId ? "Edit Variant" : "New Variant"}
                  </h4>
                  <div className="grid gap-3 md:grid-cols-2">
                    <PosterFormField label="Variant Name">
                      <input value={variantForm.variantName} onChange={(e) => setVariantField("variantName", e.target.value)} placeholder="e.g. Control, Variant A" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                    <PosterFormField label="Weight (1-100)" hint="Higher weight = more traffic share.">
                      <input type="number" min={1} max={100} value={variantForm.weight} onChange={(e) => setVariantField("weight", e.target.value)} className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                  </div>
                  <div className="grid gap-3 md:grid-cols-3" style={{ marginTop: 12 }}>
                    <PosterFormField label="Desktop Image URL (Optional)">
                      <input value={variantForm.desktopImage} onChange={(e) => setVariantField("desktopImage", e.target.value)} placeholder="posters/variant-desktop.jpg" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                    <PosterFormField label="Mobile Image URL (Optional)">
                      <input value={variantForm.mobileImage} onChange={(e) => setVariantField("mobileImage", e.target.value)} placeholder="posters/variant-mobile.jpg" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                    <PosterFormField label="Tablet Image URL (Optional)">
                      <input value={variantForm.tabletImage} onChange={(e) => setVariantField("tabletImage", e.target.value)} placeholder="posters/variant-tablet.jpg" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                  </div>
                  <div className="grid gap-3 md:grid-cols-2" style={{ marginTop: 12 }}>
                    <PosterFormField label="Link URL (Optional)">
                      <input value={variantForm.linkUrl} onChange={(e) => setVariantField("linkUrl", e.target.value)} placeholder="https://example.com/sale" className="rounded-lg border px-3 py-2.5" style={fieldBaseStyle} />
                    </PosterFormField>
                    <PosterFormField label="Status">
                      <label className="flex items-center gap-2 rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, display: "flex" }}>
                        <input type="checkbox" checked={variantForm.active} onChange={(e) => setVariantField("active", e.target.checked)} />
                        Active variant
                      </label>
                    </PosterFormField>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button type="button" disabled={savingVariant} onClick={() => { void saveVariant(); }} className="btn-primary" style={{ padding: "8px 14px", borderRadius: 8, fontWeight: 700, fontSize: "0.82rem" }}>
                      {savingVariant ? "Saving..." : editingVariantId ? "Update Variant" : "Create Variant"}
                    </button>
                    <button type="button" onClick={() => { setShowVariantForm(false); setEditingVariantId(null); setVariantForm(emptyVariantForm); }} style={{ padding: "8px 14px", borderRadius: 8, border: "1px solid var(--line)", background: "var(--surface-2)", color: "var(--ink-light)", fontWeight: 700, fontSize: "0.82rem" }}>
                      Cancel
                    </button>
                  </div>
                </div>
              )}

              {/* Variants Table */}
              {variantsLoading ? (
                <div className="skeleton" style={{ height: 80, borderRadius: 12 }} />
              ) : variants.length === 0 ? (
                <p style={{ color: "var(--muted)", fontSize: "0.82rem" }}>No variants yet. Add one to start A/B testing.</p>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr style={{ borderBottom: "2px solid var(--line)" }}>
                        {["Name", "Weight", "Active", "Impressions", "Clicks", "CTR", "Actions"].map((h) => (
                          <th key={h} style={{ padding: "8px 10px", textAlign: "left", fontSize: "0.72rem", color: "var(--muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {variants.map((v) => (
                        <tr key={v.id} style={{ borderBottom: "1px solid var(--line)" }}>
                          <td style={variantTableCellStyle}>
                            <span style={{ fontWeight: 600, color: "var(--ink)" }}>{v.variantName}</span>
                          </td>
                          <td style={variantTableCellStyle}>{v.weight}</td>
                          <td style={variantTableCellStyle}>
                            <StatusBadge value={v.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                          </td>
                          <td style={{ ...variantTableCellStyle, color: "#60a5fa", fontWeight: 600 }}>{v.impressions.toLocaleString()}</td>
                          <td style={{ ...variantTableCellStyle, color: "#34d399", fontWeight: 600 }}>{v.clicks.toLocaleString()}</td>
                          <td style={{ ...variantTableCellStyle, color: "#c084fc", fontWeight: 600 }}>{v.clickThroughRate.toFixed(2)}%</td>
                          <td style={variantTableCellStyle}>
                            <div className="flex gap-2">
                              <button type="button" onClick={() => editVariant(v)} style={{ padding: "4px 10px", borderRadius: 6, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700, fontSize: "0.72rem" }}>Edit</button>
                              <button type="button" onClick={() => setDeleteVariantTarget(v)} style={{ padding: "4px 10px", borderRadius: 6, border: "1px solid rgba(239,68,68,0.2)", background: "rgba(239,68,68,0.08)", color: "#fca5a5", fontWeight: 700, fontSize: "0.72rem" }}>Delete</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </section>

        <section style={panelStyle}>
          <div className="mb-3 flex items-center justify-between"><h2 style={{ margin: 0, color: "var(--ink)" }}>{showDeleted ? "Deleted Posters" : "Active Posters"}</h2><span style={{ color: "var(--muted)", fontSize: "0.8rem" }}>{status}</span></div>
          {loading && <div className="skeleton" style={{ height: 120, borderRadius: 12 }} />}
          {!loading && list.length === 0 && <p style={{ color: "var(--muted)" }}>No posters found.</p>}
          <div className="grid gap-4">
            {Array.from(grouped.entries()).map(([placement, arr]) => (
              <div key={placement} style={{ border: "1px solid var(--line)", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "10px 12px", background: "var(--brand-soft)", borderBottom: "1px solid var(--line)", color: "#dbeafe", fontWeight: 800 }}>{placement}</div>
                <div className="grid gap-3 p-3">
                  {arr.sort((a, b) => (a.sortOrder - b.sortOrder) || a.name.localeCompare(b.name)).map((p) => {
                    const posterAnalytics = analyticsMap.get(p.id);
                    return (
                    <div key={p.id} className="poster-card-row" style={{ display: "grid", gridTemplateColumns: "170px 1fr", gap: 12, border: "1px solid var(--line)", borderRadius: 12, padding: 10, background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ position: "relative", aspectRatio: "16/7", borderRadius: 10, overflow: "hidden", background: "var(--surface-3)" }}>
                        {p.desktopImage ? <PosterImageFill imageKey={p.desktopImage} alt={p.name} /> : null}
                      </div>
                      <div>
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <strong style={{ color: "var(--ink)" }}>{p.name}</strong>
                          <span style={{ padding: "2px 8px", borderRadius: 999, fontSize: "0.68rem", border: "1px solid var(--line)", color: "var(--ink-light)" }}>{p.size}</span>
                          <StatusBadge value={p.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                          <span style={{ fontSize: "0.72rem", color: "var(--muted)" }}>#{p.sortOrder}</span>
                        </div>
                        <div style={{ fontSize: "0.78rem", color: "var(--muted)", display: "grid", gap: 3 }}>
                          <div>Slug: {p.slug}</div>
                          <div>Link: {p.linkType}{p.linkTarget ? ` -> ${p.linkTarget}` : ""}{p.openInNewTab ? " (new tab)" : ""}</div>
                          {p.title && <div>Title: {p.title}</div>}
                        </div>

                        {/* Inline analytics for this poster */}
                        {showAnalytics && posterAnalytics && (
                          <div style={{ marginTop: 8, display: "flex", flexWrap: "wrap", gap: 12, fontSize: "0.75rem" }}>
                            <span style={{ color: "#60a5fa" }}>{posterAnalytics.impressionCount.toLocaleString()} impressions</span>
                            <span style={{ color: "#34d399" }}>{posterAnalytics.clickCount.toLocaleString()} clicks</span>
                            <span style={{ color: "#c084fc" }}>{posterAnalytics.clickThroughRate.toFixed(2)}% CTR</span>
                          </div>
                        )}

                        <div className="mt-3 flex flex-wrap gap-2">
                          {!showDeleted && <>
                            <button type="button" onClick={() => edit(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid var(--line-bright)", background: "var(--brand-soft)", color: "#9fe9ff", fontWeight: 700, fontSize: "0.75rem" }}>Edit</button>
                            <button type="button" onClick={() => duplicate(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(167,139,250,0.2)", background: "rgba(124,58,237,0.08)", color: "#c4b5fd", fontWeight: 700, fontSize: "0.75rem" }}>Duplicate</button>
                            <button type="button" onClick={() => setDeleteTarget(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(239,68,68,0.2)", background: "rgba(239,68,68,0.08)", color: "#fca5a5", fontWeight: 700, fontSize: "0.75rem" }}>Delete</button>
                          </>}
                          {showDeleted && <button type="button" disabled={restoreId === p.id} onClick={() => { void restore(p.id); }} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(34,197,94,0.2)", background: "rgba(34,197,94,0.08)", color: "#86efac", fontWeight: 700, fontSize: "0.75rem" }}>{restoreId === p.id ? "Restoring..." : "Restore"}</button>}
                        </div>
                      </div>
                    </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>
      <Footer />
      <ConfirmModal
        open={Boolean(deleteTarget)}
        title="Delete Poster"
        message={deleteTarget ? `Soft delete \"${deleteTarget.name}\"?` : ""}
        confirmLabel={deleteBusy ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={deleteBusy}
        onConfirm={() => { void deletePoster(); }}
        onCancel={() => { if (!deleteBusy) setDeleteTarget(null); }}
      />
      <ConfirmModal
        open={Boolean(deleteVariantTarget)}
        title="Delete Variant"
        message={deleteVariantTarget ? `Delete variant \"${deleteVariantTarget.variantName}\"? This cannot be undone.` : ""}
        confirmLabel={deletingVariant ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={deletingVariant}
        onConfirm={() => { void deleteVariant(); }}
        onCancel={() => { if (!deletingVariant) setDeleteVariantTarget(null); }}
      />
      <style jsx>{`
        .poster-select {
          appearance: none;
          background-image:
            linear-gradient(45deg, transparent 50%, var(--ink-light) 50%),
            linear-gradient(135deg, var(--ink-light) 50%, transparent 50%);
          background-position:
            calc(100% - 18px) calc(50% - 3px),
            calc(100% - 12px) calc(50% - 3px);
          background-size: 6px 6px, 6px 6px;
          background-repeat: no-repeat;
          padding-right: 34px;
        }
        @media (max-width: 768px) {
          .poster-card-row {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </div>
  );
}
