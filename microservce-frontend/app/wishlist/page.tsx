"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { emitCartUpdate, emitWishlistUpdate } from "../../lib/navEvents";
import type { WishlistItem, WishlistCollection } from "../../lib/types/wishlist";
import { emptyWishlist } from "../../lib/types/wishlist";
import EmptyState from "../components/ui/EmptyState";
import CollectionTabs from "../components/wishlist/CollectionTabs";
import CollectionHeader from "../components/wishlist/CollectionHeader";
import WishlistItemCard from "../components/wishlist/WishlistItemCard";
import {
  useWishlist,
  useWishlistCollections,
  useRemoveWishlistItem,
  useMoveWishlistItemToCart,
  useClearWishlist,
  useCreateCollection,
  useUpdateCollection,
  useDeleteCollection,
  useToggleCollectionShare,
  useUpdateItemNote,
  useMoveItemToCollection,
} from "../../lib/hooks/queries/useWishlist";

export default function WishlistPage() {
  const router = useRouter();
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = session;

  // --- React Query: data fetching ---
  const { data: wishlist = emptyWishlist, isLoading: wishlistLoading } = useWishlist(apiClient);
  const { data: collections = [] } = useWishlistCollections(apiClient);

  // --- React Query: mutations ---
  const removeItemMutation = useRemoveWishlistItem(apiClient);
  const moveToCartMutation = useMoveWishlistItemToCart(apiClient);
  const clearWishlistMutation = useClearWishlist(apiClient);
  const createCollectionMutation = useCreateCollection(apiClient);
  const updateCollectionMutation = useUpdateCollection(apiClient);
  const deleteCollectionMutation = useDeleteCollection(apiClient);
  const toggleShareMutation = useToggleCollectionShare(apiClient);
  const updateItemNoteMutation = useUpdateItemNote(apiClient);
  const moveItemToCollectionMutation = useMoveItemToCollection(apiClient);

  // --- Item-level tracking state ---
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [movingItemId, setMovingItemId] = useState<string | null>(null);

  // Collection state
  const [activeCollectionId, setActiveCollectionId] = useState<string | null>(null);
  const [creatingCollection, setCreatingCollection] = useState(false);
  const [newCollectionName, setNewCollectionName] = useState("");

  // Item note state
  const [editingNote, setEditingNote] = useState<string | null>(null);
  const [noteText, setNoteText] = useState("");

  // Collection inline-edit state
  const [editingCollectionName, setEditingCollectionName] = useState(false);
  const [editingCollectionDesc, setEditingCollectionDesc] = useState(false);
  const [collectionNameDraft, setCollectionNameDraft] = useState("");
  const [collectionDescDraft, setCollectionDescDraft] = useState("");

  const newCollectionInputRef = useRef<HTMLInputElement>(null);

  // --- Auth redirect ---
  useEffect(() => {
    if (sessionStatus === "ready" && !isAuthenticated) {
      router.replace("/");
    }
  }, [sessionStatus, isAuthenticated, router]);

  // --- Derived busy state ---
  const busy =
    removingItemId !== null ||
    movingItemId !== null ||
    clearWishlistMutation.isPending ||
    wishlistLoading;

  // --- Status message ---
  const status = wishlistLoading
    ? "Loading wishlist..."
    : "Wishlist ready.";

  // --- Mutation wrappers ---

  const removeItem = (itemId: string) => {
    if (!apiClient || busy) return;
    setRemovingItemId(itemId);
    removeItemMutation.mutate(itemId, {
      onSuccess: () => {
        toast.success("Removed from wishlist");
        emitWishlistUpdate();
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to remove wishlist item");
      },
      onSettled: () => setRemovingItemId(null),
    });
  };

  const clearWishlist = () => {
    if (!apiClient || busy || wishlist.itemCount === 0) return;
    clearWishlistMutation.mutate(undefined, {
      onSuccess: () => {
        setActiveCollectionId(null);
        emitWishlistUpdate();
        toast.success("Wishlist cleared");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to clear wishlist");
      },
    });
  };

  const moveToCart = (item: WishlistItem) => {
    if (!apiClient || busy) return;
    if ((item.productType || "").toUpperCase() === "PARENT") {
      toast.error("Parent products cannot be bought directly. Select a variation.");
      return;
    }
    setMovingItemId(item.id);
    moveToCartMutation.mutate(item, {
      onSuccess: () => {
        toast.success("Moved to cart");
        emitCartUpdate();
        emitWishlistUpdate();
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to move item to cart");
      },
      onSettled: () => setMovingItemId(null),
    });
  };

  const moveToCollection = (item: WishlistItem, collectionId: string) => {
    if (!apiClient || busy) return;
    setMovingItemId(item.id);
    moveItemToCollectionMutation.mutate({ itemId: item.id, collectionId }, {
      onSuccess: () => {
        toast.success("Moved to collection");
        emitWishlistUpdate();
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to move item");
      },
      onSettled: () => setMovingItemId(null),
    });
  };

  // --- Collection handlers ---

  const createCollection = () => {
    if (!apiClient || !newCollectionName.trim()) return;
    setCreatingCollection(true);
    createCollectionMutation.mutate(newCollectionName.trim(), {
      onSuccess: () => {
        setNewCollectionName("");
        toast.success("Collection created");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to create collection");
      },
      onSettled: () => setCreatingCollection(false),
    });
  };

  const updateCollection = (id: string, body: { name?: string; description?: string }) => {
    if (!apiClient) return;
    updateCollectionMutation.mutate({ collectionId: id, body }, {
      onSuccess: () => {
        toast.success("Collection updated");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to update collection");
      },
    });
  };

  const deleteCollection = (id: string) => {
    if (!apiClient) return;
    deleteCollectionMutation.mutate(id, {
      onSuccess: () => {
        setActiveCollectionId(null);
        toast.success("Collection deleted");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to delete collection");
      },
    });
  };

  const toggleShare = (collection: WishlistCollection) => {
    if (!apiClient) return;
    toggleShareMutation.mutate({ collectionId: collection.id, shared: !collection.shared }, {
      onSuccess: () => {
        toast.success(collection.shared ? "Sharing disabled" : "Sharing enabled");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to toggle sharing");
      },
    });
  };

  const copyShareLink = (shareToken: string) => {
    const link = `${window.location.origin}/wishlist/shared/${shareToken}`;
    void navigator.clipboard.writeText(link);
    toast.success("Share link copied to clipboard");
  };

  // --- Note handlers ---

  const saveNote = (itemId: string) => {
    if (!apiClient) return;
    updateItemNoteMutation.mutate({ itemId, note: noteText }, {
      onSuccess: () => {
        setEditingNote(null);
        setNoteText("");
        toast.success("Note saved");
      },
      onError: (err) => {
        toast.error(err instanceof Error ? err.message : "Failed to save note");
      },
    });
  };

  // --- Derived data ---

  const activeCollection = activeCollectionId
    ? collections.find((c) => c.id === activeCollectionId) ?? null
    : null;

  const displayedItems: WishlistItem[] = activeCollection
    ? activeCollection.items
    : wishlist.items;

  const displayedItemCount = activeCollection
    ? activeCollection.itemCount
    : wishlist.itemCount;

  // --- Confirm delete state ---
  const [confirmDelete, setConfirmDelete] = useState(false);

  // --- Show new collection input ---
  const [showNewCollectionInput, setShowNewCollectionInput] = useState(false);

  useEffect(() => {
    if (showNewCollectionInput && newCollectionInputRef.current) {
      newCollectionInputRef.current.focus();
    }
  }, [showNewCollectionInput]);

  if (sessionStatus === "loading" || sessionStatus === "idle") {
    return (
      <div className="grid min-h-screen place-items-center bg-bg">
        <div className="text-center">
          <div className="spinner-lg" />
          <p className="mt-4 text-base text-muted">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <div className="min-h-screen bg-bg">
      <AppNav
        email={(profile?.email as string) || ""}
        isSuperAdmin={session.isSuperAdmin}
        isVendorAdmin={session.isVendorAdmin}
        canViewAdmin={canViewAdmin}
        canManageAdminOrders={session.canManageAdminOrders}
        canManageAdminProducts={session.canManageAdminProducts}
        canManageAdminCategories={session.canManageAdminCategories}
        canManageAdminVendors={session.canManageAdminVendors}
        canManageAdminPosters={session.canManageAdminPosters}
        apiClient={apiClient}
        emailVerified={emailVerified}
        onLogout={() => { void logout(); }}
      />

      <main className="mx-auto max-w-7xl px-4 py-4">
        <nav className="breadcrumb">
          <Link href="/">Home</Link>
          <span className="breadcrumb-sep">&rsaquo;</span>
          <span className="breadcrumb-current">Wishlist</span>
        </nav>

        {/* Header */}
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="m-0 font-[Syne,sans-serif] text-[1.75rem] font-extrabold text-white">
              My Wishlist
            </h1>
            <p className="mt-1 text-sm text-muted">
              {status} {wishlist.itemCount > 0 && `\u00b7 ${wishlist.itemCount} item${wishlist.itemCount !== 1 ? "s" : ""}`}
            </p>
          </div>
          <div className="flex gap-2.5">
            <Link
              href="/products"
              className="rounded-md border border-line-bright bg-brand-soft px-[18px] py-[9px] text-sm font-bold text-brand no-underline"
            >
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearWishlist(); }}
              disabled={busy || wishlist.itemCount === 0}
              className="rounded-md border border-danger-glow bg-danger-soft px-[18px] py-[9px] text-sm font-bold text-danger disabled:cursor-not-allowed disabled:opacity-40"
            >
              {clearWishlistMutation.isPending ? "Clearing..." : "Clear Wishlist"}
            </button>
          </div>
        </div>

        {/* Collection Tabs */}
        <CollectionTabs
          collections={collections}
          activeCollectionId={activeCollectionId}
          onSelectCollection={(id) => { setActiveCollectionId(id); setConfirmDelete(false); setEditingCollectionName(false); setEditingCollectionDesc(false); }}
          wishlistItemCount={wishlist.itemCount}
          newCollectionName={newCollectionName}
          onNewCollectionNameChange={setNewCollectionName}
          showNewCollectionInput={showNewCollectionInput}
          onToggleNewInput={setShowNewCollectionInput}
          creatingCollection={creatingCollection}
          onCreate={createCollection}
          newCollectionInputRef={newCollectionInputRef}
        />

        {/* Collection Management Panel */}
        {activeCollection && (
          <CollectionHeader
            collection={activeCollection}
            editingName={editingCollectionName}
            nameDraft={collectionNameDraft}
            onNameDraftChange={setCollectionNameDraft}
            onStartEditName={() => { setEditingCollectionName(true); setCollectionNameDraft(activeCollection.name); }}
            onCancelEditName={() => setEditingCollectionName(false)}
            onSaveName={() => {
              if (collectionNameDraft.trim()) {
                void updateCollection(activeCollection.id, { name: collectionNameDraft.trim() });
                setEditingCollectionName(false);
              }
            }}
            editingDesc={editingCollectionDesc}
            descDraft={collectionDescDraft}
            onDescDraftChange={setCollectionDescDraft}
            onStartEditDesc={() => { setEditingCollectionDesc(true); setCollectionDescDraft(activeCollection.description || ""); }}
            onCancelEditDesc={() => setEditingCollectionDesc(false)}
            onSaveDesc={() => {
              void updateCollection(activeCollection.id, { description: collectionDescDraft.trim() || undefined });
              setEditingCollectionDesc(false);
            }}
            savingCollection={updateCollectionMutation.isPending}
            togglingShare={toggleShareMutation.isPending}
            onToggleShare={() => { void toggleShare(activeCollection); }}
            onCopyShareLink={() => copyShareLink(activeCollection.shareToken!)}
            confirmDelete={confirmDelete}
            onConfirmDelete={() => { void deleteCollection(activeCollection.id); setConfirmDelete(false); }}
            onCancelDelete={() => setConfirmDelete(false)}
            onRequestDelete={() => setConfirmDelete(true)}
            deletingCollection={deleteCollectionMutation.isPending}
          />
        )}

        {/* Empty state */}
        {displayedItemCount === 0 && !wishlistLoading && (
          <EmptyState
            icon={
              <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 21s-6.7-4.35-9.33-8.08C.8 10.23 1.2 6.7 4.02 4.82A5.42 5.42 0 0 1 12 6.09a5.42 5.42 0 0 1 7.98-1.27c2.82 1.88 3.22 5.41 1.35 8.1C18.7 16.65 12 21 12 21z" />
              </svg>
            }
            title={activeCollection ? "This collection is empty" : "Wishlist is empty"}
            description={activeCollection ? "Add items to this collection from your wishlist." : "Save products here and revisit them anytime."}
            actionLabel="Browse Products"
            onAction={() => router.push("/products")}
          />
        )}

        {/* Wishlist Items */}
        <div className="flex flex-col gap-3">
          {displayedItems.map((item) => (
            <WishlistItemCard
              key={item.id}
              item={item}
              busy={busy}
              isEditingNote={editingNote === item.id}
              noteText={noteText}
              onNoteTextChange={setNoteText}
              onStartEditNote={() => { setEditingNote(item.id); setNoteText(item.note || ""); }}
              onCancelEditNote={() => { setEditingNote(null); setNoteText(""); }}
              onSaveNote={() => { void saveNote(item.id); }}
              savingNote={updateItemNoteMutation.isPending}
              movingItemId={movingItemId}
              removingItemId={removingItemId}
              onMoveToCart={() => { void moveToCart(item); }}
              onRemove={() => { void removeItem(item.id); }}
              collections={collections}
              activeCollection={activeCollection}
              onMoveToCollection={(collectionId) => { void moveToCollection(item, collectionId); }}
            />
          ))}
        </div>
      </main>

      <Footer />
    </div>
  );
}
