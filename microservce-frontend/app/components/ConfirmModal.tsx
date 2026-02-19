"use client";

import { useEffect, useRef } from "react";

type ConfirmModalProps = {
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    danger?: boolean;
    loading?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
};

/**
 * A reusable confirmation modal for destructive or important actions.
 * Traps focus and supports Escape to close.
 */
export default function ConfirmModal({
    open,
    title,
    message,
    confirmLabel = "Confirm",
    cancelLabel = "Cancel",
    danger = false,
    loading = false,
    onConfirm,
    onCancel,
}: ConfirmModalProps) {
    const confirmRef = useRef<HTMLButtonElement>(null);

    // Focus the confirm button when modal opens
    useEffect(() => {
        if (open && confirmRef.current) {
            confirmRef.current.focus();
        }
    }, [open]);

    // Close on Escape key
    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === "Escape") onCancel();
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [open, onCancel]);

    if (!open) return null;

    return (
        <div className="confirm-modal-overlay" onClick={onCancel}>
            <div
                className="confirm-modal"
                onClick={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="confirm-modal-title"
            >
                <h3 id="confirm-modal-title" className="confirm-modal-title">
                    {title}
                </h3>
                <p className="confirm-modal-message">{message}</p>
                <div className="confirm-modal-actions">
                    <button
                        onClick={onCancel}
                        disabled={loading}
                        className="btn-outline confirm-modal-cancel"
                    >
                        {cancelLabel}
                    </button>
                    <button
                        ref={confirmRef}
                        onClick={onConfirm}
                        disabled={loading}
                        className={`confirm-modal-confirm ${danger ? "confirm-modal-danger" : "btn-primary"}`}
                    >
                        {loading && (
                            <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent mr-2" />
                        )}
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
