"use client";

import { useState } from "react";
import { money } from "../../../../lib/format";
import type { TopProductsData } from "./types";
import {
  num, SkeletonGrid,
} from "./helpers";

interface ProductsTabProps {
  topProducts: TopProductsData | null;
}

export default function ProductsTab({ topProducts }: ProductsTabProps) {
  const [productTab, setProductTab] = useState<"revenue" | "views" | "sold" | "wishlisted">("revenue");

  return (
    <div className="mt-6 rounded-lg border border-line bg-white/[0.03] px-7 py-6">
      <h2 className="mb-4 text-lg font-bold text-ink">Top Products</h2>
      <div className="mb-4 flex gap-1">
        {(["revenue", "views", "sold", "wishlisted"] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setProductTab(t)}
            className={`cursor-pointer rounded-md border-none px-3.5 py-1.5 text-[0.78rem] font-semibold transition-all duration-200 ${
              productTab === t
                ? "bg-brand/15 text-brand"
                : "bg-white/[0.04] text-muted"
            }`}
          >
            {t === "revenue" ? "By Revenue" : t === "views" ? "By Views" : t === "sold" ? "By Sold" : "By Wishlisted"}
          </button>
        ))}
      </div>
      {!topProducts ? (
        <SkeletonGrid count={3} height={60} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-base">
            <thead>
              <tr>
                <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">#</th>
                <th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Product</th>
                {productTab === "revenue" && <><th className="border-b border-line px-3 py-2.5 text-left text-xs uppercase tracking-[0.06em] text-muted">Qty Sold</th><th className="border-b border-line px-3 py-2.5 text-right text-xs uppercase tracking-[0.06em] text-muted">Revenue</th></>}
                {productTab === "views" && <th className="border-b border-line px-3 py-2.5 text-right text-xs uppercase tracking-[0.06em] text-muted">Views</th>}
                {productTab === "sold" && <th className="border-b border-line px-3 py-2.5 text-right text-xs uppercase tracking-[0.06em] text-muted">Sold</th>}
                {productTab === "wishlisted" && <th className="border-b border-line px-3 py-2.5 text-right text-xs uppercase tracking-[0.06em] text-muted">Wishlisted</th>}
              </tr>
            </thead>
            <tbody>
              {productTab === "revenue" && topProducts.byRevenue?.map((p, i) => (
                <tr key={p.productId}>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{i + 1}</td>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{p.productName || "Unnamed Product"}</td>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{num(p.quantitySold)}</td>
                  <td className="border-b border-line px-3 py-2.5 text-right font-semibold text-brand">{money(p.totalRevenue)}</td>
                </tr>
              ))}
              {productTab === "views" && topProducts.byViews?.map((p, i) => (
                <tr key={p.id}>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{i + 1}</td>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{p.name || "Unnamed Product"}</td>
                  <td className="border-b border-line px-3 py-2.5 text-right font-semibold text-ink">{num(p.viewCount)}</td>
                </tr>
              ))}
              {productTab === "sold" && topProducts.bySold?.map((p, i) => (
                <tr key={p.id}>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{i + 1}</td>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{p.name || "Unnamed Product"}</td>
                  <td className="border-b border-line px-3 py-2.5 text-right font-semibold text-ink">{num(p.soldCount)}</td>
                </tr>
              ))}
              {productTab === "wishlisted" && topProducts.byWishlisted?.map((p, i) => (
                <tr key={p.productId}>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{i + 1}</td>
                  <td className="border-b border-line px-3 py-2.5 text-ink">{p.productName || "Unnamed Product"}</td>
                  <td className="border-b border-line px-3 py-2.5 text-right font-semibold text-ink">{num(p.wishlistCount)}</td>
                </tr>
              ))}
              {((productTab === "revenue" && !topProducts.byRevenue?.length) ||
                (productTab === "views" && !topProducts.byViews?.length) ||
                (productTab === "sold" && !topProducts.bySold?.length) ||
                (productTab === "wishlisted" && !topProducts.byWishlisted?.length)) && (
                <tr><td colSpan={4} className="border-b border-line px-3 py-2.5 text-center text-muted">No data available.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
