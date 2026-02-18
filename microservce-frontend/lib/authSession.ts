import { Auth0Client, createAuth0Client } from "@auth0/auth0-spa-js";
import { useCallback, useEffect, useMemo, useState } from "react";
import { createApiClient } from "./apiClient";

type SessionStatus = "idle" | "loading" | "ready" | "error";

type UserProfile = Record<string, unknown> | null;

let auth0Singleton: Auth0Client | null = null;

const env = {
  domain: process.env.NEXT_PUBLIC_AUTH0_DOMAIN || "",
  clientId: process.env.NEXT_PUBLIC_AUTH0_CLIENT_ID || "",
  audience: process.env.NEXT_PUBLIC_AUTH0_AUDIENCE || "",
  apiBase: process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me",
};

async function getAuth0(): Promise<Auth0Client> {
  if (auth0Singleton) return auth0Singleton;
  auth0Singleton = await createAuth0Client({
    domain: env.domain,
    clientId: env.clientId,
    authorizationParams: {
      audience: env.audience || undefined,
      scope: "openid profile email",
      redirect_uri: typeof window !== "undefined" ? window.location.origin : undefined,
    },
  });
  return auth0Singleton;
}

export function useAuthSession() {
  const [client, setClient] = useState<Auth0Client | null>(null);
  const [status, setStatus] = useState<SessionStatus>("idle");
  const [error, setError] = useState<string>("");
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profile, setProfile] = useState<UserProfile>(null);

  useEffect(() => {
    const init = async () => {
      if (!env.domain || !env.clientId) {
        setStatus("error");
        setError("Missing Auth0 config. Check NEXT_PUBLIC_AUTH0_DOMAIN and NEXT_PUBLIC_AUTH0_CLIENT_ID.");
        return;
      }

      try {
        setStatus("loading");
        const auth0 = await getAuth0();
        const hasCallback =
          window.location.search.includes("code=") && window.location.search.includes("state=");

        if (hasCallback) {
          const result = await auth0.handleRedirectCallback();
          const returnTo =
            (result.appState && typeof result.appState.returnTo === "string"
              ? result.appState.returnTo
              : window.location.pathname) || "/";
          window.history.replaceState({}, document.title, returnTo);
        }

        const authed = await auth0.isAuthenticated();
        setClient(auth0);
        setIsAuthenticated(authed);
        if (authed) {
          const user = await auth0.getUser();
          setProfile(user ? (user as UserProfile) : null);
        } else {
          setProfile(null);
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
      getToken: () =>
        client.getTokenSilently({
          authorizationParams: {
            audience: env.audience || undefined,
            scope: "openid profile email",
          },
        }),
    });
  }, [client]);

  const login = useCallback(
    async (returnTo: string) => {
      if (!client) return;
      await client.loginWithRedirect({
        appState: { returnTo },
        authorizationParams: {
          audience: env.audience || undefined,
          scope: "openid profile email",
        },
      });
    },
    [client]
  );

  const signup = useCallback(
    async (returnTo: string) => {
      if (!client) return;
      await client.loginWithRedirect({
        appState: { returnTo },
        authorizationParams: {
          screen_hint: "signup",
          audience: env.audience || undefined,
          scope: "openid profile email",
        },
      });
    },
    [client]
  );

  const logout = useCallback(async () => {
    if (!client) return;
    await client.logout({
      logoutParams: { returnTo: window.location.origin },
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
      (profile?.name as string) ||
      (profile?.nickname as string) ||
      (profile?.email as string) ||
      "Customer";
    await apiClient.post("/customers/register-auth0", { name: profileName });
  }, [apiClient, isAuthenticated, profile]);

  return {
    env,
    status,
    error,
    isAuthenticated,
    profile,
    apiClient,
    login,
    signup,
    logout,
    ensureCustomer,
  };
}
