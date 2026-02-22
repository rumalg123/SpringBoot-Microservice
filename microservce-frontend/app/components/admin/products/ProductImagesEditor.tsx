"use client";

import Image from "next/image";
import type { DragEvent } from "react";
import type { ProductImagesEditorProps } from "./types";

export default function ProductImagesEditor({
  form,
  maxImageCount,
  resolveImageUrl,
  uploadingImages,
  productMutationBusy,
  uploadImages,
  setDragImageIndex,
  onImageDrop,
  removeImage,
}: ProductImagesEditorProps) {
  return (
    <div className="rounded-lg border border-[var(--line)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs text-[var(--muted)]">
          Product Images ({form.images.length}/{maxImageCount})
        </p>
        <label className="cursor-pointer rounded-md border border-[var(--line)] px-2 py-1 text-xs" style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}>
          {uploadingImages ? "Uploading..." : "Upload Images"}
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            multiple
            className="hidden"
            disabled={productMutationBusy || form.images.length >= maxImageCount}
            onChange={(e) => {
              void uploadImages(e);
            }}
          />
        </label>
      </div>
      <p className="mb-2 text-[11px] text-[var(--muted)]">Max 5 images, 1MB each, 540x540 max. Drag to reorder. First image is main.</p>
      {form.images.length === 0 && <p className="text-xs text-[var(--muted)]">No images uploaded.</p>}
      <div className="grid gap-2">
        {form.images.map((imageName, index: number) => {
          const imageUrl = resolveImageUrl(imageName);
          return (
            <div
              key={`${imageName}-${index}`}
              draggable
              onDragStart={() => setDragImageIndex(index)}
              onDragOver={(e: DragEvent<HTMLDivElement>) => e.preventDefault()}
              onDrop={() => onImageDrop(index)}
              className="flex items-center gap-2 rounded-lg border border-[var(--line)] p-2"
              style={{ background: "var(--surface-2)" }}
            >
              <div className="h-12 w-12 overflow-hidden rounded-md border border-[var(--line)]" style={{ background: "var(--surface-3)" }}>
                {imageUrl ? (
                  <Image src={imageUrl} alt={imageName} width={48} height={48} className="h-full w-full object-cover" unoptimized />
                ) : (
                  <div className="grid h-full w-full place-items-center text-[10px] text-[var(--muted)]">IMG</div>
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs text-[var(--ink)]">{imageName}</p>
                <p className="text-[10px] text-[var(--muted)]">{index === 0 ? "Main image" : `Position ${index + 1}`}</p>
              </div>
              <button
                type="button"
                onClick={() => removeImage(index)}
                disabled={productMutationBusy}
                className="rounded border border-red-900/30 px-2 py-1 text-[10px] text-red-400 disabled:cursor-not-allowed disabled:opacity-60"
                style={{ background: "rgba(239,68,68,0.06)" }}
              >
                Remove
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
