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
      <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
        <header style={{ background: "var(--header-bg)", backdropFilter: "blur(12px)", borderBottom: "1px solid var(--brand-soft)", position: "sticky", top: 0, zIndex: 100 }}>
          <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between">
            <Link href="/" className="no-underline flex items-center gap-2">
              <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: 900, fontSize: "1.2rem", background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
                Rumal Store
              </span>
            </Link>
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-8">
          <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "20px", padding: "48px 24px", textAlign: "center" }}>
            <p style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--ink-light)", margin: "0 0 12px" }}>{status}</p>
            <Link href="/" style={{ color: "var(--brand)", fontSize: "0.875rem", textDecoration: "none" }}>← Back to Home</Link>
          </div>
        </main>
        <Footer />
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <header style={{ background: "var(--header-bg)", backdropFilter: "blur(12px)", borderBottom: "1px solid var(--brand-soft)", position: "sticky", top: 0, zIndex: 100 }}>
        <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between">
          <Link href="/" className="no-underline flex items-center gap-2">
            <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: 900, fontSize: "1.2rem", background: "var(--gradient-brand)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
              Rumal Store
            </span>
          </Link>
          <Link href="/" className="no-underline" style={{ padding: "8px 18px", borderRadius: "10px", background: "var(--gradient-brand)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
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

        <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid var(--line-bright)", borderRadius: "20px", padding: "28px", marginTop: "8px" }}>
          <div style={{ marginBottom: "24px" }}>
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.5rem", fontWeight: 900, color: "#fff", margin: "0 0 6px" }}>
              {data.collectionName}
            </h1>
            {data.description && (
              <p style={{ fontSize: "0.85rem", color: "var(--muted)", margin: 0 }}>{data.description}</p>
            )}
            <p style={{ fontSize: "0.75rem", color: "var(--muted-2)", margin: "8px 0 0" }}>
              {data.itemCount} {data.itemCount === 1 ? "item" : "items"}
            </p>
          </div>

          {data.items.length === 0 ? (
            <p style={{ textAlign: "center", color: "var(--muted)", padding: "32px 0" }}>This wishlist is empty.</p>
          ) : (
            <div style={{ display: "grid", gap: "16px", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))" }}>
              {data.items.map((item: WishlistItem) => {
                const imgUrl = resolveImageUrl(item.mainImage);
                return (
                  <Link
                    key={item.id}
                    href={`/products/${encodeURIComponent(item.productSlug || item.productId)}`}
                    className="product-card no-underline"
                    style={{ display: "flex", flexDirection: "column" }}
                  >
                    <div style={{ position: "relative", aspectRatio: "1/1", overflow: "hidden", background: "var(--surface-2)", borderRadius: "12px 12px 0 0" }}>
                      {imgUrl ? (
                        <Image src={imgUrl} alt={item.productName} width={300} height={300} className="product-card-img" unoptimized />
                      ) : (
                        <div style={{ display: "grid", placeItems: "center", width: "100%", height: "100%", background: "linear-gradient(135deg, var(--surface), #1c1c38)", color: "var(--muted-2)", fontSize: "0.75rem" }}>No Image</div>
                      )}
                    </div>
                    <div className="product-card-body" style={{ padding: "12px", flex: 1 }}>
                      <p style={{ margin: "0 0 6px", fontSize: "0.85rem", fontWeight: 600, color: "var(--ink)", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
                        {item.productName}
                      </p>
                      {item.sellingPriceSnapshot !== null && (
                        <span className="price-current" style={{ fontSize: "0.9rem" }}>{money(item.sellingPriceSnapshot)}</span>
                      )}
                      {item.note && (
                        <p style={{ margin: "6px 0 0", fontSize: "0.72rem", color: "var(--muted)", fontStyle: "italic" }}>
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
