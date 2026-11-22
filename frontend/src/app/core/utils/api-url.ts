import { environment } from '../../../environments/environment';

export function apiBaseUrl(): string {
  const baseApi =
    (typeof window !== 'undefined' && window.localStorage?.getItem('esibuy_api_url')) ||
    environment.apiUrl;
  return baseApi.endsWith('/') ? baseApi.slice(0, -1) : baseApi;
}
