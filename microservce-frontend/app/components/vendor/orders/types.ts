"use client";

/* ── Types ── */

export type VendorOrder = {
  id: string;
  orderId: string;
  vendorId: string;
  status: string;
  itemCount: number;
  quantity: number;
  orderTotal: number;
  currency: string;
  discountAmount: number | null;
  shippingAmount: number | null;
  platformFee: number | null;
  payoutAmount: number | null;
  trackingNumber: string | null;
  carrierCode: string | null;
  estimatedDeliveryDate: string | null;
  refundAmount: number | null;
  createdAt: string;
  updatedAt: string;
};

export type PageResponse = {
  content: VendorOrder[];
  totalPages?: number;
  totalElements?: number;
  number?: number;
  size?: number;
  page?: { number?: number; size?: number; totalElements?: number; totalPages?: number };
};

export type VendorOrderItem = {
  id: string;
  productId: string;
  productSku: string;
  item: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  discountAmount: number | null;
  fulfilledQuantity: number | null;
  cancelledQuantity: number | null;
};

export type ShippingAddress = {
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
};

export type VendorOrderDetail = VendorOrder & {
  items: VendorOrderItem[];
  shippingAddress: ShippingAddress | null;
};

/* ── Helpers ── */

export function extractErrorMessage(error: unknown): string {
  if (error && typeof error === "object") {
    const err = error as Record<string, unknown>;
    const response = err.response as Record<string, unknown> | undefined;
    if (response) {
      const data = response.data as Record<string, unknown> | undefined;
      if (data) {
        if (typeof data.error === "string" && data.error.trim()) return data.error;
        if (typeof data.message === "string" && data.message.trim()) return data.message;
      }
    }
    if (typeof err.message === "string" && err.message.trim()) return err.message;
  }
  if (error instanceof Error) return error.message;
  return "An unexpected error occurred";
}

export function formatCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined) return "--";
  return `$${value.toFixed(2)}`;
}

export function truncateId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) + "..." : id;
}

/* ── Status filter options ── */

export const STATUS_OPTIONS = [
  "ALL",
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "CANCELLED",
  "REFUNDED",
  "FAILED",
  "PARTIALLY_FULFILLED",
  "ON_HOLD",
  "RETURNED",
];
