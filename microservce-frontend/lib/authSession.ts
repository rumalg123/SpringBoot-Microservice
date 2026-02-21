import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js";
import { useCallback, useEffect, useMemo, useState } from "react";
import { createApiClient } from "./apiClient";

type SessionStatus = "idle" | "loading" | "ready" | "error";
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
  apiBase: process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me",
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
  const [canViewAdmin, setCanViewAdmin] = useState(false);
  const [hasCustomerRole, setHasCustomerRole] = useState(false);
  const [emailVerified, setEmailVerified] = useState<boolean | null>(null);

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
          const customerRole = isCustomerByClaims(parsedClaims, env.claimsNamespace);
          setProfile(userProfile);
          setCanViewAdmin(superAdmin);
          setHasCustomerRole(customerRole);
          setEmailVerified(resolveEmailVerified(parsedClaims, userProfile, env.claimsNamespace));
        } else {
          setProfile(null);
          setCanViewAdmin(false);
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
    await client.logout({
      redirectUri: typeof window === "undefined" ? undefined : window.location.origin,
    });
  }, [client]);

  const ensureCustomer = useCallback(async () => {
    if (!apiClient || !isAuthenticated || !hasCustomerRole) return;

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
  }, [apiClient, isAuthenticated, hasCustomerRole, profile]);

  const resendVerificationEmail = useCallback(async () => {
    if (!apiClient || !isAuthenticated) return;
    await apiClient.post("/auth/resend-verification");
  }, [apiClient, isAuthenticated]);

  return {
    env,
    status,
    error,
    isAuthenticated,
    canViewAdmin,
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
