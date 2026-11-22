import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import {
  INSUFFICIENT_STOCK_MESSAGE,
  PREMIUM_ONLY_MESSAGE,
  cartRejectionMessage,
  insufficientStockMessage,
  premiumOnlyMessage,
} from './cart-error-message';

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

// HU16: el carrito rechaza un exclusivo a un cliente que no es Premium (403 con code PREMIUM_ONLY).
describe('premiumOnlyMessage', () => {
  it('returns the backend message when the product is only for Premium clients', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: { message: 'Este producto es exclusivo para clientes Premium', code: 'PREMIUM_ONLY' },
    });

    expect(premiumOnlyMessage(error)).toBe('Este producto es exclusivo para clientes Premium');
  });

  it('falls back to the default message when the backend sends only the code', () => {
    const error = new HttpErrorResponse({ status: 403, error: { code: 'PREMIUM_ONLY' } });

    expect(premiumOnlyMessage(error)).toBe(PREMIUM_ONLY_MESSAGE);
  });

  it('returns null for other errors', () => {
    expect(premiumOnlyMessage(new HttpErrorResponse({ status: 403, error: { message: 'Prohibido' } }))).toBeNull();
    expect(premiumOnlyMessage(new Error('failure'))).toBeNull();
  });
});

describe('cartRejectionMessage', () => {
  it('explains both stock and Premium-only rejections and ignores the rest', () => {
    expect(cartRejectionMessage(new HttpErrorResponse({ status: 409, error: { code: 'INSUFFICIENT_STOCK' } })))
      .toBe(INSUFFICIENT_STOCK_MESSAGE);
    expect(cartRejectionMessage(new HttpErrorResponse({ status: 403, error: { code: 'PREMIUM_ONLY' } })))
      .toBe(PREMIUM_ONLY_MESSAGE);
    expect(cartRejectionMessage(new HttpErrorResponse({ status: 500 }))).toBeNull();
  });
});
