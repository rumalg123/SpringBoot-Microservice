"use client";

import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import type { AxiosInstance } from "axios";
import PosterFormField from "./PosterFormField";
import PosterLinkTargetEditor from "./PosterLinkTargetEditor";
import {
  type Placement,
  type Size,
  type SlugStatus,
  type PosterFormState,
  placementDefaultSize,
  slugify,
} from "./types";
import { API_BASE } from "../../../../lib/constants";

function posterImageSources(key: string | null): { primary: string | null; fallback: string | null } {
  if (!key) return { primary: null, fallback: null };
  const normalized = key.replace(/^\/+/, "");
  const apiBase = API_BASE;
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
      className="object-cover"
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

type Props = {
  form: PosterFormState;
  slugStatus: SlugStatus;
  submitting: boolean;
  uploading: boolean;
  isEditing: boolean;
  apiClient?: AxiosInstance | null;
  onFieldChange: <K extends keyof PosterFormState>(key: K, value: PosterFormState[K]) => void;
  onSlugEditedChange: (edited: boolean) => void;
  onSubmit: (e: FormEvent) => void;
  onCancel: () => void;
  onUploadImage: (e: ChangeEvent<HTMLInputElement>, target: "desktopImage" | "mobileImage") => void;
};

export default function PosterFormPanel({
  form,
  slugStatus,
  submitting,
  uploading,
  isEditing,
  apiClient,
  onFieldChange,
  onSlugEditedChange,
  onSubmit,
  onCancel,
  onUploadImage,
}: Props) {
  const applyPreset = (placement: Placement) => {
    onFieldChange("placement", placement);
    onFieldChange("size", placementDefaultSize[placement]);
    if (!form.ctaLabel && placement === "HOME_HERO") {
      onFieldChange("ctaLabel", "Shop Now");
    }
  };

  return (
    <>
      <form onSubmit={(e) => { void onSubmit(e); }} className="grid gap-3">
        <div className="grid gap-3 md:grid-cols-2">
          <PosterFormField label="Poster Name">
            <input value={form.name} onChange={(e) => onFieldChange("name", e.target.value)} placeholder="Summer Sale Hero Banner" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
          </PosterFormField>
          <PosterFormField label="Slug" hint="Auto-fills from poster name. You can edit it.">
            <div className="flex gap-2">
              <input value={form.slug} onChange={(e) => { onSlugEditedChange(true); onFieldChange("slug", slugify(e.target.value)); }} placeholder="summer-sale-hero-banner" className="w-full rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
              <button type="button" onClick={() => { onSlugEditedChange(false); onFieldChange("slug", slugify(form.name)); }} className="px-3 rounded-[8px] border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold">Auto</button>
            </div>
          </PosterFormField>
        </div>

        <div className={`text-xs ${slugStatus === "taken" ? "text-[#f87171]" : slugStatus === "available" ? "text-[#34d399]" : "text-muted-2"}`}>
          {slugStatus === "taken" ? "Slug taken" : slugStatus === "available" ? "Slug available" : slugStatus === "checking" ? "Checking slug..." : "Slug auto-fills from name"}
        </div>

        <div className="grid gap-3 md:grid-cols-4">
          <PosterFormField label="Placement">
            <select value={form.placement} onChange={(e) => applyPreset(e.target.value as Placement)} className="poster-select rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5">
              {Object.keys(placementDefaultSize).map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </PosterFormField>
          <PosterFormField label="Size" hint="Placement can auto-suggest size.">
            <select value={form.size} onChange={(e) => onFieldChange("size", e.target.value as Size)} className="poster-select rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5">
              {["HERO", "WIDE", "TALL", "SQUARE", "STRIP", "CUSTOM"].map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </PosterFormField>
          <PosterFormField label="Sort Order">
            <input value={form.sortOrder} onChange={(e) => onFieldChange("sortOrder", e.target.value)} placeholder="0" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
          </PosterFormField>
          <PosterFormField label="Status">
            <label className="flex items-center gap-2 rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5"><input type="checkbox" checked={form.active} onChange={(e) => onFieldChange("active", e.target.checked)} />Active poster</label>
          </PosterFormField>
        </div>

        <div className="grid gap-3 md:grid-cols-2">
          <PosterFormField label="Desktop Image" hint="Required. Upload or paste object key.">
            <div className="grid gap-2">
              <div className="flex gap-2">
                <input value={form.desktopImage} onChange={(e) => onFieldChange("desktopImage", e.target.value)} placeholder="posters/uuid.jpg" className="w-full rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
                <label className="px-3 py-2 rounded-[8px] border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold" style={{ cursor: uploading ? "not-allowed" : "pointer" }}>
                  {uploading ? "..." : "Upload"}
                  <input hidden type="file" accept="image/*" onChange={(e) => { void onUploadImage(e, "desktopImage"); }} />
                </label>
              </div>
              <div className="relative h-[66px] rounded-[8px] overflow-hidden border border-line bg-surface-3">
                {form.desktopImage
                  ? <PosterImageFill imageKey={form.desktopImage} alt="Desktop poster preview" />
                  : <div className="h-full grid place-items-center text-muted text-[0.75rem]">Desktop preview</div>}
              </div>
            </div>
          </PosterFormField>
          <PosterFormField label="Mobile Image (Optional)" hint="Leave empty if frontend should use desktop image.">
            <div className="grid gap-2">
              <div className="flex gap-2">
                <input value={form.mobileImage} onChange={(e) => onFieldChange("mobileImage", e.target.value)} placeholder="posters/uuid-mobile.jpg" className="w-full rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
                <button type="button" onClick={() => onFieldChange("mobileImage", form.desktopImage)} className="px-3 py-2 rounded-[8px] border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold">Copy</button>
                <label className="px-3 py-2 rounded-[8px] border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold" style={{ cursor: uploading ? "not-allowed" : "pointer" }}>
                  {uploading ? "..." : "Upload"}
                  <input hidden type="file" accept="image/*" onChange={(e) => { void onUploadImage(e, "mobileImage"); }} />
                </label>
              </div>
              <div className="relative h-[66px] rounded-[8px] overflow-hidden border border-line bg-surface-3">
                {form.mobileImage
                  ? <PosterImageFill imageKey={form.mobileImage || null} alt="Mobile poster preview" />
                  : <div className="h-full grid place-items-center text-muted text-[0.75rem]">Mobile preview</div>}
              </div>
            </div>
          </PosterFormField>
        </div>

        <PosterLinkTargetEditor
          apiClient={apiClient}
          linkType={form.linkType}
          linkTarget={form.linkTarget}
          openInNewTab={form.openInNewTab}
          disabled={submitting}
          onLinkTypeChange={(value) => onFieldChange("linkType", value)}
          onLinkTargetChange={(value) => onFieldChange("linkTarget", value)}
          onOpenInNewTabChange={(value) => onFieldChange("openInNewTab", value)}
        />

        <div className="grid gap-3 md:grid-cols-2">
          <PosterFormField label="Title (Optional)">
            <input value={form.title} onChange={(e) => onFieldChange("title", e.target.value)} placeholder="Main headline" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
          </PosterFormField>
          <PosterFormField label="CTA Label (Optional)">
            <div className="flex gap-2">
              <input value={form.ctaLabel} onChange={(e) => onFieldChange("ctaLabel", e.target.value)} placeholder="Shop Now" className="w-full rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
              <button type="button" onClick={() => { onFieldChange("title", form.title || form.name); onFieldChange("ctaLabel", form.ctaLabel || "Shop Now"); }} className="px-3 rounded-[8px] border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold">Autofill</button>
            </div>
          </PosterFormField>
        </div>

        <PosterFormField label="Subtitle (Optional)">
          <textarea value={form.subtitle} onChange={(e) => onFieldChange("subtitle", e.target.value)} rows={2} placeholder="Supporting text for the campaign banner" className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
        </PosterFormField>

        <div className="grid gap-3 md:grid-cols-3">
          <PosterFormField label="Background Color / CSS (Optional)" hint="Use a hex color or CSS gradient string.">
            <div className="flex gap-2">
              <input value={form.backgroundColor} onChange={(e) => onFieldChange("backgroundColor", e.target.value)} placeholder="#0f172a or linear-gradient(...)" className="w-full rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
              <div className="w-[44px] rounded-[8px] border border-line" style={{ background: form.backgroundColor || "var(--surface-3)" }} />
            </div>
          </PosterFormField>
          <PosterFormField label="Start At (Optional)" hint="Local time input; backend stores UTC.">
            <input type="datetime-local" value={form.startAt} onChange={(e) => onFieldChange("startAt", e.target.value)} className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
          </PosterFormField>
          <PosterFormField label="End At (Optional)" hint="Must be after start time.">
            <input type="datetime-local" value={form.endAt} onChange={(e) => onFieldChange("endAt", e.target.value)} className="rounded-lg border border-line bg-surface-2 text-ink px-3 py-2.5" />
          </PosterFormField>
        </div>

        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={submitting || slugStatus === "taken"} className="btn-primary px-3.5 py-2.5 rounded-md font-extrabold">
            {submitting ? (isEditing ? "Saving..." : "Creating...") : (isEditing ? "Save Poster" : "Create Poster")}
          </button>
          {isEditing && <button type="button" onClick={onCancel} className="px-3.5 py-2.5 rounded-md border border-line bg-surface-2 text-ink-light font-bold">Cancel Edit</button>}
        </div>
      </form>

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
      `}</style>
    </>
  );
}
