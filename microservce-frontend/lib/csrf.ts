export const CSRF_COOKIE_NAME = "rs_csrf_token";
export const CSRF_HEADER_NAME = "X-CSRF-Token";

export function getCsrfToken(): string {
  if (typeof document === "undefined") {
    return "";
  }

  const escapedName = CSRF_COOKIE_NAME.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : "";
}
