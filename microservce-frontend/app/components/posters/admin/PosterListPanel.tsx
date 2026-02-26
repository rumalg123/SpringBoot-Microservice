"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import StatusBadge, { ACTIVE_INACTIVE_COLORS } from "../../ui/StatusBadge";
import { type Poster, type PosterAnalytics } from "./types";
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
  posters: Poster[];
  loading: boolean;
  status: string;
  showDeleted: boolean;
  showAnalytics: boolean;
  analyticsMap: Map<string, PosterAnalytics>;
  restoreId: string | null;
  onEdit: (p: Poster) => void;
  onDuplicate: (p: Poster) => void;
  onDelete: (p: Poster) => void;
  onRestore: (id: string) => void;
};

export default function PosterListPanel({
  posters,
  loading,
  status,
  showDeleted,
  showAnalytics,
  analyticsMap,
  restoreId,
  onEdit,
  onDuplicate,
  onDelete,
  onRestore,
}: Props) {
  const grouped = useMemo(() => {
    const m = new Map<string, Poster[]>();
    for (const p of posters) {
      const arr = m.get(p.placement) || [];
      arr.push(p);
      m.set(p.placement, arr);
    }
    return m;
  }, [posters]);

  return (
    <section className="bg-[rgba(17,17,40,0.7)] border border-line rounded-2xl p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="m-0 text-ink">{showDeleted ? "Deleted Posters" : "Active Posters"}</h2>
        <span className="text-muted text-[0.8rem]">{status}</span>
      </div>
      {loading && <div className="skeleton h-[120px] rounded-[12px]" />}
      {!loading && posters.length === 0 && <p className="text-muted">No posters found.</p>}
      <div className="grid gap-4">
        {Array.from(grouped.entries()).map(([placement, arr]) => (
          <div key={placement} className="border border-line rounded-[12px] overflow-hidden">
            <div className="px-3 py-2.5 bg-brand-soft border-b border-line text-[#dbeafe] font-extrabold">{placement}</div>
            <div className="grid gap-3 p-3">
              {arr.sort((a, b) => (a.sortOrder - b.sortOrder) || a.name.localeCompare(b.name)).map((p) => {
                const posterAnalytics = analyticsMap.get(p.id);
                return (
                <div key={p.id} className="poster-card-row grid gap-3 border border-line rounded-[12px] p-2.5 bg-[rgba(255,255,255,0.02)]" style={{ gridTemplateColumns: "170px 1fr" }}>
                  <div className="relative aspect-[16/7] rounded-[10px] overflow-hidden bg-surface-3">
                    {p.desktopImage ? <PosterImageFill imageKey={p.desktopImage} alt={p.name} /> : null}
                  </div>
                  <div>
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <strong className="text-ink">{p.name}</strong>
                      <span className="px-2 py-0.5 rounded-full text-[0.68rem] border border-line text-ink-light">{p.size}</span>
                      <StatusBadge value={p.active ? "Active" : "Inactive"} colorMap={ACTIVE_INACTIVE_COLORS} />
                      <span className="text-xs text-muted">#{p.sortOrder}</span>
                    </div>
                    <div className="grid gap-[3px] text-[0.78rem] text-muted">
                      <div>Slug: {p.slug}</div>
                      <div>Link: {p.linkType}{p.linkTarget ? ` -> ${p.linkTarget}` : ""}{p.openInNewTab ? " (new tab)" : ""}</div>
                      {p.title && <div>Title: {p.title}</div>}
                    </div>

                    {/* Inline analytics for this poster */}
                    {showAnalytics && posterAnalytics && (
                      <div className="mt-2 flex flex-wrap gap-3 text-[0.75rem]">
                        <span className="text-[#60a5fa]">{posterAnalytics.impressionCount.toLocaleString()} impressions</span>
                        <span className="text-[#34d399]">{posterAnalytics.clickCount.toLocaleString()} clicks</span>
                        <span className="text-[#c084fc]">{posterAnalytics.clickThroughRate.toFixed(2)}% CTR</span>
                      </div>
                    )}

                    <div className="mt-3 flex flex-wrap gap-2">
                      {!showDeleted && <>
                        <button type="button" onClick={() => onEdit(p)} className="px-3 py-[7px] rounded-lg border border-line-bright bg-brand-soft text-[#9fe9ff] font-bold text-[0.75rem]">Edit</button>
                        <button type="button" onClick={() => onDuplicate(p)} className="px-3 py-[7px] rounded-lg border border-[rgba(167,139,250,0.2)] bg-[rgba(124,58,237,0.08)] text-[#c4b5fd] font-bold text-[0.75rem]">Duplicate</button>
                        <button type="button" onClick={() => onDelete(p)} className="px-3 py-[7px] rounded-lg border border-[rgba(239,68,68,0.2)] bg-[rgba(239,68,68,0.08)] text-[#fca5a5] font-bold text-[0.75rem]">Delete</button>
                      </>}
                      {showDeleted && <button type="button" disabled={restoreId === p.id} onClick={() => { void onRestore(p.id); }} className="px-3 py-[7px] rounded-lg border border-[rgba(34,197,94,0.2)] bg-[rgba(34,197,94,0.08)] text-[#86efac] font-bold text-[0.75rem]">{restoreId === p.id ? "Restoring..." : "Restore"}</button>}
                    </div>
                  </div>
                </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <style jsx>{`
        @media (max-width: 768px) {
          .poster-card-row {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </section>
  );
}
