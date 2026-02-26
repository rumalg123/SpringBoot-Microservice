export type OrderAddress = {
  sourceAddressId: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
};

export type OrderItem = {
  id: string;
  productId: string;
  vendorId: string;
  productSku: string;
  item: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  discountAmount: number;
  fulfilledQuantity: number | null;
  cancelledQuantity: number | null;
};

export type Order = {
  id: string;
  orderNumber?: string;
  customerId: string;
  /** Display-friendly summary string, e.g. "Product A + 2 more". Not an iterable — see OrderDetail.items for the full list. */
  item: string;
  quantity: number;
  itemCount: number;
  orderTotal: number;
  currency: string;
  subtotal: number;
  lineDiscountTotal: number;
  cartDiscountTotal: number;
  shippingAmount: number;
  shippingDiscountTotal: number;
  totalDiscount: number;
  couponCode: string | null;
  couponReservationId: string | null;
  paymentId: string | null;
  paymentMethod: string | null;
  paymentGatewayRef: string | null;
  paidAt: string | null;
  customerNote: string | null;
  status: string;
  expiresAt: string | null;
  refundAmount: number | null;
  refundReason: string | null;
  refundInitiatedAt: string | null;
  refundCompletedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CustomerSummary = {
  id: string;
  name: string;
  email: string;
};

export type OrderDetail = {
  id: string;
  orderNumber?: string;
  customerId: string;
  item: string;
  quantity: number;
  itemCount: number;
  orderTotal: number;
  currency: string;
  subtotal: number;
  lineDiscountTotal: number;
  cartDiscountTotal: number;
  shippingAmount: number;
  shippingDiscountTotal: number;
  totalDiscount: number;
  couponCode: string | null;
  couponReservationId: string | null;
  paymentId: string | null;
  paymentMethod: string | null;
  paymentGatewayRef: string | null;
  paidAt: string | null;
  customerNote: string | null;
  adminNote: string | null;
  status: string;
  expiresAt: string | null;
  refundAmount: number | null;
  refundReason: string | null;
  refundInitiatedAt: string | null;
  refundCompletedAt: string | null;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
  shippingAddress: OrderAddress | null;
  billingAddress: OrderAddress | null;
  customer: CustomerSummary | null;
  warnings: string[];
};

export type OrderStatusAudit = {
  id: string;
  fromStatus: string | null;
  toStatus: string;
  actorSub: string;
  actorRoles: string | null;
  actorType: string | null;
  changeSource: string | null;
  note: string | null;
  createdAt: string;
};

export type VendorOrder = {
  id: string;
  orderId: string;
  vendorId: string;
  vendorName?: string;
  status: string;
  itemCount: number;
  quantity: number;
  orderTotal: number;
  currency: string;
  discountAmount: number;
  shippingAmount: number;
  platformFee: number;
  payoutAmount: number;
  trackingNumber: string | null;
  trackingUrl: string | null;
  carrierCode: string | null;
  estimatedDeliveryDate: string | null;
  refundAmount: number | null;
  refundedAmount: number | null;
  refundedQuantity: number | null;
  refundReason: string | null;
  refundInitiatedAt: string | null;
  refundCompletedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CancelOrderRequest = {
  reason?: string;
};

export type InvoiceLineItem = {
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
};

export type InvoiceResponse = {
  orderId: string;
  invoiceNumber: string;
  customerName: string;
  customerEmail: string;
  items: InvoiceLineItem[];
  subtotal: number;
  discountTotal: number;
  shippingTotal: number;
  grandTotal: number;
  currency: string;
  issuedAt: string;
};
