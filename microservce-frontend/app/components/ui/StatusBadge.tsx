"use client";

type ColorDef = { bg: string; border: string; color: string };

export const LIFECYCLE_COLORS: Record<string, ColorDef> = {
  DRAFT: { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" },
  ACTIVE: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  PAUSED: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  ARCHIVED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

export const APPROVAL_COLORS: Record<string, ColorDef> = {
  NOT_REQUIRED: { bg: "rgba(255,255,255,0.03)", border: "var(--line)", color: "var(--muted)" },
  PENDING: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  APPROVED: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  REJECTED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

export const ORDER_STATUS_COLORS: Record<string, ColorDef> = {
  PENDING: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  CONFIRMED: { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" },
  PROCESSING: { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" },
  SHIPPED: { bg: "var(--accent-soft)", border: "rgba(124,58,237,0.3)", color: "var(--accent)" },
  DELIVERED: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  CANCELLED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
  REFUNDED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
  FAILED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

export const VENDOR_STATUS_COLORS: Record<string, ColorDef> = {
  ACTIVE: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  INACTIVE: { bg: "rgba(255,255,255,0.03)", border: "var(--line)", color: "var(--muted)" },
  SUSPENDED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
  PENDING_DELETION: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  DELETED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
  PENDING_VERIFICATION: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  VERIFIED: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
};

export const ACTIVE_INACTIVE_COLORS: Record<string, ColorDef> = {
  Active: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  Inactive: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

export const VERIFICATION_COLORS: Record<string, ColorDef> = {
  UNVERIFIED: { bg: "rgba(255,255,255,0.03)", border: "var(--line)", color: "var(--muted)" },
  PENDING_VERIFICATION: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  VERIFIED: { bg: "var(--success-soft)", border: "rgba(34,197,94,0.3)", color: "var(--success)" },
  VERIFICATION_REJECTED: { bg: "var(--danger-soft)", border: "rgba(239,68,68,0.25)", color: "#f87171" },
};

type Props = { value: string; colorMap?: Record<string, ColorDef> };

export default function StatusBadge({ value, colorMap }: Props) {
  const map = colorMap || {};
  const c = map[value] || { bg: "transparent", border: "var(--line)", color: "var(--muted)" };
  return (
    <span style={{ display: "inline-block", padding: "2px 8px", borderRadius: 999, fontSize: "0.68rem", fontWeight: 700, border: `1px solid ${c.border}`, color: c.color, background: c.bg, whiteSpace: "nowrap" }}>
      {value.replace(/_/g, " ")}
    </span>
  );
}
