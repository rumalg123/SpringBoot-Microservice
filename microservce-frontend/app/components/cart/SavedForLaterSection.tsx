"use client";

import Link from "next/link";
import { money } from "../../../lib/format";
import type { CartItem } from "../../../lib/types/cart";

type Props = {
  items: CartItem[];
  busy: boolean;
  movingItemId: string | null;
  removingItemId: string | null;
  onMoveToCart: (itemId: string) => void;
  onRemove: (itemId: string) => void;
};

export default function SavedForLaterSection({
  items,
  busy,
  movingItemId,
  removingItemId,
  onMoveToCart,
  onRemove,
}: Props) {
  if (items.length === 0) return null;

  return (
    <section className="mt-6">
      <h2 className="font-[Syne,sans-serif] text-[1.2rem] font-extrabold text-white mb-3">
        Saved for Later ({items.length})
      </h2>
      <div className="grid gap-2.5 grid-cols-[repeat(auto-fill,minmax(260px,1fr))]">
        {items.map((item) => (
          <article key={item.id} className="glass-card p-3.5">
            <Link
              href={`/products/${encodeURIComponent(item.productSlug)}`}
              className="no-underline font-bold text-white text-[0.85rem] leading-[1.4]"
            >
              {item.productName}
            </Link>
            <p className="mt-1 text-xs text-muted-2 font-mono">
              SKU: {item.productSku}
            </p>
            <p className="mt-1.5 mb-2.5 text-[0.85rem] font-bold text-brand">
              {money(item.unitPrice)}
            </p>
            <div className="flex gap-1.5">
              <button
                onClick={() => { onMoveToCart(item.id); }}
                disabled={busy}
                className={`px-3.5 py-[5px] rounded-lg border border-brand/25 bg-brand/[0.06] text-brand text-xs font-bold ${busy ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
              >
                {movingItemId === item.id ? "Moving..." : "Move to Cart"}
              </button>
              <button
                onClick={() => { onRemove(item.id); }}
                disabled={busy}
                className={`px-3.5 py-[5px] rounded-lg border border-danger/25 bg-danger/[0.06] text-danger text-xs font-bold ${busy ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
              >
                {removingItemId === item.id ? "Removing..." : "Remove"}
              </button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
