/* ── Inventory shared types ── */

export type Warehouse = {
  id: string;
  name: string;
  description: string | null;
  vendorId: string | null;
  warehouseType: string;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  countryCode: string | null;
  contactName: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type StockItem = {
  id: string;
  productId: string;
  productName?: string;
  vendorId: string;
  warehouseId: string;
  warehouseName: string;
  sku: string | null;
  quantityOnHand: number;
  quantityReserved: number;
  quantityAvailable: number;
  lowStockThreshold: number;
  backorderable: boolean;
  stockStatus: string;
  createdAt: string;
  updatedAt: string;
};

export type StockMovement = {
  id: string;
  stockItemId: string;
  productId: string;
  productName?: string;
  productSku?: string;
  warehouseId: string;
  warehouseName?: string;
  movementType: string;
  quantityChange: number;
  quantityBefore: number;
  quantityAfter: number;
  referenceType: string | null;
  referenceId: string | null;
  actorType: string | null;
  actorId: string | null;
  actorName?: string;
  note: string | null;
  createdAt: string;
};

export type StockReservation = {
  id: string;
  orderId: string;
  productId: string;
  productName?: string;
  productSku?: string;
  stockItemId: string;
  warehouseId: string;
  warehouseName?: string;
  quantityReserved: number;
  status: string;
  reservedAt: string;
  expiresAt: string;
  confirmedAt: string | null;
  releasedAt: string | null;
  releaseReason: string | null;
  createdAt: string;
};

export type WarehouseFormData = {
  id?: string;
  name: string;
  description: string;
  vendorId: string;
  warehouseType: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
};

export type StockItemFormData = {
  id?: string;
  productId: string;
  vendorId: string;
  warehouseId: string;
  sku: string;
  quantityOnHand: number | "";
  lowStockThreshold: number | "";
  backorderable: boolean;
};

export const EMPTY_WAREHOUSE_FORM: WarehouseFormData = {
  name: "",
  description: "",
  vendorId: "",
  warehouseType: "VENDOR_OWNED",
  addressLine1: "",
  addressLine2: "",
  city: "",
  state: "",
  postalCode: "",
  countryCode: "US",
  contactName: "",
  contactPhone: "",
  contactEmail: "",
};

export const EMPTY_STOCK_FORM: StockItemFormData = {
  productId: "",
  vendorId: "",
  warehouseId: "",
  sku: "",
  quantityOnHand: 0,
  lowStockThreshold: 10,
  backorderable: false,
};

export type PagedData<T> = {
  content: T[];
  totalPages?: number;
  totalElements?: number;
  number?: number;
  page?: { number?: number; totalPages?: number; totalElements?: number };
};

export function resolvePage<T>(data: PagedData<T>) {
  return {
    content: data.content ?? [],
    page: data.page?.number ?? data.number ?? 0,
    totalPages: data.page?.totalPages ?? data.totalPages ?? 0,
    totalElements: data.page?.totalElements ?? data.totalElements ?? 0,
  };
}
