export const queryKeys = {
  cart: {
    all: ["cart"] as const,
    me: () => [...queryKeys.cart.all, "me"] as const,
  },
  addresses: {
    all: ["addresses"] as const,
    me: () => [...queryKeys.addresses.all, "me"] as const,
  },
  orders: {
    all: ["orders"] as const,
    me: () => [...queryKeys.orders.all, "me"] as const,
    detail: (id: string) => [...queryKeys.orders.all, "detail", id] as const,
    payment: (orderId: string) => [...queryKeys.orders.all, "payment", orderId] as const,
  },
  customer: {
    all: ["customer"] as const,
    me: () => [...queryKeys.customer.all, "me"] as const,
    commPrefs: () => [...queryKeys.customer.all, "commPrefs"] as const,
    linkedAccounts: () => [...queryKeys.customer.all, "linkedAccounts"] as const,
    activityLog: (page: number) => [...queryKeys.customer.all, "activityLog", page] as const,
    couponUsage: (page: number) => [...queryKeys.customer.all, "couponUsage", page] as const,
  },
  wishlist: {
    all: ["wishlist"] as const,
    me: () => [...queryKeys.wishlist.all, "me"] as const,
    collections: () => [...queryKeys.wishlist.all, "collections"] as const,
  },
  products: {
    all: ["products"] as const,
    list: (params: Record<string, unknown>) => [...queryKeys.products.all, "list", params] as const,
    detail: (id: string) => [...queryKeys.products.all, "detail", id] as const,
  },
  categories: {
    all: ["categories"] as const,
    tree: () => [...queryKeys.categories.all, "tree"] as const,
  },
  reviews: {
    all: ["reviews"] as const,
    byProduct: (productId: string, page: number) => [...queryKeys.reviews.all, productId, page] as const,
  },
  insights: {
    all: ["insights"] as const,
    me: () => [...queryKeys.insights.all, "me"] as const,
    orderSummary: () => [...queryKeys.insights.all, "orderSummary"] as const,
  },
};
