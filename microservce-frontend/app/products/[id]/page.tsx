"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useParams } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import { useAuthSession } from "../../../lib/authSession";

type Variation = {
  name: string;
  value: string;
};

type ProductDetail = {
  id: string;
  parentProductId: string | null;
  name: string;
  shortDescription: string;
  description: string;
  images: string[];
  regularPrice: number;
  discountedPrice: number | null;
  sellingPrice: number;
  vendorId: string;
  mainCategory: string | null;
  subCategories: string[];
  categories: string[];
  productType: string;
  variations: Variation[];
  sku: string;
};
type VariationSummary = {
  id: string;
  name: string;
  sku: string;
};

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function resolveImageUrl(imageName: string): string | null {
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (base) {
    return `${base.replace(/\/+$/, "")}/${imageName.replace(/^\/+/, "")}`;
  }
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = imageName
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  return `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
}

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const { isAuthenticated, profile, logout, canViewAdmin, login, apiClient } = useAuthSession();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [variations, setVariations] = useState<VariationSummary[]>([]);
  const [selectedVariationId, setSelectedVariationId] = useState("");
  const [selectedVariation, setSelectedVariation] = useState<ProductDetail | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [status, setStatus] = useState("Loading product...");

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products/${params.id}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as ProductDetail;
        setProduct(data);
        setSelectedImageIndex(0);
        setStatus("Product loaded.");
        if (data.productType === "PARENT") {
          const vRes = await fetch(`${apiBase}/products/${params.id}/variations`, { cache: "no-store" });
          if (vRes.ok) {
            const vData = (await vRes.json()) as VariationSummary[];
            setVariations(vData || []);
          }
        }
      } catch {
        setStatus("Product not found.");
        toast.error("Failed to load product");
      }
    };
    void run();
  }, [params.id]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") {
      setSelectedVariation(null);
      return;
    }
    if (!selectedVariationId) {
      setSelectedVariation(null);
      setSelectedImageIndex(0);
      return;
    }
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products/${selectedVariationId}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed to load variation");
        const data = (await res.json()) as ProductDetail;
        setSelectedVariation(data);
        setSelectedImageIndex(0);
      } catch {
        toast.error("Failed to load selected variation details");
      }
    };
    void run();
  }, [selectedVariationId, product]);

  const buyNow = async () => {
    if (!apiClient || !product) return;
    const targetProductId =
      product.productType === "PARENT" ? selectedVariationId.trim() : product.id;

    if (!targetProductId) {
      toast.error("Select a variation before buying");
      return;
    }

    try {
      await apiClient.post("/orders/me", {
        productId: targetProductId,
        quantity,
      });
      toast.success("Order placed successfully");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to place order");
    }
  };
  const displayProduct = selectedVariation || product;
  if (!displayProduct) {
    return (
      <main className="mx-auto min-h-screen max-w-7xl px-6 py-8">
        <p className="rounded-xl border border-dashed border-[var(--line)] p-4 text-sm text-[var(--muted)]">{status}</p>
      </main>
    );
  }

  return (
    <main className="mx-auto min-h-screen max-w-7xl px-6 py-8">
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          canViewAdmin={canViewAdmin}
          onLogout={() => {
            void logout();
          }}
        />
      )}

      <section className="card-surface animate-rise rounded-3xl p-6 md:p-8">
        <div className="mb-6 flex items-center justify-between">
          <Link href="/products" className="text-sm font-semibold text-[var(--brand)] hover:underline">
            Back to catalog
          </Link>
          <p className="text-xs tracking-[0.2em] text-[var(--muted)]">PRODUCT DETAIL</p>
        </div>

        {!product && (
          <p className="rounded-xl border border-dashed border-[var(--line)] p-4 text-sm text-[var(--muted)]">
            {status}
          </p>
        )}

        {product && (
          <div className="grid gap-8 lg:grid-cols-[1fr,1fr]">
            <div className="space-y-3">
              <div className="h-80 rounded-2xl border border-[var(--line)] bg-[linear-gradient(160deg,#eee7db,#f9f5ee)] p-4 text-sm text-[var(--muted)]">
                {resolveImageUrl(displayProduct.images?.[selectedImageIndex] || "") ? (
                  <Image
                    src={resolveImageUrl(displayProduct.images[selectedImageIndex]) || ""}
                    alt={displayProduct.name}
                    width={800}
                    height={800}
                    className="h-full w-full rounded-xl object-cover"
                    unoptimized
                  />
                ) : (
                  displayProduct.images?.[selectedImageIndex] || "main-image.jpg"
                )}
              </div>
              <div className="grid grid-cols-5 gap-2">
                {displayProduct.images?.slice(0, 5).map((img, index) => {
                  const imageUrl = resolveImageUrl(img);
                  return (
                    <button
                      key={`${img}-${index}`}
                      onClick={() => setSelectedImageIndex(index)}
                      className={`overflow-hidden rounded-xl border bg-white px-1 py-1 text-xs ${
                        selectedImageIndex === index ? "border-[var(--brand)]" : "border-[var(--line)]"
                      }`}
                    >
                      {imageUrl ? (
                        <Image src={imageUrl} alt={img} width={96} height={56} className="h-14 w-full rounded-md object-cover" unoptimized />
                      ) : (
                        <span className="block truncate px-1 py-4 text-[10px] text-[var(--muted)]">{img}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="space-y-4">
              <h1 className="text-4xl text-[var(--ink)]">{displayProduct.name}</h1>
              <p className="text-sm leading-6 text-[var(--muted)]">{displayProduct.description}</p>
              <p className="text-xs text-[var(--muted)]">SKU: {displayProduct.sku}</p>
              <div className="flex items-center gap-3">
                <span className="text-2xl font-semibold text-[var(--ink)]">{money(displayProduct.sellingPrice)}</span>
                {displayProduct.discountedPrice !== null && (
                  <span className="text-sm text-[var(--muted)] line-through">{money(displayProduct.regularPrice)}</span>
                )}
              </div>

              <div className="flex flex-wrap gap-2">
                {displayProduct.mainCategory && (
                  <span className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs text-white">
                    Main: {displayProduct.mainCategory}
                  </span>
                )}
                {displayProduct.subCategories.map((c) => (
                  <span key={c} className="rounded-full bg-[var(--brand-soft)] px-3 py-1 text-xs capitalize text-[var(--ink)]">
                    {c}
                  </span>
                ))}
              </div>

              {displayProduct.variations.length > 0 && (
                <div className="rounded-xl border border-[var(--line)] bg-white p-4">
                  <p className="text-xs tracking-[0.2em] text-[var(--muted)]">
                    {displayProduct.productType === "PARENT" ? "AVAILABLE ATTRIBUTES" : "VARIATIONS"}
                  </p>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {displayProduct.variations.map((v, i) => (
                      <div key={`${v.name}-${i}`} className="rounded-lg border border-[var(--line)] px-3 py-2 text-sm">
                        {displayProduct.productType === "PARENT" ? (
                          <span className="capitalize text-[var(--ink)]">{v.name}</span>
                        ) : (
                          <>
                            <span className="capitalize text-[var(--muted)]">{v.name}:</span> {v.value}
                          </>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="pt-2">
                {isAuthenticated ? (
                  <div className="space-y-3">
                    {product.productType === "PARENT" && (
                      <div>
                        <label className="mb-1 block text-xs tracking-[0.2em] text-[var(--muted)]">SELECT VARIATION</label>
                        <select
                          value={selectedVariationId}
                          onChange={(e) => setSelectedVariationId(e.target.value)}
                          className="w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-sm text-[var(--ink)]"
                        >
                          <option value="">Choose variation</option>
                          {variations.map((v) => (
                            <option key={v.id} value={v.id}>
                              {v.name} ({v.sku})
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    <div className="flex items-center gap-3">
                      <div className="inline-flex items-center overflow-hidden rounded-lg border border-[var(--line)] bg-white">
                        <button
                          onClick={() => setQuantity((old) => Math.max(1, old - 1))}
                          className="px-3 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
                        >
                          -
                        </button>
                        <span className="min-w-10 border-x border-[var(--line)] px-3 py-2 text-center text-sm text-[var(--ink)]">
                          {quantity}
                        </span>
                        <button
                          onClick={() => setQuantity((old) => old + 1)}
                          className="px-3 py-2 text-sm text-[var(--ink)] hover:bg-[var(--brand-soft)]"
                        >
                          +
                        </button>
                      </div>
                      <button onClick={() => void buyNow()} className="btn-brand rounded-xl px-5 py-2 text-sm font-semibold">
                        Buy Now
                      </button>
                      <Link href="/orders" className="rounded-xl border border-[var(--line)] bg-white px-4 py-2 text-sm text-[var(--ink)]">
                        My Orders
                      </Link>
                    </div>
                  </div>
                ) : (
                  <button onClick={() => void login("/products")} className="btn-brand rounded-xl px-5 py-2 text-sm font-semibold">
                    Sign In to Continue
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </section>
    </main>
  );
}
