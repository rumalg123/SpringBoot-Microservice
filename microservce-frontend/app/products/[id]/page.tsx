"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useParams } from "next/navigation";
import toast from "react-hot-toast";
import AppNav from "../../components/AppNav";
import Footer from "../../components/Footer";
import { useAuthSession } from "../../../lib/authSession";

type Variation = { name: string; value: string };
type ProductDetail = {
  id: string; slug: string; parentProductId: string | null; name: string;
  shortDescription: string; description: string; images: string[];
  regularPrice: number; discountedPrice: number | null; sellingPrice: number;
  vendorId: string; mainCategory: string | null; mainCategorySlug: string | null;
  subCategories: string[]; subCategorySlugs: string[]; categories: string[];
  productType: string; variations: Variation[]; sku: string;
};
type VariationSummary = { id: string; name: string; sku: string; variations: Variation[] };
type WishlistItem = { id: string; productId: string };
type WishlistResponse = { items: WishlistItem[]; itemCount: number };

function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
}
function resolveImageUrl(imageName: string): string | null {
  const normalized = imageName.replace(/^\/+/, "");
  const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
  const encoded = normalized.split("/").map((s) => encodeURIComponent(s)).join("/");
  const apiUrl = `${apiBase.replace(/\/+$/, "")}/products/images/${encoded}`;
  const base = (process.env.NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL || "").trim();
  if (!base) return apiUrl;
  if (normalized.startsWith("products/")) {
    return `${base.replace(/\/+$/, "")}/${normalized}`;
  }
  return apiUrl;
}
function calcDiscount(regular: number, selling: number): number | null {
  return regular > selling && regular > 0 ? Math.round(((regular - selling) / regular) * 100) : null;
}
function normalizeVariationName(v: string) { return v.trim().toLowerCase(); }
function normalizeVariationValue(v: string) { return v.trim().toLowerCase(); }
function slugify(v: string) {
  return v.toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").replace(/-+/g, "-");
}

const darkSelect: React.CSSProperties = {
  width: "100%", padding: "10px 14px", borderRadius: "10px",
  border: "1px solid rgba(0,212,255,0.15)", background: "rgba(0,212,255,0.04)",
  color: "#c8c8e8", fontSize: "0.85rem", outline: "none",
  appearance: "none", WebkitAppearance: "none",
};

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
  const [wishlistItemId, setWishlistItemId] = useState("");
  const [wishlistPending, setWishlistPending] = useState(false);

  useEffect(() => {
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products/${params.id}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as ProductDetail;
        setProduct(data);
        setSelectedImageIndex(0); setSelectedVariationId(""); setSelectedVariation(null); setSelectedAttributes({});
        setStatus("Product loaded.");
        if (data.productType === "PARENT") {
          const vRes = await fetch(`${apiBase}/products/${params.id}/variations`, { cache: "no-store" });
          setVariations(vRes.ok ? ((await vRes.json()) as VariationSummary[] || []) : []);
        } else { setVariations([]); }
      } catch { setStatus("Product not found."); toast.error("Failed to load product"); }
    };
    void run();
  }, [params.id]);

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
    const run = async () => {
      try {
        const apiBase = process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me";
        const res = await fetch(`${apiBase}/products/${selectedVariationId}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        setSelectedVariation((await res.json()) as ProductDetail);
        setSelectedImageIndex(0);
      } catch { toast.error("Failed to load selected variation details"); }
    };
    void run();
  }, [selectedVariationId, product]);

  useEffect(() => {
    if (!isAuthenticated || !apiClient) { setWishlistItemId(""); return; }
    const targetProductId = (selectedVariation?.id || product?.id || "").trim();
    if (!targetProductId) { setWishlistItemId(""); return; }
    let cancelled = false;
    const run = async () => {
      try {
        const res = await apiClient.get("/wishlist/me");
        const data = (res.data as WishlistResponse) || { items: [], itemCount: 0 };
        if (cancelled) return;
        const matched = (data.items || []).find((item) => item.productId === targetProductId);
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
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to add product to cart");
    } finally { setAddingToCart(false); }
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
      } else {
        const res = await apiClient.post("/wishlist/me/items", { productId: targetProductId });
        const data = (res.data as WishlistResponse) || { items: [], itemCount: 0 };
        const matched = (data.items || []).find((item) => item.productId === targetProductId);
        setWishlistItemId(matched?.id || "");
        toast.success("Added to wishlist");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Wishlist update failed");
    } finally { setWishlistPending(false); }
  };

  const displayProduct = selectedVariation || product;
  const requiresVariationSelection = product?.productType === "PARENT";
  const allAttributesSelected = !requiresVariationSelection || parentAttributeNames.every((n) => (selectedAttributes[n] || "").trim());
  const hasMatchingVariation = !requiresVariationSelection || Boolean(selectedVariationId.trim());
  const canAddToCart = !addingToCart && quantity > 0 && (!requiresVariationSelection || (allAttributesSelected && hasMatchingVariation));

  /* Loading / not found */
  if (!displayProduct) {
    return (
      <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
        {isAuthenticated && (
          <AppNav email={(profile?.email as string) || ""} canViewAdmin={canViewAdmin} apiClient={apiClient} emailVerified={emailVerified} onLogout={() => { void logout(); }} />
        )}
        <main className="mx-auto max-w-7xl px-4 py-8">
          <div style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(16px)", border: "1px solid rgba(0,212,255,0.1)", borderRadius: "20px", padding: "48px 24px", textAlign: "center" }}>
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="rgba(0,212,255,0.4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ margin: "0 auto 16px" }}>
              <path d="M5 8h14M5 8a2 2 0 1 0 0-4h14a2 2 0 1 0 0 4M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8m-9 4h4" />
            </svg>
            <p style={{ fontSize: "1.1rem", fontWeight: 700, color: "#c8c8e8", margin: "0 0 12px" }}>{status}</p>
            <Link href="/products" style={{ color: "#00d4ff", fontSize: "0.875rem", textDecoration: "none" }}>← Back to Shop</Link>
          </div>
        </main>
      </div>
    );
  }

  const discount = calcDiscount(displayProduct.regularPrice, displayProduct.sellingPrice);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      {isAuthenticated && (
        <AppNav email={(profile?.email as string) || ""} canViewAdmin={canViewAdmin} apiClient={apiClient} emailVerified={emailVerified} onLogout={() => { void logout(); }} />
      )}

      {!isAuthenticated && (
        <header style={{ background: "rgba(10,10,28,0.95)", backdropFilter: "blur(12px)", borderBottom: "1px solid rgba(0,212,255,0.08)", position: "sticky", top: 0, zIndex: 100 }}>
          <div className="mx-auto max-w-7xl px-4 py-3 flex items-center justify-between">
            <Link href="/" className="no-underline flex items-center gap-2">
              <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: 900, fontSize: "1.2rem", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
                Rumal Store
              </span>
            </Link>
            <Link href="/" className="no-underline" style={{ padding: "8px 18px", borderRadius: "10px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", fontSize: "0.8rem", fontWeight: 700 }}>
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
          className="animate-rise"
          style={{ background: "rgba(17,17,40,0.7)", backdropFilter: "blur(20px)", border: "1px solid rgba(0,212,255,0.1)", borderRadius: "24px", padding: "28px", marginTop: "4px" }}
        >
          <div style={{ display: "grid", gap: "40px", gridTemplateColumns: "1fr 1fr" }}>
            {/* Image Gallery */}
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              <div style={{ position: "relative", aspectRatio: "1", overflow: "hidden", borderRadius: "16px", border: "1px solid rgba(0,212,255,0.1)", background: "rgba(0,0,10,0.5)" }}>
                {discount && (
                  <span className="badge-sale" style={{ top: "12px", left: "12px" }}>−{discount}% OFF</span>
                )}
                {resolveImageUrl(displayProduct.images?.[selectedImageIndex] || "") ? (
                  <Image
                    src={resolveImageUrl(displayProduct.images[selectedImageIndex]) || ""}
                    alt={displayProduct.name}
                    width={800} height={800}
                    className="h-full w-full object-cover"
                    unoptimized
                  />
                ) : (
                  <div style={{ display: "grid", placeItems: "center", height: "100%", background: "linear-gradient(135deg, rgba(0,212,255,0.05), rgba(124,58,237,0.05))" }}>
                    <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="rgba(0,212,255,0.3)" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M5 8h14M5 8a2 2 0 1 0 0-4h14a2 2 0 1 0 0 4M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8m-9 4h4" />
                    </svg>
                  </div>
                )}
              </div>

              {/* Thumbnails */}
              {displayProduct.images?.length > 1 && (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "8px" }}>
                  {displayProduct.images.slice(0, 5).map((img, index) => {
                    const imageUrl = resolveImageUrl(img);
                    const isSelected = selectedImageIndex === index;
                    return (
                      <button
                        key={`${img}-${index}`}
                        onClick={() => setSelectedImageIndex(index)}
                        disabled={addingToCart}
                        style={{
                          aspectRatio: "1", overflow: "hidden", borderRadius: "10px", padding: 0,
                          border: isSelected ? "2px solid #00d4ff" : "2px solid rgba(0,212,255,0.12)",
                          background: "rgba(0,0,10,0.5)", cursor: "pointer",
                          boxShadow: isSelected ? "0 0 10px rgba(0,212,255,0.3)" : "none",
                          transition: "border-color 0.2s, box-shadow 0.2s",
                        }}
                      >
                        {imageUrl ? (
                          <Image src={imageUrl} alt={img} width={120} height={120} className="h-full w-full object-cover" unoptimized />
                        ) : (
                          <div style={{ display: "grid", placeItems: "center", height: "100%", fontSize: "20px" }}>
                            📦
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Product Info */}
            <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
              {/* Title & Rating */}
              <div>
                <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.7rem", fontWeight: 900, color: "#fff", lineHeight: 1.2, margin: "0 0 10px" }}>
                  {displayProduct.name}
                </h1>
                <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
                  <span className="star-rating" style={{ fontSize: "0.95rem" }}>★★★★☆</span>
                  <span style={{ fontSize: "0.8rem", color: "var(--muted)" }}>4.5 · 1,200+ ratings</span>
                  <span style={{ fontSize: "0.8rem", color: "var(--muted)" }}>·</span>
                  <span style={{ fontSize: "0.8rem", color: "#4ade80", fontWeight: 700 }}>In Stock</span>
                </div>
              </div>

              {/* Price */}
              <div style={{ borderRadius: "14px", background: "rgba(0,212,255,0.04)", border: "1px solid rgba(0,212,255,0.1)", padding: "16px 18px" }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: "12px", flexWrap: "wrap" }}>
                  <span style={{ fontSize: "2rem", fontWeight: 900, color: "#00d4ff" }}>
                    {money(displayProduct.sellingPrice)}
                  </span>
                  {displayProduct.discountedPrice !== null && (
                    <>
                      <span style={{ fontSize: "1.1rem", color: "var(--muted)", textDecoration: "line-through" }}>
                        {money(displayProduct.regularPrice)}
                      </span>
                      {discount && (
                        <span className="price-discount-badge" style={{ fontSize: "0.8rem" }}>−{discount}% OFF</span>
                      )}
                    </>
                  )}
                </div>
                {displayProduct.discountedPrice !== null && (
                  <p style={{ margin: "6px 0 0", fontSize: "0.75rem", color: "#4ade80", fontWeight: 600 }}>
                    You save {money(displayProduct.regularPrice - displayProduct.sellingPrice)}
                  </p>
                )}
              </div>

              {/* Description & SKU */}
              <div>
                <p style={{ fontSize: "0.875rem", lineHeight: 1.7, color: "rgba(200,200,232,0.8)", margin: "0 0 8px" }}>
                  {displayProduct.description}
                </p>
                <p style={{ fontSize: "0.72rem", color: "var(--muted-2)", fontFamily: "monospace", margin: 0 }}>
                  SKU: {displayProduct.sku}
                </p>
              </div>

              {/* Categories */}
              <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                {displayProduct.mainCategory && (
                  <Link
                    href={`/categories/${encodeURIComponent(displayProduct.mainCategorySlug || slugify(displayProduct.mainCategory))}`}
                    className="no-underline"
                    style={{ borderRadius: "20px", background: "linear-gradient(135deg, #00d4ff, #7c3aed)", color: "#fff", padding: "4px 12px", fontSize: "0.72rem", fontWeight: 700 }}
                  >
                    {displayProduct.mainCategory}
                  </Link>
                )}
                {displayProduct.subCategories.map((c, index) => (
                  <Link
                    key={`${c}-${index}`}
                    href={`/categories/${encodeURIComponent(displayProduct.subCategorySlugs?.[index] || slugify(c))}`}
                    className="no-underline"
                    style={{ borderRadius: "20px", background: "rgba(0,212,255,0.07)", border: "1px solid rgba(0,212,255,0.18)", color: "#00d4ff", padding: "4px 12px", fontSize: "0.72rem", fontWeight: 700 }}
                  >
                    {c}
                  </Link>
                ))}
              </div>

              {/* Specifications / Variation Options (display panel) */}
              {displayProduct.variations.length > 0 && (
                <div style={{ borderRadius: "12px", border: "1px solid rgba(0,212,255,0.1)", background: "rgba(0,212,255,0.03)", padding: "14px 16px" }}>
                  <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: "0 0 10px" }}>
                    {displayProduct.productType === "PARENT" ? "Available Options" : "Specifications"}
                  </p>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                    {displayProduct.variations.map((v, i) => (
                      <span
                        key={`${v.name}-${i}`}
                        style={{ borderRadius: "8px", border: "1px solid rgba(0,212,255,0.12)", background: "rgba(0,212,255,0.04)", padding: "6px 12px", fontSize: "0.8rem" }}
                      >
                        {displayProduct.productType === "PARENT" ? (
                          <span style={{ fontWeight: 700, color: "#c8c8e8" }}>{v.name}</span>
                        ) : (
                          <><span style={{ color: "var(--muted)" }}>{v.name}: </span><span style={{ fontWeight: 700, color: "#fff" }}>{v.value}</span></>
                        )}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Purchase Controls */}
              <div style={{ borderTop: "1px solid rgba(0,212,255,0.08)", paddingTop: "20px" }}>
                {isAuthenticated ? (
                  <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
                    {/* Variation Selector */}
                    {product?.productType === "PARENT" && (
                      <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                        <p style={{ fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", margin: 0 }}>Select Variation</p>
                        {parentAttributeNames.length === 0 && <p style={{ fontSize: "0.8rem", color: "var(--muted)" }}>No variation attributes configured.</p>}
                        {parentAttributeNames.map((attributeName) => (
                          <div key={attributeName}>
                            <label style={{ display: "block", fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "6px" }}>
                              {attributeName}
                            </label>
                            <select
                              value={selectedAttributes[attributeName] || ""}
                              onChange={(e) => setSelectedAttributes((old) => ({ ...old, [attributeName]: e.target.value }))}
                              disabled={addingToCart || (variationOptionsByAttribute[attributeName] || []).length === 0}
                              style={darkSelect}
                            >
                              <option value="">Select {attributeName}</option>
                              {(variationOptionsByAttribute[attributeName] || []).map((value) => (
                                <option key={`${attributeName}-${value}`} value={value}>{value}</option>
                              ))}
                            </select>
                          </div>
                        ))}
                        {variations.length > 0 && !allAttributesSelected && (
                          <p style={{ fontSize: "0.78rem", color: "var(--muted)" }}>Select all attributes to continue.</p>
                        )}
                        {allAttributesSelected && !selectedVariationId && variations.length > 0 && (
                          <p style={{ fontSize: "0.78rem", fontWeight: 700, color: "#ef4444" }}>No variation matches the selected combination.</p>
                        )}
                        {variations.length === 0 && <p style={{ fontSize: "0.78rem", color: "var(--muted)" }}>No variations available.</p>}
                      </div>
                    )}

                    {/* Quantity */}
                    <div>
                      <label style={{ display: "block", fontSize: "0.65rem", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.1em", color: "var(--muted)", marginBottom: "8px" }}>Quantity</label>
                      <div className="qty-stepper">
                        <button disabled={addingToCart} onClick={() => setQuantity((old) => Math.max(1, old - 1))}>−</button>
                        <span>{quantity}</span>
                        <button disabled={addingToCart} onClick={() => setQuantity((old) => old + 1)}>+</button>
                      </div>
                    </div>

                    {/* Add to Cart + Wishlist */}
                    <div style={{ display: "flex", flexWrap: "wrap", gap: "10px" }}>
                      <button
                        disabled={!canAddToCart}
                        onClick={() => void addToCart()}
                        style={{
                          flex: 1, minWidth: "140px", padding: "13px 20px", borderRadius: "12px", border: "none",
                          background: canAddToCart ? "linear-gradient(135deg, #00d4ff, #7c3aed)" : "rgba(0,212,255,0.2)",
                          color: "#fff", fontSize: "0.9rem", fontWeight: 800,
                          cursor: canAddToCart ? "pointer" : "not-allowed",
                          boxShadow: canAddToCart ? "0 0 20px rgba(0,212,255,0.2)" : "none",
                          display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
                        }}
                      >
                        {addingToCart && <span className="spinner-sm" />}
                        {addingToCart ? "Adding..." : "Add to Cart"}
                      </button>
                      <button
                        disabled={wishlistPending}
                        onClick={() => { void toggleWishlist(); }}
                        style={{
                          padding: "13px 18px", borderRadius: "12px",
                          border: wishlistItemId ? "1px solid rgba(239,68,68,0.4)" : "1px solid rgba(0,212,255,0.25)",
                          background: wishlistItemId ? "rgba(239,68,68,0.06)" : "rgba(0,212,255,0.06)",
                          color: wishlistItemId ? "#ef4444" : "#00d4ff",
                          fontSize: "0.8rem", fontWeight: 700, cursor: wishlistPending ? "not-allowed" : "pointer",
                          display: "inline-flex", alignItems: "center", gap: "6px",
                        }}
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill={wishlistItemId ? "currentColor" : "none"} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
                        </svg>
                        {wishlistPending ? "Saving..." : (wishlistItemId ? "Wishlisted" : "Wishlist")}
                      </button>
                      <Link
                        href="/cart"
                        className="no-underline"
                        style={{
                          padding: "13px 18px", borderRadius: "12px",
                          border: "1px solid rgba(0,212,255,0.2)", background: "rgba(0,0,10,0.4)",
                          color: "#c8c8e8", fontSize: "0.8rem", fontWeight: 700,
                          display: "inline-flex", alignItems: "center", gap: "6px",
                        }}
                      >
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                          <path d="M16 10a4 4 0 0 1-8 0" />
                        </svg>
                        Open Cart
                      </Link>
                    </div>
                  </div>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                    <p style={{ fontSize: "0.875rem", color: "var(--muted)", margin: 0 }}>Sign in to add this product to cart</p>
                    <button
                      disabled={signingInToBuy}
                      onClick={async () => {
                        if (signingInToBuy) return;
                        setSigningInToBuy(true);
                        try { await login(`/products/${params.id}`); } finally { setSigningInToBuy(false); }
                      }}
                      style={{
                        padding: "13px 20px", borderRadius: "12px", border: "none",
                        background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                        color: "#fff", fontSize: "0.9rem", fontWeight: 800, cursor: "pointer",
                        display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "8px",
                      }}
                    >
                      {signingInToBuy ? "Redirecting..." : "Sign In to Continue"}
                    </button>
                  </div>
                )}
              </div>

              {/* Trust Badges */}
              <div style={{ display: "flex", flexWrap: "wrap", gap: "16px", borderTop: "1px solid rgba(0,212,255,0.08)", paddingTop: "16px" }}>
                {[
                  { icon: "M5 12h14M12 5l7 7-7 7", label: "Free Shipping" },
                  { icon: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", label: "Secure Payment" },
                  { icon: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8M3 8v4h4M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16m18 0v-4h-4", label: "30-Day Returns" },
                ].map(({ icon, label }) => (
                  <span key={label} style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.75rem", color: "var(--muted)" }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#00d4ff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d={icon} />
                    </svg>
                    {label}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
