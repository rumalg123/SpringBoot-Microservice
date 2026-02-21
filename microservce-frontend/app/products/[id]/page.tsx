"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useParams } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

type Variation = {
  name: string;
  value: string;
};

type ProductDetail = {
  id: string;
  slug: string;
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
  mainCategorySlug: string | null;
  subCategories: string[];
  subCategorySlugs: string[];
  categories: string[];
  productType: string;
  variations: Variation[];
  sku: string;
};
type VariationSummary = {
  id: string;
  name: string;
  sku: string;
  variations: Variation[];
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

function calcDiscount(regular: number, selling: number): number | null {
  if (regular > selling && regular > 0) {
    return Math.round(((regular - selling) / regular) * 100);
  }
  return null;
}

function normalizeVariationName(value: string): string {
  return value.trim().toLowerCase();
}

function normalizeVariationValue(value: string): string {
  return value.trim().toLowerCase();
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

export default function ProductDetailPage() {
  const params = useParams<{ id: string }>();
  const { isAuthenticated, profile, logout, canViewAdmin, login, apiClient, emailVerified } = useAuthSession();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [variations, setVariations] = useState<VariationSummary[]>([]);
  const [selectedVariationId, setSelectedVariationId] = useState("");
  const [selectedAttributes, setSelectedAttributes] = useState<Record<string, string>>({});
  const [selectedVariation, setSelectedVariation] = useState<ProductDetail | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [status, setStatus] = useState("Loading product...");
  const [addingToCart, setAddingToCart] = useState(false);
  const [signingInToBuy, setSigningInToBuy] = useState(false);

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products/${params.id}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as ProductDetail;
        setProduct(data);
        setSelectedImageIndex(0);
        setSelectedVariationId("");
        setSelectedVariation(null);
        setSelectedAttributes({});
        setStatus("Product loaded.");
        if (data.productType === "PARENT") {
          const vRes = await fetch(`${apiBase}/products/${params.id}/variations`, { cache: "no-store" });
          if (vRes.ok) {
            const vData = (await vRes.json()) as VariationSummary[];
            setVariations(vData || []);
          } else {
            setVariations([]);
          }
        } else {
          setVariations([]);
        }
      } catch {
        setStatus("Product not found.");
        toast.error("Failed to load product");
      }
    };
    void run();
  }, [params.id]);

  const parentAttributeNames = useMemo(() => {
    if (!product || product.productType !== "PARENT") return [];
    const directNames = (product.variations || [])
      .map((pair) => normalizeVariationName(pair.name || ""))
      .filter(Boolean);
    if (directNames.length > 0) {
      return Array.from(new Set(directNames));
    }
    const derived = new Set<string>();
    for (const variation of variations) {
      for (const pair of variation.variations || []) {
        const name = normalizeVariationName(pair.name || "");
        if (name) derived.add(name);
      }
    }
    return Array.from(derived);
  }, [product, variations]);

  const variationOptionsByAttribute = useMemo(() => {
    const options: Record<string, string[]> = {};
    for (const attributeName of parentAttributeNames) {
      const values = new Set<string>();
      for (const variation of variations) {
        const match = (variation.variations || []).find(
          (pair) => normalizeVariationName(pair.name || "") === attributeName
        );
        const value = (match?.value || "").trim();
        if (value) values.add(value);
      }
      options[attributeName] = Array.from(values).sort((a, b) => a.localeCompare(b));
    }
    return options;
  }, [parentAttributeNames, variations]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") {
      setSelectedAttributes({});
      return;
    }
    setSelectedAttributes((old) => {
      const next: Record<string, string> = {};
      parentAttributeNames.forEach((name) => {
        next[name] = old[name] || "";
      });
      return next;
    });
  }, [product, parentAttributeNames]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") {
      setSelectedVariationId("");
      return;
    }
    if (parentAttributeNames.length === 0 || variations.length === 0) {
      setSelectedVariationId("");
      return;
    }
    const missingRequired = parentAttributeNames.some((name) => !(selectedAttributes[name] || "").trim());
    if (missingRequired) {
      setSelectedVariationId("");
      return;
    }
    const matched = variations.find((variation) =>
      parentAttributeNames.every((name) => {
        const candidate = (variation.variations || []).find(
          (pair) => normalizeVariationName(pair.name || "") === name
        );
        return normalizeVariationValue(candidate?.value || "") === normalizeVariationValue(selectedAttributes[name] || "");
      })
    );
    setSelectedVariationId(matched ? matched.id : "");
  }, [product, parentAttributeNames, selectedAttributes, variations]);

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

  const addToCart = async () => {
    if (!apiClient || !product) return;
    if (addingToCart) return;
    if (product.productType === "PARENT") {
      const missingRequired = parentAttributeNames.some((name) => !(selectedAttributes[name] || "").trim());
      if (missingRequired) {
        toast.error("Select all variation attributes before adding to cart");
        return;
      }
    }
    const targetProductId =
      product.productType === "PARENT" ? selectedVariationId.trim() : product.id;

    if (!targetProductId) {
      toast.error("Select a variation before adding to cart");
      return;
    }

    setAddingToCart(true);
    try {
      await apiClient.post("/cart/me/items", {
        productId: targetProductId,
        quantity,
      });
      toast.success("Added to cart");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to add product to cart");
    } finally {
      setAddingToCart(false);
    }
  };

  const displayProduct = selectedVariation || product;
  const requiresVariationSelection = product?.productType === "PARENT";
  const allAttributesSelected =
    !requiresVariationSelection || parentAttributeNames.every((name) => (selectedAttributes[name] || "").trim());
  const hasMatchingVariation = !requiresVariationSelection || Boolean(selectedVariationId.trim());
  const canAddToCart = !addingToCart
    && quantity > 0
    && (!requiresVariationSelection || (allAttributesSelected && hasMatchingVariation));

  if (!displayProduct) {
    return (
      <div className="min-h-screen bg-[var(--bg)]">
        {isAuthenticated && (
          <AppNav
            email={(profile?.email as string) || ""}
            canViewAdmin={canViewAdmin}
            apiClient={apiClient}
            emailVerified={emailVerified}
            onLogout={() => { void logout(); }}
          />
        )}
        <main className="mx-auto max-w-7xl px-4 py-8">
          <div className="rounded-xl bg-white p-8 text-center shadow-sm">
            <p className="text-3xl">📦</p>
            <p className="mt-2 text-lg font-semibold text-[var(--ink)]">{status}</p>
            <Link href="/products" className="mt-3 inline-block text-sm text-[var(--brand)] hover:underline">
              ← Back to Shop
            </Link>
          </div>
        </main>
      </div>
    );
  }

  const discount = calcDiscount(displayProduct.regularPrice, displayProduct.sellingPrice);

  return (
    <div className="min-h-screen bg-[var(--bg)]">
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          canViewAdmin={canViewAdmin}
          apiClient={apiClient}
          emailVerified={emailVerified}
          onLogout={() => { void logout(); }}
        />
      )}

      {!isAuthenticated && (
        <header className="bg-[var(--header-bg)] shadow-lg">
          <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
            <Link href="/" className="flex items-center gap-2 text-white no-underline">
              <span className="text-2xl">🛒</span>
              <p className="text-lg font-bold text-white">Rumal Store</p>
            </Link>
            <Link href="/" className="rounded-lg bg-[var(--brand)] px-5 py-2 text-sm font-semibold text-white no-underline transition hover:bg-[var(--brand-hover)]">
              Sign In
            </Link>
          </div>
        </header>
      )}

      <main className="mx-auto max-w-7xl px-4 py-4">
        {/* Breadcrumb */}
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">›</span>
          <Link href="/products">Products</Link>
          <span className="breadcrumb-sep">›</span>
          <span className="breadcrumb-current line-clamp-1">{displayProduct.name}</span>
        </nav>

        {/* Product Detail Card */}
        <section className="animate-rise rounded-2xl bg-white p-6 shadow-sm md:p-8">
          <div className="grid gap-8 lg:grid-cols-[1fr,1fr]">
            {/* Image Gallery */}
            <div className="space-y-3">
              <div className="relative aspect-square overflow-hidden rounded-xl border border-[var(--line)] bg-[#fafafa]">
                {discount && <span className="badge-sale text-base">-{discount}% OFF</span>}
                {resolveImageUrl(displayProduct.images?.[selectedImageIndex] || "") ? (
                  <Image
                    src={resolveImageUrl(displayProduct.images[selectedImageIndex]) || ""}
                    alt={displayProduct.name}
                    width={800}
                    height={800}
                    className="h-full w-full object-cover"
                    unoptimized
                  />
                ) : (
                  <div className="grid h-full w-full place-items-center bg-gradient-to-br from-gray-100 to-gray-200 text-6xl">
                    📦
                  </div>
                )}
              </div>
              {/* Thumbnails */}
              {displayProduct.images?.length > 1 && (
                <div className="grid grid-cols-5 gap-2">
                  {displayProduct.images.slice(0, 5).map((img, index) => {
                    const imageUrl = resolveImageUrl(img);
                    return (
                      <button
                        key={`${img}-${index}`}
                        disabled={addingToCart}
                        onClick={() => setSelectedImageIndex(index)}
                        className={`aspect-square overflow-hidden rounded-lg border-2 transition disabled:cursor-not-allowed disabled:opacity-60 ${selectedImageIndex === index
                          ? "border-[var(--brand)] shadow-md"
                          : "border-[var(--line)] hover:border-[var(--brand)]"
                          }`}
                      >
                        {imageUrl ? (
                          <Image src={imageUrl} alt={img} width={120} height={120} className="h-full w-full object-cover" unoptimized />
                        ) : (
                          <div className="grid h-full w-full place-items-center bg-gray-100 text-lg">📦</div>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Product Info */}
            <div className="space-y-5">
              <div>
                <h1 className="text-2xl font-bold leading-tight text-[var(--ink)] md:text-3xl">
                  {displayProduct.name}
                </h1>
                <div className="mt-2 flex items-center gap-3">
                  <span className="star-rating text-base">★★★★☆</span>
                  <span className="text-sm text-[var(--muted)]">4.5 | 1,200+ ratings</span>
                  <span className="text-sm text-[var(--muted)]">•</span>
                  <span className="text-sm text-[var(--success)]">In Stock</span>
                </div>
              </div>

              {/* Price Section */}
              <div className="rounded-xl bg-[var(--brand-soft)] p-4">
                <div className="flex items-baseline gap-3">
                  <span className="text-3xl font-extrabold text-[var(--brand)]">
                    {money(displayProduct.sellingPrice)}
                  </span>
                  {displayProduct.discountedPrice !== null && (
                    <>
                      <span className="text-lg text-[var(--muted)] line-through">
                        {money(displayProduct.regularPrice)}
                      </span>
                      {discount && (
                        <span className="price-discount-badge text-sm">-{discount}% OFF</span>
                      )}
                    </>
                  )}
                </div>
                {displayProduct.discountedPrice !== null && (
                  <p className="mt-1 text-xs text-[var(--success)]">
                    💰 You save {money(displayProduct.regularPrice - displayProduct.sellingPrice)}
                  </p>
                )}
              </div>

              {/* Description */}
              <div>
                <p className="text-sm leading-relaxed text-[var(--ink-light)]">{displayProduct.description}</p>
                <p className="mt-2 text-xs text-[var(--muted)]">SKU: {displayProduct.sku}</p>
              </div>

              {/* Categories */}
              <div className="flex flex-wrap gap-2">
                {displayProduct.mainCategory && (
                  <Link
                    href={`/categories/${encodeURIComponent(displayProduct.mainCategorySlug || slugify(displayProduct.mainCategory))}`}
                    className="rounded-full bg-[var(--brand)] px-3 py-1 text-xs font-semibold text-white no-underline"
                  >
                    {displayProduct.mainCategory}
                  </Link>
                )}
                {displayProduct.subCategories.map((c, index) => (
                  <Link
                    key={`${c}-${index}`}
                    href={`/categories/${encodeURIComponent(displayProduct.subCategorySlugs?.[index] || slugify(c))}`}
                    className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium capitalize text-[var(--ink)] no-underline"
                  >
                    {c}
                  </Link>
                ))}
              </div>

              {/* Variations */}
              {displayProduct.variations.length > 0 && (
                <div className="rounded-xl border border-[var(--line)] bg-[#fafafa] p-4">
                  <p className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                    {displayProduct.productType === "PARENT" ? "Available Options" : "Specifications"}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {displayProduct.variations.map((v, i) => (
                      <span
                        key={`${v.name}-${i}`}
                        className="rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm transition hover:border-[var(--brand)]"
                      >
                        {displayProduct.productType === "PARENT" ? (
                          <span className="font-medium text-[var(--ink)]">{v.name}</span>
                        ) : (
                          <>
                            <span className="text-[var(--muted)]">{v.name}:</span>{" "}
                            <span className="font-medium">{v.value}</span>
                          </>
                        )}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Purchase Controls */}
              <div className="border-t border-[var(--line)] pt-5">
                {isAuthenticated ? (
                  <div className="space-y-4">
                    {/* Variation Selector */}
                    {product?.productType === "PARENT" && (
                      <div className="space-y-3">
                        <label className="mb-2 block text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                          Select Variation
                        </label>
                        {parentAttributeNames.length === 0 && (
                          <p className="text-xs text-[var(--muted)]">No variation attributes configured for this parent product.</p>
                        )}
                        {parentAttributeNames.map((attributeName) => (
                          <div key={attributeName}>
                            <label className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-[var(--muted)]">
                              {attributeName}
                            </label>
                            <select
                              value={selectedAttributes[attributeName] || ""}
                              onChange={(e) =>
                                setSelectedAttributes((old) => ({
                                  ...old,
                                  [attributeName]: e.target.value,
                                }))
                              }
                              disabled={addingToCart || (variationOptionsByAttribute[attributeName] || []).length === 0}
                              className="w-full rounded-lg border border-[var(--line)] bg-white px-4 py-3 text-sm text-[var(--ink)] disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              <option value="">Select {attributeName}</option>
                              {(variationOptionsByAttribute[attributeName] || []).map((value) => (
                                <option key={`${attributeName}-${value}`} value={value}>
                                  {value}
                                </option>
                              ))}
                            </select>
                          </div>
                        ))}
                        {variations.length === 0 && (
                          <p className="text-xs text-[var(--muted)]">No variations available for this parent product.</p>
                        )}
                        {variations.length > 0 && !allAttributesSelected && (
                          <p className="text-xs text-[var(--muted)]">Select all attributes to continue.</p>
                        )}
                        {allAttributesSelected && !selectedVariationId && variations.length > 0 && (
                          <p className="text-xs font-semibold text-red-600">No variation matches the selected attribute combination.</p>
                        )}
                      </div>
                    )}

                    <div className="space-y-3">
                      <p className="text-xs text-[var(--muted)]">
                        Add this product to your cart and complete checkout from the cart page.
                      </p>
                    </div>

                    {/* Quantity + Buy */}
                    <div className="flex flex-wrap items-center gap-3">
                      <div>
                        <label className="mb-2 block text-xs font-bold uppercase tracking-wider text-[var(--muted)]">
                          Quantity
                        </label>
                        <div className="qty-stepper">
                          <button disabled={addingToCart} onClick={() => setQuantity((old) => Math.max(1, old - 1))}>−</button>
                          <span>{quantity}</span>
                          <button disabled={addingToCart} onClick={() => setQuantity((old) => old + 1)}>+</button>
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-3">
                      <button
                        disabled={!canAddToCart}
                        onClick={() => void addToCart()}
                        className="btn-primary flex-1 px-8 py-3 text-base disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {addingToCart ? "Adding..." : "🛒 Add to Cart"}
                      </button>
                      <Link
                        href="/cart"
                        className="btn-outline flex items-center justify-center px-6 py-3 text-sm no-underline"
                      >
                        📦 Open Cart
                      </Link>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <p className="text-sm text-[var(--muted)]">Sign in to add this product to cart</p>
                    <button
                      disabled={signingInToBuy}
                      onClick={async () => {
                        if (signingInToBuy) return;
                        setSigningInToBuy(true);
                        try {
                          await login(`/products/${params.id}`);
                        } finally {
                          setSigningInToBuy(false);
                        }
                      }}
                      className="btn-primary w-full py-3 text-base disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {signingInToBuy ? "Redirecting..." : "🔑 Sign In to Continue"}
                    </button>
                  </div>
                )}
              </div>

              {/* Trust Badges */}
              <div className="flex flex-wrap gap-4 border-t border-[var(--line)] pt-4 text-xs text-[var(--muted)]">
                <span className="flex items-center gap-1">🚚 Free Shipping</span>
                <span className="flex items-center gap-1">🔒 Secure Payment</span>
                <span className="flex items-center gap-1">🔄 30-Day Returns</span>
              </div>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}

