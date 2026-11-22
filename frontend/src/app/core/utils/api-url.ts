import { environment } from '../../../environments/environment';

/**
 * URL del servidor de ESIBuy, fijada al compilar en environment.apiUrl.
 * Nunca se lee de localStorage ni de nada que se pueda cambiar desde el navegador (A-06).
 * Vacía en desarrollo: las peticiones van relativas y las pasa el proxy de ng serve.
 */
export function apiBaseUrl(): string {
  const baseApi = environment.apiUrl;
  return baseApi.endsWith('/') ? baseApi.slice(0, -1) : baseApi;
}
