"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import ConfirmModal from "../../components/ConfirmModal";
import AdminPageShell from "../../components/ui/AdminPageShell";
import PosterFormPanel from "../../components/posters/admin/PosterFormPanel";
import PosterVariantsSection from "../../components/posters/admin/PosterVariantsSection";
import PosterAnalyticsSummary from "../../components/posters/admin/PosterAnalyticsSummary";
import PosterListPanel from "../../components/posters/admin/PosterListPanel";
import {
  type Poster,
  type PosterAnalytics,
  type PosterVariant,
  type PosterFormState,
  type VariantFormState,
  type SlugStatus,
  emptyForm,
  emptyVariantForm,
  panelStyle,
  slugify,
  toIsoOrNull,
  toLocalDateTime,
  getApiErrorMessage,
} from "../../components/posters/admin/types";
import { useAuthSession } from "../../../lib/authSession";

export default function AdminPostersPage() {
  const session = useAuthSession();
  const queryClient = useQueryClient();

  const [showDeleted, setShowDeleted] = useState(false);
  const [form, setForm] = useState<PosterFormState>(emptyForm);
  const [slugEdited, setSlugEdited] = useState(false);
  const [slugStatus, setSlugStatus] = useState<SlugStatus>("idle");
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Poster | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [restoreId, setRestoreId] = useState<string | null>(null);

  // Analytics state
  const [showAnalytics, setShowAnalytics] = useState(false);

  // Variants state
  const [variantForm, setVariantForm] = useState<VariantFormState>(emptyVariantForm);
  const [showVariantForm, setShowVariantForm] = useState(false);
  const [editingVariantId, setEditingVariantId] = useState<string | null>(null);
  const [savingVariant, setSavingVariant] = useState(false);
  const [deleteVariantTarget, setDeleteVariantTarget] = useState<PosterVariant | null>(null);
  const [deletingVariant, setDeletingVariant] = useState(false);

  const setField = <K extends keyof PosterFormState>(key: K, value: PosterFormState[K]) => setForm((s) => ({ ...s, [key]: value }));
  const setVariantField = <K extends keyof VariantFormState>(key: K, value: VariantFormState[K]) => setVariantForm((s) => ({ ...s, [key]: value }));

  const isReady = session.status === "ready" && session.isAuthenticated && !!session.apiClient;

  /* ───── active posters query ───── */
  const { data: items = [], isLoading: loadingActive, status: activeStatus } = useQuery<Poster[]>({
    queryKey: ["admin-posters"],
    queryFn: async () => {
      const a = await session.apiClient!.get("/admin/posters");
      const raw = a.data as { content?: Poster[] };
      return raw.content || [];
    },
    enabled: isReady,
  });

  /* ───── deleted posters query ───── */
  const { data: deletedItems = [], isLoading: loadingDeleted, isFetched: deletedLoaded } = useQuery<Poster[]>({
    queryKey: ["admin-posters", "deleted"],
    queryFn: async () => {
      const d = await session.apiClient!.get("/admin/posters/deleted");
      const raw = d.data as { content?: Poster[] };
      return raw.content || [];
    },
    enabled: isReady && showDeleted,
  });

  /* ───── analytics query ───── */
  const { data: analytics = [], isLoading: analyticsLoading } = useQuery<PosterAnalytics[]>({
    queryKey: ["admin-posters", "analytics"],
    queryFn: async () => {
      const res = await session.apiClient!.get("/admin/posters/analytics");
      return (res.data as PosterAnalytics[]) || [];
    },
    enabled: isReady && showAnalytics,
  });

  /* ───── variants query ───── */
  const { data: variants = [], isLoading: variantsLoading } = useQuery<PosterVariant[]>({
    queryKey: ["admin-posters", form.id, "variants"],
    queryFn: async () => {
      const res = await session.apiClient!.get(`/admin/posters/${form.id}/variants`);
      return (res.data as PosterVariant[]) || [];
    },
    enabled: isReady && !!form.id,
  });

  // Reset variant form state when form.id changes
  useEffect(() => {
    if (!form.id) {
      setShowVariantForm(false);
      setEditingVariantId(null);
      setVariantForm(emptyVariantForm);
    }
  }, [form.id]);

  const loading = loadingActive || (showDeleted && loadingDeleted);
  const list = showDeleted ? deletedItems : items;
  const status = loading
    ? "Loading posters..."
    : activeStatus === "error"
      ? "Failed to load posters."
      : "Posters loaded.";

  // Build a lookup map from poster id -> analytics
  const analyticsMap = useMemo(() => {
    const m = new Map<string, PosterAnalytics>();
    for (const a of analytics) m.set(a.id, a);
    return m;
  }, [analytics]);

  /* ───── save variant mutation ───── */
  const saveVariantMutation = useMutation({
    mutationFn: async (payload: object) => {
      if (editingVariantId) {
        await session.apiClient!.put(`/admin/posters/${form.id}/variants/${editingVariantId}`, payload);
      } else {
        await session.apiClient!.post(`/admin/posters/${form.id}/variants`, payload);
      }
    },
    onSuccess: () => {
      toast.success(editingVariantId ? "Variant updated" : "Variant created");
      setVariantForm(emptyVariantForm);
      setShowVariantForm(false);
      setEditingVariantId(null);
      void queryClient.invalidateQueries({ queryKey: ["admin-posters", form.id, "variants"] });
    },
    onError: (e) => {
      toast.error(getApiErrorMessage(e, "Failed to save variant"));
    },
    onSettled: () => {
      setSavingVariant(false);
    },
  });

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
    const payload = {
      variantName: variantForm.variantName.trim(),
      weight,
      desktopImage: variantForm.desktopImage.trim() || undefined,
      mobileImage: variantForm.mobileImage.trim() || undefined,
      tabletImage: variantForm.tabletImage.trim() || undefined,
      linkUrl: variantForm.linkUrl.trim() || undefined,
      active: variantForm.active,
    };
    saveVariantMutation.mutate(payload);
  };

  /* ───── delete variant mutation ───── */
  const deleteVariantMutation = useMutation({
    mutationFn: async (variantId: string) => {
      await session.apiClient!.delete(`/admin/posters/${form.id}/variants/${variantId}`);
    },
    onSuccess: () => {
      toast.success("Variant deleted");
      setDeleteVariantTarget(null);
      void queryClient.invalidateQueries({ queryKey: ["admin-posters", form.id, "variants"] });
    },
    onError: (e) => {
      toast.error(getApiErrorMessage(e, "Failed to delete variant"));
    },
    onSettled: () => {
      setDeletingVariant(false);
    },
  });

  const deleteVariant = async () => {
    if (!session.apiClient || !form.id || !deleteVariantTarget || deletingVariant) return;
    setDeletingVariant(true);
    deleteVariantMutation.mutate(deleteVariantTarget.id);
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

  /* ───── slug effects (unchanged) ───── */
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
      setField(target, uploaded as PosterFormState[typeof target]);
      toast.success("Image uploaded");
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Upload failed"));
    } finally {
      setUploading(false);
    }
  };

  /* ───── submit (create/update) mutation ───── */
  const submitMutation = useMutation({
    mutationFn: async ({ payload, isEdit }: { payload: object; isEdit: boolean }) => {
      if (isEdit) {
        await session.apiClient!.put(`/admin/posters/${form.id}`, payload);
      } else {
        await session.apiClient!.post("/admin/posters", payload);
      }
    },
    onSuccess: (_, { isEdit }) => {
      toast.success(isEdit ? "Poster updated" : "Poster created");
      setForm(emptyForm);
      setSlugEdited(false);
      void queryClient.invalidateQueries({ queryKey: ["admin-posters"] });
    },
    onError: (err) => {
      toast.error(getApiErrorMessage(err, "Save failed"));
    },
    onSettled: () => {
      setSubmitting(false);
    },
  });

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

      submitMutation.mutate({ payload, isEdit: !!form.id });
    } catch (err) {
      toast.error(getApiErrorMessage(err, "Save failed"));
      setSubmitting(false);
    }
  };

  /* ───── delete poster mutation ───── */
  const deletePosterMutation = useMutation({
    mutationFn: async (posterId: string) => {
      await session.apiClient!.delete(`/admin/posters/${posterId}`);
    },
    onSuccess: (_, posterId) => {
      toast.success("Poster deleted");
      setDeleteTarget(null);
      if (form.id === posterId) {
        setForm(emptyForm);
        setSlugEdited(false);
      }
      void queryClient.invalidateQueries({ queryKey: ["admin-posters"] });
    },
    onError: (err) => {
      toast.error(getApiErrorMessage(err, "Delete failed"));
    },
    onSettled: () => {
      setDeleteBusy(false);
    },
  });

  const deletePoster = async () => {
    if (!session.apiClient || !deleteTarget || deleteBusy) return;
    setDeleteBusy(true);
    deletePosterMutation.mutate(deleteTarget.id);
  };

  /* ───── restore poster mutation ───── */
  const restorePosterMutation = useMutation({
    mutationFn: async (id: string) => {
      await session.apiClient!.post(`/admin/posters/${id}/restore`);
    },
    onSuccess: () => {
      toast.success("Poster restored");
      void queryClient.invalidateQueries({ queryKey: ["admin-posters"] });
    },
    onError: (err) => {
      toast.error(getApiErrorMessage(err, "Restore failed"));
    },
    onSettled: () => {
      setRestoreId(null);
    },
  });

  const restore = async (id: string) => {
    if (!session.apiClient || restoreId) return;
    setRestoreId(id);
    restorePosterMutation.mutate(id);
  };

  if (session.status === "loading" || session.status === "idle") {
    return <div className="min-h-screen bg-bg grid place-items-center"><div className="spinner-lg" /></div>;
  }
  if (!session.isAuthenticated) return null;

  return (
    <AdminPageShell
      title="Posters"
      breadcrumbs={[{ label: "Admin", href: "/admin/dashboard" }, { label: "Posters" }]}
      actions={
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => setShowAnalytics((v) => !v)} disabled={analyticsLoading} className={`px-3 py-2 rounded-md border border-line-bright font-bold ${showAnalytics ? "bg-accent-soft text-accent" : "bg-brand-soft text-brand"} ${analyticsLoading ? "opacity-70 cursor-not-allowed" : "cursor-pointer"}`}>
            {analyticsLoading ? "Loading..." : showAnalytics ? "Hide Analytics" : "Show Analytics"}
          </button>
          <button type="button" onClick={() => setShowDeleted((v) => !v)} disabled={loading} className={`px-3 py-2 rounded-md border border-line-bright font-bold ${showDeleted ? "bg-danger-soft text-danger" : "bg-brand-soft text-brand"} ${loading ? "opacity-70 cursor-not-allowed" : "cursor-pointer"}`}>
            {showDeleted ? "Showing Deleted" : "Show Deleted"}
          </button>
        </div>
      }
    >
      {/* Analytics Summary */}
      {showAnalytics && (
        <PosterAnalyticsSummary analytics={analytics} analyticsLoading={analyticsLoading} />
      )}

      {/* Poster Form + Variants */}
      <section className="mb-5" style={panelStyle}>
        <PosterFormPanel
          form={form}
          slugStatus={slugStatus}
          submitting={submitting}
          uploading={uploading}
          isEditing={Boolean(form.id)}
          apiClient={session.apiClient}
          onFieldChange={setField}
          onSlugEditedChange={setSlugEdited}
          onSubmit={submit}
          onCancel={() => { setForm(emptyForm); setSlugEdited(false); }}
          onUploadImage={uploadImage}
        />

        {/* A/B Variants Section - shown only when editing a poster */}
        {form.id && (
          <PosterVariantsSection
            variants={variants}
            variantsLoading={variantsLoading}
            variantForm={variantForm}
            showVariantForm={showVariantForm}
            editingVariantId={editingVariantId}
            savingVariant={savingVariant}
            onVariantFieldChange={setVariantField}
            onShowVariantForm={() => { setVariantForm(emptyVariantForm); setEditingVariantId(null); setShowVariantForm(true); }}
            onCancelVariantForm={() => { setShowVariantForm(false); setEditingVariantId(null); setVariantForm(emptyVariantForm); }}
            onSaveVariant={saveVariant}
            onEditVariant={editVariant}
            onDeleteVariant={setDeleteVariantTarget}
          />
        )}
      </section>

      {/* Poster List */}
      <PosterListPanel
        posters={list}
        loading={loading}
        status={status}
        showDeleted={showDeleted}
        showAnalytics={showAnalytics}
        analyticsMap={analyticsMap}
        restoreId={restoreId}
        onEdit={edit}
        onDuplicate={duplicate}
        onDelete={setDeleteTarget}
        onRestore={restore}
      />

      <ConfirmModal
        open={Boolean(deleteTarget)}
        title="Delete Poster"
        message={deleteTarget ? `Soft delete "${deleteTarget.name}"?` : ""}
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
        message={deleteVariantTarget ? `Delete variant "${deleteVariantTarget.variantName}"? This cannot be undone.` : ""}
        confirmLabel={deletingVariant ? "Deleting..." : "Delete"}
        cancelLabel="Cancel"
        danger
        loading={deletingVariant}
        onConfirm={() => { void deleteVariant(); }}
        onCancel={() => { if (!deletingVariant) setDeleteVariantTarget(null); }}
      />
    </AdminPageShell>
  );
}
