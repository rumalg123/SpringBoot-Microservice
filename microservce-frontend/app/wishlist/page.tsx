"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import toast from "react-hot-toast";
import AppNav from "../components/AppNav";
import Footer from "../components/Footer";
import { useAuthSession } from "../../lib/authSession";
import { emitCartUpdate, emitWishlistUpdate } from "../../lib/navEvents";
import { money } from "../../lib/format";
import type { WishlistItem, WishlistResponse, WishlistCollection, WishlistApiRaw } from "../../lib/types/wishlist";
import EmptyState from "../components/ui/EmptyState";
import { emptyWishlist } from "../../lib/types/wishlist";

export default function WishlistPage() {
  const router = useRouter();
  const session = useAuthSession();
  const { status: sessionStatus, isAuthenticated, profile, logout, canViewAdmin, apiClient, emailVerified } = session;

  const [wishlist, setWishlist] = useState<WishlistResponse>(emptyWishlist);
  const [status, setStatus] = useState("Loading wishlist...");
  const [loading, setLoading] = useState(false);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);
  const [movingItemId, setMovingItemId] = useState<string | null>(null);
  const [clearing, setClearing] = useState(false);

  // Collection state
  const [collections, setCollections] = useState<WishlistCollection[]>([]);
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
  const [deletingCollection, setDeletingCollection] = useState(false);
  const [togglingShare, setTogglingShare] = useState(false);
  const [savingNote, setSavingNote] = useState(false);
  const [savingCollection, setSavingCollection] = useState(false);

  const newCollectionInputRef = useRef<HTMLInputElement>(null);

  const loadWishlist = useCallback(async () => {
    if (!apiClient) return;
    setLoading(true);
    try {
      const res = await apiClient.get<WishlistApiRaw>("/wishlist/me");
      const raw = res.data;
      const items: WishlistItem[] = raw.content ?? raw.items ?? [];
      const itemCount = Number(raw.page?.totalElements ?? raw.itemCount ?? items.length);
      setWishlist({ keycloakId: raw.keycloakId ?? "", items, itemCount });
      setStatus("Wishlist ready.");
    } catch (err) {
      setStatus(err instanceof Error ? err.message : "Failed to load wishlist");
      setWishlist(emptyWishlist);
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  const loadCollections = useCallback(async () => {
    if (!apiClient) return;
    try {
      const res = await apiClient.get<WishlistCollection[]>("/wishlist/me/collections");
      const data = res.data ?? [];
      setCollections(data);
    } catch {
      // silently ignore - collections are optional enhancement
    }
  }, [apiClient]);

  useEffect(() => {
    if (sessionStatus !== "ready") return;
    if (!isAuthenticated) { router.replace("/"); return; }
    void loadWishlist();
    void loadCollections();
  }, [sessionStatus, isAuthenticated, router, loadWishlist, loadCollections]);

  const busy = removingItemId !== null || movingItemId !== null || clearing || loading;

  const removeItem = async (itemId: string) => {
    if (!apiClient || busy) return;
    setRemovingItemId(itemId);
    try {
      await apiClient.delete(`/wishlist/me/items/${itemId}`);
      setWishlist((old) => {
        const nextItems = old.items.filter((item) => item.id !== itemId);
        return { ...old, items: nextItems, itemCount: nextItems.length };
      });
      // Also remove from collections local state
      setCollections((old) =>
        old.map((c) => ({
          ...c,
          items: c.items.filter((i) => i.id !== itemId),
          itemCount: c.items.filter((i) => i.id !== itemId).length,
        }))
      );
      toast.success("Removed from wishlist");
      emitWishlistUpdate();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to remove wishlist item");
    } finally { setRemovingItemId(null); }
  };

  const clearWishlist = async () => {
    if (!apiClient || busy || wishlist.itemCount === 0) return;
    setClearing(true);
    try {
      await apiClient.delete("/wishlist/me");
      setWishlist(emptyWishlist);
      setCollections([]);
      setActiveCollectionId(null);
      emitWishlistUpdate();
      toast.success("Wishlist cleared");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to clear wishlist");
    } finally { setClearing(false); }
  };

  const moveToCart = async (item: WishlistItem) => {
    if (!apiClient || busy) return;
    if ((item.productType || "").toUpperCase() === "PARENT") {
      toast.error("Parent products cannot be bought directly. Select a variation.");
      return;
    }
    setMovingItemId(item.id);
    try {
      await apiClient.post(`/wishlist/me/items/${item.id}/move-to-cart`);
      setWishlist((old) => {
        const nextItems = old.items.filter((e) => e.id !== item.id);
        return { ...old, items: nextItems, itemCount: nextItems.length };
      });
      setCollections((old) =>
        old.map((c) => ({
          ...c,
          items: c.items.filter((i) => i.id !== item.id),
          itemCount: c.items.filter((i) => i.id !== item.id).length,
        }))
      );
      toast.success("Moved to cart");
      emitCartUpdate();
      emitWishlistUpdate();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item to cart");
    } finally { setMovingItemId(null); }
  };

  const moveToCollection = async (item: WishlistItem, collectionId: string) => {
    if (!apiClient || busy) return;
    setMovingItemId(item.id);
    try {
      await apiClient.post("/wishlist/me/items", { productId: item.productId, collectionId });
      await apiClient.delete(`/wishlist/me/items/${item.id}`);
      await Promise.all([loadWishlist(), loadCollections()]);
      toast.success("Moved to collection");
      emitWishlistUpdate();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to move item");
    } finally { setMovingItemId(null); }
  };

  // --- Collection handlers ---

  const createCollection = async () => {
    if (!apiClient || !newCollectionName.trim()) return;
    setCreatingCollection(true);
    try {
      const res = await apiClient.post<WishlistCollection>("/wishlist/me/collections", { name: newCollectionName.trim() });
      const created = res.data;
      setCollections((old) => [...old, created]);
      setNewCollectionName("");
      toast.success("Collection created");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create collection");
    } finally { setCreatingCollection(false); }
  };

  const updateCollection = async (id: string, body: { name?: string; description?: string }) => {
    if (!apiClient) return;
    setSavingCollection(true);
    try {
      const res = await apiClient.put<WishlistCollection>(`/wishlist/me/collections/${id}`, body);
      const updated = res.data;
      setCollections((old) => old.map((c) => (c.id === id ? updated : c)));
      toast.success("Collection updated");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update collection");
    } finally { setSavingCollection(false); }
  };

  const deleteCollection = async (id: string) => {
    if (!apiClient) return;
    setDeletingCollection(true);
    try {
      await apiClient.delete(`/wishlist/me/collections/${id}`);
      setCollections((old) => old.filter((c) => c.id !== id));
      setActiveCollectionId(null);
      toast.success("Collection deleted");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to delete collection");
    } finally { setDeletingCollection(false); }
  };

  const toggleShare = async (collection: WishlistCollection) => {
    if (!apiClient) return;
    setTogglingShare(true);
    try {
      if (collection.shared) {
        await apiClient.delete(`/wishlist/me/collections/${collection.id}/share`);
        setCollections((old) =>
          old.map((c) => (c.id === collection.id ? { ...c, shared: false, shareToken: null } : c))
        );
        toast.success("Sharing disabled");
      } else {
        const res = await apiClient.post<WishlistCollection>(`/wishlist/me/collections/${collection.id}/share`);
        const updated = res.data;
        setCollections((old) => old.map((c) => (c.id === collection.id ? updated : c)));
        toast.success("Sharing enabled");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to toggle sharing");
    } finally { setTogglingShare(false); }
  };

  const copyShareLink = (shareToken: string) => {
    const link = `${window.location.origin}/wishlist/shared/${shareToken}`;
    void navigator.clipboard.writeText(link);
    toast.success("Share link copied to clipboard");
  };

  // --- Note handlers ---

  const saveNote = async (itemId: string) => {
    if (!apiClient) return;
    setSavingNote(true);
    try {
      const res = await apiClient.put(`/wishlist/me/items/${itemId}/note`, { note: noteText });
      const updatedItem = res.data as WishlistItem;
      // Update in flat wishlist
      setWishlist((old) => ({
        ...old,
        items: old.items.map((i) => (i.id === itemId ? { ...i, note: updatedItem.note } : i)),
      }));
      // Update in collections
      setCollections((old) =>
        old.map((c) => ({
          ...c,
          items: c.items.map((i) => (i.id === itemId ? { ...i, note: updatedItem.note } : i)),
        }))
      );
      setEditingNote(null);
      setNoteText("");
      toast.success("Note saved");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save note");
    } finally { setSavingNote(false); }
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
      <div style={{ minHeight: "100vh", background: "var(--bg)", display: "grid", placeItems: "center" }}>
        <div style={{ textAlign: "center" }}>
          <div className="spinner-lg" />
          <p style={{ marginTop: "16px", color: "var(--muted)", fontSize: "0.875rem" }}>Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) return null;

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
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
            <h1 style={{ fontFamily: "'Syne', sans-serif", fontSize: "1.75rem", fontWeight: 800, color: "#fff", margin: 0 }}>
              My Wishlist
            </h1>
            <p style={{ marginTop: "4px", fontSize: "0.8rem", color: "var(--muted)" }}>
              {status} {wishlist.itemCount > 0 && `\u00b7 ${wishlist.itemCount} item${wishlist.itemCount !== 1 ? "s" : ""}`}
            </p>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <Link
              href="/products"
              className="no-underline"
              style={{ padding: "9px 18px", borderRadius: "10px", border: "1px solid var(--line-bright)", color: "var(--brand)", background: "var(--brand-soft)", fontSize: "0.8rem", fontWeight: 700 }}
            >
              Continue Shopping
            </Link>
            <button
              onClick={() => { void clearWishlist(); }}
              disabled={busy || wishlist.itemCount === 0}
              style={{
                padding: "9px 18px", borderRadius: "10px",
                border: "1px solid var(--danger-glow)", background: "var(--danger-soft)",
                color: "var(--danger)", fontSize: "0.8rem", fontWeight: 700,
                cursor: busy || wishlist.itemCount === 0 ? "not-allowed" : "pointer",
                opacity: wishlist.itemCount === 0 ? 0.4 : 1,
              }}
            >
              {clearing ? "Clearing..." : "Clear Wishlist"}
            </button>
          </div>
        </div>

        {/* Collection Tabs */}
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px", marginBottom: "16px" }}>
          {/* All Items tab */}
          <button
            onClick={() => { setActiveCollectionId(null); setConfirmDelete(false); }}
            style={{
              padding: "8px 16px",
              borderRadius: "10px",
              border: activeCollectionId === null ? "1px solid var(--brand)" : "1px solid var(--line-bright)",
              background: activeCollectionId === null ? "var(--brand-soft)" : "transparent",
              color: activeCollectionId === null ? "var(--brand)" : "var(--muted)",
              fontSize: "0.8rem",
              fontWeight: 700,
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              transition: "all 0.15s ease",
            }}
          >
            All Items
            <span
              style={{
                fontSize: "0.65rem",
                fontWeight: 700,
                padding: "1px 7px",
                borderRadius: "20px",
                background: activeCollectionId === null ? "var(--brand)" : "var(--line-bright)",
                color: activeCollectionId === null ? "#fff" : "var(--muted)",
              }}
            >
              {wishlist.itemCount}
            </span>
          </button>

          {/* Collection tabs */}
          {collections.map((col) => (
            <button
              key={col.id}
              onClick={() => { setActiveCollectionId(col.id); setConfirmDelete(false); setEditingCollectionName(false); setEditingCollectionDesc(false); }}
              style={{
                padding: "8px 16px",
                borderRadius: "10px",
                border: activeCollectionId === col.id ? "1px solid var(--brand)" : "1px solid var(--line-bright)",
                background: activeCollectionId === col.id ? "var(--brand-soft)" : "transparent",
                color: activeCollectionId === col.id ? "var(--brand)" : "var(--muted)",
                fontSize: "0.8rem",
                fontWeight: 700,
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: "6px",
                transition: "all 0.15s ease",
              }}
            >
              {col.name}
              <span
                style={{
                  fontSize: "0.65rem",
                  fontWeight: 700,
                  padding: "1px 7px",
                  borderRadius: "20px",
                  background: activeCollectionId === col.id ? "var(--brand)" : "var(--line-bright)",
                  color: activeCollectionId === col.id ? "#fff" : "var(--muted)",
                }}
              >
                {col.itemCount}
              </span>
            </button>
          ))}

          {/* New collection button / input */}
          {!showNewCollectionInput ? (
            <button
              onClick={() => { setShowNewCollectionInput(true); }}
              style={{
                padding: "8px 12px",
                borderRadius: "10px",
                border: "1px dashed var(--line-bright)",
                background: "transparent",
                color: "var(--muted)",
                fontSize: "0.8rem",
                fontWeight: 700,
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: "4px",
                transition: "all 0.15s ease",
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--brand)"; e.currentTarget.style.color = "var(--brand)"; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--line-bright)"; e.currentTarget.style.color = "var(--muted)"; }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              New Collection
            </button>
          ) : (
            <div style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
              <input
                ref={newCollectionInputRef}
                type="text"
                value={newCollectionName}
                onChange={(e) => setNewCollectionName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && newCollectionName.trim()) void createCollection();
                  if (e.key === "Escape") { setShowNewCollectionInput(false); setNewCollectionName(""); }
                }}
                placeholder="Collection name..."
                style={{
                  padding: "7px 12px",
                  borderRadius: "10px",
                  border: "1px solid var(--line-bright)",
                  background: "var(--bg-card)",
                  color: "#fff",
                  fontSize: "0.8rem",
                  outline: "none",
                  width: "160px",
                }}
              />
              <button
                onClick={() => { void createCollection(); }}
                disabled={creatingCollection || !newCollectionName.trim()}
                style={{
                  padding: "7px 14px",
                  borderRadius: "10px",
                  border: "none",
                  background: !newCollectionName.trim() ? "var(--line-bright)" : "var(--gradient-brand)",
                  color: "#fff",
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  cursor: !newCollectionName.trim() ? "not-allowed" : "pointer",
                }}
              >
                {creatingCollection ? "Creating..." : "Add"}
              </button>
              <button
                onClick={() => { setShowNewCollectionInput(false); setNewCollectionName(""); }}
                style={{
                  padding: "7px 10px",
                  borderRadius: "10px",
                  border: "1px solid var(--line-bright)",
                  background: "transparent",
                  color: "var(--muted)",
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                Cancel
              </button>
            </div>
          )}
        </div>

        {/* Collection Management Panel */}
        {activeCollection && (
          <div
            className="glass-card"
            style={{
              border: "1px solid var(--line-bright)",
              borderRadius: "16px",
              padding: "18px 20px",
              marginBottom: "16px",
            }}
          >
            <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
              <div style={{ flex: 1, minWidth: "200px" }}>
                {/* Editable collection name */}
                {editingCollectionName ? (
                  <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "6px" }}>
                    <input
                      type="text"
                      value={collectionNameDraft}
                      onChange={(e) => setCollectionNameDraft(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && collectionNameDraft.trim()) {
                          void updateCollection(activeCollection.id, { name: collectionNameDraft.trim() });
                          setEditingCollectionName(false);
                        }
                        if (e.key === "Escape") setEditingCollectionName(false);
                      }}
                      autoFocus
                      style={{
                        padding: "4px 10px",
                        borderRadius: "8px",
                        border: "1px solid var(--line-bright)",
                        background: "var(--bg-card)",
                        color: "#fff",
                        fontSize: "1rem",
                        fontWeight: 700,
                        outline: "none",
                        fontFamily: "'Syne', sans-serif",
                      }}
                    />
                    <button
                      onClick={() => {
                        if (collectionNameDraft.trim()) {
                          void updateCollection(activeCollection.id, { name: collectionNameDraft.trim() });
                          setEditingCollectionName(false);
                        }
                      }}
                      disabled={savingCollection || !collectionNameDraft.trim()}
                      style={{
                        padding: "4px 12px", borderRadius: "8px", border: "none",
                        background: "var(--gradient-brand)", color: "#fff",
                        fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                      }}
                    >
                      {savingCollection ? "Saving..." : "Save"}
                    </button>
                    <button
                      onClick={() => setEditingCollectionName(false)}
                      style={{
                        padding: "4px 10px", borderRadius: "8px",
                        border: "1px solid var(--line-bright)", background: "transparent",
                        color: "var(--muted)", fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <h2
                    onClick={() => { setEditingCollectionName(true); setCollectionNameDraft(activeCollection.name); }}
                    style={{
                      fontFamily: "'Syne', sans-serif", fontSize: "1.1rem", fontWeight: 700, color: "#fff",
                      margin: "0 0 4px 0", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: "6px",
                    }}
                    title="Click to edit name"
                  >
                    {activeCollection.name}
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.6 }}>
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                  </h2>
                )}

                {/* Editable description */}
                {editingCollectionDesc ? (
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <input
                      type="text"
                      value={collectionDescDraft}
                      onChange={(e) => setCollectionDescDraft(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          void updateCollection(activeCollection.id, { description: collectionDescDraft.trim() || undefined });
                          setEditingCollectionDesc(false);
                        }
                        if (e.key === "Escape") setEditingCollectionDesc(false);
                      }}
                      autoFocus
                      placeholder="Add a description..."
                      style={{
                        padding: "4px 10px",
                        borderRadius: "8px",
                        border: "1px solid var(--line-bright)",
                        background: "var(--bg-card)",
                        color: "var(--muted)",
                        fontSize: "0.8rem",
                        outline: "none",
                        flex: 1,
                        maxWidth: "320px",
                      }}
                    />
                    <button
                      onClick={() => {
                        void updateCollection(activeCollection.id, { description: collectionDescDraft.trim() || undefined });
                        setEditingCollectionDesc(false);
                      }}
                      disabled={savingCollection}
                      style={{
                        padding: "4px 12px", borderRadius: "8px", border: "none",
                        background: "var(--gradient-brand)", color: "#fff",
                        fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                      }}
                    >
                      {savingCollection ? "Saving..." : "Save"}
                    </button>
                    <button
                      onClick={() => setEditingCollectionDesc(false)}
                      style={{
                        padding: "4px 10px", borderRadius: "8px",
                        border: "1px solid var(--line-bright)", background: "transparent",
                        color: "var(--muted)", fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <p
                    onClick={() => { setEditingCollectionDesc(true); setCollectionDescDraft(activeCollection.description || ""); }}
                    style={{
                      fontSize: "0.8rem", color: "var(--muted)", margin: 0, cursor: "pointer",
                      display: "inline-flex", alignItems: "center", gap: "4px",
                    }}
                    title="Click to edit description"
                  >
                    {activeCollection.description || "No description"}
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.4 }}>
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                  </p>
                )}
              </div>

              {/* Share + Delete actions */}
              <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                {/* Share toggle */}
                <button
                  onClick={() => { void toggleShare(activeCollection); }}
                  disabled={togglingShare}
                  style={{
                    padding: "8px 14px",
                    borderRadius: "10px",
                    border: activeCollection.shared ? "1px solid var(--brand)" : "1px solid var(--line-bright)",
                    background: activeCollection.shared ? "var(--brand-soft)" : "transparent",
                    color: activeCollection.shared ? "var(--brand)" : "var(--muted)",
                    fontSize: "0.75rem",
                    fontWeight: 700,
                    cursor: togglingShare ? "not-allowed" : "pointer",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "6px",
                    transition: "all 0.15s ease",
                  }}
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" />
                    <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
                    <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
                  </svg>
                  {togglingShare ? "Updating..." : activeCollection.shared ? "Shared" : "Share"}
                </button>

                {/* Copy share link */}
                {activeCollection.shared && activeCollection.shareToken && (
                  <button
                    onClick={() => copyShareLink(activeCollection.shareToken!)}
                    style={{
                      padding: "8px 14px",
                      borderRadius: "10px",
                      border: "1px solid var(--line-bright)",
                      background: "transparent",
                      color: "var(--brand)",
                      fontSize: "0.75rem",
                      fontWeight: 700,
                      cursor: "pointer",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                    }}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                    </svg>
                    Copy Link
                  </button>
                )}

                {/* Delete collection */}
                {!activeCollection.isDefault && (
                  <>
                    {confirmDelete ? (
                      <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                        <span style={{ fontSize: "0.72rem", color: "var(--danger)", fontWeight: 600 }}>Delete this collection?</span>
                        <button
                          onClick={() => { void deleteCollection(activeCollection.id); setConfirmDelete(false); }}
                          disabled={deletingCollection}
                          style={{
                            padding: "6px 12px", borderRadius: "8px", border: "none",
                            background: "var(--danger)", color: "#fff",
                            fontSize: "0.72rem", fontWeight: 700,
                            cursor: deletingCollection ? "not-allowed" : "pointer",
                          }}
                        >
                          {deletingCollection ? "Deleting..." : "Yes, Delete"}
                        </button>
                        <button
                          onClick={() => setConfirmDelete(false)}
                          style={{
                            padding: "6px 12px", borderRadius: "8px",
                            border: "1px solid var(--line-bright)", background: "transparent",
                            color: "var(--muted)", fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setConfirmDelete(true)}
                        style={{
                          padding: "8px 14px",
                          borderRadius: "10px",
                          border: "1px solid var(--danger-glow)",
                          background: "var(--danger-soft)",
                          color: "var(--danger)",
                          fontSize: "0.75rem",
                          fontWeight: 700,
                          cursor: "pointer",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                        }}
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
        )}

        {/* Empty state */}
        {displayedItemCount === 0 && !loading && (
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
        <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
          {displayedItems.map((item) => {
            const isParent = (item.productType || "").toUpperCase() === "PARENT";
            const isEditingThisNote = editingNote === item.id;
            return (
              <article
                key={item.id}
                className="animate-rise glass-card"
                style={{
                  border: "1px solid var(--line-bright)",
                  borderRadius: "16px",
                  padding: "18px 20px",
                }}
              >
                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: "12px" }}>
                  <div style={{ flex: 1, minWidth: "200px" }}>
                    <Link
                      href={`/products/${encodeURIComponent(item.productSlug)}`}
                      className="no-underline"
                      style={{ fontWeight: 700, color: "#fff", fontSize: "0.95rem" }}
                      onMouseEnter={(e) => { e.currentTarget.style.color = "var(--brand)"; }}
                      onMouseLeave={(e) => { e.currentTarget.style.color = "#fff"; }}
                    >
                      {item.productName}
                    </Link>

                    {/* Display existing note below item name */}
                    {item.note && !isEditingThisNote && (
                      <p style={{ margin: "4px 0 0 0", fontSize: "0.78rem", color: "var(--muted)", fontStyle: "italic" }}>
                        {item.note}
                      </p>
                    )}

                    <div style={{ marginTop: "6px", display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                      <span
                        style={{
                          fontSize: "0.65rem", fontWeight: 700, letterSpacing: "0.1em", textTransform: "uppercase",
                          padding: "2px 10px", borderRadius: "20px",
                          background: "var(--brand-soft)", border: "1px solid var(--line-bright)", color: "var(--brand)",
                        }}
                      >
                        {item.productType}
                      </span>
                      <span style={{ fontSize: "0.875rem", fontWeight: 700, color: "var(--brand)" }}>
                        {money(item.sellingPriceSnapshot)}
                      </span>
                    </div>

                    {/* Inline note editor */}
                    {isEditingThisNote && (
                      <div style={{ marginTop: "8px", display: "flex", alignItems: "center", gap: "6px" }}>
                        <input
                          type="text"
                          value={noteText}
                          onChange={(e) => setNoteText(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") void saveNote(item.id);
                            if (e.key === "Escape") { setEditingNote(null); setNoteText(""); }
                          }}
                          autoFocus
                          placeholder="Add a note..."
                          style={{
                            padding: "6px 10px",
                            borderRadius: "8px",
                            border: "1px solid var(--line-bright)",
                            background: "var(--bg-card)",
                            color: "#fff",
                            fontSize: "0.78rem",
                            outline: "none",
                            flex: 1,
                            maxWidth: "300px",
                          }}
                        />
                        <button
                          onClick={() => { void saveNote(item.id); }}
                          disabled={savingNote}
                          style={{
                            padding: "6px 12px", borderRadius: "8px", border: "none",
                            background: "var(--gradient-brand)", color: "#fff",
                            fontSize: "0.72rem", fontWeight: 700,
                            cursor: savingNote ? "not-allowed" : "pointer",
                          }}
                        >
                          {savingNote ? "Saving..." : "Save"}
                        </button>
                        <button
                          onClick={() => { setEditingNote(null); setNoteText(""); }}
                          style={{
                            padding: "6px 10px", borderRadius: "8px",
                            border: "1px solid var(--line-bright)", background: "transparent",
                            color: "var(--muted)", fontSize: "0.72rem", fontWeight: 700, cursor: "pointer",
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    )}
                  </div>

                  <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "8px" }}>
                    {/* Note icon button */}
                    <button
                      onClick={() => {
                        if (isEditingThisNote) {
                          setEditingNote(null);
                          setNoteText("");
                        } else {
                          setEditingNote(item.id);
                          setNoteText(item.note || "");
                        }
                      }}
                      title={item.note ? "Edit note" : "Add note"}
                      style={{
                        padding: "8px 10px",
                        borderRadius: "10px",
                        border: "1px solid var(--line-bright)",
                        background: isEditingThisNote ? "var(--brand-soft)" : "transparent",
                        color: item.note ? "var(--brand)" : "var(--muted)",
                        fontSize: "0.78rem",
                        cursor: "pointer",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        transition: "all 0.15s ease",
                      }}
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
                      onClick={() => { void moveToCart(item); }}
                      disabled={busy || isParent}
                      style={{
                        padding: "8px 16px", borderRadius: "10px", border: "none",
                        background: busy || isParent ? "var(--line-bright)" : "var(--gradient-brand)",
                        color: "#fff", fontSize: "0.78rem", fontWeight: 700,
                        cursor: busy || isParent ? "not-allowed" : "pointer",
                        display: "inline-flex", alignItems: "center", gap: "6px",
                      }}
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><line x1="3" y1="6" x2="21" y2="6" />
                        <path d="M16 10a4 4 0 0 1-8 0" />
                      </svg>
                      {movingItemId === item.id ? "Moving..." : isParent ? "Select Variation" : "Move to Cart"}
                    </button>
                    <button
                      onClick={() => { void removeItem(item.id); }}
                      disabled={busy}
                      style={{
                        padding: "8px 14px", borderRadius: "10px",
                        border: "1px solid var(--danger-glow)", background: "var(--danger-soft)",
                        color: "var(--danger)", fontSize: "0.72rem", fontWeight: 700,
                        cursor: busy ? "not-allowed" : "pointer", opacity: busy ? 0.5 : 1,
                      }}
                    >
                      {removingItemId === item.id ? "Removing..." : "Remove"}
                    </button>
                    {!activeCollection && collections.length > 0 && (
                      <select
                        value=""
                        onChange={(e) => { if (e.target.value) void moveToCollection(item, e.target.value); }}
                        disabled={!!movingItemId || !!removingItemId}
                        className="filter-select"
                        style={{ minWidth: "auto", fontSize: "0.72rem" }}
                      >
                        <option value="">Move to collection…</option>
                        {collections.map((c) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </main>

      <Footer />
    </div>
  );
}
