import { HttpErrorResponse } from '@angular/common/http';

export const INSUFFICIENT_STOCK_CODE = 'INSUFFICIENT_STOCK';
export const INSUFFICIENT_STOCK_MESSAGE = 'No hay stock suficiente para la cantidad solicitada';

/**
 * Devuelve el mensaje que manda el backend cuando el carrito rechaza una cantidad
 * por falta de stock (409 con code INSUFFICIENT_STOCK), o null si el error es otro.
 */
export function insufficientStockMessage(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.error?.code !== INSUFFICIENT_STOCK_CODE) {
    return null;
  }
  const message = error.error?.message;
  return typeof message === 'string' && message.trim() ? message : INSUFFICIENT_STOCK_MESSAGE;
}

export const PREMIUM_ONLY_CODE = 'PREMIUM_ONLY';
export const PREMIUM_ONLY_MESSAGE = 'Este producto es exclusivo para clientes Premium';

/**
 * Devuelve el mensaje que manda el backend cuando rechaza un producto exclusivo a un cliente
 * que no es Premium (403 con code PREMIUM_ONLY, HU16), o null si el error es otro.
 */
export function premiumOnlyMessage(error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse) || error.error?.code !== PREMIUM_ONLY_CODE) {
    return null;
  }
  const message = error.error?.message;
  return typeof message === 'string' && message.trim() ? message : PREMIUM_ONLY_MESSAGE;
}

/** Mensaje para cualquier rechazo del carrito que se puede explicar al cliente (stock o exclusivo). */
export function cartRejectionMessage(error: unknown): string | null {
  return insufficientStockMessage(error) ?? premiumOnlyMessage(error);
}
