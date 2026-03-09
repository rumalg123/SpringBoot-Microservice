"use client";

import type { AxiosInstance } from "axios";
import Image from "next/image";
import { useState, type ChangeEvent } from "react";
import StarRating from "./StarRating";
import toast from "react-hot-toast";
import { getErrorMessage } from "../../../lib/error";
import { resolveImageUrl } from "../../../lib/image";
import type { ApiRequestConfig } from "../../../lib/apiClient";

const MAX_REVIEW_IMAGES = 5;
const MAX_REVIEW_IMAGE_BYTES = 1_048_576;
const ALLOWED_IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

type ReviewImageUploadResponse = {
  keys: string[];
};

type Props = {
  productId: string;
  apiClient: AxiosInstance;
  onSuccess: () => void;
  onCancel: () => void;
};

export default function ReviewForm({ productId, apiClient, onSuccess, onCancel }: Props) {
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState("");
  const [comment, setComment] = useState("");
  const [images, setImages] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [uploadingImages, setUploadingImages] = useState(false);
  const [uploadInputKey, setUploadInputKey] = useState(0);

  const canSubmit = rating >= 1 && comment.trim().length > 0 && !submitting && !uploadingImages;

  const createUniqueIdempotencyKey = () => {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  };

  const validateUploadableFiles = (files: FileList | null): File[] => {
    const selected = Array.from(files ?? []);
    if (selected.length === 0) {
      return [];
    }
    const remainingSlots = MAX_REVIEW_IMAGES - images.length;
    if (remainingSlots <= 0) {
      throw new Error(`You can upload up to ${MAX_REVIEW_IMAGES} images per review`);
    }
    if (selected.length > remainingSlots) {
      throw new Error(`You can only add ${remainingSlots} more image${remainingSlots === 1 ? "" : "s"}`);
    }
    for (const file of selected) {
      if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        throw new Error(`${file.name} must be a JPG, PNG, or WEBP image`);
      }
      if (file.size > MAX_REVIEW_IMAGE_BYTES) {
        throw new Error(`${file.name} exceeds the 1MB limit`);
      }
    }
    return selected;
  };

  const handleImageUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    let files: File[] = [];
    try {
      files = validateUploadableFiles(event.target.files);
      if (files.length === 0) {
        return;
      }
      setUploadingImages(true);
      const formData = new FormData();
      for (const file of files) {
        formData.append("files", file);
      }
      const response = await apiClient.post<ReviewImageUploadResponse>("/reviews/me/images", formData, {
        idempotencyKey: createUniqueIdempotencyKey(),
      } as ApiRequestConfig);
      const uploadedKeys = Array.isArray(response.data?.keys) ? response.data.keys : [];
      setImages((current) => [...current, ...uploadedKeys].slice(0, MAX_REVIEW_IMAGES));
      toast.success(`${uploadedKeys.length} image${uploadedKeys.length === 1 ? "" : "s"} uploaded`);
    } catch (error: unknown) {
      toast.error(getErrorMessage(error) || "Failed to upload review images");
    } finally {
      setUploadingImages(false);
      event.target.value = "";
      setUploadInputKey((value) => value + 1);
    }
  };

  const removeImage = async (imageKey: string) => {
    try {
      await apiClient.delete("/reviews/me/images", {
        data: { keys: [imageKey] },
        idempotencyKey: createUniqueIdempotencyKey(),
      } as ApiRequestConfig);
    } catch (error: unknown) {
      toast.error(getErrorMessage(error) || "Failed to remove review image");
      return;
    }
    setImages((current) => current.filter((value) => value !== imageKey));
  };

  const handleCancel = async () => {
    if (images.length > 0) {
      try {
        await apiClient.delete("/reviews/me/images", {
          data: { keys: images },
          idempotencyKey: createUniqueIdempotencyKey(),
        } as ApiRequestConfig);
      } catch {
      }
    }
    setImages([]);
    onCancel();
  };

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      await apiClient.post("/reviews/me", {
        productId,
        rating,
        title: title.trim() || null,
        comment: comment.trim(),
        images,
      });
      toast.success("Review submitted successfully!");
      onSuccess();
    } catch (error: unknown) {
      toast.error(getErrorMessage(error) || "Failed to submit review");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="p-6 rounded-lg border border-line-bright bg-surface">
      <h3 className="text-lg font-bold text-ink mb-4">Write a Review</h3>

      {/* Star rating */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Rating
        </label>
        <StarRating value={rating} onChange={setRating} size={28} interactive />
        {rating === 0 && <p className="text-[0.72rem] text-muted mt-1">Click to rate</p>}
      </div>

      {/* Title */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Title (optional)
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder="Summarize your experience"
          className="form-input w-full px-[14px] py-[10px] rounded-md border border-line bg-bg text-ink text-[0.85rem]"
        />
      </div>

      {/* Comment */}
      <div className="mb-4">
        <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted mb-[6px]">
          Your Review
        </label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={2000}
          rows={4}
          placeholder="Share your experience with this product..."
          className="w-full px-[14px] py-[10px] rounded-md border border-line bg-bg text-ink text-[0.85rem] resize-y font-[inherit]"
        />
        <p className="text-[0.68rem] text-muted mt-1 text-right">{comment.length}/2000</p>
      </div>

      <div className="mb-4">
        <div className="mb-[6px] flex items-center justify-between gap-3">
          <label className="block text-[0.72rem] font-bold uppercase tracking-[0.1em] text-muted">
            Photos
          </label>
          <label className={`rounded-md border border-line px-3 py-2 text-[0.78rem] font-semibold ${uploadingImages || images.length >= MAX_REVIEW_IMAGES ? "cursor-not-allowed text-muted" : "cursor-pointer text-ink-light"}`}>
            {uploadingImages ? "Uploading..." : "Add Photos"}
            <input
              key={uploadInputKey}
              type="file"
              accept="image/png,image/jpeg,image/webp"
              multiple
              className="hidden"
              disabled={uploadingImages || submitting || images.length >= MAX_REVIEW_IMAGES}
              onChange={(event) => {
                void handleImageUpload(event);
              }}
            />
          </label>
        </div>
        <p className="text-[0.68rem] text-muted mb-2">Up to 5 images. JPG, PNG, or WEBP only. 1MB max each.</p>
        {images.length === 0 ? (
          <p className="text-[0.72rem] text-muted">No images added.</p>
        ) : (
          <div className="grid gap-2">
            {images.map((imageKey, index) => {
              const imageUrl = resolveImageUrl(imageKey);
              return (
                <div
                  key={imageKey}
                  className="flex items-center gap-3 rounded-lg border border-line bg-bg px-3 py-2"
                >
                  <div className="relative h-14 w-14 overflow-hidden rounded-md border border-line-bright bg-surface">
                    {imageUrl ? (
                      <Image
                        src={imageUrl}
                        alt={`Review image ${index + 1}`}
                        fill
                        sizes="56px"
                        className="object-cover"
                        unoptimized
                      />
                    ) : (
                      <div className="grid h-full w-full place-items-center text-[0.65rem] text-muted">IMG</div>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[0.78rem] font-semibold text-ink-light">{imageKey}</p>
                    <p className="text-[0.68rem] text-muted">Photo {index + 1}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      void removeImage(imageKey);
                    }}
                    disabled={uploadingImages || submitting}
                    className="rounded-md border border-line px-3 py-[8px] text-[0.72rem] font-bold text-muted disabled:cursor-not-allowed"
                  >
                    Remove
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex gap-[10px] justify-end">
        <button
          onClick={() => {
            void handleCancel();
          }}
          disabled={submitting || uploadingImages}
          className="px-5 py-[10px] rounded-md border border-line bg-transparent text-ink-light text-[0.85rem] font-semibold cursor-pointer"
        >
          Cancel
        </button>
        <button
          onClick={handleSubmit}
          disabled={!canSubmit}
          className={`px-5 py-[10px] rounded-md border-none text-[0.85rem] font-bold ${canSubmit ? "bg-[image:var(--gradient-brand)] text-white cursor-pointer" : "bg-line-bright text-muted cursor-not-allowed"}`}
        >
          {submitting ? "Submitting..." : "Submit Review"}
        </button>
      </div>
    </div>
  );
}
