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
  if (typeof err === "object" && err !== null) {
    const maybe = err as {
      message?: string;
      response?: { data?: { message?: string; error?: string } | string };
    };
    const data = maybe.response?.data;
    if (typeof data === "string" && data.trim()) return data.trim();
    if (data && typeof data === "object") {
      if (typeof data.message === "string" && data.message.trim()) return data.message.trim();
      if (typeof data.error === "string" && data.error.trim()) return data.error.trim();
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) return maybe.message.trim();
  }
  return fallback;
}

