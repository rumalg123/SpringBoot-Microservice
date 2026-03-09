export function slugify(value: string) {
  return value
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .replace(/-+/g, "-");
}

export function splitName(fullName: string) {
  const normalized = fullName.trim().replace(/\s+/g, " ");
  if (!normalized) return { firstName: "", lastName: "" };
  const i = normalized.indexOf(" ");
  if (i < 0) return { firstName: normalized, lastName: "" };
  return {
    firstName: normalized.slice(0, i).trim(),
    lastName: normalized.slice(i + 1).trim(),
  };
}

export function getApiErrorMessage(err: unknown, fallback: string) {
  const extractNestedError = (value: string): string => {
    const trimmed = value.trim();
    if (!trimmed) return "";
    const parseJsonError = (candidate: string): string => {
      try {
        const parsed = JSON.parse(candidate) as { message?: unknown; error?: unknown };
        if (typeof parsed.message === "string" && parsed.message.trim()) return parsed.message.trim();
        if (typeof parsed.error === "string" && parsed.error.trim()) return parsed.error.trim();
      } catch {
        return "";
      }
      return "";
    };
    const direct = parseJsonError(trimmed);
    if (direct) return direct;
    const jsonStart = trimmed.indexOf("{");
    if (jsonStart >= 0) {
      const nested = parseJsonError(trimmed.slice(jsonStart));
      if (nested) return nested;
    }
    const separator = trimmed.lastIndexOf(":");
    if (separator >= 0 && separator < trimmed.length - 1) {
      const suffix = trimmed.slice(separator + 1).trim();
      if (suffix) return suffix;
    }
    return trimmed;
  };

  if (typeof err === "object" && err !== null) {
    const maybe = err as {
      message?: string;
      response?: { data?: { message?: string; error?: string } | string };
    };
    const data = maybe.response?.data;
    if (typeof data === "string" && data.trim()) return extractNestedError(data);
    if (data && typeof data === "object") {
      if (typeof data.message === "string" && data.message.trim()) return extractNestedError(data.message);
      if (typeof data.error === "string" && data.error.trim()) return extractNestedError(data.error);
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) return extractNestedError(maybe.message);
  }
  return fallback;
}
