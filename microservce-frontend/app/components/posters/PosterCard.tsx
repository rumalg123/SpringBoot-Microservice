"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { API_BASE } from "../../../lib/constants";

type PosterLinkType = "PRODUCT" | "CATEGORY" | "SEARCH" | "URL" | "NONE";
type PosterSize = "HERO" | "WIDE" | "TALL" | "SQUARE" | "STRIP" | "CUSTOM";

export type PosterItem = {
  id: string;
  slug: string;
  name: string;
  placement: string;
  size: PosterSize;
  desktopImage: string;
  mobileImage: string | null;
  linkType: PosterLinkType;
  linkTarget: string | null;
  openInNewTab: boolean;
  title: string | null;
  subtitle: string | null;
  ctaLabel: string | null;
  backgroundColor: string | null;
  sortOrder: number;
  active: boolean;
};

export type Variant = "strip" | "hero" | "grid" | "tile";

function resolvePosterImageSources(imageName: string | null): { primary: string | null; fallback: string | null } {
  if (!imageName) return { primary: null, fallback: null };
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = API_BASE;
  const encoded = normalized.split("/").map((s) => encodeURIComponent(s)).join("/");
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

function resolvePosterHref(poster: PosterItem): string | null {
  const target = (poster.linkTarget || "").trim();
  switch (poster.linkType) {
    case "PRODUCT":
      if (!target) return null;
      if (target.startsWith("/products/")) return target;
      return `/products/${encodeURIComponent(target)}`;
    case "CATEGORY":
      if (!target) return null;
      if (target.startsWith("/categories/")) return target;
      return `/categories/${encodeURIComponent(target)}`;
    case "SEARCH":
      if (!target) return null;
      return target.startsWith("?") ? `/products${target}` : `/products?${target}`;
    case "URL":
      return target || null;
    case "NONE":
    default:
      return null;
  }
}

function isExternal(href: string | null): boolean {
  return !!href && /^https?:\/\//i.test(href);
}

export function aspectByVariant(variant: Variant): string {
  switch (variant) {
    case "hero": return "16 / 6";
    case "strip": return "16 / 3.2";
    case "tile": return "1 / 1";
    case "grid":
    default: return "16 / 7";
  }
}

export default function PosterCard({ poster, variant }: { poster: PosterItem; variant: Variant }) {
  const imageSources = useMemo(() => resolvePosterImageSources(poster.desktopImage), [poster.desktopImage]);
  const [imageUrl, setImageUrl] = useState<string | null>(imageSources?.primary ?? null);
  useEffect(() => {
    setImageUrl(imageSources?.primary ?? null);
  }, [imageSources]);
  const href = resolvePosterHref(poster);
  const bg = poster.backgroundColor || "linear-gradient(135deg, rgba(0,212,255,0.08), rgba(124,58,237,0.08))";

  const inner = (
    <div
      className={`relative overflow-hidden border border-brand/[0.12] shadow-[0_10px_30px_rgba(0,0,0,0.18)] ${variant === "hero" ? "rounded-xl" : "rounded-lg"}`}
      style={{ background: bg, aspectRatio: aspectByVariant(variant) }}
    >
      {imageUrl ? (
        <Image
          src={imageUrl}
          alt={poster.title || poster.name}
          fill
          sizes={variant === "hero" ? "100vw" : "(max-width: 1024px) 100vw, 50vw"}
          className="object-cover"
          unoptimized
          onError={() => {
            if (imageSources?.fallback && imageUrl !== imageSources.fallback) {
              setImageUrl(imageSources.fallback);
              return;
            }
            setImageUrl(null);
          }}
        />
      ) : (
        <div className="absolute inset-0 bg-[linear-gradient(135deg,#0f172a,#111827)]" />
      )}

      <div
        className="absolute inset-0"
        style={{
          background: variant === "hero"
            ? "linear-gradient(90deg, rgba(7,10,18,0.8) 0%, rgba(7,10,18,0.45) 45%, rgba(7,10,18,0.15) 100%)"
            : "linear-gradient(180deg, rgba(7,10,18,0.1) 0%, rgba(7,10,18,0.65) 100%)",
        }}
      />

      <div
        className={`absolute inset-0 flex ${variant === "hero" ? "items-center px-6 py-[22px]" : "items-end p-3.5"}`}
      >
        <div className={variant === "hero" ? "max-w-[540px]" : "max-w-full"}>
          {poster.title && (
            <div
              className={`text-white font-extrabold leading-[1.15] [text-shadow:0_2px_10px_rgba(0,0,0,0.35)] ${variant === "hero" ? "text-[clamp(1.15rem,2vw,2rem)]" : "text-[0.95rem]"} ${poster.subtitle ? "mb-1.5" : ""}`}
            >
              {poster.title}
            </div>
          )}
          {poster.subtitle && (
            <div
              className={`text-white/[0.88] leading-[1.35] line-clamp-2 ${variant === "hero" ? "text-[0.9rem]" : "text-[0.75rem]"} ${poster.ctaLabel ? "mb-2.5" : ""}`}
            >
              {poster.subtitle}
            </div>
          )}
          {poster.ctaLabel && (
            <span
              className={`inline-flex items-center gap-1.5 rounded-full bg-brand/20 text-[#d9fbff] border border-brand/[0.26] font-bold ${variant === "hero" ? "px-3 py-2 text-[0.78rem]" : "px-2.5 py-1.5 text-xs"}`}
            >
              {poster.ctaLabel}
              <span aria-hidden>→</span>
            </span>
          )}
        </div>
      </div>
    </div>
  );

  if (!href) return inner;
  if (isExternal(href)) {
    return (
      <a href={href} target={poster.openInNewTab ? "_blank" : "_self"} rel={poster.openInNewTab ? "noreferrer noopener" : undefined} className="no-underline">
        {inner}
      </a>
    );
  }
  return (
    <Link href={href} className="no-underline">
      {inner}
    </Link>
  );
}
