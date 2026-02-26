import axios, { AxiosError, AxiosHeaders, AxiosInstance, AxiosRequestConfig } from "axios";
import { IDEMPOTENCY_WINDOW_MS, IDEMPOTENCY_MAX_CACHE_SIZE } from "./constants";

type CreateApiClientOptions = {
  baseURL: string;
  getToken: () => Promise<string>;
  onError?: (message: string) => void;
};

export type ApiRequestConfig = AxiosRequestConfig & {
  skipAuth?: boolean;
  disableIdempotency?: boolean;
  idempotencyKey?: string;
  idempotencyWindowMs?: number;
};

type CachedIdempotencyKey = {
  key: string;
  expiresAt: number;
};

const idempotencyKeyCache = new Map<string, CachedIdempotencyKey>();
const DEFAULT_IDEMPOTENCY_WINDOW_MS = IDEMPOTENCY_WINDOW_MS;

function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

function normalizeForStableJson(value: unknown): unknown {
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map((item) => normalizeForStableJson(item));
  const obj = value as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const key of Object.keys(obj).sort()) {
    const normalized = normalizeForStableJson(obj[key]);
    if (normalized !== undefined) {
      out[key] = normalized;
    }
  }
  return out;
}

function stableStringify(value: unknown): string {
  try {
    return JSON.stringify(normalizeForStableJson(value));
  } catch {
    return String(value);
  }
}

function cleanupExpiredIdempotencyKeys(now: number): void {
  for (const [fingerprint, entry] of idempotencyKeyCache.entries()) {
    if (entry.expiresAt <= now) {
      idempotencyKeyCache.delete(fingerprint);
    }
  }
}

function resolveIdempotencyKeyForFingerprint(fingerprint: string, windowMs: number): string {
  const now = Date.now();
  cleanupExpiredIdempotencyKeys(now);
  const existing = idempotencyKeyCache.get(fingerprint);
  if (existing && existing.expiresAt > now) {
    return existing.key;
  }
  // Evict oldest entries if cache exceeds max size
  if (idempotencyKeyCache.size >= IDEMPOTENCY_MAX_CACHE_SIZE) {
    const oldest = idempotencyKeyCache.keys().next().value;
    if (oldest !== undefined) idempotencyKeyCache.delete(oldest);
  }
  const created = generateIdempotencyKey();
  idempotencyKeyCache.set(fingerprint, {
    key: created,
    expiresAt: now + Math.max(windowMs, 1_000),
  });
  return created;
}

export function createApiClient(options: CreateApiClientOptions): AxiosInstance {
  const client = axios.create({
    baseURL: options.baseURL,
    timeout: 30_000,
  });

  client.interceptors.request.use(async (config) => {
    const skipAuth = (config as ApiRequestConfig).skipAuth;
    if (skipAuth) {
      return config;
    }

    const token = await options.getToken();
    const headers = new AxiosHeaders(config.headers);
    headers.set("Authorization", `Bearer ${token}`);
    const isFormData = typeof FormData !== "undefined" && config.data instanceof FormData;
    const method = (config.method || "get").toLowerCase();
    const isMutating = method === "post" || method === "put" || method === "patch" || method === "delete";
    const requestConfig = config as ApiRequestConfig;

    if (isMutating && !requestConfig.disableIdempotency && !headers.has("Idempotency-Key")) {
      if (requestConfig.idempotencyKey && requestConfig.idempotencyKey.trim()) {
        headers.set("Idempotency-Key", requestConfig.idempotencyKey.trim());
      } else {
        const windowMs = requestConfig.idempotencyWindowMs ?? DEFAULT_IDEMPOTENCY_WINDOW_MS;
        const urlPart = String(config.url || "");
        const paramsPart = stableStringify(config.params ?? null);
        const bodyPart = isFormData ? "[multipart-form-data]" : stableStringify(config.data ?? null);
        const fingerprint = `${method.toUpperCase()}|${urlPart}|${paramsPart}|${bodyPart}`;
        headers.set("Idempotency-Key", resolveIdempotencyKeyForFingerprint(fingerprint, windowMs));
      }
    }

    if (isFormData) {
      headers.delete("Content-Type");
    } else if (!headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    config.headers = headers;
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
      const status = error.response?.status;
      const statusText = error.response?.statusText;
      const data = error.response?.data;
      const extractMessage = (payload: unknown): string => {
        if (typeof payload === "string") return payload;
        if (payload && typeof payload === "object") {
          const obj = payload as Record<string, unknown>;
          if (typeof obj.message === "string" && obj.message.trim()) return obj.message;
          if (typeof obj.error === "string" && obj.error.trim()) return obj.error;
        }
        return "";
      };
      const detail = extractMessage(data);
      const message = status
        ? detail
          ? `${status} ${statusText}: ${detail}`
          : `${status} ${statusText}`
        : error.message;

      if (options.onError) {
        options.onError(message);
      }
      return Promise.reject(new Error(message));
    }
  );

  return client;
}
