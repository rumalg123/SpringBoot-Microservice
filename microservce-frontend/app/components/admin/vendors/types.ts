export type VendorStatus = "PENDING" | "ACTIVE" | "SUSPENDED";
export type VerificationStatus = "UNVERIFIED" | "PENDING_VERIFICATION" | "VERIFIED" | "VERIFICATION_REJECTED";
export type VendorUserRole = "OWNER" | "MANAGER";
export type SlugStatus = "idle" | "checking" | "available" | "taken" | "invalid";

export type Vendor = {
  id: string;
  name: string;
  slug: string;
  contactEmail: string;
  contactPersonName: string | null;
  status: VendorStatus;
  active: boolean;
  acceptingOrders: boolean;
  deleted: boolean;
  deletionRequestedAt?: string | null;
  deletionRequestReason?: string | null;
  verificationStatus?: VerificationStatus;
};

export type VendorUser = {
  id: string;
  vendorId: string;
  keycloakUserId: string;
  email: string;
  displayName: string | null;
  role: VendorUserRole;
  active: boolean;
};

export type VendorForm = {
  id?: string;
  name: string;
  slug: string;
  contactEmail: string;
  contactPersonName: string;
  status: VendorStatus;
  active: boolean;
  acceptingOrders: boolean;
};

export type VendorDeletionEligibility = {
  vendorId: string;
  eligible: boolean;
  totalOrders: number;
  pendingOrders: number;
  lastOrderAt?: string | null;
  refundHoldUntil?: string | null;
  blockingReasons: string[];
};

export type VendorLifecycleAuditAction =
  | "CREATED"
  | "UPDATED"
  | "STOP_ORDERS"
  | "RESUME_ORDERS"
  | "DELETE_REQUESTED"
  | "DELETE_CONFIRMED"
  | "DELETE_CONFIRMED_LEGACY"
  | "RESTORED";

export type VendorLifecycleAudit = {
  id: string;
  vendorId: string;
  action: VendorLifecycleAuditAction | string;
  actorSub?: string | null;
  actorRoles?: string | null;
  actorType?: string | null;
  changeSource?: string | null;
  reason?: string | null;
  createdAt: string;
};

export type OnboardForm = {
  keycloakUserId: string;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  vendorUserRole: VendorUserRole;
  createIfMissing: boolean;
};

export type VendorOnboardResponse = {
  vendorId: string;
  keycloakUserCreated: boolean;
  keycloakActionEmailSent?: boolean;
  keycloakUserId: string;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  vendorMembership: VendorUser;
};

export const emptyVendorForm: VendorForm = {
  name: "",
  slug: "",
  contactEmail: "",
  contactPersonName: "",
  status: "PENDING",
  active: true,
  acceptingOrders: true,
};

export const emptyOnboardForm: OnboardForm = {
  keycloakUserId: "",
  email: "",
  firstName: "",
  lastName: "",
  displayName: "",
  vendorUserRole: "OWNER",
  createIfMissing: true,
};
