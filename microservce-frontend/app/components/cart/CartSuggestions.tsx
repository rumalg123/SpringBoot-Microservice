"use client";

import Link from "next/link";
import Image from "next/image";
import { money, calcDiscount } from "../../../lib/format";
import { resolveImageUrl } from "../../../lib/image";
import type { PersonalizationProduct } from "../../../lib/personalization";

type Props = {
  suggestions: PersonalizationProduct[];
  visible: boolean;
};

export default function CartSuggestions({ suggestions, visible }: Props) {
  if (!visible || suggestions.length === 0) return null;

  return (
    <section className="mx-auto max-w-7xl px-4 pb-8">
      <h2 className="font-[Syne,sans-serif] text-[1.3rem] font-extrabold text-white mb-4">Complete Your Purchase</h2>
      <div className="flex gap-4 overflow-x-auto pb-2">
        {suggestions.map((p) => {
          const discount = calcDiscount(p.regularPrice, p.sellingPrice);
          const imgUrl = resolveImageUrl(p.mainImage);
          return (
            <Link href={`/products/${encodeURIComponent((p.slug || p.id).trim())}`} key={p.id} className="product-card no-underline min-w-[200px] max-w-[220px] shrink-0">
              {discount && <span className="badge-sale">-{discount}%</span>}
              <div className="relative aspect-square overflow-hidden bg-surface-2">
                {imgUrl ? (<Image src={imgUrl} alt={p.name} width={300} height={300} className="product-card-img" unoptimized />) : (<div className="grid place-items-center w-full h-full bg-gradient-to-br from-surface to-[#1c1c38] text-muted-2 text-[0.75rem]">No Image</div>)}
              </div>
              <div className="product-card-body">
                <p className="mb-1 text-sm font-semibold text-ink line-clamp-1">{p.name}</p>
                <span className="price-current text-[0.85rem]">{money(p.sellingPrice)}</span>
              </div>
            </Link>
          );
        })}
      </div>
    </section>
  );
}
