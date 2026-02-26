"use client";

import Link from "next/link";
import type { WishlistItem, WishlistCollection } from "../../../lib/types/wishlist";
import { money } from "../../../lib/format";

export type WishlistItemCardProps = {
  item: WishlistItem;
  busy: boolean;
  isEditingNote: boolean;
  noteText: string;
  onNoteTextChange: (value: string) => void;
  onStartEditNote: () => void;
  onCancelEditNote: () => void;
  onSaveNote: () => void;
  savingNote: boolean;
  movingItemId: string | null;
  removingItemId: string | null;
  onMoveToCart: () => void;
  onRemove: () => void;
  collections: WishlistCollection[];
  activeCollection: WishlistCollection | null;
  onMoveToCollection: (collectionId: string) => void;
};

export default function WishlistItemCard({
  item,
  busy,
  isEditingNote,
  noteText,
  onNoteTextChange,
  onStartEditNote,
  onCancelEditNote,
  onSaveNote,
  savingNote,
  movingItemId,
  removingItemId,
  onMoveToCart,
  onRemove,
  collections,
  activeCollection,
  onMoveToCollection,
}: WishlistItemCardProps) {
  const isParent = (item.productType || "").toUpperCase() === "PARENT";

  return (
    <article className="animate-rise glass-card border border-line-bright rounded-lg px-5 py-[18px]">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex-1 min-w-[200px]">
          <Link
            href={`/products/${encodeURIComponent(item.productSlug)}`}
            className="no-underline font-bold text-white text-[0.95rem] hover:text-brand transition-colors duration-200"
          >
            {item.productName}
          </Link>

          {/* Display existing note below item name */}
          {item.note && !isEditingNote && (
            <p className="mt-1 text-[0.78rem] text-muted italic">
              {item.note}
            </p>
          )}

          <div className="mt-[6px] flex flex-wrap items-center gap-2">
            <span className="text-[0.65rem] font-bold tracking-[0.1em] uppercase px-[10px] py-[2px] rounded-full bg-brand-soft border border-line-bright text-brand">
              {item.productType}
            </span>
            <span className="text-base font-bold text-brand">
              {money(item.sellingPriceSnapshot)}
            </span>
          </div>

          {/* Inline note editor */}
          {isEditingNote && (
            <div className="mt-2 flex items-center gap-[6px]">
              <input
                type="text"
                value={noteText}
                onChange={(e) => onNoteTextChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void onSaveNote();
                  if (e.key === "Escape") { onCancelEditNote(); }
                }}
                autoFocus
                placeholder="Add a note..."
                className="px-[10px] py-[6px] rounded-[8px] border border-line-bright bg-[var(--bg-card)] text-white text-[0.78rem] outline-none flex-1 max-w-[300px]"
              />
              <button
                onClick={() => { void onSaveNote(); }}
                disabled={savingNote}
                className={`px-3 py-[6px] rounded-[8px] border-none bg-[image:var(--gradient-brand)] text-white text-[0.72rem] font-bold ${savingNote ? "cursor-not-allowed" : "cursor-pointer"}`}
              >
                {savingNote ? "Saving..." : "Save"}
              </button>
              <button
                onClick={() => { onCancelEditNote(); }}
                className="px-[10px] py-[6px] rounded-[8px] border border-line-bright bg-transparent text-muted text-[0.72rem] font-bold cursor-pointer"
              >
                Cancel
              </button>
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Note icon button */}
          <button
            onClick={() => {
              if (isEditingNote) {
                onCancelEditNote();
              } else {
                onStartEditNote();
              }
            }}
            title={item.note ? "Edit note" : "Add note"}
            className={`px-[10px] py-2 rounded-md border border-line-bright text-[0.78rem] cursor-pointer inline-flex items-center gap-1 transition-all duration-150 ${isEditingNote ? "bg-brand-soft" : "bg-transparent"} ${item.note ? "text-brand" : "text-muted"}`}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
              <polyline points="10 9 9 9 8 9" />
            </svg>
          </button>
          <button
            onClick={() => { void onMoveToCart(); }}
            disabled={busy || isParent}
            className={`px-4 py-2 rounded-md border-none text-white text-[0.78rem] font-bold inline-flex items-center gap-[6px] ${busy || isParent ? "bg-line-bright cursor-not-allowed" : "bg-[image:var(--gradient-brand)] cursor-pointer"}`}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
            {movingItemId === item.id ? "Moving..." : isParent ? "Select Variation" : "Move to Cart"}
          </button>
          <button
            onClick={() => { void onRemove(); }}
            disabled={busy}
            className={`px-[14px] py-2 rounded-md border border-danger-glow bg-danger-soft text-danger text-[0.72rem] font-bold ${busy ? "cursor-not-allowed opacity-50" : "cursor-pointer"}`}
          >
            {removingItemId === item.id ? "Removing..." : "Remove"}
          </button>
          {!activeCollection && collections.length > 0 && (
            <select
              value=""
              onChange={(e) => { if (e.target.value) void onMoveToCollection(e.target.value); }}
              disabled={!!movingItemId || !!removingItemId}
              className="filter-select min-w-[auto] text-[0.72rem]"
            >
              <option value="">Move to collection...</option>
              {collections.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          )}
        </div>
      </div>
    </article>
  );
}
