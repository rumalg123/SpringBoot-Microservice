"use client";

import { useEffect, useMemo, useState } from "react";
import type { AxiosInstance } from "axios";

export type KeycloakUserLookupResult = {
  id: string;
  email?: string | null;
  username?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  displayName?: string | null;
  enabled?: boolean;
  emailVerified?: boolean;
};

type KeycloakUserLookupFieldProps = {
  apiClient: AxiosInstance | null;
  disabled?: boolean;
  label?: string;
  helperText?: string;
  onSelect: (user: KeycloakUserLookupResult) => void;
};

function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as { response?: { data?: { error?: string; message?: string } }; message?: string };
    return maybe.response?.data?.error || maybe.response?.data?.message || maybe.message || "Search failed";
  }
  return "Search failed";
}

export default function KeycloakUserLookupField({
  apiClient,
  disabled = false,
  label = "Find Keycloak User",
  helperText = "Search by email, name or username and click a result to autofill the fields below.",
  onSelect,
}: KeycloakUserLookupFieldProps) {
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [results, setResults] = useState<KeycloakUserLookupResult[]>([]);
  const [expanded, setExpanded] = useState(false);

  const normalizedQuery = query.trim();

  useEffect(() => {
    if (!apiClient || disabled) {
      setResults([]);
      setLoading(false);
      return;
    }
    if (normalizedQuery.length < 2) {
      setResults([]);
      setLoading(false);
      setError("");
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      setError("");
      try {
        const res = await apiClient.get("/admin/keycloak/users/search", {
          params: { q: normalizedQuery, limit: 8 },
        });
        if (cancelled) return;
        setResults(Array.isArray(res.data) ? (res.data as KeycloakUserLookupResult[]) : []);
        setExpanded(true);
      } catch (err) {
        if (cancelled) return;
        setResults([]);
        setError(getErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [apiClient, disabled, normalizedQuery]);

  const hasResults = results.length > 0;
  const showDropdown = expanded && (loading || hasResults || Boolean(error)) && normalizedQuery.length >= 2;

  const statusText = useMemo(() => {
    if (loading) return "Searching...";
    if (error) return error;
    if (!hasResults && normalizedQuery.length >= 2) return "No users found.";
    return "";
  }, [loading, error, hasResults, normalizedQuery.length]);

  return (
    <div className="space-y-2">
      <label className="space-y-1 text-sm">
        <span
          className="block text-xs font-semibold uppercase tracking-[0.12em]"
          style={{ color: "rgba(255,255,255,0.65)" }}
        >
          {label}
        </span>
        <input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setExpanded(true);
          }}
          onFocus={() => setExpanded(true)}
          disabled={disabled || !apiClient}
          className="w-full rounded-xl px-3 py-2"
          style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
          placeholder="Search user by email, name or username"
        />
      </label>

      <p className="text-xs" style={{ color: "rgba(255,255,255,0.55)" }}>
        {helperText}
      </p>

      {showDropdown && (
        <div
          className="max-h-72 overflow-auto rounded-xl"
          style={{ border: "1px solid var(--line)", background: "var(--surface-2)" }}
        >
          {statusText && !hasResults ? (
            <div className="px-3 py-3 text-sm" style={{ color: error ? "#fca5a5" : "rgba(255,255,255,0.65)" }}>
              {statusText}
            </div>
          ) : (
            <div className="divide-y" style={{ borderColor: "rgba(255,255,255,0.05)" }}>
              {results.map((user) => {
                const displayName = (user.displayName || "").trim() || (user.email || "").trim() || (user.username || "").trim() || user.id;
                return (
                  <button
                    key={user.id}
                    type="button"
                    onClick={() => {
                      onSelect(user);
                      setExpanded(false);
                      setQuery(user.email || user.username || displayName);
                    }}
                    className="flex w-full items-start justify-between gap-3 px-3 py-3 text-left hover:bg-white/5"
                    style={{ color: "var(--ink)" }}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold">{displayName}</span>
                      <span className="block truncate text-xs" style={{ color: "rgba(255,255,255,0.65)" }}>
                        {user.email || user.username || "No email/username"}
                      </span>
                      <span className="block truncate font-mono text-[11px]" style={{ color: "rgba(255,255,255,0.45)" }}>
                        {user.id}
                      </span>
                    </span>
                    <span className="shrink-0 text-[10px]" style={{ color: "rgba(255,255,255,0.55)" }}>
                      {(user.enabled ?? true) ? "Enabled" : "Disabled"}
                      {" | "}
                      {(user.emailVerified ?? false) ? "Email verified" : "Email unverified"}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
