/* ───── Dashboard shared types ───── */

export type PlatformOrderSummary = {
  totalOrders: number; pendingOrders: number; processingOrders: number;
  shippedOrders: number; deliveredOrders: number; cancelledOrders: number;
  refundedOrders: number; totalRevenue: number; totalDiscount: number;
  totalShipping: number; averageOrderValue: number; orderCompletionRate: number;
};
export type CustomerPlatformSummary = {
  totalCustomers: number; activeCustomers: number; newCustomersThisMonth: number;
  loyaltyDistribution: Record<string, number>;
};
export type ProductPlatformSummary = {
  totalProducts: number; activeProducts: number; draftProducts: number;
  pendingApproval: number; totalViews: number; totalSold: number;
};
export type VendorPlatformSummary = {
  totalVendors: number; activeVendors: number; pendingVendors: number;
  suspendedVendors: number; verifiedVendors: number;
  avgCommissionRate: number; avgFulfillmentRate: number;
};
export type PaymentPlatformSummary = {
  totalPayments: number; successfulPayments: number; failedPayments: number;
  totalSuccessAmount: number; totalRefundAmount: number;
  chargebackCount: number; avgPaymentAmount: number;
};
export type InventoryHealthSummary = {
  totalSkus: number; inStockCount: number; lowStockCount: number;
  outOfStockCount: number; backorderCount: number;
  totalQuantityOnHand: number; totalQuantityReserved: number;
};
export type PromotionPlatformSummary = {
  totalCampaigns: number; activeCampaigns: number; scheduledCampaigns: number;
  expiredCampaigns: number; flashSaleCount: number; totalBudget: number;
  totalBurnedBudget: number; budgetUtilizationPercent: number;
};
export type ReviewPlatformSummary = {
  totalReviews: number; activeReviews: number; avgRating: number;
  verifiedPurchasePercent: number; totalReported: number; reviewsThisMonth: number;
};
export type WishlistPlatformSummary = { totalWishlistItems: number; uniqueCustomers: number; uniqueProducts: number };
export type CartPlatformSummary = { totalActiveCarts: number; totalCartItems: number; totalSavedForLater: number; avgCartValue: number; avgItemsPerCart: number };
export type DailyRevenueBucket = { date: string; revenue: number; orderCount: number };
export type MonthlyGrowthBucket = { month: string; newCustomers: number; totalActive: number };

export type DashboardData = {
  orders: PlatformOrderSummary | null;
  customers: CustomerPlatformSummary | null;
  products: ProductPlatformSummary | null;
  vendors: VendorPlatformSummary | null;
  payments: PaymentPlatformSummary | null;
  inventory: InventoryHealthSummary | null;
  promotions: PromotionPlatformSummary | null;
  reviews: ReviewPlatformSummary | null;
  wishlist: WishlistPlatformSummary | null;
  cart: CartPlatformSummary | null;
  revenueTrend: DailyRevenueBucket[];
};

export type TopProductEntry = { productId: string; productName: string; vendorId: string; quantitySold: number; totalRevenue: number };
export type ProductViewEntry = { id: string; name: string; vendorId: string; viewCount: number };
export type ProductSoldEntry = { id: string; name: string; vendorId: string; soldCount: number };
export type MostWishedProduct = { productId: string; productName: string; wishlistCount: number };
export type TopProductsData = { byRevenue: TopProductEntry[]; byViews: ProductViewEntry[]; bySold: ProductSoldEntry[]; byWishlisted: MostWishedProduct[] };
export type VendorLeaderboardEntry = { id: string; name: string; totalOrdersCompleted: number; averageRating: number; fulfillmentRate: number; disputeRate: number; verified: boolean };
export type VendorLeaderboardData = { summary: VendorPlatformSummary | null; leaderboard: VendorLeaderboardEntry[] };
export type InventoryHealthData = { summary: InventoryHealthSummary | null; lowStockAlerts: { productId: string; vendorId: string; sku: string; quantityAvailable: number; lowStockThreshold: number; stockStatus: string }[] };
export type CustomerSegmentationData = { summary: CustomerPlatformSummary | null; growthTrend: MonthlyGrowthBucket[] };
export type PromotionRoiData = { summary: PromotionPlatformSummary | null; campaigns: { campaignId: string; name: string; vendorId: string; budgetAmount: number; burnedBudgetAmount: number; utilizationPercent: number; benefitType: string; isFlashSale: boolean }[] };
export type ReviewAnalyticsData = { summary: ReviewPlatformSummary | null; ratingDistribution: Record<string, number> };
export type RevenueTrendData = { trend: DailyRevenueBucket[]; statusBreakdown: Record<string, number> };

export type Tab = "overview" | "products" | "customers" | "vendors" | "inventory" | "promotions" | "reviews";
