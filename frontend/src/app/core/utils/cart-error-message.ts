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
