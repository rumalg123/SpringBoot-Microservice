"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useParams, useRouter } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";
import { emitCartUpdate, emitWishlistUpdate } from "../../../lib/navEvents";
import { money, calcDiscount } from "../../../lib/format";
import { resolveImageUrl } from "../../../lib/image";
import ReviewSection from "../../components/reviews/ReviewSection";
import { trackProductView, trackAddToCart, trackWishlistAdd, fetchSimilarProducts, fetchBoughtTogether, type PersonalizationProduct } from "../../../lib/personalization";
import type { Variation, ProductDetail, VariationSummary } from "../../../lib/types/product";
import type { WishlistItem, WishlistResponse } from "../../../lib/types/wishlist";
import ProductCard from "../../components/catalog/ProductCard";
import ProductImageGallery from "../../components/catalog/ProductImageGallery";
import PurchasePanel from "../../components/catalog/PurchasePanel";
import { API_BASE } from "../../../lib/constants";

function normalizeVariationName(v: string) { return v.trim().toLowerCase(); }
function normalizeVariationValue(v: string) { return v.trim().toLowerCase(); }
function slugify(v: string) {
  return v.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}


export default function ProductDetailClient() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const {
    isAuthenticated,
    profile,
    logout,
    isSuperAdmin,
    isVendorAdmin,
    canViewAdmin,
    canManageAdminOrders,
    canManageAdminProducts,
    canManageAdminCategories,
    canManageAdminVendors,
    canManageAdminPosters,
    login,
    apiClient,
    emailVerified,
    token,
  } = useAuthSession();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [variations, setVariations] = useState<VariationSummary[]>([]);
  const [selectedVariationId, setSelectedVariationId] = useState("");
  const [selectedAttributes, setSelectedAttributes] = useState<Record<string, string>>({});
  const [selectedVariation, setSelectedVariation] = useState<ProductDetail | null>(null);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [status, setStatus] = useState("Loading product...");
  const [addingToCart, setAddingToCart] = useState(false);
  const [buyingNow, setBuyingNow] = useState(false);
  const [signingInToBuy, setSigningInToBuy] = useState(false);
  const [wishlistItemId, setWishlistItemId] = useState("");
  const [wishlistPending, setWishlistPending] = useState(false);
  const [similarProducts, setSimilarProducts] = useState<PersonalizationProduct[]>([]);
  const [boughtTogether, setBoughtTogether] = useState<PersonalizationProduct[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/products/${params.id}`, { cache: "no-store", signal });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as ProductDetail;
        if (signal.aborted) return;
        setProduct(data);
        setSelectedImageIndex(0); setSelectedVariationId(""); setSelectedVariation(null); setSelectedAttributes({});
        setStatus("Product loaded.");
        if (data.productType === "PARENT") {
          const vRes = await fetch(`${API_BASE}/products/${params.id}/variations`, { cache: "no-store", signal });
          if (signal.aborted) return;
          setVariations(vRes.ok ? ((await vRes.json()) as VariationSummary[] || []) : []);
        } else { setVariations([]); }
      } catch (err) {
        if (signal.aborted) return;
        setStatus("Product not found."); toast.error("Failed to load product");
      }
    };
    void run();
    return () => controller.abort();
  }, [params.id]);

  // Track product view and fetch personalization data
  useEffect(() => {
    if (!product) return;
    trackProductView({
      id: product.id,
      categories: product.categories,
      vendorId: product.vendorId,
      brandName: null,
      sellingPrice: product.sellingPrice,
    }, token);
    fetchBoughtTogether(product.id, 4).then(setBoughtTogether).catch((e) => console.error("Failed to load bought together:", e));
    fetchSimilarProducts(product.id, 8).then(setSimilarProducts).catch((e) => console.error("Failed to load similar products:", e));
  }, [product?.id, token]);

  const parentAttributeNames = useMemo(() => {
    if (!product || product.productType !== "PARENT") return [];
    const directNames = (product.variations || []).map((p) => normalizeVariationName(p.name || "")).filter(Boolean);
    if (directNames.length > 0) return Array.from(new Set(directNames));
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
        const match = (variation.variations || []).find((pair) => normalizeVariationName(pair.name || "") === attributeName);
        const value = (match?.value || "").trim();
        if (value) values.add(value);
      }
      options[attributeName] = Array.from(values).sort((a, b) => a.localeCompare(b));
    }
    return options;
  }, [parentAttributeNames, variations]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") { setSelectedAttributes({}); return; }
    setSelectedAttributes((old) => {
      const next: Record<string, string> = {};
      parentAttributeNames.forEach((name) => { next[name] = old[name] || ""; });
      return next;
    });
  }, [product, parentAttributeNames]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") { setSelectedVariationId(""); return; }
    if (parentAttributeNames.length === 0 || variations.length === 0) { setSelectedVariationId(""); return; }
    if (parentAttributeNames.some((n) => !(selectedAttributes[n] || "").trim())) { setSelectedVariationId(""); return; }
    const matched = variations.find((variation) =>
      parentAttributeNames.every((name) => {
        const candidate = (variation.variations || []).find((pair) => normalizeVariationName(pair.name || "") === name);
        return normalizeVariationValue(candidate?.value || "") === normalizeVariationValue(selectedAttributes[name] || "");
      })
    );
    setSelectedVariationId(matched ? matched.id : "");
  }, [product, parentAttributeNames, selectedAttributes, variations]);

  useEffect(() => {
    if (!product || product.productType !== "PARENT") { setSelectedVariation(null); return; }
    if (!selectedVariationId) { setSelectedVariation(null); setSelectedImageIndex(0); return; }
    const controller = new AbortController();
    const { signal } = controller;
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/products/${selectedVariationId}`, { cache: "no-store", signal });
        if (!res.ok) throw new Error("Failed");
        if (signal.aborted) return;
        setSelectedVariation((await res.json()) as ProductDetail);
        setSelectedImageIndex(0);
      } catch (err) {
        if (signal.aborted) return;
        toast.error("Failed to load selected variation details");
      }
    };
    void run();
    return () => controller.abort();
  }, [selectedVariationId, product]);

  useEffect(() => {
    if (!isAuthenticated || !apiClient) { setWishlistItemId(""); return; }
    const targetProductId = (selectedVariation?.id || product?.id || "").trim();
    if (!targetProductId) { setWishlistItemId(""); return; }
    let cancelled = false;
    const run = async () => {
      try {
        const res = await apiClient.get<{ content?: WishlistItem[]; items?: WishlistItem[] }>("/wishlist/me");
        const items: WishlistItem[] = res.data.content ?? res.data.items ?? [];
        if (cancelled) return;
        const matched = items.find((item) => item.productId === targetProductId);
        setWishlistItemId(matched?.id || "");
      } catch { if (!cancelled) setWishlistItemId(""); }
    };
    void run();
    return () => { cancelled = true; };
  }, [isAuthenticated, apiClient, product?.id, selectedVariation?.id]);

  const addToCart = async () => {
    if (!apiClient || !product || addingToCart) return;
    if (product.productType === "PARENT") {
      if (parentAttributeNames.some((n) => !(selectedAttributes[n] || "").trim())) {
        toast.error("Select all variation attributes before adding to cart");
        return;
      }
    }
    const targetProductId = product.productType === "PARENT" ? selectedVariationId.trim() : product.id;
    if (!targetProductId) { toast.error("Select a variation before adding to cart"); return; }
    setAddingToCart(true);
    try {
      await apiClient.post("/cart/me/items", { productId: targetProductId, quantity });
      toast.success("Added to cart");
      emitCartUpdate();
      if (product) trackAddToCart({ id: targetProductId, categories: product.categories, vendorId: product.vendorId, sellingPrice: displayProduct?.sellingPrice }, token);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to add product to cart");
    } finally { setAddingToCart(false); }
  };

  const buyNow = async () => {
    if (!apiClient || !product || buyingNow) return;
    if (product.productType === "PARENT") {
      if (parentAttributeNames.some((n) => !(selectedAttributes[n] || "").trim())) {
        toast.error("Select all variation attributes before purchasing");
        return;
      }
    }
    const targetProductId = product.productType === "PARENT" ? selectedVariationId.trim() : product.id;
    if (!targetProductId) { toast.error("Select a variation before purchasing"); return; }
    setBuyingNow(true);
    try {
      await apiClient.post("/cart/me/items", { productId: targetProductId, quantity });
      emitCartUpdate();
      router.push("/cart");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to proceed to checkout");
      setBuyingNow(false);
    }
  };

  const toggleWishlist = async () => {
    if (!apiClient || !isAuthenticated || wishlistPending) return;
    const targetProductId = (selectedVariation?.id || product?.id || "").trim();
    if (!targetProductId) return;
    setWishlistPending(true);
    try {
      if (wishlistItemId) {
        await apiClient.delete(`/wishlist/me/items/${wishlistItemId}`);
        setWishlistItemId("");
        toast.success("Removed from wishlist");
        emitWishlistUpdate();
      } else {
        const res = await apiClient.post<WishlistResponse>("/wishlist/me/items", { productId: targetProductId });
        const data = res.data ?? { items: [], itemCount: 0 };
        const matched = (data.items || []).find((item) => item.productId === targetProductId);
        setWishlistItemId(matched?.id || "");
        toast.success("Added to wishlist");
        emitWishlistUpdate();
        if (product) trackWishlistAdd({ id: targetProductId, categories: product.categories, vendorId: product.vendorId, sellingPrice: displayProduct?.sellingPrice }, token);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Wishlist update failed");
    } finally { setWishlistPending(false); }
  };

  const displayProduct = selectedVariation || product;
  const requiresVariationSelection = product?.productType === "PARENT";
  const allAttributesSelected = !requiresVariationSelection || parentAttributeNames.every((n) => (selectedAttributes[n] || "").trim());
  const hasMatchingVariation = !requiresVariationSelection || Boolean(selectedVariationId.trim());
  const productReady = quantity > 0 && (!requiresVariationSelection || (allAttributesSelected && hasMatchingVariation));
  const canAddToCart = !addingToCart && !buyingNow && productReady;
  const canBuyNow = !buyingNow && !addingToCart && productReady;

  /* Loading / not found */
  if (!displayProduct) {
    return (
      <div className="min-h-screen bg-bg">
        {isAuthenticated && (
          <AppNav
            email={(profile?.email as string) || ""}
            canViewAdmin={canViewAdmin}
            canManageAdminOrders={canManageAdminOrders}
            canManageAdminProducts={canManageAdminProducts}
            canManageAdminPosters={canManageAdminPosters}
            apiClient={apiClient}
            emailVerified={emailVerified}
            onLogout={() => { void logout(); }}
          />
        )}
        <main className="mx-auto max-w-7xl px-4 py-8">
          <div className="rounded-xl border border-line-bright bg-[rgba(17,17,40,0.7)] px-6 py-12 text-center backdrop-blur-[16px]">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--brand-glow)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="mx-auto mb-4">
              <path d="M5 8h14M5 8a2 2 0 1 0 0-4h14a2 2 0 1 0 0 4M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8m-9 4h4" />
            </svg>
            <p className="mb-3 mt-0 text-[1.1rem] font-bold text-ink-light">{status}</p>
            <Link href="/products" className="text-base text-brand no-underline">← Back to Shop</Link>
          </div>
        </main>
      </div>
    );
  }

  const discount = calcDiscount(displayProduct.regularPrice, displayProduct.sellingPrice);

  return (
    <div className="min-h-screen bg-bg">
      {isAuthenticated && (
        <AppNav
          email={(profile?.email as string) || ""}
          isSuperAdmin={isSuperAdmin}
          isVendorAdmin={isVendorAdmin}
          canViewAdmin={canViewAdmin}
          canManageAdminOrders={canManageAdminOrders}
          canManageAdminProducts={canManageAdminProducts}
          canManageAdminCategories={canManageAdminCategories}
          canManageAdminVendors={canManageAdminVendors}
          canManageAdminPosters={canManageAdminPosters}
          apiClient={apiClient}
          emailVerified={emailVerified}
          onLogout={() => { void logout(); }}
        />
      )}

      {!isAuthenticated && (
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

        {/* Main Card */}
        <section
          className="animate-rise mt-1 rounded-[24px] border border-line-bright bg-[rgba(17,17,40,0.7)] p-7 backdrop-blur-[20px]"
        >
          <div className="grid grid-cols-2 gap-10">
            {/* Image Gallery */}
            <ProductImageGallery
              images={displayProduct.images}
              name={displayProduct.name}
              selectedIndex={selectedImageIndex}
              onSelectIndex={setSelectedImageIndex}
              discount={discount}
              disabled={addingToCart}
            />

            {/* Product Info */}
            <div className="flex flex-col gap-5">
              {/* Title & Rating */}
              <div>
                <h1 className="mb-2.5 mt-0 font-[Syne,sans-serif] text-[1.7rem] font-black leading-[1.2] text-white">
                  {displayProduct.name}
                </h1>
                <div className="flex flex-wrap items-center gap-2.5">
                  <span className="star-rating text-[0.95rem]">★★★★☆</span>
                  <span className="text-sm text-muted">4.5 · 1,200+ ratings</span>
                  <span className="text-sm text-muted">·</span>
                  <span className="text-sm font-bold text-success">In Stock</span>
                </div>
              </div>

              {/* Price */}
              <div className="rounded-[14px] border border-line-bright bg-brand-soft px-[18px] py-4">
                <div className="flex flex-wrap items-baseline gap-3">
                  <span className="text-[2rem] font-black text-brand">
                    {money(displayProduct.sellingPrice)}
                  </span>
                  {displayProduct.discountedPrice !== null && (
                    <>
                      <span className="text-[1.1rem] text-muted line-through">
                        {money(displayProduct.regularPrice)}
                      </span>
                      {discount && (
                        <span className="price-discount-badge text-sm">−{discount}% OFF</span>
                      )}
                    </>
                  )}
                </div>
                {displayProduct.discountedPrice !== null && (
                  <p className="mt-1.5 mb-0 text-xs font-semibold text-success">
                    You save {money(displayProduct.regularPrice - displayProduct.sellingPrice)}
                  </p>
                )}
              </div>

              {/* Description & SKU */}
              <div>
                <p className="mb-2 mt-0 text-base leading-[1.7] text-ink-light">
                  {displayProduct.description}
                </p>
                <p className="m-0 font-mono text-xs text-muted-2">
                  SKU: {displayProduct.sku}
                </p>
              </div>

              {/* Categories */}
              <div className="flex flex-wrap gap-2">
                {displayProduct.mainCategory && (
                  <Link
                    href={`/categories/${encodeURIComponent(displayProduct.mainCategorySlug || slugify(displayProduct.mainCategory))}`}
                    className="rounded-xl bg-[var(--gradient-brand)] px-3 py-1 text-xs font-bold text-white no-underline"
                  >
                    {displayProduct.mainCategory}
                  </Link>
                )}
                {displayProduct.subCategories.map((c, index) => (
                  <Link
                    key={`${c}-${index}`}
                    href={`/categories/${encodeURIComponent(displayProduct.subCategorySlugs?.[index] || slugify(c))}`}
                    className="rounded-xl border border-line-bright bg-brand-soft px-3 py-1 text-xs font-bold text-brand no-underline"
                  >
                    {c}
                  </Link>
                ))}
              </div>

              {/* Specifications / Variation Options (display panel) */}
              {displayProduct.variations.length > 0 && (
                <div className="rounded-[12px] border border-line-bright bg-brand-soft px-4 py-3.5">
                  <p className="mb-2.5 mt-0 text-[0.65rem] font-bold uppercase tracking-[0.1em] text-muted">
                    {displayProduct.productType === "PARENT" ? "Available Options" : "Specifications"}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    {displayProduct.variations.map((v, i) => (
                      <span
                        key={`${v.name}-${i}`}
                        className="rounded-[8px] border border-line-bright bg-brand-soft px-3 py-1.5 text-sm"
                      >
                        {displayProduct.productType === "PARENT" ? (
                          <span className="font-bold text-ink-light">{v.name}</span>
                        ) : (
                          <><span className="text-muted">{v.name}: </span><span className="font-bold text-white">{v.value}</span></>
                        )}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Purchase Controls */}
              <PurchasePanel
                isAuthenticated={isAuthenticated}
                productType={product?.productType || ""}
                parentAttributeNames={parentAttributeNames}
                variationOptionsByAttribute={variationOptionsByAttribute}
                selectedAttributes={selectedAttributes}
                onAttributeChange={(name, value) => setSelectedAttributes((old) => ({ ...old, [name]: value }))}
                allAttributesSelected={allAttributesSelected}
                selectedVariationId={selectedVariationId}
                variationsCount={variations.length}
                quantity={quantity}
                onQuantityChange={setQuantity}
                canAddToCart={canAddToCart}
                canBuyNow={canBuyNow}
                addingToCart={addingToCart}
                buyingNow={buyingNow}
                wishlistItemId={wishlistItemId}
                wishlistPending={wishlistPending}
                signingInToBuy={signingInToBuy}
                onAddToCart={() => void addToCart()}
                onBuyNow={() => void buyNow()}
                onToggleWishlist={() => void toggleWishlist()}
                onSignIn={async () => {
                  if (signingInToBuy) return;
                  setSigningInToBuy(true);
                  try { await login(`/products/${params.id}`); } finally { setSigningInToBuy(false); }
                }}
              />

              {/* Trust Badges */}
              <div className="flex flex-wrap gap-4 border-t border-brand-soft pt-4">
                {[
                  { icon: "M5 12h14M12 5l7 7-7 7", label: "Free Shipping" },
                  { icon: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", label: "Secure Payment" },
                  { icon: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8M3 8v4h4M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16m18 0v-4h-4", label: "30-Day Returns" },
                ].map(({ icon, label }) => (
                  <span key={label} className="flex items-center gap-1.5 text-xs text-muted">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--brand)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d={icon} />
                    </svg>
                    {label}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </section>

        {/* Frequently Bought Together */}
        {boughtTogether.length > 0 && (
          <section className="mx-auto max-w-7xl px-4 py-8">
            <h2 className="mb-4 font-[Syne,sans-serif] text-[1.3rem] font-extrabold text-white">Frequently Bought Together</h2>
            <div className="flex gap-4 overflow-x-auto pb-2">
              {boughtTogether.map((p) => {
                const imgUrl = resolveImageUrl(p.mainImage);
                return (
                  <Link href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`} key={p.id} className="product-card shrink-0 no-underline" style={{ minWidth: "180px", maxWidth: "200px" }}>
                    <div className="relative aspect-square overflow-hidden bg-surface-2">
                      {imgUrl ? (<Image src={imgUrl} alt={p.name} width={300} height={300} className="product-card-img" unoptimized />) : (<div className="grid h-full w-full place-items-center bg-[linear-gradient(135deg,var(--surface),#1c1c38)] text-xs text-muted-2">No Image</div>)}
                    </div>
                    <div className="product-card-body">
                      <p className="mb-1 line-clamp-1 text-sm font-semibold text-ink">{p.name}</p>
                      <span className="price-current text-sm">{money(p.sellingPrice)}</span>
                    </div>
                  </Link>
                );
              })}
            </div>
          </section>
        )}

        {product && (
          <ReviewSection
            productId={product.id}
            vendorId={product.vendorId}
            isAuthenticated={isAuthenticated}
            apiClient={apiClient}
          />
        )}

        {/* You May Also Like */}
        {similarProducts.length > 0 && (
          <section className="mx-auto max-w-7xl px-4 py-8">
            <h2 className="mb-4 font-[Syne,sans-serif] text-[1.3rem] font-extrabold text-white">You May Also Like</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {similarProducts.map((p, idx) => (
                <ProductCard key={p.id} product={p} index={idx} />
              ))}
            </div>
          </section>
        )}
      </main>

      <Footer />
    </div>
  );
}
