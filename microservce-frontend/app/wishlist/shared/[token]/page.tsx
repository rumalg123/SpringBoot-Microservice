"use client";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Footer from "../../../components/Footer";
import { money } from "../../../../lib/format";
import { resolveImageUrl } from "../../../../lib/image";
import { API_BASE } from "../../../../lib/constants";
import type { SharedWishlistResponse, WishlistItem } from "../../../../lib/types/wishlist";

export default function SharedWishlistPage() {
  const params = useParams<{ token: string }>();
  const [data, setData] = useState<SharedWishlistResponse | null>(null);
  const [status, setStatus] = useState("Loading shared wishlist...");

  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const res = await fetch(
          `${API_BASE}/wishlist/shared/${encodeURIComponent(params.token)}`,
          { cache: "no-store", signal: controller.signal },
        );
        if (!res.ok) throw new Error("Not found");
        if (controller.signal.aborted) return;
        setData((await res.json()) as SharedWishlistResponse);
        setStatus("ready");
      } catch {
        if (!controller.signal.aborted) setStatus("This shared wishlist is not available.");
      }
    };
    void run();
    return () => controller.abort();
  }, [params.token]);

  if (!data) {
    return (
      <div className="min-h-screen bg-bg">
        <header className="sticky top-0 z-[100] border-b border-brand-soft bg-header-bg backdrop-blur-[12px]">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2 no-underline">
              <span className="font-[Syne,sans-serif] text-[1.2rem] font-black" style={{ background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
                Rumal Store
              </span>
            </Link>
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-8">
          <div className="rounded-xl border border-line-bright bg-[rgba(17,17,40,0.7)] px-6 py-12 text-center backdrop-blur-[16px]">
            <p className="mb-3 mt-0 text-[1.1rem] font-bold text-ink-light">{status}</p>
            <Link href="/" className="text-base text-brand no-underline">← Back to Home</Link>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg">
      <header className="sticky top-0 z-[100] border-b border-brand-soft bg-header-bg backdrop-blur-[12px]">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
          <Link href="/" className="flex items-center gap-2 no-underline">
            <span className="font-[Syne,sans-serif] text-[1.2rem] font-black" style={{ background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
              Rumal Store
            </span>
          </Link>
          <Link href="/" className="rounded-md bg-[var(--gradient-brand)] px-[18px] py-2 text-sm font-bold text-white no-underline">
            Sign In
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 py-8">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current">Shared Wishlist</span>
        </nav>

        <div className="mt-2 rounded-xl border border-line-bright bg-[rgba(17,17,40,0.7)] p-7 backdrop-blur-[16px]">
          <div className="mb-6">
            <h1 className="mb-1.5 mt-0 font-[Syne,sans-serif] text-[1.5rem] font-black text-white">
              {data.collectionName}
            </h1>
            {data.description && (
              <p className="m-0 text-sm text-muted">{data.description}</p>
            )}
            <p className="mt-2 mb-0 text-xs text-muted-2">
              {data.itemCount} {data.itemCount === 1 ? "item" : "items"}
            </p>
          </div>

          {data.items.length === 0 ? (
            <p className="py-8 text-center text-muted">This wishlist is empty.</p>
          ) : (
            <div className="grid gap-4" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
              {data.items.map((item: WishlistItem) => {
                const imgUrl = resolveImageUrl(item.mainImage);
                return (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug || item.productId)}`}
                    className="product-card flex flex-col no-underline"
                  >
                    <div className="relative aspect-square overflow-hidden rounded-t-[12px] bg-surface-2">
                      {imgUrl ? (
                        <Image src={imgUrl} alt={item.productName} width={300} height={300} className="product-card-img" unoptimized />
                      ) : (
                        <div className="grid h-full w-full place-items-center bg-[linear-gradient(135deg,var(--surface),#1c1c38)] text-xs text-muted-2">No Image</div>
                      )}
                    </div>
                    <div className="product-card-body flex-1 p-3">
                      <p className="mb-1.5 line-clamp-2 text-sm font-semibold text-ink">
                        {item.productName}
                      </p>
                      {item.sellingPriceSnapshot !== null && (
                        <span className="price-current text-[0.9rem]">{money(item.sellingPriceSnapshot)}</span>
                      )}
                      {item.note && (
                        <p className="mt-1.5 mb-0 text-xs italic text-muted">
                          &ldquo;{item.note}&rdquo;
                        </p>
                      )}
                    </div>
                  </Link>
                );
              })}
            </div>
          )}
        </div>
      </main>

      <Footer />
    </div>
  );
}
