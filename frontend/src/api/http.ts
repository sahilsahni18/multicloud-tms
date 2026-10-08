import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type { AuthResponse, Problem } from './types';

/**
 * One axios instance for the whole app.
 *
 * - Adds the in-memory access token to every request.
 * - On a 401 it calls /auth/refresh once (the refresh token is an httpOnly
 *   cookie the browser sends by itself), then retries the original request.
 *   Concurrent 401s share the same refresh call.
 * - If the refresh fails the session is over and onSessionExpired runs.
 */
export const http = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`,
  withCredentials: true,
});

interface SessionHooks {
  getToken: () => string | null;
  onRefreshed: (auth: AuthResponse) => void;
  onSessionExpired: () => void;
}

let hooks: SessionHooks | null = null;
let refreshing: Promise<AuthResponse> | null = null;

export function configureSession(sessionHooks: SessionHooks) {
  hooks = sessionHooks;
}

/** Exchanges the refresh cookie for a new access token. Deduplicated across callers. */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshing) {
    refreshing = http
      .post<AuthResponse>('/auth/refresh', null, { skipAuthRefresh: true } as AxiosRequestConfig)
      .then((res) => {
        hooks?.onRefreshed(res.data);
        return res.data;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

http.interceptors.request.use((config) => {
  const token = hooks?.getToken();
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type RetryableConfig = AxiosRequestConfig & { _retried?: boolean; skipAuthRefresh?: boolean };

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryableConfig | undefined;
    const isAuthCall = config?.url?.startsWith('/auth/');
    if (error.response?.status !== 401 || !config || config._retried || config.skipAuthRefresh || isAuthCall) {
      return Promise.reject(error);
    }
    config._retried = true;
    try {
      const auth = await refreshSession();
      config.headers = { ...config.headers, Authorization: `Bearer ${auth.accessToken}` };
      return http(config);
    } catch {
      hooks?.onSessionExpired();
      return Promise.reject(error);
    }
  },
);

/** Human-readable message from any API error. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  const problem = problemOf(error);
  if (problem?.errors?.length) {
    return problem.errors.map((e) => `${e.field}: ${e.message}`).join(', ');
  }
  if (problem?.detail) return problem.detail;
  if (error instanceof AxiosError && !error.response) return 'Cannot reach the server';
  return fallback;
}

/** Finds the problem+json body in an axios error, an RTK Query error, or a rejected thunk payload. */
export function problemOf(error: unknown): Problem | undefined {
  if (error instanceof AxiosError) {
    const data = error.response?.data;
    return data && typeof data === 'object' && 'title' in data ? (data as Problem) : undefined;
  }
  if (error && typeof error === 'object') {
    if ('detail' in error || ('title' in error && 'status' in error)) return error as Problem;
    if ('data' in error) return (error as { data?: Problem }).data;
  }
  return undefined;
}

/** Downloads a file from the API (used for the CSV export). */
export async function download(url: string, params: unknown, fallbackName: string) {
  const res = await http.get<Blob>(url, { params, responseType: 'blob', paramsSerializer: { indexes: null } });
  const disposition = String(res.headers['content-disposition'] ?? '');
  const name = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName;
  const href = URL.createObjectURL(res.data);
  const link = document.createElement('a');
  link.href = href;
  link.download = name;
  link.click();
  URL.revokeObjectURL(href);
}
