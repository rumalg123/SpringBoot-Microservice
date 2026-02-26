export type Customer = {
  id: string;
  keycloakId: string;
  name: string;
  email: string;
  phone: string | null;
  avatarUrl: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  loyaltyTier: string;
  loyaltyPoints: number;
  socialProviders: string[];
  active: boolean;
  deactivatedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CustomerAddress = {
  id: string;
  customerId: string;
  label: string | null;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
  deleted?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type AddressForm = {
  id?: string;
  label: string;
  recipientName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
};

export const emptyAddressForm: AddressForm = {
  label: "",
  recipientName: "",
  phone: "",
  line1: "",
  line2: "",
  city: "",
  state: "",
  postalCode: "",
  countryCode: "US",
  defaultShipping: false,
  defaultBilling: false,
};

export type CommunicationPreferences = {
  id: string;
  customerId: string;
  emailMarketing: boolean;
  smsMarketing: boolean;
  pushNotifications: boolean;
  orderUpdates: boolean;
  promotionalAlerts: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ActivityLogEntry = {
  id: string;
  customerId: string;
  action: string;
  details: string;
  ipAddress: string;
  createdAt: string;
};

export type LinkedAccounts = {
  customerId: string;
  providers: string[];
};

export type CouponUsageEntry = {
  reservationId: string;
  couponCode: string;
  promotionName: string;
  discountAmount: number;
  orderId: string;
  orderItem?: string;
  committedAt: string;
};

export type CustomerOrderSummary = {
  customerId: string;
  totalOrders: number;
  activeOrders: number;
  completedOrders: number;
  totalSpent: number;
  totalSaved: number;
  averageOrderValue: number;
  uniqueVendorsOrdered: number;
};

export type MonthlySpendBucket = {
  month: string;
  amount: number;
  orderCount: number;
};

export type CustomerProfileSummary = {
  id: string;
  name: string;
  email: string;
  loyaltyTier: string;
  loyaltyPoints: number;
  memberSince: string;
  active: boolean;
};

export type CustomerInsights = {
  orderSummary: CustomerOrderSummary | null;
  spendingTrend: MonthlySpendBucket[];
  profile: CustomerProfileSummary | null;
};
