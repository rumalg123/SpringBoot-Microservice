import type { AxiosError } from "axios";

/** Extract a user-friendly error message from an unknown error. */
export function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as {
      response?: { data?: { error?: string; message?: string } };
      message?: string;
    };
    return (
      maybe.response?.data?.error ||
      maybe.response?.data?.message ||
      maybe.message ||
      "Request failed"
    );
  }
  return "Request failed";
}

/** True if the error is a network/connection failure (no response received). */
export function isNetworkError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const axErr = err as AxiosError;
  return axErr.code === "ERR_NETWORK" || axErr.message === "Network Error";
}

/** True if the request timed out. */
export function isTimeoutError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const axErr = err as AxiosError;
  return axErr.code === "ECONNABORTED" || axErr.code === "ERR_CANCELED";
}

/** True if the server returned a 400/422 validation error. */
export function isValidationError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const axErr = err as AxiosError;
  const status = axErr.response?.status;
  return status === 400 || status === 422;
}

/** Extract per-field validation errors from a 400/422 response. */
export function getValidationErrors(err: unknown): Record<string, string> {
  if (typeof err !== "object" || err === null) return {};
  const axErr = err as AxiosError<{ fieldErrors?: Record<string, string>; errors?: Record<string, string> }>;
  const data = axErr.response?.data;
  if (!data || typeof data !== "object") return {};
  return data.fieldErrors ?? data.errors ?? {};
}

/** All-in-one error handler: returns an appropriate message for the error type. */
export function handleApiError(err: unknown, fallback = "Something went wrong"): string {
  if (isNetworkError(err)) return "Network error — please check your connection";
  if (isTimeoutError(err)) return "Request timed out — please try again";
  return getErrorMessage(err) || fallback;
}
