"use client";

export type AdminOrder = {
  id: string;
  customerId: string;
  item: string;
  quantity: number;
  itemCount: number;
  orderTotal: number;
  status: string;
  createdAt: string;
};

export type AdminOrdersPageResponse = {
  content: AdminOrder[];
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
  page?: { number?: number; size?: number; totalElements?: number; totalPages?: number };
};

export type OrderStatusAudit = {
  id: string;
  fromStatus: string | null;
  toStatus: string;
  actorSub: string | null;
  actorRoles: string | null;
  actorType: string;
  changeSource: string;
  note: string | null;
  createdAt: string;
};

export type VendorOrder = {
  id: string;
  orderId: string;
  vendorId: string;
  status: string;
  itemCount: number;
  quantity: number;
  orderTotal: number;
  createdAt: string;
};

export type VendorOrderStatusAudit = {
  id: string;
  fromStatus: string | null;
  toStatus: string;
  actorSub: string | null;
  actorRoles: string | null;
  actorType: string;
  changeSource: string;
  note: string | null;
  createdAt: string;
};

