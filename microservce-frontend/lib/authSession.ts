import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createApiClient, clearIdempotencyCache } from "./apiClient";
import { API_BASE } from "./constants";
import { CSRF_HEADER_NAME, getCsrfToken } from "./csrf";
import type { LoadingStatus } from "./types/status";

type SessionStatus = LoadingStatus;
type UserProfile = Record<string, unknown> | null;
type TokenClaims = Record<string, unknown>;
type SessionApiResponse = {
  authenticated: boolean;
  claims: TokenClaims | null;
  profile: UserProfile;
  expiresAt: string | null;
};

const env = {
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL || "",
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM || "",
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || "",
  audience: process.env.NEXT_PUBLIC_KEYCLOAK_AUDIENCE || "",
  scope: process.env.NEXT_PUBLIC_KEYCLOAK_SCOPE || "",
  claimsNamespace: process.env.NEXT_PUBLIC_KEYCLOAK_CLAIMS_NAMESPACE || "",
  apiBase: API_BASE,
};
const SESSION_SYNC_INTERVAL_MS = 2 * 60 * 1000;

function asObject(value: unknown): Record<string, unknown> | null {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function toStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => (typeof item === "string" ? item.trim() : String(item).trim()))
    .filter(Boolean);
}

function toBooleanClaim(value: unknown): boolean | null {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    if (value.toLowerCase() === "true") return true;
    if (value.toLowerCase() === "false") return false;
  }
  return null;
}

function toTrimmedString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

const PLATFORM_ORDERS_READ = "platform.orders.read";
const PLATFORM_ORDERS_MANAGE = "platform.orders.manage";
const PLATFORM_PAYMENTS_READ = "platform.payments.read";
const PLATFORM_PAYMENTS_MANAGE = "platform.payments.manage";
const PLATFORM_PRODUCTS_MANAGE = "platform.products.manage";
const PLATFORM_CATEGORIES_MANAGE = "platform.categories.manage";
const PLATFORM_POSTERS_MANAGE = "platform.posters.manage";
const PLATFORM_PROMOTIONS_MANAGE = "platform.promotions.manage";
const PLATFORM_REVIEWS_MANAGE = "platform.reviews.manage";
const PLATFORM_INVENTORY_MANAGE = "platform.inventory.manage";

const VENDOR_ORDERS_READ = "vendor.orders.read";
const VENDOR_ORDERS_MANAGE = "vendor.orders.manage";
const VENDOR_PRODUCTS_MANAGE = "vendor.products.manage";
const VENDOR_PROMOTIONS_MANAGE = "vendor.promotions.manage";
const VENDOR_INVENTORY_MANAGE = "vendor.inventory.manage";
const VENDOR_ANALYTICS_READ = "vendor.analytics.read";
const VENDOR_FINANCE_READ = "vendor.finance.read";
const VENDOR_FINANCE_MANAGE = "vendor.finance.manage";
const VENDOR_SETTINGS_MANAGE = "vendor.settings.manage";

function normalizePermissionCode(permission: string): string {
  const normalized = permission.trim().toLowerCase();
  if (!normalized) return "";
  if (normalized.includes(".")) return normalized;

  switch (normalized) {
    case "orders_read":
      return VENDOR_ORDERS_READ;
    case "orders_manage":
      return VENDOR_ORDERS_MANAGE;
    case "products_manage":
      return VENDOR_PRODUCTS_MANAGE;
    case "promotions_manage":
      return VENDOR_PROMOTIONS_MANAGE;
    case "inventory_manage":
      return VENDOR_INVENTORY_MANAGE;
    case "analytics_read":
      return VENDOR_ANALYTICS_READ;
    case "finance_read":
      return VENDOR_FINANCE_READ;
    case "finance_manage":
      return VENDOR_FINANCE_MANAGE;
    case "settings_manage":
      return VENDOR_SETTINGS_MANAGE;
    default:
      return normalized;
  }
}

function toPermissionCodeSet(value: unknown): Set<string> {
  const codes = new Set<string>();
  for (const item of toStringArray(value)) {
    const normalized = normalizePermissionCode(item);
    if (normalized) {
      codes.add(normalized);
    }
  }
  return codes;
}

function toBoolean(value: unknown): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value === "string") {
    return value.trim().toLowerCase() === "true";
  }
  return false;
}

function collectActiveVendorPermissionCodes(vendorStaffAccess: unknown): Set<string> {
  if (!Array.isArray(vendorStaffAccess)) return new Set<string>();

  const codes = new Set<string>();
  for (const row of vendorStaffAccess) {
    const record = asObject(row);
    if (!record || !toBoolean(record.active)) continue;
    for (const permission of toPermissionCodeSet(record.permissions)) {
      codes.add(permission);
    }
  }
  return codes;
}

function countActiveVendorMemberships(vendorMemberships: unknown): number {
  if (!Array.isArray(vendorMemberships)) return 0;
  let count = 0;
  for (const row of vendorMemberships) {
    const record = asObject(row);
    if (!record) continue;
    const vendorId = toTrimmedString(record.vendorId);
    if (vendorId) {
      count += 1;
    }
  }
  return count;
}

function composeName(first: string, last: string): string {
  return `${first} ${last}`.trim();
}

function normalizeRoleValue(role: string): string {
  return role
    .trim()
    .toLowerCase()
    .replace(/^role[_:-]?/, "")
    .replace(/[-\s]+/g, "_");
}

function hasRole(roles: string[], requiredRole: string): boolean {
  const normalizedRequiredRole = normalizeRoleValue(requiredRole);
  return roles.some((role) => normalizeRoleValue(role) === normalizedRequiredRole);
}

function hasRoleByClaims(
  claims: Record<string, unknown> | null,
  namespace: string,
  requiredRole: string
): boolean {
  const role = requiredRole.trim();
  if (!claims || !role) return false;

  const directRoles = toStringArray(claims.roles);
  if (hasRole(directRoles, role)) {
    return true;
  }

  const trimmedNamespace = namespace.trim();
  if (trimmedNamespace) {
    const normalizedNamespace = trimmedNamespace.endsWith("/") ? trimmedNamespace : `${trimmedNamespace}/`;
    const namespacedRoles = toStringArray(claims[`${normalizedNamespace}roles`]);
    if (hasRole(namespacedRoles, role)) {
      return true;
    }
  }

  const realmAccess = asObject(claims.realm_access);
  const realmRoles = toStringArray(realmAccess?.roles);
  if (hasRole(realmRoles, role)) {
    return true;
  }

  const resourceAccess = asObject(claims.resource_access);
  if (!resourceAccess) return false;
  for (const resource of Object.values(resourceAccess)) {
    const resourceRoles = toStringArray(asObject(resource)?.roles);
    if (hasRole(resourceRoles, role)) {
      return true;
    }
  }

  return false;
}

function isSuperAdminByClaims(
  claims: Record<string, unknown> | null,
  namespace: string
): boolean {
  return hasRoleByClaims(claims, namespace, "super_admin");
}

function isVendorAdminByClaims(
  claims: Record<string, unknown> | null,
  namespace: string
): boolean {
  return hasRoleByClaims(claims, namespace, "vendor_admin");
}

function isPlatformStaffByClaims(
  claims: Record<string, unknown> | null,
  namespace: string
): boolean {
  return hasRoleByClaims(claims, namespace, "platform_staff");
}

function isVendorStaffByClaims(
  claims: Record<string, unknown> | null,
  namespace: string
): boolean {
  return hasRoleByClaims(claims, namespace, "vendor_staff");
}

function isCustomerByClaims(
  claims: Record<string, unknown> | null,
  namespace: string
): boolean {
  return hasRoleByClaims(claims, namespace, "customer");
}

function resolveEmailVerified(
  claims: Record<string, unknown> | null,
  profile: UserProfile,
  namespace: string
): boolean | null {
  const standard = toBooleanClaim(claims?.email_verified);
  if (standard !== null) return standard;

  const trimmedNamespace = namespace.trim();
  if (trimmedNamespace) {
    const normalizedNamespace = trimmedNamespace.endsWith("/") ? trimmedNamespace : `${trimmedNamespace}/`;
    const namespaced = toBooleanClaim(claims?.[`${normalizedNamespace}email_verified`]);
    if (namespaced !== null) return namespaced;
  }

  return toBooleanClaim(profile?.email_verified) ?? false;
}

function resolveProfileName(
  claims: Record<string, unknown> | null,
  profile: UserProfile
): string {
  const claimName = toTrimmedString(claims?.name);
  if (claimName) return claimName;

  const claimGiven = toTrimmedString(claims?.given_name);
  const claimFamily = toTrimmedString(claims?.family_name);
  const claimComposed = composeName(claimGiven, claimFamily);
  if (claimComposed) return claimComposed;

  const profileName = toTrimmedString(profile?.name);
  if (profileName) return profileName;

  const profileFirst = toTrimmedString(profile?.firstName);
  const profileLast = toTrimmedString(profile?.lastName);
  const profileComposed = composeName(profileFirst, profileLast);
  if (profileComposed) return profileComposed;

  const preferredUsername = toTrimmedString(claims?.preferred_username)
    || toTrimmedString(profile?.preferred_username)
    || toTrimmedString(profile?.username);
  if (preferredUsername) return preferredUsername;

  return toTrimmedString(claims?.email) || toTrimmedString(profile?.email);
}

function resolveReturnTo(returnTo: string): string {
  if (typeof window === "undefined") return returnTo;
  const trimmed = returnTo.trim();
  if (!trimmed) return window.location.origin;

  try {
    return new URL(trimmed).toString();
  } catch {
    const normalizedPath = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
    return `${window.location.origin}${normalizedPath}`;
  }
}

function resolveKeycloakBaseUrl(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

function buildResetCredentialsUrl(returnTo: string, loginHint?: string): string {
  const base = resolveKeycloakBaseUrl(env.url);
  const params = new URLSearchParams({
    client_id: env.clientId,
    redirect_uri: resolveReturnTo(returnTo),
  });
  const normalizedLoginHint = (loginHint || "").trim();
  if (normalizedLoginHint) {
    params.set("login_hint", normalizedLoginHint);
  }
  return `${base}/realms/${encodeURIComponent(env.realm)}/login-actions/reset-credentials?${params.toString()}`;
}

function buildBffAuthUrl(path: string, returnTo: string): string {
  if (typeof window === "undefined") return path;
  const url = new URL(path, window.location.origin);
  url.searchParams.set("returnTo", resolveReturnTo(returnTo));
  return url.toString();
}

function buildLogoutUrl(returnTo: string): string {
  if (typeof window === "undefined") return `/api/auth/logout?returnTo=${encodeURIComponent(returnTo)}`;
  const url = new URL("/api/auth/logout", window.location.origin);
  url.searchParams.set("returnTo", resolveReturnTo(returnTo));
  return url.toString();
}

export function useAuthSession() {
  const [status, setStatus] = useState<SessionStatus>("idle");
  const [error, setError] = useState("");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profile, setProfile] = useState<UserProfile>(null);
  const [claims, setClaims] = useState<TokenClaims | null>(null);
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [isPlatformStaff, setIsPlatformStaff] = useState(false);
  const [isVendorAdmin, setIsVendorAdmin] = useState(false);
  const [isVendorStaff, setIsVendorStaff] = useState(false);
  const [canViewAdmin, setCanViewAdmin] = useState(false);
  const [canManageAdminOrders, setCanManageAdminOrders] = useState(false);
  const [canManageAdminPayments, setCanManageAdminPayments] = useState(false);
  const [canManageAdminProducts, setCanManageAdminProducts] = useState(false);
  const [canManageAdminCategories, setCanManageAdminCategories] = useState(false);
  const [canManageAdminPosters, setCanManageAdminPosters] = useState(false);
  const [canManageAdminVendors, setCanManageAdminVendors] = useState(false);
  const [canManageAdminPromotions, setCanManageAdminPromotions] = useState(false);
  const [canManageAdminReviews, setCanManageAdminReviews] = useState(false);
  const [canManageAdminInventory, setCanManageAdminInventory] = useState(false);
  const [canViewVendorAnalytics, setCanViewVendorAnalytics] = useState(false);
  const [canViewVendorFinance, setCanViewVendorFinance] = useState(false);
  const [canManageVendorFinance, setCanManageVendorFinance] = useState(false);
  const [canManageVendorSettings, setCanManageVendorSettings] = useState(false);
  const [hasCustomerRole, setHasCustomerRole] = useState(false);
  const [emailVerified, setEmailVerified] = useState<boolean | null>(null);
  const customerBootstrapDoneRef = useRef(false);
  const sessionSyncInFlightRef = useRef<Promise<void> | null>(null);

  const apiClient = useMemo(() => {
    return createApiClient({
      baseURL: env.apiBase,
    });
  }, []);

  const resetPermissions = useCallback(() => {
    setProfile(null);
    setClaims(null);
    setIsSuperAdmin(false);
    setIsPlatformStaff(false);
    setIsVendorAdmin(false);
    setIsVendorStaff(false);
    setCanViewAdmin(false);
    setCanManageAdminOrders(false);
    setCanManageAdminPayments(false);
    setCanManageAdminProducts(false);
    setCanManageAdminCategories(false);
    setCanManageAdminPosters(false);
    setCanManageAdminVendors(false);
    setCanManageAdminPromotions(false);
    setCanManageAdminReviews(false);
    setCanManageAdminInventory(false);
    setCanViewVendorAnalytics(false);
    setCanViewVendorFinance(false);
    setCanManageVendorFinance(false);
    setCanManageVendorSettings(false);
    setHasCustomerRole(false);
    setEmailVerified(null);
  }, []);

  const hydrateCapabilities = useCallback(async (
    parsedClaims: TokenClaims | null,
    userProfile: UserProfile
  ) => {
    const superAdmin = isSuperAdminByClaims(parsedClaims, env.claimsNamespace);
    const platformStaff = isPlatformStaffByClaims(parsedClaims, env.claimsNamespace);
    const vendorAdminRole = isVendorAdminByClaims(parsedClaims, env.claimsNamespace);
    const vendorStaffRole = isVendorStaffByClaims(parsedClaims, env.claimsNamespace);
    const customerRole = isCustomerByClaims(parsedClaims, env.claimsNamespace);
    const anyAdmin = superAdmin || platformStaff || vendorAdminRole || vendorStaffRole;
    let effectiveVendorAdmin = vendorAdminRole;
    let effectiveVendorStaff = vendorStaffRole;
    let manageOrders = superAdmin || effectiveVendorAdmin;
    let managePayments = superAdmin || platformStaff;
    let manageProducts = superAdmin || effectiveVendorAdmin;
    let manageCategories = superAdmin;
    let managePosters = superAdmin;
    let manageVendors = superAdmin;
    let managePromotions = superAdmin || effectiveVendorAdmin;
    let manageReviews = superAdmin;
    let manageInventory = superAdmin || effectiveVendorAdmin;
    let viewVendorAnalytics = superAdmin || effectiveVendorAdmin;
    let viewVendorFinance = superAdmin || effectiveVendorAdmin;
    let manageVendorFinance = superAdmin || effectiveVendorAdmin;
    let manageVendorSettings = superAdmin || effectiveVendorAdmin;

    if (anyAdmin) {
      try {
        const capabilitiesRes = await fetch(`${env.apiBase}/admin/me/capabilities`, {
          method: "GET",
          credentials: "same-origin",
          cache: "no-store",
        });
        if (capabilitiesRes.ok) {
          const capabilities = (await capabilitiesRes.json()) as Record<string, unknown>;
          const platformPermissionCodes = toPermissionCodeSet(capabilities.platformPermissions);
          const activeVendorMembershipCount = countActiveVendorMemberships(capabilities.vendorMemberships);
          const vendorPermissionCodes = collectActiveVendorPermissionCodes(capabilities.vendorStaffAccess);
          effectiveVendorAdmin = vendorAdminRole && activeVendorMembershipCount > 0;
          effectiveVendorStaff = vendorStaffRole && vendorPermissionCodes.size > 0;

          const platformCanManageOrders =
            platformPermissionCodes.has(PLATFORM_ORDERS_READ)
            || platformPermissionCodes.has(PLATFORM_ORDERS_MANAGE);
          const platformCanManagePayments =
            platformPermissionCodes.has(PLATFORM_PAYMENTS_READ)
            || platformPermissionCodes.has(PLATFORM_PAYMENTS_MANAGE);
          const platformCanManageProducts = platformPermissionCodes.has(PLATFORM_PRODUCTS_MANAGE);
          const platformCanManageCategories = platformPermissionCodes.has(PLATFORM_CATEGORIES_MANAGE);
          const platformCanManagePosters = platformPermissionCodes.has(PLATFORM_POSTERS_MANAGE);
          const platformCanManagePromotions = platformPermissionCodes.has(PLATFORM_PROMOTIONS_MANAGE);
          const platformCanManageReviews = platformPermissionCodes.has(PLATFORM_REVIEWS_MANAGE);
          const platformCanManageInventory =
            platformPermissionCodes.has(PLATFORM_INVENTORY_MANAGE)
            || platformCanManageProducts;

          const vendorStaffCanManageOrders =
            vendorPermissionCodes.has(VENDOR_ORDERS_READ)
            || vendorPermissionCodes.has(VENDOR_ORDERS_MANAGE);
          const vendorStaffCanManageProducts = vendorPermissionCodes.has(VENDOR_PRODUCTS_MANAGE);
          const vendorStaffCanManagePromotions = vendorPermissionCodes.has(VENDOR_PROMOTIONS_MANAGE);
          const vendorStaffCanManageInventory =
            vendorPermissionCodes.has(VENDOR_INVENTORY_MANAGE)
            || vendorStaffCanManageProducts;
          const vendorStaffCanViewAnalytics = vendorPermissionCodes.has(VENDOR_ANALYTICS_READ);
          const vendorStaffCanManageFinance = vendorPermissionCodes.has(VENDOR_FINANCE_MANAGE);
          const vendorStaffCanViewFinance =
            vendorStaffCanManageFinance
            || vendorPermissionCodes.has(VENDOR_FINANCE_READ);
          const vendorStaffCanManageSettings = vendorPermissionCodes.has(VENDOR_SETTINGS_MANAGE);

          manageOrders =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManageOrders)
            || platformCanManageOrders
            || Boolean(capabilities.canManageAdminOrders);
          managePayments =
            superAdmin
            || platformCanManagePayments;
          manageProducts =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManageProducts)
            || platformCanManageProducts
            || Boolean(capabilities.canManageAdminProducts);
          manageCategories =
            superAdmin
            || platformCanManageCategories
            || Boolean(capabilities.canManageAdminCategories);
          managePosters =
            superAdmin
            || platformCanManagePosters
            || Boolean(capabilities.canManageAdminPosters);
          manageVendors = superAdmin || Boolean(capabilities.canManageAdminVendors);
          managePromotions =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManagePromotions)
            || platformCanManagePromotions
            || Boolean(capabilities.canManageAdminPromotions);
          manageReviews =
            superAdmin
            || platformCanManageReviews
            || Boolean(capabilities.canManageAdminReviews);
          manageInventory =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManageInventory)
            || platformCanManageInventory
            || Boolean(capabilities.canManageAdminInventory);
          viewVendorAnalytics =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanViewAnalytics);
          viewVendorFinance =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanViewFinance);
          manageVendorFinance =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManageFinance);
          manageVendorSettings =
            superAdmin
            || effectiveVendorAdmin
            || (effectiveVendorStaff && vendorStaffCanManageSettings);
        }
      } catch {
        // Keep coarse-role fallback if capabilities endpoint is unavailable.
      }
    }

    setProfile(userProfile);
    setClaims(parsedClaims);
    setIsSuperAdmin(superAdmin);
    setIsPlatformStaff(platformStaff);
    setIsVendorAdmin(effectiveVendorAdmin);
    setIsVendorStaff(effectiveVendorStaff);
    setCanViewAdmin(anyAdmin);
    setCanManageAdminOrders(manageOrders);
    setCanManageAdminPayments(managePayments);
    setCanManageAdminProducts(manageProducts);
    setCanManageAdminCategories(manageCategories);
    setCanManageAdminPosters(managePosters);
    setCanManageAdminVendors(manageVendors);
    setCanManageAdminPromotions(managePromotions);
    setCanManageAdminReviews(manageReviews);
    setCanManageAdminInventory(manageInventory);
    setCanViewVendorAnalytics(viewVendorAnalytics);
    setCanViewVendorFinance(viewVendorFinance);
    setCanManageVendorFinance(manageVendorFinance);
    setCanManageVendorSettings(manageVendorSettings);
    setHasCustomerRole(customerRole);
    setEmailVerified(resolveEmailVerified(parsedClaims, userProfile, env.claimsNamespace));
  }, []);

  const loadSession = useCallback(async (force = false) => {
    if (sessionSyncInFlightRef.current && !force) {
      return sessionSyncInFlightRef.current;
    }

    const request = (async () => {
      setStatus((current) => (current === "idle" ? "loading" : current));
      try {
        const response = await fetch("/api/auth/session", {
          method: "GET",
          credentials: "same-origin",
          cache: "no-store",
        });

        if (response.status === 503) {
          const body = (await response.json().catch(() => ({ message: "" }))) as { message?: string };
          setStatus("error");
          setError(body.message || "Session validation is temporarily unavailable");
          return;
        }

        if (!response.ok) {
          setStatus("error");
          setError("Auth session initialization failed");
          return;
        }

        const payload = (await response.json()) as SessionApiResponse;
        if (!payload.authenticated) {
          setIsAuthenticated(false);
          resetPermissions();
          setError("");
          setStatus("ready");
          return;
        }

        const parsedClaims = payload.claims || null;
        let userProfile = payload.profile || null;
        const resolvedProfileName = resolveProfileName(parsedClaims, userProfile);
        if (resolvedProfileName) {
          userProfile = {
            ...(userProfile || {}),
            name: resolvedProfileName,
          };
        }

        setIsAuthenticated(true);
        setError("");
        await hydrateCapabilities(parsedClaims, userProfile);
        setStatus("ready");
      } catch (e) {
        setStatus("error");
        setError(e instanceof Error ? e.message : "Auth initialization failed");
      }
    })().finally(() => {
      sessionSyncInFlightRef.current = null;
    });

    sessionSyncInFlightRef.current = request;
    return request;
  }, [hydrateCapabilities, resetPermissions]);

  useEffect(() => {
    void loadSession(true);
  }, [loadSession]);

  const login = useCallback(async (returnTo: string) => {
    if (typeof window !== "undefined") {
      window.location.assign(buildBffAuthUrl("/api/auth/login", returnTo));
    }
  }, []);

  const signup = useCallback(async (returnTo: string) => {
    if (typeof window !== "undefined") {
      window.location.assign(buildBffAuthUrl("/api/auth/signup", returnTo));
    }
  }, []);

  const changePassword = useCallback(async (returnTo: string) => {
    if (typeof window !== "undefined") {
      window.location.assign(buildBffAuthUrl("/api/auth/change-password", returnTo));
    }
  }, []);

  const forgotPassword = useCallback(async (returnTo: string, loginHint?: string) => {
    const target = buildResetCredentialsUrl(returnTo, loginHint);
    if (typeof window !== "undefined") {
      window.location.assign(target);
    }
  }, []);

  const logout = useCallback(async () => {
    if (typeof localStorage !== "undefined") {
      localStorage.removeItem("_ps_merged");
    }
    clearIdempotencyCache();
    if (typeof window !== "undefined") {
      const logoutUrl = buildLogoutUrl(window.location.pathname || "/");
      const response = await fetch(logoutUrl, {
        method: "POST",
        headers: {
          [CSRF_HEADER_NAME]: getCsrfToken(),
          Accept: "application/json",
        },
        credentials: "include",
      });
      if (!response.ok) {
        throw new Error("Logout failed");
      }
      const payload = (await response.json()) as { redirectUrl?: string };
      window.location.assign(payload.redirectUrl || "/");
    }
  }, []);

  const ensureCustomer = useCallback(async () => {
    if (!isAuthenticated) return;

    try {
      await apiClient.get("/customers/me");
      return;
    } catch (err) {
      const message = err instanceof Error ? err.message : "";
      if (!message.startsWith("404")) {
        throw err;
      }
    }

    const profileName =
      (profile?.name as string)
      || (profile?.preferred_username as string)
      || (profile?.email as string)
      || "Customer";
    await apiClient.post("/customers/register-identity", { name: profileName });
  }, [apiClient, isAuthenticated, profile]);

  useEffect(() => {
    if (!isAuthenticated) {
      customerBootstrapDoneRef.current = false;
      sessionSyncInFlightRef.current = null;
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (status !== "ready") return;

    const refreshOnResume = () => {
      if (document.visibilityState === "visible") {
        void loadSession(false);
      }
    };

    const intervalId = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        void loadSession(false);
      }
    }, SESSION_SYNC_INTERVAL_MS);

    window.addEventListener("focus", refreshOnResume);
    document.addEventListener("visibilitychange", refreshOnResume);
    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", refreshOnResume);
      document.removeEventListener("visibilitychange", refreshOnResume);
    };
  }, [status, loadSession]);

  useEffect(() => {
    if (status !== "ready" || customerBootstrapDoneRef.current) return;
    if (!isAuthenticated) return;
    if (emailVerified === false) return;

    let cancelled = false;
    const run = async () => {
      try {
        await ensureCustomer();
        if (!cancelled) {
          customerBootstrapDoneRef.current = true;
        }
      } catch {
        // Keep page-level bootstrap fallback (orders/profile) if this fails.
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [status, isAuthenticated, emailVerified, ensureCustomer]);

  useEffect(() => {
    if (!isAuthenticated) return;
    import("./personalization").then(({ mergeSession }) => {
      mergeSession().catch(() => {});
    }).catch(() => {});
  }, [isAuthenticated]);

  const resendVerificationEmail = useCallback(async () => {
    if (!isAuthenticated) return;
    await apiClient.post("/auth/resend-verification");
  }, [apiClient, isAuthenticated]);

  return {
    env,
    token: null,
    getAccessToken: null,
    status,
    error,
    isAuthenticated,
    isSuperAdmin,
    isPlatformStaff,
    isVendorAdmin,
    isVendorStaff,
    canViewAdmin,
    canManageAdminOrders,
    canManageAdminPayments,
    canManageAdminProducts,
    canManageAdminCategories,
    canManageAdminPosters,
    canManageAdminVendors,
    canManageAdminPromotions,
    canManageAdminReviews,
    canManageAdminInventory,
    canViewVendorAnalytics,
    canViewVendorFinance,
    canManageVendorFinance,
    canManageVendorSettings,
    hasCustomerRole,
    emailVerified,
    profile,
    claims,
    apiClient,
    login,
    signup,
    changePassword,
    forgotPassword,
    logout,
    ensureCustomer,
    resendVerificationEmail,
    refreshSession: loadSession,
  };
}
