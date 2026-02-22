"use client";

import { CSSProperties, useEffect, useMemo, useState } from "react";
import Image from "next/image";
import Link from "next/link";

type PosterPlacement =
  | "HOME_HERO"
  | "HOME_TOP_STRIP"
  | "HOME_MID_LEFT"
  | "HOME_MID_RIGHT"
  | "HOME_BOTTOM_GRID"
  | "CATEGORY_TOP"
  | "CATEGORY_SIDEBAR"
  | "PRODUCT_DETAIL_SIDE";

type PosterLinkType = "PRODUCT" | "CATEGORY" | "SEARCH" | "URL" | "NONE";
type PosterSize = "HERO" | "WIDE" | "TALL" | "SQUARE" | "STRIP" | "CUSTOM";

type PosterItem = {
  id: string;
  slug: string;
  name: string;
  placement: PosterPlacement;
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

type Variant = "strip" | "hero" | "grid" | "tile";

type Props = {
  placement: PosterPlacement;
  variant?: Variant;
  maxItems?: number;
  className?: string;
  style?: CSSProperties;
};

function resolvePosterImageUrl(imageName: string | null): string | null {
  if (!imageName) return null;
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map((s) => encodeURIComponent(s)).join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/posters/images/${encoded}`;
  const cdnBase = (process.env.NEXT_PUBLIC_POSTER_IMAGE_BASE_URL || "").trim();
  if (!cdnBase) return apiUrl;
  if (normalized.startsWith("posters/")) {
    return `${cdnBase.replace(/\/+$/, "")}/${normalized}`;
  }
  return apiUrl;
}

function resolvePosterHref(poster: PosterItem): string | null {
  const target = (poster.linkTarget || "").trim();
  switch (poster.linkType) {
    case "PRODUCT":
      return target ? `/products/${encodeURIComponent(target)}` : null;
    case "CATEGORY":
      return target ? `/categories/${encodeURIComponent(target)}` : null;
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

function aspectByVariant(variant: Variant): string {
  switch (variant) {
    case "hero": return "16 / 6";
    case "strip": return "16 / 3.2";
    case "tile": return "1 / 1";
    case "grid":
    default: return "16 / 7";
  }
}

function PosterCard({ poster, variant }: { poster: PosterItem; variant: Variant }) {
  const imageUrl = resolvePosterImageUrl(poster.desktopImage);
  const href = resolvePosterHref(poster);
  const bg = poster.backgroundColor || "linear-gradient(135deg, rgba(0,212,255,0.08), rgba(124,58,237,0.08))";

  const inner = (
    <div
      style={{
        position: "relative",
        overflow: "hidden",
        borderRadius: variant === "hero" ? "20px" : "16px",
        border: "1px solid rgba(0,212,255,0.12)",
        background: bg,
        aspectRatio: aspectByVariant(variant),
        boxShadow: "0 10px 30px rgba(0,0,0,0.18)",
      }}
    >
      {imageUrl ? (
        <Image
          src={imageUrl}
          alt={poster.title || poster.name}
          fill
          sizes={variant === "hero" ? "100vw" : "(max-width: 1024px) 100vw, 50vw"}
          style={{ objectFit: "cover" }}
          unoptimized
        />
      ) : (
        <div style={{ position: "absolute", inset: 0, background: "linear-gradient(135deg,#0f172a,#111827)" }} />
      )}

      <div
        style={{
          position: "absolute",
          inset: 0,
          background: variant === "hero"
            ? "linear-gradient(90deg, rgba(7,10,18,0.8) 0%, rgba(7,10,18,0.45) 45%, rgba(7,10,18,0.15) 100%)"
            : "linear-gradient(180deg, rgba(7,10,18,0.1) 0%, rgba(7,10,18,0.65) 100%)",
        }}
      />

      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          alignItems: variant === "hero" ? "center" : "flex-end",
          padding: variant === "hero" ? "22px 24px" : "14px 14px",
        }}
      >
        <div style={{ maxWidth: variant === "hero" ? "540px" : "100%" }}>
          {poster.title && (
            <div
              style={{
                color: "#fff",
                fontWeight: 800,
                fontSize: variant === "hero" ? "clamp(1.15rem, 2vw, 2rem)" : "0.95rem",
                lineHeight: 1.15,
                marginBottom: poster.subtitle ? "6px" : "0",
                textShadow: "0 2px 10px rgba(0,0,0,0.35)",
              }}
            >
              {poster.title}
            </div>
          )}
          {poster.subtitle && (
            <div
              style={{
                color: "rgba(255,255,255,0.88)",
                fontSize: variant === "hero" ? "0.9rem" : "0.75rem",
                lineHeight: 1.35,
                marginBottom: poster.ctaLabel ? "10px" : "0",
                display: "-webkit-box",
                WebkitLineClamp: variant === "hero" ? 2 : 2,
                WebkitBoxOrient: "vertical",
                overflow: "hidden",
              }}
            >
              {poster.subtitle}
            </div>
          )}
          {poster.ctaLabel && (
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "6px",
                padding: variant === "hero" ? "8px 12px" : "6px 10px",
                borderRadius: "999px",
                background: "rgba(0,212,255,0.2)",
                color: "#d9fbff",
                border: "1px solid rgba(0,212,255,0.26)",
                fontSize: variant === "hero" ? "0.78rem" : "0.7rem",
                fontWeight: 700,
              }}
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

export default function PosterSlot({ placement, variant = "strip", maxItems = 1, className, style }: Props) {
  const [items, setItems] = useState<PosterItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const run = async () => {
      setLoading(true);
      try {
        const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
        const res = await fetch(`${apiBase}/posters?placement=${encodeURIComponent(placement)}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        const data = ((await res.json()) as PosterItem[]) || [];
        if (!active) return;
        setItems(data.slice(0, Math.max(1, maxItems)));
      } catch {
        if (active) setItems([]);
      } finally {
        if (active) setLoading(false);
      }
    };
    void run();
    return () => { active = false; };
  }, [placement, maxItems]);

  const visibleItems = useMemo(() => items.filter(Boolean), [items]);
  if (loading && visibleItems.length === 0) return null;
  if (visibleItems.length === 0) return null;

  if (variant === "grid") {
    return (
      <section className={className} style={style}>
        <div className="grid gap-4 md:grid-cols-2">
          {visibleItems.map((poster) => <PosterCard key={poster.id} poster={poster} variant="grid" />)}
        </div>
      </section>
    );
  }

  if (maxItems > 1 && visibleItems.length > 1) {
    return (
      <section className={className} style={style}>
        <div className="grid gap-4 md:grid-cols-2">
          {visibleItems.map((poster) => <PosterCard key={poster.id} poster={poster} variant={variant} />)}
        </div>
      </section>
    );
  }

  return (
    <section className={className} style={style}>
      <PosterCard poster={visibleItems[0]} variant={variant} />
    </section>
  );
}

