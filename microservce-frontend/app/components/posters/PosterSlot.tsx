"use client";

import { CSSProperties, useEffect, useMemo, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { API_BASE } from "../../../lib/constants";

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
  autoplayMs?: number;
  className?: string;
  style?: CSSProperties;
};

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

function CarouselControls({
  count,
  index,
  onPrev,
  onNext,
  onSelect,
  compact,
}: {
  count: number;
  index: number;
  onPrev: () => void;
  onNext: () => void;
  onSelect: (next: number) => void;
  compact: boolean;
}) {
  return (
    <>
      <button
        type="button"
        onClick={onPrev}
        aria-label="Previous poster"
        className={`absolute left-2.5 top-1/2 -translate-y-1/2 rounded-full border border-white/[0.22] bg-[rgba(7,10,18,0.55)] text-white grid place-items-center font-extrabold backdrop-blur-sm cursor-pointer z-[2] ${compact ? "w-[34px] h-[34px] text-[0.9rem]" : "w-10 h-10 text-lg"}`}
      >
        ‹
      </button>

      <button
        type="button"
        onClick={onNext}
        aria-label="Next poster"
        className={`absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full border border-white/[0.22] bg-[rgba(7,10,18,0.55)] text-white grid place-items-center font-extrabold backdrop-blur-sm cursor-pointer z-[2] ${compact ? "w-[34px] h-[34px] text-[0.9rem]" : "w-10 h-10 text-lg"}`}
      >
        ›
      </button>

      <div
        aria-label="Poster slide indicators"
        className="absolute left-1/2 bottom-2.5 -translate-x-1/2 flex items-center gap-1.5 px-2 py-1.5 rounded-full bg-[rgba(7,10,18,0.45)] border border-white/[0.12] backdrop-blur-sm z-[2]"
      >
        {Array.from({ length: count }).map((_, dotIndex) => (
          <button
            key={dotIndex}
            type="button"
            onClick={() => onSelect(dotIndex)}
            aria-label={`Show poster ${dotIndex + 1}`}
            aria-pressed={dotIndex === index}
            className={`h-2 rounded-full border-none cursor-pointer transition-all duration-[140ms] ease-linear ${dotIndex === index ? "w-[18px] bg-brand/95" : "w-2 bg-white/45"}`}
          />
        ))}
      </div>
    </>
  );
}

export default function PosterSlot({
  placement,
  variant = "strip",
  maxItems = 12,
  autoplayMs = 6000,
  className,
  style,
}: Props) {
  const [items, setItems] = useState<PosterItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeIndex, setActiveIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const touchStartXRef = useRef<number | null>(null);
  const touchStartYRef = useRef<number | null>(null);
  const touchSuppressClickRef = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      setLoading(true);
      try {
        const res = await fetch(`${API_BASE}/posters?placement=${encodeURIComponent(placement)}`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) throw new Error("Failed");
        const json = await res.json();
        const data: PosterItem[] = Array.isArray(json) ? json : (json.content ?? []);
        if (controller.signal.aborted) return;
        setItems(data.slice(0, Math.max(1, maxItems)));
      } catch {
        if (!controller.signal.aborted) setItems([]);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void run();
    return () => controller.abort();
  }, [placement, maxItems]);

  const visibleItems = useMemo(() => items.filter(Boolean), [items]);
  const canRotate = visibleItems.length > 1;
  const movePrev = () => {
    if (!canRotate) return;
    setActiveIndex((currentIndex) => (currentIndex - 1 + visibleItems.length) % visibleItems.length);
  };
  const moveNext = () => {
    if (!canRotate) return;
    setActiveIndex((currentIndex) => (currentIndex + 1) % visibleItems.length);
  };

  useEffect(() => {
    setActiveIndex((current) => {
      if (visibleItems.length === 0) return 0;
      return current >= visibleItems.length ? 0 : current;
    });
  }, [visibleItems.length]);

  useEffect(() => {
    if (!canRotate || paused || autoplayMs <= 0) return;
    const timer = window.setInterval(() => {
      setActiveIndex((current) => (current + 1) % visibleItems.length);
    }, autoplayMs);
    return () => window.clearInterval(timer);
  }, [autoplayMs, canRotate, paused, visibleItems.length]);

  if (loading && visibleItems.length === 0) return null;
  if (visibleItems.length === 0) return null;

  if (!canRotate && variant === "grid") {
    return (
      <section className={className} style={style}>
        <div className="grid gap-4 md:grid-cols-2">
          {visibleItems.map((poster) => <PosterCard key={poster.id} poster={poster} variant="grid" />)}
        </div>
      </section>
    );
  }

  if (canRotate) {
    const current = visibleItems[activeIndex] ?? visibleItems[0];
    return (
      <section
        className={className}
        style={style}
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
        onFocusCapture={() => setPaused(true)}
        onBlurCapture={() => setPaused(false)}
      >
        <div
          className="relative touch-pan-y"
          onTouchStart={(event) => {
            const touch = event.touches[0];
            if (!touch) return;
            touchStartXRef.current = touch.clientX;
            touchStartYRef.current = touch.clientY;
            touchSuppressClickRef.current = false;
            setPaused(true);
          }}
          onTouchEnd={(event) => {
            const startX = touchStartXRef.current;
            const startY = touchStartYRef.current;
            touchStartXRef.current = null;
            touchStartYRef.current = null;
            setPaused(false);
            const touch = event.changedTouches[0];
            if (!touch || startX == null || startY == null) return;
            const deltaX = touch.clientX - startX;
            const deltaY = touch.clientY - startY;
            const absX = Math.abs(deltaX);
            const absY = Math.abs(deltaY);
            if (absX < 40 || absX <= absY) return;
            touchSuppressClickRef.current = true;
            if (deltaX < 0) moveNext();
            else movePrev();
            window.setTimeout(() => {
              touchSuppressClickRef.current = false;
            }, 250);
          }}
          onTouchCancel={() => {
            touchStartXRef.current = null;
            touchStartYRef.current = null;
            touchSuppressClickRef.current = false;
            setPaused(false);
          }}
          onClickCapture={(event) => {
            if (!touchSuppressClickRef.current) return;
            event.preventDefault();
            event.stopPropagation();
          }}
        >
          <PosterCard poster={current} variant={variant} />
          <CarouselControls
            count={visibleItems.length}
            index={activeIndex}
            compact={variant === "tile"}
            onPrev={movePrev}
            onNext={moveNext}
            onSelect={(next) => setActiveIndex(next)}
          />
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
