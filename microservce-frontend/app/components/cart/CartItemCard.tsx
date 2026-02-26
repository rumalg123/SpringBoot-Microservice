"use client";

import Link from "next/link";
import { money } from "../../../lib/format";
import type { CartItem } from "../../../lib/types/cart";

type Props = {
  item: CartItem;
  busy: boolean;
  updatingItemId: string | null;
  savingItemId: string | null;
  removingItemId: string | null;
  onUpdateQuantity: (itemId: string, quantity: number) => void;
  onSaveForLater: (itemId: string) => void;
  onRemove: (itemId: string) => void;
};

export default function CartItemCard({
  item,
  busy,
  updatingItemId,
  savingItemId,
  removingItemId,
  onUpdateQuantity,
  onSaveForLater,
  onRemove,
}: Props) {
  return (
    <article className="animate-rise glass-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-[200px] flex-1">
          <Link
            href={`/products/${encodeURIComponent(item.productSlug)}`}
            className="no-underline font-bold text-white text-[0.95rem] leading-[1.4] transition-colors hover:text-brand"
          >
            {item.productName}
          </Link>
          <p className="mt-1 text-xs text-muted-2 font-mono">
            SKU: {item.productSku}
          </p>
          <p className="mt-1.5 text-[0.9rem] font-bold text-brand">
            {money(item.unitPrice)} each
          </p>
          <p className="mt-0.5 text-[0.75rem] text-muted">
            Line total: <strong className="text-ink-light">{money(item.lineTotal)}</strong>
          </p>
        </div>

        <div className="flex flex-col items-end gap-2.5">
          <div className="qty-stepper">
            <button disabled={busy || item.quantity <= 1} onClick={() => { onUpdateQuantity(item.id, item.quantity - 1); }}>−</button>
            <span>{updatingItemId === item.id ? "…" : item.quantity}</span>
            <button disabled={busy} onClick={() => { onUpdateQuantity(item.id, item.quantity + 1); }}>+</button>
          </div>
          <div className="flex gap-1.5">
            <button
              onClick={() => { onSaveForLater(item.id); }}
              disabled={busy}
              className={`px-3.5 py-[5px] rounded-lg border border-accent/25 bg-accent/[0.06] text-[#a78bfa] text-xs font-bold ${busy ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
            >
              {savingItemId === item.id ? "Saving..." : "Save for Later"}
            </button>
            <button
              onClick={() => { onRemove(item.id); }}
              disabled={busy}
              className={`px-3.5 py-[5px] rounded-lg border border-danger/25 bg-danger/[0.06] text-danger text-xs font-bold ${busy ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
            >
              {removingItemId === item.id ? "Removing..." : "Remove"}
            </button>
          </div>
        </div>
      </div>
    </article>
  );
}
