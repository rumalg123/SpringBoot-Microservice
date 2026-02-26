"use client";

export const ORDER_STATUSES = [
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "RETURN_REQUESTED",
  "REFUND_PENDING",
  "REFUNDED",
  "CANCELLED",
  "CLOSED",
] as const;

export type OrderStatusValue = typeof ORDER_STATUSES[number];

export const ORDER_STATUS_TRANSITIONS: Record<OrderStatusValue, OrderStatusValue[]> = {
  PENDING: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED", "RETURN_REQUESTED"],
  DELIVERED: ["RETURN_REQUESTED", "CLOSED"],
  RETURN_REQUESTED: ["REFUND_PENDING", "CLOSED"],
  REFUND_PENDING: ["REFUNDED", "CLOSED"],
  REFUNDED: [],
  CANCELLED: [],
  CLOSED: [],
};

export type StatusChipStyle = {
  bg: string;
  border: string;
  color: string;
};

export function getStatusChip(status: string): StatusChipStyle {
  const normalized = (status || "").toUpperCase();
  if (["DELIVERED", "CLOSED"].includes(normalized)) {
    return { bg: "var(--success-soft)", border: "var(--success-glow)", color: "var(--success)" };
  }
  if (["CANCELLED", "REFUNDED"].includes(normalized)) {
    return { bg: "var(--danger-soft)", border: "var(--danger-glow)", color: "var(--danger)" };
  }
  if (["REFUND_PENDING", "RETURN_REQUESTED"].includes(normalized)) {
    return { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" };
  }
  return { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" };
}

export function normalizeOrderStatus(status: string | null | undefined): OrderStatusValue {
  const normalized = String(status || "PENDING").trim().toUpperCase();
  return (ORDER_STATUSES as readonly string[]).includes(normalized)
    ? (normalized as OrderStatusValue)
    : "PENDING";
}

export function canTransitionOrderStatus(current: string | null | undefined, next: string | null | undefined) {
  const from = normalizeOrderStatus(current);
  const to = normalizeOrderStatus(next);
  if (from === to) return true;
  return ORDER_STATUS_TRANSITIONS[from].includes(to);
}

export function getAllowedNextStatuses(current: string | null | undefined): OrderStatusValue[] {
  const from = normalizeOrderStatus(current);
  return [from, ...ORDER_STATUS_TRANSITIONS[from]];
}

