"use client";

import type { RefObject } from "react";
import type { WishlistCollection } from "../../../lib/types/wishlist";

export type CollectionTabsProps = {
  collections: WishlistCollection[];
  activeCollectionId: string | null;
  onSelectCollection: (id: string | null) => void;
  wishlistItemCount: number;
  newCollectionName: string;
  onNewCollectionNameChange: (value: string) => void;
  showNewCollectionInput: boolean;
  onToggleNewInput: (show: boolean) => void;
  creatingCollection: boolean;
  onCreate: () => void;
  newCollectionInputRef: RefObject<HTMLInputElement | null>;
};

export default function CollectionTabs({
  collections,
  activeCollectionId,
  onSelectCollection,
  wishlistItemCount,
  newCollectionName,
  onNewCollectionNameChange,
  showNewCollectionInput,
  onToggleNewInput,
  creatingCollection,
  onCreate,
  newCollectionInputRef,
}: CollectionTabsProps) {
  return (
    <div className="flex flex-wrap items-center gap-2 mb-4">
      {/* All Items tab */}
      <button
        onClick={() => { onSelectCollection(null); }}
        className={`px-4 py-2 rounded-md text-sm font-bold cursor-pointer inline-flex items-center gap-[6px] transition-all duration-150 ${activeCollectionId === null ? "border border-brand bg-brand-soft text-brand" : "border border-line-bright bg-transparent text-muted"}`}
      >
        All Items
        <span
          className={`text-[0.65rem] font-bold px-[7px] py-[1px] rounded-full ${activeCollectionId === null ? "bg-brand text-white" : "bg-line-bright text-muted"}`}
        >
          {wishlistItemCount}
        </span>
      </button>

      {/* Collection tabs */}
      {collections.map((col) => (
        <button
          key={col.id}
          onClick={() => { onSelectCollection(col.id); }}
          className={`px-4 py-2 rounded-md text-sm font-bold cursor-pointer inline-flex items-center gap-[6px] transition-all duration-150 ${activeCollectionId === col.id ? "border border-brand bg-brand-soft text-brand" : "border border-line-bright bg-transparent text-muted"}`}
        >
          {col.name}
          <span
            className={`text-[0.65rem] font-bold px-[7px] py-[1px] rounded-full ${activeCollectionId === col.id ? "bg-brand text-white" : "bg-line-bright text-muted"}`}
          >
            {col.itemCount}
          </span>
        </button>
      ))}

      {/* New collection button / input */}
      {!showNewCollectionInput ? (
        <button
          onClick={() => { onToggleNewInput(true); }}
          className="px-3 py-2 rounded-md border border-dashed border-line-bright bg-transparent text-muted text-sm font-bold cursor-pointer inline-flex items-center gap-1 transition-all duration-150 hover:border-brand hover:text-brand"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          New Collection
        </button>
      ) : (
        <div className="inline-flex items-center gap-[6px]">
          <input
            ref={newCollectionInputRef}
            type="text"
            value={newCollectionName}
            onChange={(e) => onNewCollectionNameChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && newCollectionName.trim()) void onCreate();
              if (e.key === "Escape") { onToggleNewInput(false); onNewCollectionNameChange(""); }
            }}
            placeholder="Collection name..."
            className="px-3 py-[7px] rounded-md border border-line-bright bg-[var(--bg-card)] text-white text-sm outline-none w-[160px]"
          />
          <button
            onClick={() => { void onCreate(); }}
            disabled={creatingCollection || !newCollectionName.trim()}
            className={`px-[14px] py-[7px] rounded-md border-none text-white text-[0.75rem] font-bold ${!newCollectionName.trim() ? "bg-line-bright cursor-not-allowed" : "bg-[image:var(--gradient-brand)] cursor-pointer"}`}
          >
            {creatingCollection ? "Creating..." : "Add"}
          </button>
          <button
            onClick={() => { onToggleNewInput(false); onNewCollectionNameChange(""); }}
            className="px-[10px] py-[7px] rounded-md border border-line-bright bg-transparent text-muted text-[0.75rem] font-bold cursor-pointer"
          >
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}
