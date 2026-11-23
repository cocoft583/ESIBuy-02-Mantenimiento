import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertsService, CustomerAlert, UNREAD_POLL_INTERVAL_MS } from './alerts.service';
import { AuthService } from './auth.service';

@Component({ template: '' })
class EmptyPage {}

const COUNT_URL = '/api/customer/alerts/unread-count';

describe('AlertsService', () => {
  let service: AlertsService;
  let httpTesting: HttpTestingController;
  let currentUser: ReturnType<typeof signal<{ role: string } | null>>;

  function setVisibility(state: DocumentVisibilityState): void {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
  }

  beforeEach(() => {
    vi.useFakeTimers();
    setVisibility('visible');
    currentUser = signal<{ role: string } | null>({ role: 'CLIENT' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'otra', component: EmptyPage }]),
        { provide: AuthService, useValue: { currentUser } },
      ],
    });
    service = TestBed.inject(AlertsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
    vi.useRealTimers();
    setVisibility('visible');
  });

  it('pide las alertas del cliente con la cookie de sesión', () => {
    const alert: CustomerAlert = {
      id: 'a-1', type: 'PRICE_DROP', productId: 'p-1', productName: 'Teclado',
      message: 'Ha bajado de precio', createdAt: '2026-11-22T10:00:00Z', read: false,
    };
    let alerts: CustomerAlert[] | undefined;
    service.getAlerts().subscribe((value) => (alerts = value));

    const request = httpTesting.expectOne('/api/customer/alerts');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush([alert]);

    expect(alerts).toEqual([alert]);
  });

  it('trata una respuesta vacía como una bandeja sin alertas', () => {
    let alerts: CustomerAlert[] | undefined;
    service.getAlerts().subscribe((value) => (alerts = value));

    httpTesting.expectOne('/api/customer/alerts').flush(null);

    expect(alerts).toEqual([]);
  });

  it('al marcar una alerta como leída vuelve a pedir el contador', () => {
    service.markAsRead('a/1').subscribe();

    const request = httpTesting.expectOne('/api/customer/alerts/a%2F1/read');
    expect(request.request.method).toBe('PATCH');
    request.flush(null);
    httpTesting.expectOne(COUNT_URL).flush({ count: 2 });

    expect(service.unreadCount()).toBe(2);
  });

  it('al marcar todas como leídas el contador se queda a cero', () => {
    service.refreshUnreadCount();
    httpTesting.expectOne(COUNT_URL).flush({ count: 4 });

    service.markAllAsRead().subscribe();
    const request = httpTesting.expectOne('/api/customer/alerts/read-all');
    expect(request.request.method).toBe('PATCH');
    request.flush(null);

    expect(service.unreadCount()).toBe(0);
  });

  it('al eliminar una alerta vuelve a pedir el contador', () => {
    service.delete('a-1').subscribe();

    const request = httpTesting.expectOne('/api/customer/alerts/a-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    httpTesting.expectOne(COUNT_URL).flush({ count: 0 });

    expect(service.unreadCount()).toBe(0);
  });

  it('pide el contador al empezar y después cada minuto', () => {
    service.startWatching();
    vi.advanceTimersByTime(0);
    httpTesting.expectOne(COUNT_URL).flush({ count: 3 });
    expect(service.unreadCount()).toBe(3);

    vi.advanceTimersByTime(UNREAD_POLL_INTERVAL_MS - 1);
    httpTesting.expectNone(COUNT_URL);

    vi.advanceTimersByTime(1);
    httpTesting.expectOne(COUNT_URL).flush({ count: 5 });
    expect(service.unreadCount()).toBe(5);
  });

  it('vuelve a pedir el contador al cambiar de página', async () => {
    service.startWatching();
    vi.advanceTimersByTime(0);
    httpTesting.expectOne(COUNT_URL).flush({ count: 1 });

    await TestBed.inject(Router).navigateByUrl('/otra');

    httpTesting.expectOne(COUNT_URL).flush({ count: 2 });
    expect(service.unreadCount()).toBe(2);
  });

  it('empezar a seguir el contador varias veces no repite las peticiones', () => {
    service.startWatching();
    service.startWatching();
    vi.advanceTimersByTime(0);

    httpTesting.expectOne(COUNT_URL).flush({ count: 1 });
  });

  it('sin un cliente con sesión no pide nada y la campana se queda a cero', () => {
    currentUser.set({ role: 'SELLER' });

    service.startWatching();
    vi.advanceTimersByTime(UNREAD_POLL_INTERVAL_MS);

    httpTesting.expectNone(COUNT_URL);
    expect(service.unreadCount()).toBe(0);
  });

  it('si el contador no se puede pedir conserva la última cifra', () => {
    service.refreshUnreadCount();
    httpTesting.expectOne(COUNT_URL).flush({ count: 3 });

    service.refreshUnreadCount();
    httpTesting.expectOne(COUNT_URL).flush('Error', { status: 500, statusText: 'Server Error' });

    expect(service.unreadCount()).toBe(3);
  });

  it('con la pestaña oculta no pregunta y al volver a ella sí', () => {
    setVisibility('hidden');
    service.startWatching();
    vi.advanceTimersByTime(UNREAD_POLL_INTERVAL_MS);
    httpTesting.expectNone(COUNT_URL);

    setVisibility('visible');
    document.dispatchEvent(new Event('visibilitychange'));

    httpTesting.expectOne(COUNT_URL).flush({ count: 6 });
    expect(service.unreadCount()).toBe(6);
  });
});
