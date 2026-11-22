import { apiBaseUrl } from './api-url';

export function resolveImageUrl(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  if (
    url.startsWith('http://') ||
    url.startsWith('https://') ||
    url.startsWith('data:') ||
    url.startsWith('blob:')
  ) {
    return url;
  }
  const baseApi = apiBaseUrl();
  if (!baseApi) {
    return url;
  }
  const cleanUrl = url.startsWith('/') ? url : `/${url}`;
  return `${baseApi}${cleanUrl}`;
}
