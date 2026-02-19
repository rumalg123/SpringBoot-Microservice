import axios, { AxiosError, AxiosHeaders, AxiosInstance, AxiosRequestConfig } from "axios";

type CreateApiClientOptions = {
  baseURL: string;
  getToken: () => Promise<string>;
  onError?: (message: string) => void;
};

export type ApiRequestConfig = AxiosRequestConfig & {
  skipAuth?: boolean;
};

export function createApiClient(options: CreateApiClientOptions): AxiosInstance {
  const client = axios.create({
    baseURL: options.baseURL,
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
