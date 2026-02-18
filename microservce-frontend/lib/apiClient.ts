import axios, { AxiosError, AxiosInstance, AxiosRequestConfig } from "axios";

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
    config.headers = {
      ...(config.headers ?? {}),
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    };
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
      const status = error.response?.status;
      const statusText = error.response?.statusText;
      const data = error.response?.data;
      const body = typeof data === "string" ? data : JSON.stringify(data);
      const message = status ? `${status} ${statusText}: ${body}` : error.message;

      if (options.onError) {
        options.onError(message);
      }
      return Promise.reject(new Error(message));
    }
  );

  return client;
}
