"use client";

import { CSSProperties, useEffect, useMemo, useRef, useState } from "react";
import { API_BASE } from "../../../lib/constants";
import PosterCard, { type PosterItem, type Variant } from "./PosterCard";
import CarouselControls from "./CarouselControls";

type PosterPlacement =
  | "HOME_HERO"
  | "HOME_TOP_STRIP"
  | "HOME_MID_LEFT"
  | "HOME_MID_RIGHT"
  | "HOME_BOTTOM_GRID"
  | "CATEGORY_TOP"
  | "CATEGORY_SIDEBAR"
  | "PRODUCT_DETAIL_SIDE";

type Props = {
  placement: PosterPlacement;
  variant?: Variant;
  maxItems?: number;
  autoplayMs?: number;
  className?: string;
  style?: CSSProperties;
};

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
