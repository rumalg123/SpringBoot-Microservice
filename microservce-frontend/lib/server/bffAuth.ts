import crypto from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "../csrf";
import {
  createBffSession,
  deleteBffSession,
  readBffSession,
  SessionStoreError,
  updateBffSession,
} from "./bffSessionStore";

const SESSION_COOKIE = "rs_bff_session";
const GUEST_CART_ID_COOKIE = "rs_guest_cart_id";
const GUEST_CART_SIGNATURE_COOKIE = "rs_guest_cart_sig";
const OAUTH_STATE_COOKIE = "rs_oauth_state";
const OAUTH_VERIFIER_COOKIE = "rs_oauth_verifier";
const OAUTH_RETURN_TO_COOKIE = "rs_oauth_return_to";
const GATEWAY_SYNC_COOKIE = "rs_gateway_sync_at";
const DEFAULT_SCOPE = "openid profile email";
const DEFAULT_SESSION_SYNC_INTERVAL_MS = 2 * 60 * 1000;
const ACCESS_TOKEN_REFRESH_MARGIN_SECONDS = 30;
const OAUTH_STATE_TTL_SECONDS = 10 * 60;
const OAUTH_STATE_VERSION = 1;

type SameSite = "lax" | "strict" | "none";

export type CookieMutation = {
  name: string;
  value: string;
  maxAge?: number;
  httpOnly?: boolean;
  sameSite?: SameSite;
};

type TokenResponse = {
  access_token?: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
  refresh_expires_in?: number;
};

type JwtPayload = Record<string, unknown> & {
  exp?: number;
  sub?: string;
  sid?: string;
  session_state?: string;
  email?: string;
  preferred_username?: string;
  name?: string;
  given_name?: string;
  family_name?: string;
  email_verified?: boolean;
};

type StoredTokens = {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  accessExpiresAtEpochSeconds: number;
  refreshExpiresAtEpochSeconds: number | null;
  claims: JwtPayload;
};

type GuestCartCookies = {
  cartId: string;
  signature: string;
};

type OauthStatePayload = {
  version: number;
  nonce: string;
  fingerprint: string;
  issuedAtEpochSeconds: number;
};

export type SessionPayload = {
  authenticated: boolean;
  claims: Record<string, unknown> | null;
  profile: Record<string, unknown> | null;
  expiresAt: string | null;
};

export class CsrfValidationError extends Error {
  constructor(message = "CSRF token is invalid or missing") {
    super(message);
    this.name = "CsrfValidationError";
  }
}

export class SessionSyncError extends Error {
  public readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "SessionSyncError";
    this.status = status;
  }
}

export { SessionStoreError };

function isProduction(): boolean {
  return process.env.NODE_ENV === "production";
}

function cookieSameSite(): SameSite {
  return "lax";
}

function buildCookieMutation(
  name: string,
  value: string,
  maxAge: number,
  httpOnly: boolean
): CookieMutation {
  return {
    name,
    value,
    maxAge,
    httpOnly,
    sameSite: cookieSameSite(),
  };
}

function deleteCookieMutation(name: string): CookieMutation {
  return {
    name,
    value: "",
    maxAge: 0,
    httpOnly: true,
    sameSite: cookieSameSite(),
  };
}

function buildSessionCookieMutation(sessionId: string, ttlSeconds: number): CookieMutation {
  return buildCookieMutation(SESSION_COOKIE, sessionId, ttlSeconds, true);
}

function readSessionId(request: NextRequest): string {
  return (request.cookies.get(SESSION_COOKIE)?.value || "").trim();
}

function applyCookieMutation(response: NextResponse, mutation: CookieMutation): void {
  response.cookies.set({
    name: mutation.name,
    value: mutation.value,
    httpOnly: mutation.httpOnly ?? true,
    secure: isProduction(),
    sameSite: mutation.sameSite ?? cookieSameSite(),
    path: "/",
    maxAge: mutation.maxAge,
  });
}

export function applyCookieMutations(response: NextResponse, mutations: CookieMutation[]): void {
  for (const mutation of mutations) {
    applyCookieMutation(response, mutation);
  }
}

export function clearAuthCookieMutations(): CookieMutation[] {
  return [
    deleteCookieMutation(SESSION_COOKIE),
    deleteCookieMutation(CSRF_COOKIE_NAME),
    deleteCookieMutation(OAUTH_STATE_COOKIE),
    deleteCookieMutation(OAUTH_VERIFIER_COOKIE),
    deleteCookieMutation(OAUTH_RETURN_TO_COOKIE),
    deleteCookieMutation(GATEWAY_SYNC_COOKIE),
  ];
}

function getEnv(name: string, fallback = ""): string {
  return (process.env[name] || fallback).trim();
}

function getKeycloakBaseUrl(): string {
  const configured = getEnv("KEYCLOAK_BASE_URL", getEnv("NEXT_PUBLIC_KEYCLOAK_URL"));
  return configured.replace(/\/+$/, "");
}

function getKeycloakRealm(): string {
  return getEnv("KEYCLOAK_REALM", getEnv("NEXT_PUBLIC_KEYCLOAK_REALM"));
}

function getBffClientId(): string {
  return getEnv("KEYCLOAK_BFF_CLIENT_ID", getEnv("NEXT_PUBLIC_KEYCLOAK_CLIENT_ID"));
}

function getBffClientSecret(): string {
  return getEnv("KEYCLOAK_BFF_CLIENT_SECRET");
}

function getRequestedScope(): string {
  const configured = getEnv("KEYCLOAK_BFF_SCOPE", getEnv("NEXT_PUBLIC_KEYCLOAK_SCOPE", DEFAULT_SCOPE));
  const normalized = configured
    .split(/\s+/)
    .map((value) => value.trim())
    .filter(Boolean);
  const scopes = normalized.length > 0 ? normalized : DEFAULT_SCOPE.split(" ");
  if (!scopes.includes("openid")) {
    scopes.unshift("openid");
  }
  return Array.from(new Set(scopes)).join(" ");
}

function getUpstreamGatewayBaseUrl(): string {
  const configured = getEnv("GATEWAY_UPSTREAM_BASE_URL", getEnv("NEXT_PUBLIC_API_BASE"));
  return configured.replace(/\/+$/, "");
}

function getSessionSyncIntervalMs(): number {
  const raw = Number.parseInt(getEnv("BFF_GATEWAY_SESSION_SYNC_INTERVAL_MS", String(DEFAULT_SESSION_SYNC_INTERVAL_MS)), 10);
  return Number.isFinite(raw) && raw > 0 ? raw : DEFAULT_SESSION_SYNC_INTERVAL_MS;
}

function getGuestCartSigningSecret(): string {
  return getEnv("GUEST_CART_SIGNING_SECRET", "dev-guest-cart-signing-secret-change-me");
}

function getOauthStateSecret(): string {
  return getEnv(
    "KEYCLOAK_BFF_STATE_SECRET",
    getEnv("KEYCLOAK_BFF_CLIENT_SECRET", getEnv("GUEST_CART_SIGNING_SECRET", "dev-bff-state-secret-change-me"))
  );
}

function authorizationEndpoint(mode: "login" | "signup"): string {
  const baseUrl = getKeycloakBaseUrl();
  const realm = encodeURIComponent(getKeycloakRealm());
  const suffix = mode === "signup"
    ? "protocol/openid-connect/registrations"
    : "protocol/openid-connect/auth";
  return `${baseUrl}/realms/${realm}/${suffix}`;
}

function tokenEndpoint(): string {
  return `${getKeycloakBaseUrl()}/realms/${encodeURIComponent(getKeycloakRealm())}/protocol/openid-connect/token`;
}

function logoutEndpoint(): string {
  return `${getKeycloakBaseUrl()}/realms/${encodeURIComponent(getKeycloakRealm())}/protocol/openid-connect/logout`;
}

function resolveAppOrigin(request: NextRequest): string {
  const forwardedProto = (request.headers.get("x-forwarded-proto") || "").trim();
  const protocol = forwardedProto || request.nextUrl.protocol.replace(/:$/, "");
  const forwardedHost = (request.headers.get("x-forwarded-host") || "").trim();
  const host = forwardedHost || (request.headers.get("host") || "").trim();
  if (!host) {
    return request.nextUrl.origin;
  }
  return `${protocol}://${host}`;
}

function normalizeReturnTo(request: NextRequest, returnTo: string | null): string {
  const appOrigin = resolveAppOrigin(request);
  const trimmed = (returnTo || "").trim();
  if (!trimmed) {
    return `${appOrigin}/`;
  }

  try {
    const asUrl = new URL(trimmed);
    if (asUrl.origin === appOrigin) {
      return asUrl.toString();
    }
    return `${appOrigin}/`;
  } catch {
    const normalizedPath = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
    return `${appOrigin}${normalizedPath}`;
  }
}

function createStateNonce(): string {
  return crypto.randomBytes(24).toString("base64url");
}

function createCsrfToken(): string {
  return crypto.randomBytes(24).toString("base64url");
}

function getClientIp(request: NextRequest): string {
  const forwardedFor = (request.headers.get("x-forwarded-for") || "").trim();
  const raw = forwardedFor ? forwardedFor.split(",")[0]?.trim() || "" : "";
  const fallback = (request.headers.get("x-real-ip") || "").trim();
  const candidate = raw || fallback;
  if (!candidate) {
    return "";
  }
  return candidate.startsWith("::ffff:") ? candidate.slice("::ffff:".length) : candidate;
}

function normalizeIpFingerprint(ip: string): string {
  const normalized = ip.trim();
  if (!normalized) {
    return "unknown-ip";
  }
  if (normalized.includes(":")) {
    const segments = normalized.split(":").filter(Boolean);
    return segments.slice(0, 4).join(":");
  }
  const segments = normalized.split(".");
  if (segments.length === 4) {
    return `${segments[0]}.${segments[1]}.${segments[2]}.0`;
  }
  return normalized;
}

function buildOauthBrowserFingerprint(request: NextRequest): string {
  const userAgent = (request.headers.get("user-agent") || "").trim().toLowerCase();
  const ipPrefix = normalizeIpFingerprint(getClientIp(request));
  return crypto.createHash("sha256")
    .update(`${userAgent}|${ipPrefix}`)
    .digest("base64url");
}

function getOauthStateKey(): Buffer {
  return crypto.createHash("sha256")
    .update(getOauthStateSecret(), "utf8")
    .digest();
}

function derivePkceVerifier(nonce: string, fingerprint: string): string {
  return crypto
    .createHmac("sha256", getOauthStateKey())
    .update(`pkce:${nonce}:${fingerprint}`, "utf8")
    .digest("base64url");
}

function signOauthState(payload: string): string {
  return crypto
    .createHmac("sha256", getOauthStateKey())
    .update(payload, "utf8")
    .digest("base64url")
    .slice(0, 22);
}

function encodeOauthStateToken(payload: OauthStatePayload): string {
  const issuedAt = payload.issuedAtEpochSeconds.toString(36);
  const body = `${payload.version}.${payload.nonce}.${issuedAt}`;
  return `${body}.${signOauthState(body)}`;
}

function decodeOauthStateToken(token: string, currentFingerprint: string): OauthStatePayload | null {
  const normalized = token.trim();
  if (!normalized) {
    return null;
  }
  const parts = normalized.split(".");
  if (parts.length !== 4) {
    return null;
  }
  const [versionRaw, nonce, issuedAtRaw, signature] = parts;
  const body = `${versionRaw}.${nonce}.${issuedAtRaw}`;
  if (!safeEqualText(signature, signOauthState(body))) {
    return null;
  }
  const version = Number.parseInt(versionRaw, 10);
  const issuedAtEpochSeconds = Number.parseInt(issuedAtRaw, 36);
  if (version !== OAUTH_STATE_VERSION || !nonce || !Number.isFinite(issuedAtEpochSeconds) || issuedAtEpochSeconds <= 0) {
    return null;
  }
  return {
    version,
    nonce,
    fingerprint: currentFingerprint,
    issuedAtEpochSeconds,
  };
}

function isRecentOauthState(payload: OauthStatePayload): boolean {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return payload.issuedAtEpochSeconds <= nowEpochSeconds
    && (nowEpochSeconds - payload.issuedAtEpochSeconds) <= OAUTH_STATE_TTL_SECONDS;
}

function safeEqualText(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && crypto.timingSafeEqual(leftBuffer, rightBuffer);
}

function signGuestCartId(cartId: string): string {
  return crypto
    .createHmac("sha256", getGuestCartSigningSecret())
    .update(cartId)
    .digest("hex");
}

function clearGuestCartCookieMutations(): CookieMutation[] {
  return [
    deleteCookieMutation(GUEST_CART_ID_COOKIE),
    deleteCookieMutation(GUEST_CART_SIGNATURE_COOKIE),
  ];
}

function buildGuestCartCookieMutations(cartId: string): CookieMutation[] {
  const signature = signGuestCartId(cartId);
  const maxAgeSeconds = 30 * 24 * 60 * 60;
  return [
    buildCookieMutation(GUEST_CART_ID_COOKIE, cartId, maxAgeSeconds, true),
    buildCookieMutation(GUEST_CART_SIGNATURE_COOKIE, signature, maxAgeSeconds, true),
  ];
}

function readGuestCartCookies(request: NextRequest): GuestCartCookies | null {
  const cartId = (request.cookies.get(GUEST_CART_ID_COOKIE)?.value || "").trim();
  const signature = (request.cookies.get(GUEST_CART_SIGNATURE_COOKIE)?.value || "").trim();
  if (!cartId || !signature) {
    return null;
  }
  if (signGuestCartId(cartId) !== signature) {
    return null;
  }
  return { cartId, signature };
}

function ensureGuestCartCookies(request: NextRequest, mutations: CookieMutation[]): GuestCartCookies {
  const existing = readGuestCartCookies(request);
  if (existing) {
    return existing;
  }
  const cartId = crypto.randomUUID();
  const signature = signGuestCartId(cartId);
  mutations.push(...buildGuestCartCookieMutations(cartId));
  return { cartId, signature };
}

function serializeGuestCartCookieHeader(guestCart: GuestCartCookies): string {
  return [
    `${GUEST_CART_ID_COOKIE}=${encodeURIComponent(guestCart.cartId)}`,
    `${GUEST_CART_SIGNATURE_COOKIE}=${encodeURIComponent(guestCart.signature)}`,
  ].join("; ");
}

function createCodeChallenge(verifier: string): string {
  return crypto.createHash("sha256").update(verifier).digest("base64url");
}

function decodeJwtPayload(token: string | null): JwtPayload | null {
  if (!token) {
    return null;
  }

  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }

  try {
    const json = Buffer.from(parts[1], "base64url").toString("utf8");
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

function toStoredTokens(tokenResponse: TokenResponse, fallback?: StoredTokens | null): StoredTokens {
  const accessToken = (tokenResponse.access_token || "").trim();
  const refreshToken = (tokenResponse.refresh_token || "").trim() || fallback?.refreshToken || null;
  const idToken = (tokenResponse.id_token || "").trim() || fallback?.idToken || null;
  const accessClaims = decodeJwtPayload(accessToken);
  if (!accessToken || !accessClaims || typeof accessClaims.exp !== "number") {
    throw new Error("Token response is missing a valid access token");
  }

  const refreshClaims = decodeJwtPayload(refreshToken);
  return {
    accessToken,
    refreshToken,
    idToken,
    accessExpiresAtEpochSeconds: accessClaims.exp,
    refreshExpiresAtEpochSeconds: refreshClaims && typeof refreshClaims.exp === "number"
      ? refreshClaims.exp
      : fallback?.refreshExpiresAtEpochSeconds ?? null,
    claims: accessClaims,
  };
}

async function requestToken(form: URLSearchParams): Promise<TokenResponse> {
  const clientSecret = getBffClientSecret();
  if (clientSecret) {
    form.set("client_secret", clientSecret);
  }

  const response = await fetch(tokenEndpoint(), {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: form.toString(),
    cache: "no-store",
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(body || `Token endpoint failed with status ${response.status}`);
  }

  return (await response.json()) as TokenResponse;
}

async function readStoredTokens(request: NextRequest): Promise<{ sessionId: string; storedTokens: StoredTokens | null }> {
  const sessionId = readSessionId(request);
  if (!sessionId) {
    return { sessionId: "", storedTokens: null };
  }
  const storedTokens = await readBffSession(sessionId);
  return {
    sessionId,
    storedTokens: storedTokens
      ? {
          accessToken: storedTokens.accessToken,
          refreshToken: storedTokens.refreshToken,
          idToken: storedTokens.idToken,
          accessExpiresAtEpochSeconds: storedTokens.accessExpiresAtEpochSeconds,
          refreshExpiresAtEpochSeconds: storedTokens.refreshExpiresAtEpochSeconds,
          claims: storedTokens.claims as JwtPayload,
        }
      : null,
  };
}

function shouldRefresh(tokens: StoredTokens): boolean {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  return tokens.accessExpiresAtEpochSeconds - nowEpochSeconds <= ACCESS_TOKEN_REFRESH_MARGIN_SECONDS;
}

function buildProfile(claims: JwtPayload | null): Record<string, unknown> | null {
  if (!claims) {
    return null;
  }

  return {
    sub: claims.sub || "",
    email: claims.email || "",
    name: claims.name || "",
    preferred_username: claims.preferred_username || "",
    given_name: claims.given_name || "",
    family_name: claims.family_name || "",
    email_verified: claims.email_verified ?? false,
  };
}

async function refreshTokens(tokens: StoredTokens): Promise<TokenResponse> {
  if (!tokens.refreshToken) {
    throw new Error("Refresh token is unavailable");
  }

  const form = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: getBffClientId(),
    refresh_token: tokens.refreshToken,
  });

  return requestToken(form);
}

async function syncGatewaySession(accessToken: string): Promise<void> {
  const response = await fetch(`${getUpstreamGatewayBaseUrl()}/auth/session`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
    },
    cache: "no-store",
  });

  if (response.status === 401 || response.status === 403) {
    throw new SessionSyncError(response.status, "Session is no longer valid");
  }
  if (!response.ok) {
    throw new SessionSyncError(response.status, "Gateway session validation is unavailable");
  }
}

async function mergeGuestCart(accessToken: string, request: NextRequest): Promise<boolean> {
  const guestCart = readGuestCartCookies(request);
  if (!guestCart) {
    return false;
  }
  const response = await fetch(`${getUpstreamGatewayBaseUrl()}/cart/me/merge-session`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      Cookie: serializeGuestCartCookieHeader(guestCart),
    },
    cache: "no-store",
  });
  return response.ok && response.headers.get("x-cart-session-cleared") === "true";
}

function shouldSyncGateway(request: NextRequest, forceSync: boolean): boolean {
  if (forceSync) {
    return true;
  }

  const raw = request.cookies.get(GATEWAY_SYNC_COOKIE)?.value || "";
  const lastSyncedAt = Number.parseInt(raw, 10);
  if (!Number.isFinite(lastSyncedAt) || lastSyncedAt <= 0) {
    return true;
  }
  return Date.now() - lastSyncedAt >= getSessionSyncIntervalMs();
}

function gatewaySyncCookieMutation(): CookieMutation {
  return buildCookieMutation(GATEWAY_SYNC_COOKIE, String(Date.now()), 24 * 60 * 60, true);
}

function ensureCsrfCookie(request: NextRequest, mutations: CookieMutation[]): void {
  if (request.cookies.get(CSRF_COOKIE_NAME)?.value) {
    return;
  }
  mutations.push(buildCookieMutation(CSRF_COOKIE_NAME, createCsrfToken(), 24 * 60 * 60, false));
}

export async function resolveSession(
  request: NextRequest,
  options?: { forceGatewaySync?: boolean }
): Promise<{ session: SessionPayload; accessToken: string | null; cookieMutations: CookieMutation[] }> {
  const cookieMutations: CookieMutation[] = [];
  let refreshedTokens = false;
  const { sessionId, storedTokens: initialTokens } = await readStoredTokens(request);
  let storedTokens = initialTokens;

  if (!storedTokens) {
    return {
      session: {
        authenticated: false,
        claims: null,
        profile: null,
        expiresAt: null,
      },
      accessToken: null,
      cookieMutations: clearAuthCookieMutations(),
    };
  }

  if (shouldRefresh(storedTokens)) {
    try {
      const refreshed = await refreshTokens(storedTokens);
      refreshedTokens = true;
      storedTokens = toStoredTokens(refreshed, storedTokens);
      const ttlSeconds = await updateBffSession(sessionId, storedTokens);
      cookieMutations.push(buildSessionCookieMutation(sessionId, ttlSeconds));
    } catch {
      await deleteBffSession(sessionId);
      return {
        session: {
          authenticated: false,
          claims: null,
          profile: null,
          expiresAt: null,
        },
        accessToken: null,
        cookieMutations: clearAuthCookieMutations(),
      };
    }
  }

  ensureCsrfCookie(request, cookieMutations);

  if (shouldSyncGateway(request, Boolean(options?.forceGatewaySync) || refreshedTokens)) {
    try {
      await syncGatewaySession(storedTokens.accessToken);
      cookieMutations.push(gatewaySyncCookieMutation());
    } catch (error) {
      if (error instanceof SessionSyncError && (error.status === 401 || error.status === 403)) {
        await deleteBffSession(sessionId);
        return {
          session: {
            authenticated: false,
            claims: null,
            profile: null,
            expiresAt: null,
          },
          accessToken: null,
          cookieMutations: clearAuthCookieMutations(),
        };
      }
      throw error;
    }
  }

  return {
    session: {
      authenticated: true,
      claims: storedTokens.claims,
      profile: buildProfile(storedTokens.claims),
      expiresAt: new Date(storedTokens.accessExpiresAtEpochSeconds * 1000).toISOString(),
    },
    accessToken: storedTokens.accessToken,
    cookieMutations,
  };
}

export function assertValidCsrf(request: NextRequest): void {
  const method = request.method.toUpperCase();
  if (method === "GET" || method === "HEAD" || method === "OPTIONS") {
    return;
  }

  const hasAuthCookies = Boolean(request.cookies.get(SESSION_COOKIE)?.value);
  if (!hasAuthCookies) {
    return;
  }

  const cookieToken = request.cookies.get(CSRF_COOKIE_NAME)?.value || "";
  const headerToken = (request.headers.get(CSRF_HEADER_NAME) || "").trim();
  const cookieBuffer = Buffer.from(cookieToken);
  const headerBuffer = Buffer.from(headerToken);
  if (!cookieToken
    || !headerToken
    || cookieBuffer.length !== headerBuffer.length
    || !crypto.timingSafeEqual(cookieBuffer, headerBuffer)) {
    throw new CsrfValidationError();
  }
}

export async function buildAuthorizationRedirect(
  request: NextRequest,
  mode: "login" | "signup" | "change-password"
): Promise<{ redirectUrl: string; cookieMutations: CookieMutation[] }> {
  const nonce = createStateNonce();
  const returnTo = normalizeReturnTo(request, request.nextUrl.searchParams.get("returnTo"));
  const fingerprint = buildOauthBrowserFingerprint(request);
  const verifier = derivePkceVerifier(nonce, fingerprint);
  const state = encodeOauthStateToken({
    version: OAUTH_STATE_VERSION,
    nonce,
    fingerprint,
    issuedAtEpochSeconds: Math.floor(Date.now() / 1000),
  });
  const redirectUri = `${resolveAppOrigin(request)}/api/auth/callback`;
  const endpoint = authorizationEndpoint(mode === "signup" ? "signup" : "login");
  const params = new URLSearchParams({
    client_id: getBffClientId(),
    redirect_uri: redirectUri,
    response_type: "code",
    scope: getRequestedScope(),
    state,
    code_challenge: createCodeChallenge(verifier),
    code_challenge_method: "S256",
  });

  if (mode === "change-password") {
    params.set("kc_action", "UPDATE_PASSWORD");
  }

  return {
    redirectUrl: `${endpoint}?${params.toString()}`,
    cookieMutations: [
      buildCookieMutation(OAUTH_STATE_COOKIE, state, OAUTH_STATE_TTL_SECONDS, true),
      buildCookieMutation(OAUTH_RETURN_TO_COOKIE, returnTo, OAUTH_STATE_TTL_SECONDS, true),
    ],
  };
}

export async function exchangeCallback(request: NextRequest): Promise<{ redirectTo: string; cookieMutations: CookieMutation[] }> {
  const code = (request.nextUrl.searchParams.get("code") || "").trim();
  const state = (request.nextUrl.searchParams.get("state") || "").trim();
  const expectedState = (request.cookies.get(OAUTH_STATE_COOKIE)?.value || "").trim();
  const cookieReturnTo = (request.cookies.get(OAUTH_RETURN_TO_COOKIE)?.value || "").trim();
  const clearFlowCookies = [
    deleteCookieMutation(OAUTH_STATE_COOKIE),
    deleteCookieMutation(OAUTH_VERIFIER_COOKIE),
    deleteCookieMutation(OAUTH_RETURN_TO_COOKIE),
    deleteCookieMutation(GATEWAY_SYNC_COOKIE),
  ];

  if (!code || !state) {
    return {
      redirectTo: `${resolveAppOrigin(request)}/?authError=invalid_callback`,
      cookieMutations: [...clearAuthCookieMutations(), ...clearFlowCookies],
    };
  }

  const currentFingerprint = buildOauthBrowserFingerprint(request);
  const decodedState = decodeOauthStateToken(state, currentFingerprint);
  if (!decodedState || !isRecentOauthState(decodedState)) {
    return {
      redirectTo: `${resolveAppOrigin(request)}/?authError=invalid_callback`,
      cookieMutations: [...clearAuthCookieMutations(), ...clearFlowCookies],
    };
  }

  if (expectedState && !safeEqualText(state, expectedState)) {
    return {
      redirectTo: `${resolveAppOrigin(request)}/?authError=invalid_callback`,
      cookieMutations: [...clearAuthCookieMutations(), ...clearFlowCookies],
    };
  }

  const verifier = derivePkceVerifier(decodedState.nonce, currentFingerprint);
  const returnTo = normalizeReturnTo(request, cookieReturnTo || "/");

  try {
    const form = new URLSearchParams({
      grant_type: "authorization_code",
      client_id: getBffClientId(),
      code,
      redirect_uri: `${resolveAppOrigin(request)}/api/auth/callback`,
      code_verifier: verifier,
    });
    const tokenResponse = await requestToken(form);
    const storedTokens = toStoredTokens(tokenResponse);
    const { sessionId, ttlSeconds } = await createBffSession({
      accessToken: storedTokens.accessToken,
      refreshToken: storedTokens.refreshToken,
      idToken: storedTokens.idToken,
      accessExpiresAtEpochSeconds: storedTokens.accessExpiresAtEpochSeconds,
      refreshExpiresAtEpochSeconds: storedTokens.refreshExpiresAtEpochSeconds,
      claims: storedTokens.claims,
    });
    const cookieMutations = [
      buildSessionCookieMutation(sessionId, ttlSeconds),
      ...clearFlowCookies,
      buildCookieMutation(CSRF_COOKIE_NAME, createCsrfToken(), 24 * 60 * 60, false),
    ];
    await syncGatewaySession(storedTokens.accessToken);
    cookieMutations.push(gatewaySyncCookieMutation());
    if (await mergeGuestCart(storedTokens.accessToken, request)) {
      cookieMutations.push(...clearGuestCartCookieMutations());
    }
    return {
      redirectTo: returnTo,
      cookieMutations,
    };
  } catch {
    return {
      redirectTo: `${resolveAppOrigin(request)}/?authError=callback_failed`,
      cookieMutations: [...clearAuthCookieMutations(), ...clearFlowCookies],
    };
  }
}

export async function buildLogoutRedirect(request: NextRequest): Promise<{ redirectUrl: string; cookieMutations: CookieMutation[] }> {
  const { sessionId, storedTokens } = await readStoredTokens(request);
  const returnTo = normalizeReturnTo(request, request.nextUrl.searchParams.get("returnTo"));

  if (storedTokens?.accessToken) {
    try {
      await fetch(`${getUpstreamGatewayBaseUrl()}/auth/logout`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${storedTokens.accessToken}`,
          Accept: "application/json",
        },
        cache: "no-store",
      });
    } catch {
      // Continue with browser/IdP logout and local cookie cleanup.
    }
  }

  const params = new URLSearchParams({
    client_id: getBffClientId(),
    post_logout_redirect_uri: returnTo,
  });
  if (storedTokens?.idToken) {
    params.set("id_token_hint", storedTokens.idToken);
  }

  await deleteBffSession(sessionId);

  return {
    redirectUrl: `${logoutEndpoint()}?${params.toString()}`,
    cookieMutations: clearAuthCookieMutations(),
  };
}

function copyUpstreamHeaders(source: Headers, target: Headers): void {
  const allowed = new Set([
    "cache-control",
    "content-type",
    "etag",
    "last-modified",
    "location",
    "retry-after",
    "x-request-id",
    "x-idempotency-status",
    "x-ratelimit-remaining",
    "x-ratelimit-burst-capacity",
    "x-ratelimit-replenish-rate",
    "x-ratelimit-requested-tokens",
    "x-ratelimit-policy",
    "x-accel-buffering",
  ]);

  source.forEach((value, key) => {
    if (allowed.has(key.toLowerCase())) {
      target.set(key, value);
    }
  });
}

export async function proxyToGateway(request: NextRequest, pathSegments: string[]): Promise<NextResponse> {
  assertValidCsrf(request);

  let resolved;
  try {
    resolved = await resolveSession(request, { forceGatewaySync: false });
  } catch (error) {
    if (error instanceof SessionSyncError) {
      const response = NextResponse.json(
        { message: error.message },
        { status: error.status >= 500 ? 503 : error.status }
      );
      applyCookieMutations(response, clearAuthCookieMutations());
      return response;
    }
    throw error;
  }

  const cookieMutations = [...resolved.cookieMutations];
  const isCartPath = pathSegments[0] === "cart" && pathSegments[1] === "me";
  let guestCart = readGuestCartCookies(request);
  if (isCartPath && !resolved.accessToken) {
    guestCart = guestCart ?? ensureGuestCartCookies(request, cookieMutations);
  }

  const upstreamUrl = `${getUpstreamGatewayBaseUrl()}/${pathSegments.join("/")}${request.nextUrl.search}`;
  const headers = new Headers();
  const allowedRequestHeaders = [
    "accept",
    "content-type",
    "idempotency-key",
    "x-requested-with",
    "x-session-id",
    CSRF_HEADER_NAME.toLowerCase(),
  ];

  for (const headerName of allowedRequestHeaders) {
    const value = request.headers.get(headerName);
    if (value) {
      headers.set(headerName, value);
    }
  }

  if (resolved.accessToken) {
    headers.set("Authorization", `Bearer ${resolved.accessToken}`);
  }
  if (isCartPath && guestCart) {
    headers.set("Cookie", serializeGuestCartCookieHeader(guestCart));
  }

  const method = request.method.toUpperCase();
  const hasBody = method !== "GET" && method !== "HEAD";
  const body = hasBody ? await request.arrayBuffer() : undefined;
  const upstreamResponse = await fetch(upstreamUrl, {
    method,
    headers,
    body,
    cache: "no-store",
    redirect: "manual",
  });

  const responseHeaders = new Headers();
  copyUpstreamHeaders(upstreamResponse.headers, responseHeaders);
  const response = new NextResponse(upstreamResponse.body, {
    status: upstreamResponse.status,
    headers: responseHeaders,
  });

  if (upstreamResponse.headers.get("x-cart-session-cleared") === "true") {
    cookieMutations.push(...clearGuestCartCookieMutations());
  }

  applyCookieMutations(response, cookieMutations);
  return response;
}
