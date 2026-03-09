import crypto from "node:crypto";
import { createClient } from "redis";

export type BffSessionRecord = {
  accessToken: string;
  refreshToken: string | null;
  idToken: string | null;
  accessExpiresAtEpochSeconds: number;
  refreshExpiresAtEpochSeconds: number | null;
  claims: Record<string, unknown>;
};

export class SessionStoreError extends Error {
  constructor(message: string, cause?: unknown) {
    super(message);
    this.name = "SessionStoreError";
    if (cause) {
      this.cause = cause;
    }
  }
}

const DEFAULT_SESSION_PREFIX = "bff:sessions:";
type BffRedisClient = ReturnType<typeof createClient>;

declare global {
  var __bffRedisClientPromise: Promise<BffRedisClient> | undefined;
}

function getRedisUrl(): string {
  const explicit = (process.env.BFF_SESSION_REDIS_URL || process.env.REDIS_URL || "").trim();
  if (explicit) {
    return explicit;
  }
  const host = (process.env.REDIS_HOST || "").trim();
  const port = (process.env.REDIS_PORT || "6379").trim();
  if (!host) {
    throw new SessionStoreError("BFF session Redis is not configured");
  }
  return `redis://${host}:${port}`;
}

function getSessionPrefix(): string {
  const configured = (process.env.BFF_SESSION_PREFIX || "").trim();
  return configured || DEFAULT_SESSION_PREFIX;
}

function getSessionKey(sessionId: string): string {
  return `${getSessionPrefix()}${sessionId}`;
}

function computeTtlSeconds(record: BffSessionRecord): number {
  const nowEpochSeconds = Math.floor(Date.now() / 1000);
  const hardExpiry = record.refreshExpiresAtEpochSeconds ?? record.accessExpiresAtEpochSeconds;
  return Math.max(60, hardExpiry - nowEpochSeconds);
}

async function getRedisClient(): Promise<BffRedisClient> {
  if (!global.__bffRedisClientPromise) {
    global.__bffRedisClientPromise = (async () => {
      const client = createClient({ url: getRedisUrl() });
      client.on("error", (error) => {
        console.error("BFF session Redis error", error);
      });
      await client.connect();
      return client;
    })();
  }
  try {
    return await global.__bffRedisClientPromise;
  } catch (error) {
    global.__bffRedisClientPromise = undefined;
    throw new SessionStoreError("BFF session Redis is unavailable", error);
  }
}

export async function createBffSession(record: BffSessionRecord): Promise<{ sessionId: string; ttlSeconds: number }> {
  const client = await getRedisClient();
  const sessionId = crypto.randomUUID();
  const ttlSeconds = computeTtlSeconds(record);
  try {
    await client.set(getSessionKey(sessionId), JSON.stringify(record), { EX: ttlSeconds });
  } catch (error) {
    throw new SessionStoreError("Failed to create BFF session", error);
  }
  return { sessionId, ttlSeconds };
}

export async function readBffSession(sessionId: string): Promise<BffSessionRecord | null> {
  const normalized = sessionId.trim();
  if (!normalized) {
    return null;
  }
  const client = await getRedisClient();
  let raw: string | null;
  try {
    raw = await client.get(getSessionKey(normalized));
  } catch (error) {
    throw new SessionStoreError("Failed to read BFF session", error);
  }
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as BffSessionRecord;
  } catch {
    await deleteBffSession(normalized);
    return null;
  }
}

export async function updateBffSession(sessionId: string, record: BffSessionRecord): Promise<number> {
  const normalized = sessionId.trim();
  if (!normalized) {
    throw new SessionStoreError("Cannot update an empty BFF session id");
  }
  const client = await getRedisClient();
  const ttlSeconds = computeTtlSeconds(record);
  try {
    await client.set(getSessionKey(normalized), JSON.stringify(record), { EX: ttlSeconds });
  } catch (error) {
    throw new SessionStoreError("Failed to update BFF session", error);
  }
  return ttlSeconds;
}

export async function deleteBffSession(sessionId: string | null | undefined): Promise<void> {
  const normalized = (sessionId || "").trim();
  if (!normalized) {
    return;
  }
  const client = await getRedisClient();
  try {
    await client.del(getSessionKey(normalized));
  } catch (error) {
    throw new SessionStoreError("Failed to delete BFF session", error);
  }
}
