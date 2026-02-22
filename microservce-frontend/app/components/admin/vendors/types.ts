export type VendorStatus = "PENDING" | "ACTIVE" | "SUSPENDED";
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
  deleted: boolean;
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

