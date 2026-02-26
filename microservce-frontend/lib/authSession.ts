import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createApiClient, clearIdempotencyCache } from "./apiClient";
import { API_BASE } from "./constants";
import type { LoadingStatus } from "./types/status";

type SessionStatus = LoadingStatus;
type UserProfile = Record<string, unknown> | null;
type TokenClaims = KeycloakTokenParsed & Record<string, unknown>;

let keycloakSingleton: KeycloakInstance | null = null;
let keycloakInitPromise: Promise<boolean> | null = null;

const env = {
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL || "",
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM || "",
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || "",
  audience: process.env.NEXT_PUBLIC_KEYCLOAK_AUDIENCE || "",
  claimsNamespace: process.env.NEXT_PUBLIC_KEYCLOAK_CLAIMS_NAMESPACE || "",
  apiBase: API_BASE,
};

function parseJwtPayload(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length < 2) return null;

  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const decoded = atob(padded);
    return JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    return null;
  }
}

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

function composeName(first: string, last: string): string {
  return `${first} ${last}`.trim();
}

function hasRole(roles: string[], requiredRole: string): boolean {
  const normalizedRequiredRole = requiredRole.trim().toLowerCase();
  return roles.some((role) => role.trim().toLowerCase() === normalizedRequiredRole);
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

function getCallbackParam(name: string): string {
  if (typeof window === "undefined") return "";

  const searchParams = new URLSearchParams(window.location.search);
  const fromSearch = (searchParams.get(name) || "").trim();
  if (fromSearch) return fromSearch;

  const hash = window.location.hash.startsWith("#")
    ? window.location.hash.slice(1)
    : window.location.hash;
  if (!hash) return "";

  const hashParams = new URLSearchParams(hash);
  return (hashParams.get(name) || "").trim();
}

function resolveKeycloakBaseUrl(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

function resolveSilentCheckSsoUri(): string | undefined {
  if (typeof window === "undefined") return undefined;
  return `${window.location.origin}/silent-check-sso.html`;
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

function getKeycloak(): KeycloakInstance {
  if (typeof window === "undefined") {
    throw new Error("Keycloak can only be initialized in the browser");
  }
  if (keycloakSingleton) return keycloakSingleton;
  keycloakSingleton = new Keycloak({
    url: env.url,
    realm: env.realm,
    clientId: env.clientId,
  });
  return keycloakSingleton;
}

function initKeycloakOnce(client: KeycloakInstance): Promise<boolean> {
  if (keycloakInitPromise) {
    return keycloakInitPromise;
  }

  const maybeInitialized = (client as KeycloakInstance & { didInitialize?: boolean }).didInitialize;
  if (maybeInitialized) {
    return Promise.resolve(Boolean(client.authenticated));
  }

  keycloakInitPromise = client
    .init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
      silentCheckSsoRedirectUri: resolveSilentCheckSsoUri(),
      silentCheckSsoFallback: true,
    })
    .catch((error) => {
      keycloakInitPromise = null;
      keycloakSingleton = null;
      throw error;
    });

  return keycloakInitPromise;
}

export function useAuthSession() {
  const [client, setClient] = useState<KeycloakInstance | null>(null);
  const [status, setStatus] = useState<SessionStatus>("idle");
  const [error, setError] = useState("");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profile, setProfile] = useState<UserProfile>(null);
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [isPlatformStaff, setIsPlatformStaff] = useState(false);
  const [isVendorAdmin, setIsVendorAdmin] = useState(false);
  const [isVendorStaff, setIsVendorStaff] = useState(false);
  const [canViewAdmin, setCanViewAdmin] = useState(false);
  const [canManageAdminOrders, setCanManageAdminOrders] = useState(false);
  const [canManageAdminProducts, setCanManageAdminProducts] = useState(false);
  const [canManageAdminCategories, setCanManageAdminCategories] = useState(false);
  const [canManageAdminPosters, setCanManageAdminPosters] = useState(false);
  const [canManageAdminVendors, setCanManageAdminVendors] = useState(false);
  const [canManageAdminPromotions, setCanManageAdminPromotions] = useState(false);
  const [canManageAdminReviews, setCanManageAdminReviews] = useState(false);
  const [hasCustomerRole, setHasCustomerRole] = useState(false);
  const [emailVerified, setEmailVerified] = useState<boolean | null>(null);
  const customerBootstrapDoneRef = useRef(false);

  useEffect(() => {
    const init = async () => {
      if (!env.url || !env.realm || !env.clientId) {
        setStatus("error");
        setError(
          "Missing Keycloak config. Check NEXT_PUBLIC_KEYCLOAK_URL, NEXT_PUBLIC_KEYCLOAK_REALM, NEXT_PUBLIC_KEYCLOAK_CLIENT_ID."
        );
        return;
      }

      try {
        setStatus("loading");
        const keycloak = getKeycloak();
        setClient(keycloak);

        let callbackAction = "";
        let callbackStatus = "";
        keycloak.onActionUpdate = (status, action) => {
          callbackStatus = (status || "").trim().toLowerCase();
          callbackAction = (action || "").trim().toUpperCase();
        };

        const keycloakError = getCallbackParam("error");
        const keycloakErrorDescription = getCallbackParam("error_description");
        const keycloakAction = getCallbackParam("kc_action").toUpperCase();
        const keycloakActionStatus = getCallbackParam("kc_action_status").toLowerCase();
        if (keycloakError || keycloakErrorDescription) {
          setError(keycloakErrorDescription || keycloakError || "");
        }

        const authenticated = await initKeycloakOnce(keycloak);

        setIsAuthenticated(Boolean(authenticated));
        if (authenticated) {
          try {
            await keycloak.updateToken(30);
          } catch {
            // Best effort token refresh on startup.
          }

          const resolvedAction = keycloakAction || callbackAction;
          const resolvedActionStatus = keycloakActionStatus || callbackStatus;
          if (resolvedAction === "UPDATE_PASSWORD" && resolvedActionStatus === "success") {
            await keycloak.logout({
              redirectUri: resolveReturnTo("/"),
            });
            return;
          }

          const parsedClaims =
            (keycloak.tokenParsed as TokenClaims | undefined)
            || (keycloak.token ? (parseJwtPayload(keycloak.token) as TokenClaims | null) : null);

          let userProfile: UserProfile = null;
          try {
            const loaded = await keycloak.loadUserProfile();
            userProfile = loaded ? ({
              ...loaded,
              email: (parsedClaims?.email as string | undefined) || loaded.email || "",
              email_verified: parsedClaims?.email_verified,
            } as Record<string, unknown>) : null;
          } catch {
            userProfile = null;
          }

          if (!userProfile && parsedClaims) {
            userProfile = {
              name: parsedClaims.name,
              preferred_username: parsedClaims.preferred_username,
              email: parsedClaims.email,
              email_verified: parsedClaims.email_verified,
            };
          }

          const resolvedProfileName = resolveProfileName(parsedClaims, userProfile);
          if (resolvedProfileName) {
            userProfile = {
              ...(userProfile || {}),
              name: resolvedProfileName,
            };
          }

          const superAdmin = isSuperAdminByClaims(parsedClaims, env.claimsNamespace);
          const platformStaff = isPlatformStaffByClaims(parsedClaims, env.claimsNamespace);
          const vendorAdmin = isVendorAdminByClaims(parsedClaims, env.claimsNamespace);
          const vendorStaff = isVendorStaffByClaims(parsedClaims, env.claimsNamespace);
          const customerRole = isCustomerByClaims(parsedClaims, env.claimsNamespace);
          const anyAdmin = superAdmin || platformStaff || vendorAdmin || vendorStaff;
          let manageOrders = superAdmin || platformStaff || vendorAdmin || vendorStaff;
          let manageProducts = superAdmin || platformStaff || vendorAdmin || vendorStaff;
          let manageCategories = superAdmin;
          let managePosters = superAdmin;
          let manageVendors = superAdmin;
          let managePromotions = superAdmin || platformStaff || vendorAdmin || vendorStaff;
          let manageReviews = superAdmin || platformStaff;

          if (anyAdmin && keycloak.token) {
            try {
              const capabilitiesRes = await fetch(`${env.apiBase}/admin/me/capabilities`, {
                method: "GET",
                headers: {
                  Authorization: `Bearer ${keycloak.token}`,
                },
              });
              if (capabilitiesRes.ok) {
                const capabilities = (await capabilitiesRes.json()) as Record<string, unknown>;
                manageOrders = Boolean(capabilities.canManageAdminOrders);
                manageProducts = Boolean(capabilities.canManageAdminProducts);
                manageCategories = Boolean(capabilities.canManageAdminCategories);
                managePosters = Boolean(capabilities.canManageAdminPosters);
                manageVendors = Boolean(capabilities.canManageAdminVendors);
                managePromotions = Boolean(capabilities.canManageAdminPromotions);
                manageReviews = Boolean(capabilities.canManageAdminReviews);
              }
            } catch {
              // Keep coarse-role fallback if capabilities endpoint is unavailable.
            }
          }

          setProfile(userProfile);
          setIsSuperAdmin(superAdmin);
          setIsPlatformStaff(platformStaff);
          setIsVendorAdmin(vendorAdmin);
          setIsVendorStaff(vendorStaff);
          setCanViewAdmin(anyAdmin);
          setCanManageAdminOrders(manageOrders);
          setCanManageAdminProducts(manageProducts);
          setCanManageAdminCategories(manageCategories);
          setCanManageAdminPosters(managePosters);
          setCanManageAdminVendors(manageVendors);
          setCanManageAdminPromotions(managePromotions);
          setCanManageAdminReviews(manageReviews);
          setHasCustomerRole(customerRole);
          setEmailVerified(resolveEmailVerified(parsedClaims, userProfile, env.claimsNamespace));
        } else {
          setProfile(null);
          setIsSuperAdmin(false);
          setIsPlatformStaff(false);
          setIsVendorAdmin(false);
          setIsVendorStaff(false);
          setCanViewAdmin(false);
          setCanManageAdminOrders(false);
          setCanManageAdminProducts(false);
          setCanManageAdminCategories(false);
          setCanManageAdminPosters(false);
          setCanManageAdminVendors(false);
          setCanManageAdminPromotions(false);
          setCanManageAdminReviews(false);
          setHasCustomerRole(false);
          setEmailVerified(null);
        }

        setStatus("ready");
      } catch (e) {
        setStatus("error");
        setError(e instanceof Error ? e.message : "Auth initialization failed");
      }
    };

    void init();
  }, []);

  const apiClient = useMemo(() => {
    if (!client) return null;
    return createApiClient({
      baseURL: env.apiBase,
      getToken: async () => {
        if (!client.authenticated) {
          throw new Error("User is not authenticated");
        }
        try {
          await client.updateToken(30);
        } catch {
          // Token refresh can fail if token is still valid or on transient network errors.
        }
        if (!client.token) {
          throw new Error("Missing access token");
        }
        return client.token;
      },
    });
  }, [client]);

  const login = useCallback(
    async (returnTo: string) => {
      if (!client) return;
      await client.login({
        redirectUri: resolveReturnTo(returnTo),
      });
    },
    [client]
  );

  const signup = useCallback(
    async (returnTo: string) => {
      if (!client) return;
      await client.register({
        redirectUri: resolveReturnTo(returnTo),
      });
    },
    [client]
  );

  const changePassword = useCallback(
    async (returnTo: string) => {
      if (!client) return;
      await client.login({
        redirectUri: resolveReturnTo(returnTo),
        action: "UPDATE_PASSWORD",
      });
    },
    [client]
  );

  const forgotPassword = useCallback(
    async (returnTo: string, loginHint?: string) => {
      const target = buildResetCredentialsUrl(returnTo, loginHint);
      if (typeof window !== "undefined") {
        window.location.assign(target);
      }
    },
    []
  );

  const logout = useCallback(async () => {
    if (!client) return;
    if (typeof localStorage !== "undefined") {
      localStorage.removeItem("_ps_merged");
    }
    clearIdempotencyCache();
    await client.logout({
      redirectUri: typeof window === "undefined" ? undefined : window.location.origin,
    });
  }, [client]);

  const ensureCustomer = useCallback(async () => {
    if (!apiClient || !isAuthenticated) return;

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
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (status !== "ready" || customerBootstrapDoneRef.current) return;
    if (!isAuthenticated || !apiClient) return;
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
  }, [status, isAuthenticated, apiClient, emailVerified, ensureCustomer]);

  // Merge anonymous personalization session on login
  useEffect(() => {
    if (!isAuthenticated || !client?.token) return;
    import("./personalization").then(({ mergeSession }) => {
      if (client.token) mergeSession(client.token).catch(() => {});
    }).catch(() => {});
  }, [isAuthenticated, client]);

  const resendVerificationEmail = useCallback(async () => {
    if (!apiClient || !isAuthenticated) return;
    await apiClient.post("/auth/resend-verification");
  }, [apiClient, isAuthenticated]);

  return {
    env,
    token: client?.token || null,
    status,
    error,
    isAuthenticated,
    isSuperAdmin,
    isPlatformStaff,
    isVendorAdmin,
    isVendorStaff,
    canViewAdmin,
    canManageAdminOrders,
    canManageAdminProducts,
    canManageAdminCategories,
    canManageAdminPosters,
    canManageAdminVendors,
    canManageAdminPromotions,
    canManageAdminReviews,
    hasCustomerRole,
    emailVerified,
    profile,
    apiClient,
    login,
    signup,
    changePassword,
    forgotPassword,
    logout,
    ensureCustomer,
    resendVerificationEmail,
  };
}
