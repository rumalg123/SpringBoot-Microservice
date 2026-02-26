const usdFormatter = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

/** Format a number as USD currency.  Handles null/NaN gracefully. */
export function money(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "\u2014";
  return usdFormatter.format(value);
}

/** Percentage discount between regular and selling price. */
export function calcDiscount(regular: number, selling: number): number | null {
  if (regular > selling && regular > 0) return Math.round(((regular - selling) / regular) * 100);
  return null;
}

/** Locale date string (e.g. "Feb 26, 2026"). */
export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
}

/** Locale date + time string. */
export function formatDateTime(date: string | Date): string {
  return new Date(date).toLocaleString("en-US", {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

/** Relative time string (e.g. "2 hours ago", "3 days ago"). */
export function formatRelativeTime(date: string | Date): string {
  const ms = Date.now() - new Date(date).getTime();
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return formatDate(date);
}

/** Truncate text to a max length with ellipsis. */
export function truncateText(text: string, max: number): string {
  if (text.length <= max) return text;
  return text.slice(0, max).trimEnd() + "\u2026";
}

/** Abbreviate large numbers (e.g. 1200 → "1.2K", 3400000 → "3.4M"). */
export function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1).replace(/\.0$/, "")}K`;
  return String(n);
}

/** Format a number as a percentage. */
export function formatPercentage(value: number, decimals = 0): string {
  return `${value.toFixed(decimals)}%`;
}
