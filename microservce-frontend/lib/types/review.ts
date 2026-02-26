export type VendorReply = {
  id: string;
  vendorId: string;
  comment: string;
  createdAt: string;
  updatedAt: string;
};

export type Review = {
  id: string;
  customerId: string;
  customerDisplayName: string;
  productId: string;
  vendorId: string;
  orderId: string;
  rating: number;
  title: string | null;
  comment: string;
  images: string[];
  helpfulCount: number;
  notHelpfulCount: number;
  verifiedPurchase: boolean;
  active: boolean;
  vendorReply: VendorReply | null;
  createdAt: string;
  updatedAt: string;
};

export type ReviewSummary = {
  productId: string;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<string, number>;
};
