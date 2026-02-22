"use client";

import type { AxiosInstance } from "axios";
import { CSSProperties, useEffect, useMemo, useState } from "react";
import PosterFormField from "./PosterFormField";

type LinkType = "PRODUCT" | "CATEGORY" | "SEARCH" | "URL" | "NONE";

type CategoryItem = {
  id: string;
  name: string;
  slug: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
  deleted?: boolean;
};

type ProductItem = {
  id: string;
  slug: string;
  name: string;
  sku: string;
  active?: boolean;
};

type ProductPageResponse = {
  content?: ProductItem[];
};

type Props = {
  apiClient?: AxiosInstance | null;
  linkType: LinkType;
  linkTarget: string;
  openInNewTab: boolean;
  disabled?: boolean;
  fieldBaseStyle: CSSProperties;
  onLinkTypeChange: (value: LinkType) => void;
  onLinkTargetChange: (value: string) => void;
  onOpenInNewTabChange: (value: boolean) => void;
};

function previewHref(linkType: LinkType, targetRaw: string): string {
  const target = targetRaw.trim();
  if (!target) return "";
  switch (linkType) {
    case "CATEGORY":
      return target.startsWith("/categories/") ? target : `/categories/${target}`;
    case "PRODUCT":
      return target.startsWith("/products/") ? target : `/products/${target}`;
    case "SEARCH":
      if (target.startsWith("?")) return `/products${target}`;
      if (target.startsWith("/products?")) return target;
      return `/products?${target}`;
    case "URL":
      return target;
    case "NONE":
    default:
      return "";
  }
}

export default function PosterLinkTargetEditor({
  apiClient,
  linkType,
  linkTarget,
  openInNewTab,
  disabled = false,
  fieldBaseStyle,
  onLinkTypeChange,
  onLinkTargetChange,
  onOpenInNewTabChange,
}: Props) {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);
  const [categoriesLoaded, setCategoriesLoaded] = useState(false);

  const [productSearch, setProductSearch] = useState("");
  const [productResults, setProductResults] = useState<ProductItem[]>([]);
  const [productLoading, setProductLoading] = useState(false);

  const [searchQueryValue, setSearchQueryValue] = useState("");
  const [searchMainCategorySlug, setSearchMainCategorySlug] = useState("");

  useEffect(() => {
    if (!apiClient || categoriesLoaded) return;
    let cancelled = false;
    const run = async () => {
      setCategoriesLoading(true);
      try {
        const res = await apiClient.get("/admin/categories");
        if (cancelled) return;
        const data = ((res.data as CategoryItem[]) || []).filter((c) => !c.deleted);
        setCategories(data);
        setCategoriesLoaded(true);
      } catch {
        if (!cancelled) {
          setCategories([]);
        }
      } finally {
        if (!cancelled) setCategoriesLoading(false);
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [apiClient, categoriesLoaded]);

  useEffect(() => {
    if (linkType !== "PRODUCT") return;
    const term = productSearch.trim();
    if (term.length < 2 || !apiClient) {
      setProductResults([]);
      setProductLoading(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setProductLoading(true);
      try {
        const params = new URLSearchParams({
          page: "0",
          size: "8",
          q: term,
          includeOrphanParents: "true",
        });
        const res = await apiClient.get(`/products?${params.toString()}`);
        if (cancelled) return;
        const data = (res.data as ProductPageResponse)?.content || [];
        setProductResults(data.filter((p) => p.active !== false));
      } catch {
        if (!cancelled) setProductResults([]);
      } finally {
        if (!cancelled) setProductLoading(false);
      }
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [apiClient, linkType, productSearch]);

  useEffect(() => {
    if (linkType !== "SEARCH") return;
    const target = linkTarget.trim();
    const params = new URLSearchParams(target.replace(/^\?/, ""));
    setSearchQueryValue(params.get("q") || "");
    setSearchMainCategorySlug(params.get("mainCategory") || "");
  }, [linkType, linkTarget]);

  const categoryOptions = useMemo(() => {
    const parentNames = new Map(categories.filter((c) => c.type === "PARENT").map((c) => [c.id, c.name]));
    return [...categories].sort((a, b) => {
      if (a.type !== b.type) return a.type === "PARENT" ? -1 : 1;
      const aLabel = a.type === "SUB" && a.parentCategoryId ? `${parentNames.get(a.parentCategoryId) || ""} / ${a.name}` : a.name;
      const bLabel = b.type === "SUB" && b.parentCategoryId ? `${parentNames.get(b.parentCategoryId) || ""} / ${b.name}` : b.name;
      return aLabel.localeCompare(bLabel);
    });
  }, [categories]);

  const selectedCategoryId = useMemo(() => {
    const target = linkTarget.trim().replace(/^\/categories\//, "");
    return categoryOptions.find((c) => c.slug === target)?.id || "";
  }, [categoryOptions, linkTarget]);

  const linkPreview = previewHref(linkType, linkTarget);

  const applySearchBuilder = () => {
    const params = new URLSearchParams();
    if (searchQueryValue.trim()) params.set("q", searchQueryValue.trim());
    if (searchMainCategorySlug.trim()) params.set("mainCategory", searchMainCategorySlug.trim());
    onLinkTargetChange(params.toString());
  };

  return (
    <div className="grid gap-3">
      <div className="grid gap-3 md:grid-cols-3">
        <PosterFormField label="Link Type">
          <select
            value={linkType}
            onChange={(e) => onLinkTypeChange(e.target.value as LinkType)}
            className="poster-select-admin rounded-lg border px-3 py-2.5"
            style={fieldBaseStyle}
            disabled={disabled}
          >
            {["NONE", "PRODUCT", "CATEGORY", "SEARCH", "URL"].map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </PosterFormField>

        <PosterFormField
          label="Link Target"
          hint={
            linkType === "SEARCH"
              ? "Query string (q=...&mainCategory=...)"
              : linkType === "CATEGORY"
                ? "Category slug (e.g., electronics)"
                : linkType === "PRODUCT"
                  ? "Product slug (preferred) or ID"
                  : linkType === "URL"
                    ? "Full URL (https://...)"
                    : "No target required"
          }
        >
          <input
            value={linkTarget}
            onChange={(e) => onLinkTargetChange(e.target.value)}
            disabled={disabled || linkType === "NONE"}
            placeholder={linkType === "NONE" ? "No target needed" : linkType === "SEARCH" ? "q=watch&mainCategory=electronics" : linkType === "URL" ? "https://example.com/campaign" : "slug or URL"}
            className="rounded-lg border px-3 py-2.5"
            style={{ ...fieldBaseStyle, opacity: linkType === "NONE" ? 0.7 : 1 }}
          />
        </PosterFormField>

        <PosterFormField label="Link Behavior">
          <label className="flex items-center gap-2 rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, display: "flex" }}>
            <input
              type="checkbox"
              checked={openInNewTab}
              onChange={(e) => onOpenInNewTabChange(e.target.checked)}
              disabled={disabled || linkType === "NONE"}
            />
            Open in new tab
          </label>
        </PosterFormField>
      </div>

      {linkType === "CATEGORY" && (
        <div className="grid gap-3 md:grid-cols-2">
          <PosterFormField label="Pick Existing Category" hint="Selecting here auto-fills the category slug.">
            <select
              value={selectedCategoryId}
              onChange={(e) => {
                const selected = categoryOptions.find((c) => c.id === e.target.value);
                onLinkTargetChange(selected ? selected.slug : "");
              }}
              className="poster-select-admin rounded-lg border px-3 py-2.5"
              style={fieldBaseStyle}
              disabled={disabled || categoriesLoading}
            >
              <option value="">{categoriesLoading ? "Loading categories..." : "Select category"}</option>
              {categoryOptions.map((c) => {
                const parent = c.parentCategoryId ? categoryOptions.find((x) => x.id === c.parentCategoryId)?.name : null;
                const label = c.type === "SUB" && parent ? `${parent} / ${c.name}` : c.name;
                return (
                  <option key={c.id} value={c.id}>
                    {label} ({c.type})
                  </option>
                );
              })}
            </select>
          </PosterFormField>
          <PosterFormField label="Generated URL Preview" hint="Frontend resolves CATEGORY links to category pages.">
            <div className="rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, minHeight: 44, display: "flex", alignItems: "center", color: linkPreview ? "var(--ink)" : "var(--muted)" }}>
              {linkPreview || "Select a category or enter a category slug"}
            </div>
          </PosterFormField>
        </div>
      )}

      {linkType === "PRODUCT" && (
        <div className="grid gap-3 md:grid-cols-2">
          <PosterFormField label="Find Existing Product" hint="Type at least 2 characters, then click a result to auto-fill product slug.">
            <div className="grid gap-2">
              <input
                value={productSearch}
                onChange={(e) => setProductSearch(e.target.value)}
                placeholder="Search by product name or SKU"
                className="rounded-lg border px-3 py-2.5"
                style={fieldBaseStyle}
                disabled={disabled}
              />
              <div className="rounded-lg border" style={{ ...fieldBaseStyle, maxHeight: 180, overflowY: "auto" }}>
                {productSearch.trim().length < 2 && <div style={{ padding: "10px 12px", color: "var(--muted)", fontSize: "0.8rem" }}>Type at least 2 characters</div>}
                {productSearch.trim().length >= 2 && productLoading && <div style={{ padding: "10px 12px", color: "var(--muted)", fontSize: "0.8rem" }}>Loading products...</div>}
                {productSearch.trim().length >= 2 && !productLoading && productResults.length === 0 && <div style={{ padding: "10px 12px", color: "var(--muted)", fontSize: "0.8rem" }}>No matching products</div>}
                {!productLoading && productResults.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => {
                      onLinkTargetChange((p.slug || p.id).trim());
                      setProductSearch(p.name);
                    }}
                    disabled={disabled}
                    style={{
                      width: "100%",
                      textAlign: "left",
                      border: "none",
                      borderTop: "1px solid rgba(255,255,255,0.03)",
                      background: "transparent",
                      cursor: disabled ? "not-allowed" : "pointer",
                      padding: "10px 12px",
                    }}
                  >
                    <div style={{ color: "var(--ink)", fontSize: "0.83rem", fontWeight: 600 }}>{p.name}</div>
                    <div style={{ color: "var(--muted)", fontSize: "0.72rem" }}>SKU: {p.sku} | slug: {p.slug || p.id}</div>
                  </button>
                ))}
              </div>
            </div>
          </PosterFormField>
          <PosterFormField label="Generated URL Preview" hint="Frontend resolves PRODUCT links to product detail pages.">
            <div className="rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, minHeight: 44, display: "flex", alignItems: "center", color: linkPreview ? "var(--ink)" : "var(--muted)" }}>
              {linkPreview || "Pick a product or enter a product slug"}
            </div>
          </PosterFormField>
        </div>
      )}

      {linkType === "SEARCH" && (
        <div className="grid gap-3 md:grid-cols-3">
          <PosterFormField label="Search Query" hint="Optional q parameter for products page.">
            <input
              value={searchQueryValue}
              onChange={(e) => setSearchQueryValue(e.target.value)}
              placeholder="headphones"
              className="rounded-lg border px-3 py-2.5"
              style={fieldBaseStyle}
              disabled={disabled}
            />
          </PosterFormField>
          <PosterFormField label="Main Category (Optional)" hint="Builds mainCategory=<slug> in URL.">
            <select
              value={searchMainCategorySlug}
              onChange={(e) => setSearchMainCategorySlug(e.target.value)}
              className="poster-select-admin rounded-lg border px-3 py-2.5"
              style={fieldBaseStyle}
              disabled={disabled || categoriesLoading}
            >
              <option value="">Any category</option>
              {categoryOptions
                .filter((c) => c.type === "PARENT")
                .map((c) => (
                  <option key={c.id} value={c.slug}>
                    {c.name}
                  </option>
                ))}
            </select>
          </PosterFormField>
          <PosterFormField label="Search URL Builder" hint="Applies the values into Link Target query string.">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={applySearchBuilder}
                disabled={disabled}
                style={{
                  padding: "10px 12px",
                  borderRadius: 10,
                  border: "1px solid var(--line-bright)",
                  background: "var(--brand-soft)",
                  color: "var(--brand)",
                  fontWeight: 700,
                  cursor: disabled ? "not-allowed" : "pointer",
                }}
              >
                Generate Query
              </button>
              <div className="rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, flex: 1, minHeight: 44, display: "flex", alignItems: "center", color: linkPreview ? "var(--ink)" : "var(--muted)" }}>
                {linkPreview || "Build or type search query"}
              </div>
            </div>
          </PosterFormField>
        </div>
      )}

      {linkType === "URL" && (
        <PosterFormField label="URL Preview" hint="External links open based on the Link Behavior setting.">
          <div className="rounded-lg border px-3 py-2.5" style={{ ...fieldBaseStyle, minHeight: 44, display: "flex", alignItems: "center", color: linkPreview ? "var(--ink)" : "var(--muted)" }}>
            {linkPreview || "Enter an external URL"}
          </div>
        </PosterFormField>
      )}

      <style jsx>{`
        .poster-select-admin {
          appearance: none;
          background-image:
            linear-gradient(45deg, transparent 50%, var(--ink-light) 50%),
            linear-gradient(135deg, var(--ink-light) 50%, transparent 50%);
          background-position:
            calc(100% - 18px) calc(50% - 3px),
            calc(100% - 12px) calc(50% - 3px);
          background-size: 6px 6px, 6px 6px;
          background-repeat: no-repeat;
          padding-right: 34px;
        }
        .poster-select-admin option {
          background: #111128;
          color: #f0f0ff;
        }
      `}</style>
    </div>
  );
}
