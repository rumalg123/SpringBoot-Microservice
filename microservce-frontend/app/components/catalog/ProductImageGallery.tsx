"use client";

import Image from "next/image";
import { resolveImageUrl } from "../../../lib/image";

type Props = {
  images: string[];
  name: string;
  selectedIndex: number;
  onSelectIndex: (index: number) => void;
  discount: number | null;
  disabled?: boolean;
};

export default function ProductImageGallery({
  images,
  name,
  selectedIndex,
  onSelectIndex,
  discount,
  disabled = false,
}: Props) {
  const mainUrl = resolveImageUrl(images?.[selectedIndex] || "");

  return (
    <div className="flex flex-col gap-3">
      <div className="relative aspect-square overflow-hidden rounded-lg border border-line-bright bg-[rgba(0,0,10,0.5)]">
        {discount && (
          <span className="badge-sale top-3 left-3">-{discount}% OFF</span>
        )}
        {mainUrl ? (
          <Image
            src={mainUrl}
            alt={name}
            width={800} height={800}
            className="h-full w-full object-cover"
            unoptimized
          />
        ) : (
          <div className="grid place-items-center h-full bg-gradient-to-br from-brand-soft to-accent-soft">
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--brand-glow)" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 8h14M5 8a2 2 0 1 0 0-4h14a2 2 0 1 0 0 4M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8m-9 4h4" />
            </svg>
          </div>
        )}
      </div>

      {images?.length > 1 && (
        <div className="grid grid-cols-5 gap-2">
          {images.slice(0, 5).map((img, index) => {
            const imageUrl = resolveImageUrl(img);
            const isSelected = selectedIndex === index;
            return (
              <button
                key={`${img}-${index}`}
                onClick={() => onSelectIndex(index)}
                disabled={disabled}
                className={`aspect-square overflow-hidden rounded-md p-0 bg-[rgba(0,0,10,0.5)] cursor-pointer transition-[border-color,box-shadow] duration-200 ${isSelected ? "border-2 border-brand shadow-[0_0_10px_var(--brand-glow)]" : "border-2 border-line-bright shadow-none"}`}
              >
                {imageUrl ? (
                  <Image src={imageUrl} alt={img} width={120} height={120} className="h-full w-full object-cover" unoptimized />
                ) : (
                  <div className="grid place-items-center h-full text-[20px]">
                    📦
                  </div>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
