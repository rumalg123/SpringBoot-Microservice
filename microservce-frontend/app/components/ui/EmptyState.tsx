"use client";
import { ReactNode } from "react";

type Props = {
  icon?: ReactNode;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
};

export default function EmptyState({ icon, title, description, actionLabel, onAction }: Props) {
  return (
    <div style={{ textAlign: "center", padding: "60px 24px", color: "var(--muted)" }}>
      {icon && <div style={{ fontSize: "2.5rem", marginBottom: 12 }}>{icon}</div>}
      <h3 style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--ink-light)", marginBottom: 6 }}>{title}</h3>
      {description && <p style={{ fontSize: "0.85rem", maxWidth: 400, margin: "0 auto 16px", lineHeight: 1.5 }}>{description}</p>}
      {actionLabel && onAction && (
        <button type="button" onClick={onAction} className="btn-primary" style={{ fontSize: "0.82rem", padding: "8px 20px" }}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}
