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
    <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
      <div style={{ position: "relative", aspectRatio: "1", overflow: "hidden", borderRadius: "16px", border: "1px solid var(--line-bright)", background: "rgba(0,0,10,0.5)" }}>
        {discount && (
          <span className="badge-sale" style={{ top: "12px", left: "12px" }}>-{discount}% OFF</span>
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
          <div style={{ display: "grid", placeItems: "center", height: "100%", background: "linear-gradient(135deg, var(--brand-soft), var(--accent-soft))" }}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--brand-glow)" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 8h14M5 8a2 2 0 1 0 0-4h14a2 2 0 1 0 0 4M5 8v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8m-9 4h4" />
            </svg>
          </div>
        )}
      </div>

      {images?.length > 1 && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: "8px" }}>
          {images.slice(0, 5).map((img, index) => {
            const imageUrl = resolveImageUrl(img);
            const isSelected = selectedIndex === index;
            return (
              <button
                key={`${img}-${index}`}
                onClick={() => onSelectIndex(index)}
                disabled={disabled}
                style={{
                  aspectRatio: "1", overflow: "hidden", borderRadius: "10px", padding: 0,
                  border: isSelected ? "2px solid var(--brand)" : "2px solid var(--line-bright)",
                  background: "rgba(0,0,10,0.5)", cursor: "pointer",
                  boxShadow: isSelected ? "0 0 10px var(--brand-glow)" : "none",
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
  );
}
