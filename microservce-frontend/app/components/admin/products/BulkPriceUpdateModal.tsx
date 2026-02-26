"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import type { BulkOperationResult } from "./types";

type BulkPriceUpdateModalProps = {
  open: boolean;
  selectedProductIds: string[];
  apiClient: { post: (url: string, data: unknown) => Promise<{ data: unknown }> } | null;
  onClose: () => void;
  onComplete: () => void;
};

export default function BulkPriceUpdateModal({
  open,
  selectedProductIds,
  apiClient,
  onClose,
  onComplete,
}: BulkPriceUpdateModalProps) {
  const [regularPrice, setRegularPrice] = useState("");
  const [discountedPrice, setDiscountedPrice] = useState("");
  const [updating, setUpdating] = useState(false);

  if (!open) return null;

  const handleUpdate = async () => {
    if (!apiClient || selectedProductIds.length === 0) return;
    const regPrice = Number(regularPrice);
    if (!Number.isFinite(regPrice) || regPrice <= 0) {
      toast.error("Regular price must be greater than 0");
      return;
    }
    const discPrice = discountedPrice.trim() ? Number(discountedPrice) : undefined;
    if (discPrice !== undefined && (!Number.isFinite(discPrice) || discPrice < 0)) {
      toast.error("Discounted price must be 0 or greater");
      return;
    }
    if (discPrice !== undefined && discPrice > regPrice) {
      toast.error("Discounted price cannot exceed regular price");
      return;
    }
    setUpdating(true);
    try {
      const items = selectedProductIds.map((productId) => ({
        productId,
        regularPrice: regPrice,
        ...(discPrice !== undefined ? { discountedPrice: discPrice } : {}),
      }));
      const res = await apiClient.post("/admin/products/bulk-price-update", { items });
      const result = res.data as BulkOperationResult;
      const msg = `Price update: ${result.successCount}/${result.totalRequested} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${msg}, ${result.failureCount} failed`);
        result.errors?.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
      } else {
        toast.success(msg);
      }
      setRegularPrice("");
      setDiscountedPrice("");
      onComplete();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Bulk price update failed";
      toast.error(message);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[1000] grid place-items-center bg-black/60 backdrop-blur-[4px]"
      onClick={() => { if (!updating) onClose(); }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-surface border border-line rounded-lg px-7 py-6 w-full max-w-[420px] text-ink"
      >
        <h3 className="text-lg font-bold mb-1 text-ink">Bulk Price Update</h3>
        <p className="text-sm text-muted mb-4">
          Update prices for {selectedProductIds.length} selected product(s).
        </p>

        <label className="block text-sm font-semibold text-ink-light mb-1">Regular Price *</label>
        <input
          type="number"
          min="0.01"
          step="0.01"
          value={regularPrice}
          onChange={(e) => setRegularPrice(e.target.value)}
          placeholder="e.g. 29.99"
          className="form-input w-full mb-3"
        />

        <label className="block text-sm font-semibold text-ink-light mb-1">Discounted Price (optional)</label>
        <input
          type="number"
          min="0"
          step="0.01"
          value={discountedPrice}
          onChange={(e) => setDiscountedPrice(e.target.value)}
          placeholder="Leave blank to keep unchanged"
          className="form-input w-full mb-5"
        />

        <div className="flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onClose}
            disabled={updating}
            className="btn-outline text-base px-4 py-2"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleUpdate(); }}
            disabled={updating || !regularPrice.trim()}
            className="btn-primary text-base px-4 py-2 inline-flex items-center gap-1.5"
          >
            {updating && (
              <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {updating ? "Updating..." : "Update Prices"}
          </button>
        </div>
      </div>
    </div>
  );
}
