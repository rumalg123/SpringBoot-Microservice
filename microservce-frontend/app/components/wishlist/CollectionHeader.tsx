"use client";

import type { WishlistCollection } from "../../../lib/types/wishlist";

export type CollectionHeaderProps = {
  collection: WishlistCollection;
  editingName: boolean;
  nameDraft: string;
  onNameDraftChange: (value: string) => void;
  onStartEditName: () => void;
  onCancelEditName: () => void;
  onSaveName: () => void;
  editingDesc: boolean;
  descDraft: string;
  onDescDraftChange: (value: string) => void;
  onStartEditDesc: () => void;
  onCancelEditDesc: () => void;
  onSaveDesc: () => void;
  savingCollection: boolean;
  togglingShare: boolean;
  onToggleShare: () => void;
  onCopyShareLink: () => void;
  confirmDelete: boolean;
  onConfirmDelete: () => void;
  onCancelDelete: () => void;
  onRequestDelete: () => void;
  deletingCollection: boolean;
};

export default function CollectionHeader({
  collection,
  editingName,
  nameDraft,
  onNameDraftChange,
  onStartEditName,
  onCancelEditName,
  onSaveName,
  editingDesc,
  descDraft,
  onDescDraftChange,
  onStartEditDesc,
  onCancelEditDesc,
  onSaveDesc,
  savingCollection,
  togglingShare,
  onToggleShare,
  onCopyShareLink,
  confirmDelete,
  onConfirmDelete,
  onCancelDelete,
  onRequestDelete,
  deletingCollection,
}: CollectionHeaderProps) {
  return (
    <div className="glass-card border border-line-bright rounded-lg px-5 py-[18px] mb-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex-1 min-w-[200px]">
          {/* Editable collection name */}
          {editingName ? (
            <div className="flex items-center gap-2 mb-[6px]">
              <input
                type="text"
                value={nameDraft}
                onChange={(e) => onNameDraftChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && nameDraft.trim()) {
                    onSaveName();
                  }
                  if (e.key === "Escape") onCancelEditName();
                }}
                autoFocus
                className="px-[10px] py-1 rounded-[8px] border border-line-bright bg-[var(--bg-card)] text-white text-lg font-bold outline-none font-[Syne,sans-serif]"
              />
              <button
                onClick={() => {
                  if (nameDraft.trim()) {
                    onSaveName();
                  }
                }}
                disabled={savingCollection || !nameDraft.trim()}
                className="px-3 py-1 rounded-[8px] border-none bg-[image:var(--gradient-brand)] text-white text-[0.72rem] font-bold cursor-pointer"
              >
                {savingCollection ? "Saving..." : "Save"}
              </button>
              <button
                onClick={() => onCancelEditName()}
                className="px-[10px] py-1 rounded-[8px] border border-line-bright bg-transparent text-muted text-[0.72rem] font-bold cursor-pointer"
              >
                Cancel
              </button>
            </div>
          ) : (
            <h2
              onClick={() => { onStartEditName(); }}
              className="font-[Syne,sans-serif] text-[1.1rem] font-bold text-white mb-1 cursor-pointer inline-flex items-center gap-[6px]"
              title="Click to edit name"
            >
              {collection.name}
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="opacity-60">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
            </h2>
          )}

          {/* Editable description */}
          {editingDesc ? (
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={descDraft}
                onChange={(e) => onDescDraftChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    onSaveDesc();
                  }
                  if (e.key === "Escape") onCancelEditDesc();
                }}
                autoFocus
                placeholder="Add a description..."
                className="px-[10px] py-1 rounded-[8px] border border-line-bright bg-[var(--bg-card)] text-muted text-sm outline-none flex-1 max-w-[320px]"
              />
              <button
                onClick={() => {
                  onSaveDesc();
                }}
                disabled={savingCollection}
                className="px-3 py-1 rounded-[8px] border-none bg-[image:var(--gradient-brand)] text-white text-[0.72rem] font-bold cursor-pointer"
              >
                {savingCollection ? "Saving..." : "Save"}
              </button>
              <button
                onClick={() => onCancelEditDesc()}
                className="px-[10px] py-1 rounded-[8px] border border-line-bright bg-transparent text-muted text-[0.72rem] font-bold cursor-pointer"
              >
                Cancel
              </button>
            </div>
          ) : (
            <p
              onClick={() => { onStartEditDesc(); }}
              className="text-sm text-muted m-0 cursor-pointer inline-flex items-center gap-1"
              title="Click to edit description"
            >
              {collection.description || "No description"}
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="opacity-40">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
            </p>
          )}
        </div>

        {/* Share + Delete actions */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Share toggle */}
          <button
            onClick={() => { onToggleShare(); }}
            disabled={togglingShare}
            className={`px-[14px] py-2 rounded-md text-[0.75rem] font-bold inline-flex items-center gap-[6px] transition-all duration-150 ${togglingShare ? "cursor-not-allowed" : "cursor-pointer"} ${collection.shared ? "border border-brand bg-brand-soft text-brand" : "border border-line-bright bg-transparent text-muted"}`}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" />
              <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
              <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
            </svg>
            {togglingShare ? "Updating..." : collection.shared ? "Shared" : "Share"}
          </button>

          {/* Copy share link */}
          {collection.shared && collection.shareToken && (
            <button
              onClick={() => onCopyShareLink()}
              className="px-[14px] py-2 rounded-md border border-line-bright bg-transparent text-brand text-[0.75rem] font-bold cursor-pointer inline-flex items-center gap-[6px]"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
              </svg>
              Copy Link
            </button>
          )}

          {/* Delete collection */}
          {!collection.isDefault && (
            <>
              {confirmDelete ? (
                <div className="flex items-center gap-[6px]">
                  <span className="text-[0.72rem] text-danger font-semibold">Delete this collection?</span>
                  <button
                    onClick={() => { onConfirmDelete(); }}
                    disabled={deletingCollection}
                    className={`px-3 py-[6px] rounded-[8px] border-none bg-danger text-white text-[0.72rem] font-bold ${deletingCollection ? "cursor-not-allowed" : "cursor-pointer"}`}
                  >
                    {deletingCollection ? "Deleting..." : "Yes, Delete"}
                  </button>
                  <button
                    onClick={() => onCancelDelete()}
                    className="px-3 py-[6px] rounded-[8px] border border-line-bright bg-transparent text-muted text-[0.72rem] font-bold cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => onRequestDelete()}
                  className="px-[14px] py-2 rounded-md border border-danger-glow bg-danger-soft text-danger text-[0.75rem] font-bold cursor-pointer inline-flex items-center gap-[6px]"
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <polyline points="3 6 5 6 21 6" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  </svg>
                  Delete
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
