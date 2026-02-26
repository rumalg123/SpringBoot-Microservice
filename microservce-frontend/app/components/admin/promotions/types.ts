/* ───── enums & types ───── */

export type ScopeType = "ORDER" | "VENDOR" | "PRODUCT" | "CATEGORY";
export type AppLevel = "LINE_ITEM" | "CART" | "SHIPPING";
export type BenefitType = "PERCENTAGE_OFF" | "FIXED_AMOUNT_OFF" | "FREE_SHIPPING" | "BUY_X_GET_Y" | "TIERED_SPEND" | "BUNDLE_DISCOUNT";
export type FundingSource = "PLATFORM" | "VENDOR" | "SHARED";
export type LifecycleStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "ARCHIVED";
export type ApprovalStatus = "NOT_REQUIRED" | "PENDING" | "APPROVED" | "REJECTED";

export type SpendTier = { thresholdAmount: number; discountAmount: number };

export type Promotion = {
  id: string;
  name: string;
  description: string;
  vendorId: string | null;
  scopeType: ScopeType;
  targetProductIds: string[];
  targetCategoryIds: string[];
  applicationLevel: AppLevel;
  benefitType: BenefitType;
  benefitValue: number | null;
  buyQuantity: number | null;
  getQuantity: number | null;
  spendTiers: SpendTier[];
  minimumOrderAmount: number | null;
  maximumDiscountAmount: number | null;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  fundingSource: FundingSource;
  stackable: boolean;
  exclusive: boolean;
  autoApply: boolean;
  priority: number;
  lifecycleStatus: LifecycleStatus;
  approvalStatus: ApprovalStatus;
  approvalNote: string | null;
  startsAt: string | null;
  endsAt: string | null;
  createdByUserSub: string | null;
  submittedAt: string | null;
  approvedAt: string | null;
  rejectedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CouponCode = {
  id: string;
  promotionId: string;
  code: string;
  active: boolean;
  maxUses: number | null;
  maxUsesPerCustomer: number | null;
  reservationTtlSeconds: number | null;
  startsAt: string | null;
  endsAt: string | null;
  createdAt: string;
};

export type Analytics = {
  promotionId: string;
  name: string;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  activeReservedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  couponCodeCount: number;
  activeCouponCodeCount: number;
  reservationCount: number;
  activeReservedReservationCount: number;
  committedReservationCount: number;
  releasedReservationCount: number;
  expiredReservationCount: number;
  committedDiscountAmount: number | null;
  releasedDiscountAmount: number | null;
};

export type PromotionAnalytics = {
  promotionId: string;
  name: string;
  vendorId: string | null;
  scopeType: string;
  applicationLevel: string;
  benefitType: string;
  lifecycleStatus: string;
  approvalStatus: string;
  budgetAmount: number | null;
  burnedBudgetAmount: number | null;
  activeReservedBudgetAmount: number | null;
  remainingBudgetAmount: number | null;
  couponCodeCount: number;
  activeCouponCodeCount: number;
  reservationCount: number;
  activeReservedReservationCount: number;
  committedReservationCount: number;
  releasedReservationCount: number;
  expiredReservationCount: number;
  committedDiscountAmount: number | null;
  releasedDiscountAmount: number | null;
  startsAt: string | null;
  endsAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PageResponse<T> = {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  page?: { number?: number; size?: number; totalElements?: number; totalPages?: number };
};

export type FormState = {
  id?: string;
  name: string;
  description: string;
  vendorId: string;
  scopeType: ScopeType;
  targetProductIds: string;
  targetCategoryIds: string;
  applicationLevel: AppLevel;
  benefitType: BenefitType;
  benefitValue: string;
  buyQuantity: string;
  getQuantity: string;
  spendTiers: SpendTier[];
  minimumOrderAmount: string;
  maximumDiscountAmount: string;
  budgetAmount: string;
  fundingSource: FundingSource;
  stackable: boolean;
  exclusive: boolean;
  autoApply: boolean;
  priority: string;
  startsAt: string;
  endsAt: string;
};

export const emptyForm: FormState = {
  name: "", description: "", vendorId: "", scopeType: "ORDER", targetProductIds: "", targetCategoryIds: "",
  applicationLevel: "CART", benefitType: "PERCENTAGE_OFF", benefitValue: "", buyQuantity: "", getQuantity: "",
  spendTiers: [], minimumOrderAmount: "", maximumDiscountAmount: "", budgetAmount: "", fundingSource: "PLATFORM",
  stackable: false, exclusive: false, autoApply: true, priority: "100", startsAt: "", endsAt: "",
};

export type CouponFormState = {
  code: string;
  maxUses: string;
  maxUsesPerCustomer: string;
  reservationTtlSeconds: string;
  active: boolean;
  startsAt: string;
  endsAt: string;
};

/* ───── helpers ───── */

export const money = (v: number | null | undefined) => v != null ? `$${Number(v).toFixed(2)}` : "\u2014";

export const panelStyle: React.CSSProperties = {
  background: "rgba(17,17,40,0.7)", border: "1px solid var(--line)", borderRadius: 16, padding: 16,
};
