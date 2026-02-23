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
    reasonEnabled?: boolean;
    reasonLabel?: string;
    reasonPlaceholder?: string;
    reasonValue?: string;
    onReasonChange?: (value: string) => void;
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
    reasonEnabled = false,
    reasonLabel = "Reason (optional)",
    reasonPlaceholder = "Add a reason for audit history...",
    reasonValue = "",
    onReasonChange,
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
                {reasonEnabled && (
                    <div style={{ marginBottom: "0.9rem" }}>
                        <label
                            style={{
                                display: "block",
                                marginBottom: "0.35rem",
                                fontSize: "0.8rem",
                                color: "var(--ink-light, #dfe7ff)",
                                fontWeight: 600,
                            }}
                        >
                            {reasonLabel}
                        </label>
                        <textarea
                            value={reasonValue}
                            onChange={(e) => onReasonChange?.(e.target.value)}
                            placeholder={reasonPlaceholder}
                            rows={3}
                            style={{
                                width: "100%",
                                borderRadius: 10,
                                border: "1px solid var(--line, rgba(255,255,255,0.12))",
                                background: "var(--surface-2, rgba(255,255,255,0.04))",
                                color: "var(--ink, #fff)",
                                padding: "0.65rem 0.75rem",
                                resize: "vertical",
                            }}
                        />
                    </div>
                )}
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
