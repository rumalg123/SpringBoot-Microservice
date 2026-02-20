import Keycloak, { KeycloakInstance, KeycloakTokenParsed } from "keycloak-js";
import { useCallback, useEffect, useMemo, useState } from "react";
import { createApiClient } from "./apiClient";

type SessionStatus = "idle" | "loading" | "ready" | "error";
type UserProfile = Record<string, unknown> | null;
type TokenClaims = KeycloakTokenParsed & Record<string, unknown>;

let keycloakSingleton: KeycloakInstance | null = null;

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

function hasScope(claims: Record<string, unknown>, scope: string): boolean {
  const raw = claims.scope;
  if (typeof raw !== "string") return false;
  return raw
    .split(" ")
    .map((part) => part.trim())
    .filter(Boolean)
    .includes(scope);
}

function isAdminByClaims(
  claims: Record<string, unknown> | null,
  audience: string,
  clientId: string,
  namespace: string
): boolean {
  if (!claims) return false;

  const permissions = toStringArray(claims.permissions);
  if (permissions.includes("read:admin-orders") || hasScope(claims, "read:admin-orders")) {
    return true;
  }

  const directRoles = toStringArray(claims.roles);
  if (directRoles.some((role) => role.toLowerCase() === "admin")) {
    return true;
  }

  const trimmedNamespace = namespace.trim();
  if (trimmedNamespace) {
    const normalizedNamespace = trimmedNamespace.endsWith("/") ? trimmedNamespace : `${trimmedNamespace}/`;
    const namespacedRoles = toStringArray(claims[`${normalizedNamespace}roles`]);
    if (namespacedRoles.some((role) => role.toLowerCase() === "admin")) {
      return true;
    }
  }

  const realmAccess = asObject(claims.realm_access);
  const realmRoles = toStringArray(realmAccess?.roles);
  if (realmRoles.some((role) => role.toLowerCase() === "admin")) {
    return true;
  }

  const resourceAccess = asObject(claims.resource_access);
  const resourceCandidates = [audience.trim(), clientId.trim(), "account"].filter(Boolean);
  for (const resourceName of resourceCandidates) {
    const resource = asObject(resourceAccess?.[resourceName]);
    const resourceRoles = toStringArray(resource?.roles);
    if (resourceRoles.some((role) => role.toLowerCase() === "admin")) {
      return true;
    }
  }

  return false;
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

  return toBooleanClaim(profile?.email_verified);
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

async function getKeycloak(): Promise<KeycloakInstance> {
  if (keycloakSingleton) return keycloakSingleton;
  keycloakSingleton = new Keycloak({
    url: env.url,
    realm: env.realm,
    clientId: env.clientId,
  });
  return keycloakSingleton;
}

export function useAuthSession() {
  const [client, setClient] = useState<KeycloakInstance | null>(null);
  const [status, setStatus] = useState<SessionStatus>("idle");
  const [error, setError] = useState("");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profile, setProfile] = useState<UserProfile>(null);
  const [canViewAdmin, setCanViewAdmin] = useState(false);
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
        const keycloak = await getKeycloak();
        setClient(keycloak);

        const params = new URLSearchParams(window.location.search);
        const keycloakError = params.get("error");
        const keycloakErrorDescription = params.get("error_description");
        if (keycloakError || keycloakErrorDescription) {
          setError(keycloakErrorDescription || keycloakError || "");
        }

        const authenticated = await keycloak.init({
          onLoad: "check-sso",
          pkceMethod: "S256",
          checkLoginIframe: false,
        });

        setIsAuthenticated(Boolean(authenticated));
        if (authenticated) {
          try {
            await keycloak.updateToken(30);
          } catch {
            // Best effort token refresh on startup.
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

          setProfile(userProfile);
          setCanViewAdmin(isAdminByClaims(parsedClaims, env.audience, env.clientId, env.claimsNamespace));
          setEmailVerified(resolveEmailVerified(parsedClaims, userProfile, env.claimsNamespace));
        } else {
          setProfile(null);
          setCanViewAdmin(false);
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

  const logout = useCallback(async () => {
    if (!client) return;
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
    await apiClient.post("/customers/register-auth0", { name: profileName });
  }, [apiClient, isAuthenticated, profile]);

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
    emailVerified,
    profile,
    apiClient,
    login,
    signup,
    logout,
    ensureCustomer,
    resendVerificationEmail,
  };
}
