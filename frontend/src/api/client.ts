import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse } from './types';
import { useSession } from '../stores/session';

export const api = axios.create({ baseURL: '/api/v1', timeout: 20000 });

api.interceptors.request.use((config) => {
  const token = useSession.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;

/** Echange le jeton de rafraichissement contre une nouvelle session. Un seul echange a la fois. */
function refresh(): Promise<string | null> {
  if (!refreshing) {
    const { refreshToken } = useSession.getState();
    refreshing = (refreshToken
      ? axios.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken }).then((r) => {
          useSession.getState().setSession(r.data);
          return r.data.accessToken;
        })
      : Promise.resolve(null)
    )
      .catch(() => null)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  const isAuthCall = original?.url?.startsWith('/auth/');
  if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
    original._retried = true;
    const token = await refresh();
    if (token) {
      original.headers.Authorization = `Bearer ${token}`;
      return api(original);
    }
    useSession.getState().clear();
  }
  return Promise.reject(error);
});

export interface ApiError { code: string; message: string; fields?: Record<string, string> }

/** Message lisible pour toute erreur d'appel. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined;
    if (data?.fields) return Object.values(data.fields).join(' ');
    if (data?.message) return data.message;
    if (!error.response) return 'Serveur injoignable. Verifiez votre connexion.';
    if (error.response.status === 401) return 'Session expiree. Reconnectez-vous.';
    if (error.response.status === 403) return "Vous n'avez pas acces a cette action.";
  }
  return 'Une erreur est survenue.';
}

export const get = <T,>(url: string, params?: object) => api.get<T>(url, { params }).then((r) => r.data);
export const post = <T,>(url: string, body?: unknown, headers?: Record<string, string>) => api.post<T>(url, body ?? {}, { headers }).then((r) => r.data);
export const put = <T,>(url: string, body?: unknown) => api.put<T>(url, body ?? {}).then((r) => r.data);
export const patch = <T,>(url: string, body?: unknown) => api.patch<T>(url, body ?? {}).then((r) => r.data);
export const del = (url: string) => api.delete(url).then(() => undefined);

export async function uploadImage(folder: 'logos' | 'dishes', file: File): Promise<{ key: string; url: string }> {
  const form = new FormData();
  form.append('folder', folder);
  form.append('file', file);
  return api.post('/files', form).then((r) => r.data);
}
