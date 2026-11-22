import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { INSUFFICIENT_STOCK_MESSAGE, insufficientStockMessage } from './cart-error-message';

describe('insufficientStockMessage', () => {
  it('returns the backend message when the cart rejects the quantity for lack of stock', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { message: 'No hay stock suficiente para la cantidad solicitada', code: 'INSUFFICIENT_STOCK' },
    });

    expect(insufficientStockMessage(error)).toBe('No hay stock suficiente para la cantidad solicitada');
  });

  it('falls back to the default stock message when the backend sends only the code', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'INSUFFICIENT_STOCK' } });

    expect(insufficientStockMessage(error)).toBe(INSUFFICIENT_STOCK_MESSAGE);
  });

  it('returns null for errors that are not about stock', () => {
    expect(insufficientStockMessage(new HttpErrorResponse({
      status: 409,
      error: { message: 'Otro conflicto', code: 'OTHER' },
    }))).toBeNull();
    expect(insufficientStockMessage(new HttpErrorResponse({ status: 500 }))).toBeNull();
    expect(insufficientStockMessage(new Error('failure'))).toBeNull();
  });
});
