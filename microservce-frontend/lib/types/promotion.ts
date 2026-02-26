export type SpendTier = {
  thresholdAmount: number;
  discountAmount: number;
};

export type PublicPromotion = {
  id: string;
  name: string;
  description: string | null;
  scopeType: string;
  applicationLevel: string;
  benefitType: string;
  benefitValue: number | null;
  buyQuantity: number | null;
  getQuantity: number | null;
  spendTiers: SpendTier[];
  minimumOrderAmount: number | null;
  maximumDiscountAmount: number | null;
  stackable: boolean;
  autoApply: boolean;
  targetProductIds: string[];
  targetCategoryIds: string[];
  flashSale: boolean;
  flashSaleStartAt: string | null;
  flashSaleEndAt: string | null;
  startsAt: string | null;
  endsAt: string | null;
};
