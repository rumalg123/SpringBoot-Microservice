export type CartItem = {
  id: string;
  productId: string;
  productSlug: string;
  productName: string;
  productSku: string;
  mainImage: string | null;
  categoryIds: string[];
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  savedForLater: boolean;
};

export type CartResponse = {
  id: string | null;
  keycloakId: string;
  items: CartItem[];
  savedForLaterItems: CartItem[];
  itemCount: number;
  totalQuantity: number;
  subtotal: number;
  note: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AppliedPromotion = {
  promotionId: string;
  promotionName: string;
  applicationLevel: string;
  benefitType: string;
  priority: number;
  exclusive: boolean;
  discountAmount: number;
};

export type RejectedPromotion = {
  promotionId: string;
  promotionName: string;
  reason: string;
};

export type CheckoutPreviewResponse = {
  itemCount: number;
  totalQuantity: number;
  couponCode: string | null;
  subtotal: number;
  lineDiscountTotal: number;
  cartDiscountTotal: number;
  shippingAmount: number;
  shippingDiscountTotal: number;
  totalDiscount: number;
  grandTotal: number;
  appliedPromotions: AppliedPromotion[];
  rejectedPromotions: RejectedPromotion[];
  pricedAt: string;
};

export type CheckoutResponse = {
  orderId: string;
  itemCount: number;
  totalQuantity: number;
  couponCode: string | null;
  couponReservationId: string | null;
  subtotal: number;
  lineDiscountTotal: number;
  cartDiscountTotal: number;
  shippingAmount: number;
  shippingDiscountTotal: number;
  totalDiscount: number;
  grandTotal: number;
  cartCleared: boolean;
};

export const emptyCart: CartResponse = {
  id: null,
  keycloakId: "",
  items: [],
  savedForLaterItems: [],
  itemCount: 0,
  totalQuantity: 0,
  subtotal: 0,
  note: null,
  createdAt: null,
  updatedAt: null,
};
