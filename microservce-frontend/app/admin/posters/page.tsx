"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import ConfirmModal from "../../components/ConfirmModal";
import { useAuthSession } from "../../../lib/authSession";

type Placement = "HOME_HERO" | "HOME_TOP_STRIP" | "HOME_MID_LEFT" | "HOME_MID_RIGHT" | "HOME_BOTTOM_GRID" | "CATEGORY_TOP" | "CATEGORY_SIDEBAR" | "PRODUCT_DETAIL_SIDE";
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

function posterImageUrl(key: string | null) {
  if (!key) return null;
  const normalized = key.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map(encodeURIComponent).join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/posters/images/${encoded}`;
  const base = (process.env.NEXT_PUBLIC_POSTER_IMAGE_BASE_URL || "").trim();
  return base && normalized.startsWith("posters/") ? `${base.replace(/\/+$/, "")}/${normalized}` : apiUrl;
}

function badgeStyle(active: boolean): React.CSSProperties {
  return {
    padding: "2px 8px",
    borderRadius: 999,
    fontSize: "0.68rem",
    fontWeight: 700,
    border: active ? "1px solid rgba(34,197,94,0.3)" : "1px solid rgba(239,68,68,0.25)",
    color: active ? "#22c55e" : "#ef4444",
    background: active ? "rgba(34,197,94,0.08)" : "rgba(239,68,68,0.08)",
  };
}

export default function AdminPostersPage() {
  const router = useRouter();
  const session = useAuthSession();
  const [items, setItems] = useState<Poster[]>([]);
  const [deletedItems, setDeletedItems] = useState<Poster[]>([]);
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

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((s) => ({ ...s, [key]: value }));

  const load = async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const [a, d] = await Promise.all([session.apiClient.get("/admin/posters"), session.apiClient.get("/admin/posters/deleted")]);
      setItems((a.data as Poster[]) || []);
      setDeletedItems((d.data as Poster[]) || []);
      setStatus("Posters loaded.");
    } catch (e) {
      setStatus(e instanceof Error ? e.message : "Failed to load posters.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isAuthenticated) { router.replace("/"); return; }
    if (!session.canViewAdmin) { router.replace("/products"); return; }
    void load();
  }, [session.status, session.isAuthenticated, session.canViewAdmin, router]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (slugEdited) return;
    setForm((s) => ({ ...s, slug: slugify(s.name) }));
  }, [form.name, slugEdited]);

  useEffect(() => {
    if (!session.apiClient) return;
    const slug = slugify(form.slug);
    if (!slug) { setSlugStatus("invalid"); return; }
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
    return () => { cancelled = true; window.clearTimeout(t); };
  }, [form.slug, form.id, session.apiClient]);

  const edit = (p: Poster) => {
    setForm({
      id: p.id, name: p.name, slug: p.slug, placement: p.placement, size: p.size,
      desktopImage: p.desktopImage || "", mobileImage: p.mobileImage || "", linkType: p.linkType,
      linkTarget: p.linkTarget || "", openInNewTab: p.openInNewTab, title: p.title || "",
      subtitle: p.subtitle || "", ctaLabel: p.ctaLabel || "", backgroundColor: p.backgroundColor || "",
      sortOrder: String(p.sortOrder ?? 0), active: p.active, startAt: toLocalDateTime(p.startAt), endAt: toLocalDateTime(p.endAt),
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
    setForm((s) => ({ ...s, placement, size: placementDefaultSize[placement], ctaLabel: s.ctaLabel || (placement === "HOME_HERO" ? "Shop Now" : "") }));
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
      setField(target, uploaded as any);
      toast.success("Image uploaded");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Upload failed");
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
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Save failed");
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
      if (form.id === deleteTarget.id) { setForm(emptyForm); setSlugEdited(false); }
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Delete failed");
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
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Restore failed");
    } finally {
      setRestoreId(null);
    }
  };

  if (session.status === "loading" || session.status === "idle") return <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}><div className="spinner-lg" /></div>;
  if (!session.isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <AppNav email={(session.profile?.email as string) || ""} canViewAdmin={session.canViewAdmin} apiClient={session.apiClient} emailVerified={session.emailVerified} onLogout={() => { void session.logout(); }} />
      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link><span className="breadcrumb-sep">›</span>
          <Link href="/admin/products">Admin</Link><span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Posters</span>
        </nav>

        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 style={{ margin: 0, color: "#fff", fontFamily: "'Syne', sans-serif", fontWeight: 800 }}>Admin Posters</h1>
            <p style={{ margin: "4px 0 0", color: "var(--muted)", fontSize: "0.85rem" }}>Auto slug, duplicate poster, placement presets, image uploads.</p>
          </div>
          <button onClick={() => setShowDeleted((v) => !v)} style={{ padding: "8px 12px", borderRadius: "10px", border: "1px solid rgba(0,212,255,0.15)", background: showDeleted ? "rgba(239,68,68,0.1)" : "rgba(0,212,255,0.06)", color: showDeleted ? "#f87171" : "#00d4ff", fontWeight: 700 }}>
            {showDeleted ? "Showing Deleted" : "Show Deleted"}
          </button>
        </div>

        <section className="mb-5" style={{ background: "rgba(17,17,40,0.7)", border: "1px solid rgba(0,212,255,0.1)", borderRadius: 16, padding: 16 }}>
          <form onSubmit={(e) => { void submit(e); }} className="grid gap-3">
            <div className="grid gap-3 md:grid-cols-2">
              <input value={form.name} onChange={(e) => setField("name", e.target.value)} placeholder="Poster name" className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
              <div className="flex gap-2">
                <input value={form.slug} onChange={(e) => { setSlugEdited(true); setField("slug", slugify(e.target.value)); }} placeholder="slug" className="w-full rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
                <button type="button" onClick={() => { setSlugEdited(false); setField("slug", slugify(form.name)); }} style={{ padding: "0 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700 }}>Auto</button>
              </div>
            </div>
            <div style={{ fontSize: "0.72rem", color: slugStatus === "taken" ? "#f87171" : slugStatus === "available" ? "#34d399" : "var(--muted-2)" }}>
              {slugStatus === "taken" ? "Slug taken" : slugStatus === "available" ? "Slug available" : slugStatus === "checking" ? "Checking slug..." : "Slug auto-fills from name"}
            </div>

            <div className="grid gap-3 md:grid-cols-4">
              <select value={form.placement} onChange={(e) => applyPreset(e.target.value as Placement)} className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }}>
                {Object.keys(placementDefaultSize).map((p) => <option key={p} value={p}>{p}</option>)}
              </select>
              <select value={form.size} onChange={(e) => setField("size", e.target.value as Size)} className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }}>
                {["HERO","WIDE","TALL","SQUARE","STRIP","CUSTOM"].map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
              <input value={form.sortOrder} onChange={(e) => setField("sortOrder", e.target.value)} placeholder="Sort order" className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
              <label className="flex items-center gap-2 rounded-lg border px-3 py-2.5" style={{ borderColor: "rgba(0,212,255,0.12)", color: "#fff" }}><input type="checkbox" checked={form.active} onChange={(e) => setField("active", e.target.checked)} />Active</label>
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <div className="mb-1 text-xs font-semibold text-[var(--muted)]">Desktop image</div>
                <div className="flex gap-2">
                  <input value={form.desktopImage} onChange={(e) => setField("desktopImage", e.target.value)} className="w-full rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
                  <label style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700, cursor: uploading ? "not-allowed" : "pointer" }}>
                    {uploading ? "..." : "Upload"}
                    <input hidden type="file" accept="image/*" onChange={(e) => { void uploadImage(e, "desktopImage"); }} />
                  </label>
                </div>
              </div>
              <div>
                <div className="mb-1 text-xs font-semibold text-[var(--muted)]">Mobile image (optional)</div>
                <div className="flex gap-2">
                  <input value={form.mobileImage} onChange={(e) => setField("mobileImage", e.target.value)} className="w-full rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
                  <button type="button" onClick={() => setField("mobileImage", form.mobileImage || form.desktopImage)} style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700 }}>Copy</button>
                  <label style={{ padding: "8px 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700, cursor: uploading ? "not-allowed" : "pointer" }}>
                    {uploading ? "..." : "Upload"}
                    <input hidden type="file" accept="image/*" onChange={(e) => { void uploadImage(e, "mobileImage"); }} />
                  </label>
                </div>
              </div>
            </div>

            <div className="grid gap-3 md:grid-cols-3">
              <select value={form.linkType} onChange={(e) => setField("linkType", e.target.value as LinkType)} className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }}>
                {["NONE","PRODUCT","CATEGORY","SEARCH","URL"].map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
              <input value={form.linkTarget} onChange={(e) => setField("linkTarget", e.target.value)} disabled={form.linkType === "NONE"} placeholder={form.linkType === "NONE" ? "No target needed" : form.linkType === "SEARCH" ? "q=watch&mainCategory=electronics" : "slug or URL"} className="md:col-span-2 rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
            </div>

            <div className="grid gap-3 md:grid-cols-2">
              <input value={form.title} onChange={(e) => setField("title", e.target.value)} placeholder="Title (optional)" className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
              <div className="flex gap-2">
                <input value={form.ctaLabel} onChange={(e) => setField("ctaLabel", e.target.value)} placeholder="CTA label (optional)" className="w-full rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
                <button type="button" onClick={() => { setField("title", form.title || form.name); setField("ctaLabel", form.ctaLabel || "Shop Now"); }} style={{ padding: "0 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700 }}>Autofill</button>
              </div>
            </div>
            <textarea value={form.subtitle} onChange={(e) => setField("subtitle", e.target.value)} rows={2} placeholder="Subtitle (optional)" className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />

            <div className="grid gap-3 md:grid-cols-3">
              <input value={form.backgroundColor} onChange={(e) => setField("backgroundColor", e.target.value)} placeholder="Background CSS (optional)" className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
              <input type="datetime-local" value={form.startAt} onChange={(e) => setField("startAt", e.target.value)} className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
              <input type="datetime-local" value={form.endAt} onChange={(e) => setField("endAt", e.target.value)} className="rounded-lg border px-3 py-2.5" style={{ background: "rgba(0,212,255,0.04)", borderColor: "rgba(0,212,255,0.12)", color: "#fff" }} />
            </div>

            <div className="flex flex-wrap gap-2">
              <button type="submit" disabled={submitting || slugStatus === "taken"} style={{ padding: "10px 14px", borderRadius: 10, border: "none", background: "linear-gradient(135deg,#00d4ff,#7c3aed)", color: "#fff", fontWeight: 800 }}>
                {submitting ? (form.id ? "Saving..." : "Creating...") : (form.id ? "Save Poster" : "Create Poster")}
              </button>
              {form.id && <button type="button" onClick={() => { setForm(emptyForm); setSlugEdited(false); }} style={{ padding: "10px 14px", borderRadius: 10, border: "1px solid rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.03)", color: "#d1d5db", fontWeight: 700 }}>Cancel Edit</button>}
            </div>
          </form>
        </section>

        <section style={{ background: "rgba(17,17,40,0.7)", border: "1px solid rgba(0,212,255,0.1)", borderRadius: 16, padding: 16 }}>
          <div className="mb-3 flex items-center justify-between"><h2 style={{ margin: 0, color: "#fff" }}>{showDeleted ? "Deleted Posters" : "Active Posters"}</h2><span style={{ color: "var(--muted)", fontSize: "0.8rem" }}>{status}</span></div>
          {loading && <div className="skeleton" style={{ height: 120, borderRadius: 12 }} />}
          {!loading && list.length === 0 && <p style={{ color: "var(--muted)" }}>No posters found.</p>}
          <div className="grid gap-4">
            {Array.from(grouped.entries()).map(([placement, arr]) => (
              <div key={placement} style={{ border: "1px solid rgba(0,212,255,0.08)", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "10px 12px", background: "rgba(0,212,255,0.03)", borderBottom: "1px solid rgba(0,212,255,0.08)", color: "#dbeafe", fontWeight: 800 }}>{placement}</div>
                <div className="grid gap-3 p-3">
                  {arr.sort((a, b) => (a.sortOrder - b.sortOrder) || a.name.localeCompare(b.name)).map((p) => (
                    <div key={p.id} style={{ display: "grid", gridTemplateColumns: "170px 1fr", gap: 12, border: "1px solid rgba(255,255,255,0.04)", borderRadius: 12, padding: 10, background: "rgba(255,255,255,0.02)" }}>
                      <div style={{ position: "relative", aspectRatio: "16/7", borderRadius: 10, overflow: "hidden", background: "rgba(0,212,255,0.03)" }}>
                        {posterImageUrl(p.desktopImage) ? <Image src={posterImageUrl(p.desktopImage) || ""} alt={p.name} fill unoptimized style={{ objectFit: "cover" }} /> : null}
                      </div>
                      <div>
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <strong style={{ color: "#fff" }}>{p.name}</strong>
                          <span style={{ padding: "2px 8px", borderRadius: 999, fontSize: "0.68rem", border: "1px solid rgba(255,255,255,0.08)", color: "#cbd5e1" }}>{p.size}</span>
                          <span style={badgeStyle(p.active)}>{p.active ? "Active" : "Inactive"}</span>
                          <span style={{ fontSize: "0.72rem", color: "var(--muted)" }}>#{p.sortOrder}</span>
                        </div>
                        <div style={{ fontSize: "0.78rem", color: "var(--muted)", display: "grid", gap: 3 }}>
                          <div>Slug: {p.slug}</div>
                          <div>Link: {p.linkType}{p.linkTarget ? ` -> ${p.linkTarget}` : ""}</div>
                          {p.title && <div>Title: {p.title}</div>}
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          {!showDeleted && <>
                            <button type="button" onClick={() => edit(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(0,212,255,0.16)", background: "rgba(0,212,255,0.06)", color: "#9fe9ff", fontWeight: 700, fontSize: "0.75rem" }}>Edit</button>
                            <button type="button" onClick={() => duplicate(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(167,139,250,0.2)", background: "rgba(124,58,237,0.08)", color: "#c4b5fd", fontWeight: 700, fontSize: "0.75rem" }}>Duplicate</button>
                            <button type="button" onClick={() => setDeleteTarget(p)} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(239,68,68,0.2)", background: "rgba(239,68,68,0.08)", color: "#fca5a5", fontWeight: 700, fontSize: "0.75rem" }}>Delete</button>
                          </>}
                          {showDeleted && <button type="button" disabled={restoreId === p.id} onClick={() => { void restore(p.id); }} style={{ padding: "7px 12px", borderRadius: 8, border: "1px solid rgba(34,197,94,0.2)", background: "rgba(34,197,94,0.08)", color: "#86efac", fontWeight: 700, fontSize: "0.75rem" }}>{restoreId === p.id ? "Restoring..." : "Restore"}</button>}
                        </div>
                      </div>
                    </div>
                  ))}
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
    </div>
  );
}
