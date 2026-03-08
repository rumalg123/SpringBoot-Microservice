"use client";

import { useEffect, useRef } from "react";

type AnalyticsLiveRefreshEvent = {
  scope: string;
  vendorId?: string | null;
  trigger: string;
  occurredAt: string;
};

type UseAnalyticsLiveStreamOptions = {
  enabled: boolean;
  url: string;
  onRefresh: (event: AnalyticsLiveRefreshEvent) => void;
  minRefreshIntervalMs?: number;
};

type ParsedSseEvent = {
  event: string;
  data: string;
};

const DEFAULT_MIN_REFRESH_INTERVAL_MS = 1_500;
const INITIAL_RETRY_DELAY_MS = 1_000;
const MAX_RETRY_DELAY_MS = 30_000;

function parseEventBlock(block: string): ParsedSseEvent | null {
  const lines = block.split("\n");
  let eventName = "message";
  const dataLines: string[] = [];

  for (const line of lines) {
    if (!line || line.startsWith(":")) {
      continue;
    }

    const separatorIndex = line.indexOf(":");
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
    let value = separatorIndex === -1 ? "" : line.slice(separatorIndex + 1);
    if (value.startsWith(" ")) {
      value = value.slice(1);
    }

    if (field === "event") {
      eventName = value || "message";
    } else if (field === "data") {
      dataLines.push(value);
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  return {
    event: eventName,
    data: dataLines.join("\n"),
  };
}

function parseBuffer(buffer: string): { events: ParsedSseEvent[]; remainder: string } {
  const normalized = buffer.replace(/\r/g, "");
  const chunks = normalized.split("\n\n");
  const remainder = chunks.pop() ?? "";
  const events = chunks
    .map(parseEventBlock)
    .filter((event): event is ParsedSseEvent => event !== null);
  return { events, remainder };
}

export function useAnalyticsLiveStream({
  enabled,
  url,
  onRefresh,
  minRefreshIntervalMs = DEFAULT_MIN_REFRESH_INTERVAL_MS,
}: UseAnalyticsLiveStreamOptions): void {
  const onRefreshRef = useRef(onRefresh);
  const lastRefreshAtRef = useRef(0);

  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    if (!enabled || !url) {
      return;
    }

    let cancelled = false;
    let reconnectDelayMs = INITIAL_RETRY_DELAY_MS;
    let reconnectTimer: number | null = null;
    let activeController: AbortController | null = null;

    const clearReconnectTimer = () => {
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
    };

    const scheduleReconnect = () => {
      if (cancelled) {
        return;
      }
      clearReconnectTimer();
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null;
        void connect();
      }, reconnectDelayMs);
      reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RETRY_DELAY_MS);
    };

    const handleEvent = (event: ParsedSseEvent) => {
      if (event.event !== "dashboard-refresh") {
        return;
      }

      try {
        const parsed = JSON.parse(event.data) as AnalyticsLiveRefreshEvent;
        const now = Date.now();
        if (now - lastRefreshAtRef.current < minRefreshIntervalMs) {
          return;
        }
        lastRefreshAtRef.current = now;
        onRefreshRef.current(parsed);
      } catch {
        // Ignore malformed live events and keep the stream running.
      }
    };

    const connect = async () => {
      clearReconnectTimer();
      if (cancelled) {
        return;
      }

      const controller = new AbortController();
      activeController = controller;

      try {
        const response = await fetch(url, {
          method: "GET",
          headers: {
            Accept: "text/event-stream",
            "Cache-Control": "no-cache",
          },
          cache: "no-store",
          credentials: "same-origin",
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`Live stream failed with status ${response.status}`);
        }

        reconnectDelayMs = INITIAL_RETRY_DELAY_MS;
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (!cancelled) {
          const { done, value } = await reader.read();
          if (done) {
            break;
          }
          buffer += decoder.decode(value, { stream: true });
          const parsed = parseBuffer(buffer);
          buffer = parsed.remainder;
          for (const event of parsed.events) {
            handleEvent(event);
          }
        }

        buffer += decoder.decode();
        const parsed = parseBuffer(buffer);
        for (const event of parsed.events) {
          handleEvent(event);
        }
      } catch {
        if (cancelled || controller.signal.aborted) {
          return;
        }
      } finally {
        if (activeController === controller) {
          activeController = null;
        }
        if (!cancelled) {
          scheduleReconnect();
        }
      }
    };

    void connect();

    return () => {
      cancelled = true;
      clearReconnectTimer();
      activeController?.abort();
    };
  }, [enabled, url, minRefreshIntervalMs]);
}
