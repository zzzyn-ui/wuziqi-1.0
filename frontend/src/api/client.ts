import axios, { type AxiosRequestConfig } from "axios";

const API_BASE = "/api";

export const api = axios.create({ baseURL: API_BASE });

api.interceptors.request.use((config) => {
  const t = localStorage.getItem("token");
  if (t) config.headers.Authorization = `Bearer ${t}`;
  return config;
});

type RetryConfig = AxiosRequestConfig & { _retry?: boolean };

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const rt = localStorage.getItem("refresh_token");
  if (!rt) return null;
  try {
    const { data } = await axios.post<{ access_token: string; refresh_token?: string }>(
      `${API_BASE}/auth/refresh`,
      { refresh_token: rt },
    );
    localStorage.setItem("token", data.access_token);
    if (data.refresh_token) localStorage.setItem("refresh_token", data.refresh_token);
    return data.access_token;
  } catch {
    return null;
  }
}

function getRefreshPromise(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

api.interceptors.response.use(
  (r) => r,
  async (err) => {
    const cfg = err.config as RetryConfig | undefined;
    const status = err.response?.status;
    if (!cfg || status !== 401) return Promise.reject(err);
    const url = String(cfg.url || "");
    if (url.includes("/auth/refresh") || url.includes("/auth/login")) return Promise.reject(err);
    if (cfg._retry) return Promise.reject(err);
    cfg._retry = true;
    const token = await getRefreshPromise();
    if (!token) {
      localStorage.removeItem("token");
      localStorage.removeItem("refresh_token");
      localStorage.removeItem("userInfo");
      if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
      return Promise.reject(err);
    }
    cfg.headers = cfg.headers || {};
    cfg.headers.Authorization = `Bearer ${token}`;
    return api(cfg);
  },
);
