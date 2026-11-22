/**
 * Devuelve la URL del backend que se pasó al compilar (ESIBUY_API_URL) o, si no se pasó
 * ninguna, la URL por defecto. Un valor que no sea una URL http o https rompe el arranque
 * a propósito, para no mandar las peticiones a un sitio que nadie ha elegido.
 */
export function resolveBuildApiUrl(value: unknown, fallback: string): string {
  if (value === undefined) {
    return fallback;
  }
  if (typeof value !== 'string') {
    throw new Error('ESIBUY_API_URL tiene que ser una URL http o https.');
  }
  const url = value.trim();
  if (!url) {
    return fallback;
  }
  if (!/^https?:\/\/[^/\s]+/i.test(url)) {
    throw new Error(`ESIBUY_API_URL tiene que ser una URL http o https: "${url}"`);
  }
  return url.replace(/\/+$/, '');
}
