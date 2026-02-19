"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
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

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}

function resolveImageUrl(imageName: string): string | null {
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (!base) return null;
  return `${base.replace(/\/+$/, "")}/${imageName.replace(/^\/+/, "")}`;
}

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const { isAuthenticated, profile, logout, canViewAdmin, login, apiClient } = useAuthSession();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [variations, setVariations] = useState<ProductDetail[]>([]);
  const [selectedVariationId, setSelectedVariationId] = useState("");
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
            const vData = (await vRes.json()) as ProductDetail[];
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
                {resolveImageUrl(product.images?.[selectedImageIndex] || "") ? (
                  <img
                    src={resolveImageUrl(product.images[selectedImageIndex]) || ""}
                    alt={product.name}
                    className="h-full w-full rounded-xl object-cover"
                  />
                ) : (
                  product.images?.[selectedImageIndex] || "main-image.jpg"
                )}
              </div>
              <div className="grid grid-cols-5 gap-2">
                {product.images?.slice(0, 5).map((img, index) => {
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
                        <img src={imageUrl} alt={img} className="h-14 w-full rounded-md object-cover" />
                      ) : (
                        <span className="block truncate px-1 py-4 text-[10px] text-[var(--muted)]">{img}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="space-y-4">
              <h1 className="text-4xl text-[var(--ink)]">{product.name}</h1>
              <p className="text-sm leading-6 text-[var(--muted)]">{product.description}</p>
              <p className="text-xs text-[var(--muted)]">SKU: {product.sku}</p>
              <div className="flex items-center gap-3">
                <span className="text-2xl font-semibold text-[var(--ink)]">{money(product.sellingPrice)}</span>
                {product.discountedPrice !== null && (
                  <span className="text-sm text-[var(--muted)] line-through">{money(product.regularPrice)}</span>
                )}
              </div>

              <div className="flex flex-wrap gap-2">
                {product.mainCategory && (
                  <span className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs text-white">
                    Main: {product.mainCategory}
                  </span>
                )}
                {product.subCategories.map((c) => (
                  <span key={c} className="rounded-full bg-[var(--brand-soft)] px-3 py-1 text-xs capitalize text-[var(--ink)]">
                    {c}
                  </span>
                ))}
              </div>

              {product.variations.length > 0 && (
                <div className="rounded-xl border border-[var(--line)] bg-white p-4">
                  <p className="text-xs tracking-[0.2em] text-[var(--muted)]">VARIATIONS</p>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {product.variations.map((v, i) => (
                      <div key={`${v.name}-${i}`} className="rounded-lg border border-[var(--line)] px-3 py-2 text-sm">
                        <span className="capitalize text-[var(--muted)]">{v.name}:</span> {v.value}
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
