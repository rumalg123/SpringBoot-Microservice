"use client";

import { useState } from "react";
import toast from "react-hot-toast";
import type { BulkOperationResult, Category } from "./types";

type BulkCategoryReassignModalProps = {
  open: boolean;
  selectedProductIds: string[];
  categories: Category[];
  apiClient: { post: (url: string, data: unknown) => Promise<{ data: unknown }> } | null;
  onClose: () => void;
  onComplete: () => void;
};

export default function BulkCategoryReassignModal({
  open,
  selectedProductIds,
  categories,
  apiClient,
  onClose,
  onComplete,
}: BulkCategoryReassignModalProps) {
  const [targetCategoryId, setTargetCategoryId] = useState("");
  const [reassigning, setReassigning] = useState(false);

  if (!open) return null;

  const handleReassign = async () => {
    if (!apiClient || selectedProductIds.length === 0) return;
    if (!targetCategoryId.trim()) {
      toast.error("Select a target category");
      return;
    }
    setReassigning(true);
    try {
      const res = await apiClient.post("/admin/products/bulk-category-reassign", {
        productIds: selectedProductIds,
        targetCategoryId: targetCategoryId.trim(),
      });
      const result = res.data as BulkOperationResult;
      const msg = `Category reassign: ${result.successCount}/${result.totalRequested} succeeded`;
      if (result.failureCount > 0) {
        toast.error(`${msg}, ${result.failureCount} failed`);
        result.errors?.slice(0, 5).forEach((err) => toast.error(err, { duration: 6000 }));
      } else {
        toast.success(msg);
      }
      setTargetCategoryId("");
      onComplete();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Bulk category reassign failed";
      toast.error(message);
    } finally {
      setReassigning(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[1000] grid place-items-center bg-black/60 backdrop-blur-[4px]"
      onClick={() => { if (!reassigning) onClose(); }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-surface border border-line rounded-lg px-7 py-6 w-full max-w-[420px] text-ink"
      >
        <h3 className="text-lg font-bold mb-1 text-ink">Bulk Reassign Category</h3>
        <p className="text-sm text-muted mb-4">
          Reassign {selectedProductIds.length} selected product(s) to a new category.
        </p>

        <label className="block text-sm font-semibold text-ink-light mb-1">Target Category *</label>
        <select
          value={targetCategoryId}
          onChange={(e) => setTargetCategoryId(e.target.value)}
          className="form-select w-full mb-5"
        >
          <option value="">-- Select a category --</option>
          {categories.filter((c) => c.type === "PARENT").length > 0 && (
            <optgroup label="Main Categories">
              {categories
                .filter((c) => c.type === "PARENT")
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
            </optgroup>
          )}
          {categories.filter((c) => c.type === "SUB").length > 0 && (
            <optgroup label="Sub Categories">
              {categories
                .filter((c) => c.type === "SUB")
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
            </optgroup>
          )}
        </select>

        <div className="flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onClose}
            disabled={reassigning}
            className="btn-outline text-base px-4 py-2"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleReassign(); }}
            disabled={reassigning || !targetCategoryId.trim()}
            className="btn-primary text-base px-4 py-2 inline-flex items-center gap-1.5"
          >
            {reassigning && (
              <span className="inline-block w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {reassigning ? "Reassigning..." : "Reassign Category"}
          </button>
        </div>
      </div>
    </div>
  );
}
