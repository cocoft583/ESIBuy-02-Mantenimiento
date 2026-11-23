import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertsService, CustomerAlert } from '../../../core/services/alerts.service';
import { ClientAlerts } from './client-alerts';

function alert(id: string, createdAt: string, read: boolean, overrides: Partial<CustomerAlert> = {}): CustomerAlert {
  return {
    id,
    type: 'PRICE_DROP',
    productId: `p-${id}`,
    productName: `Producto ${id}`,
    message: `Mensaje ${id}`,
    createdAt,
    read,
    ...overrides,
  };
}

describe('ClientAlerts', () => {
  let fixture: ComponentFixture<ClientAlerts>;
  let element: HTMLElement;
  let getAlerts: ReturnType<typeof vi.fn<() => Observable<CustomerAlert[]>>>;
  let markAsRead: ReturnType<typeof vi.fn<(id: string) => Observable<void>>>;
  let markAllAsRead: ReturnType<typeof vi.fn<() => Observable<void>>>;
  let deleteAlert: ReturnType<typeof vi.fn<(id: string) => Observable<void>>>;

  const ALERTS: CustomerAlert[] = [
    alert('antigua', '2026-11-20T09:00:00Z', true, { type: 'NEW_PRODUCT' }),
    alert('reciente', '2026-11-22T18:30:00Z', false, { productName: 'Teclado', message: 'Ha bajado a 19,99 €' }),
    alert('media', '2026-11-21T12:00:00Z', false, { type: 'AVAILABILITY' }),
  ];

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(ClientAlerts);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function items(): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('.alert-item'));
  }

  function button(item: HTMLElement, text: string): HTMLButtonElement | undefined {
    return Array.from(item.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === text);
  }

  async function refresh(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    localStorage.clear();
    getAlerts = vi.fn(() => of(ALERTS.map((a) => ({ ...a }))));
    markAsRead = vi.fn(() => of(undefined));
    markAllAsRead = vi.fn(() => of(undefined));
    deleteAlert = vi.fn(() => of(undefined));
    await TestBed.configureTestingModule({
      imports: [ClientAlerts],
      providers: [
        provideRouter([]),
        {
          provide: AlertsService,
          useValue: {
            getAlerts, markAsRead, markAllAsRead, delete: deleteAlert,
            unreadCount: signal(2).asReadonly(), startWatching: vi.fn(),
          },
        },
      ],
    }).compileComponents();
  });

  it('usa el menú del cliente con la entrada de alertas', async () => {
    await render();

    expect(element.querySelector('app-shared-sidebar[variant="client"]')).not.toBeNull();
    expect(element.querySelector('h1')?.textContent).toContain('Alertas');
    expect(element.querySelector('a[href="/client/alerts"]')).not.toBeNull();
  });

  it('enseña las alertas de la más reciente a la más antigua con el tipo, el producto, el mensaje y la fecha', async () => {
    await render();

    const list = items();
    expect(list.map((item) => item.querySelector('.alert-product')?.textContent?.trim()))
      .toEqual(['Teclado', 'Producto media', 'Producto antigua']);
    expect(list.map((item) => item.querySelector('.alert-type')?.textContent?.trim()))
      .toEqual(['Bajada de precio', 'Vuelve a estar disponible', 'Producto nuevo']);
    expect(list[0].querySelector('.alert-message')?.textContent).toContain('Ha bajado a 19,99 €');
    const date = list[0].querySelector('time');
    expect(date?.getAttribute('datetime')).toBe('2026-11-22T18:30:00Z');
    expect(date?.textContent?.trim()).toMatch(/2026/);
  });

  it('distingue las no leídas también con texto, no solo con el color', async () => {
    await render();

    const [reciente, media, antigua] = items();
    expect(reciente.classList).toContain('is-unread');
    expect(reciente.querySelector('.alert-new')?.textContent?.trim()).toBe('Nueva');
    expect(media.classList).toContain('is-unread');
    expect(antigua.classList).not.toContain('is-unread');
    expect(antigua.querySelector('.alert-new')).toBeNull();
    expect(element.querySelector('.alerts-summary')?.textContent).toContain('Tienes 2 alertas sin leer');
  });

  it('marca una alerta como leída', async () => {
    await render();

    button(items()[0], 'Marcar como leída')?.click();
    await refresh();

    expect(markAsRead).toHaveBeenCalledWith('reciente');
    expect(items()[0].classList).not.toContain('is-unread');
    expect(button(items()[0], 'Marcar como leída')).toBeUndefined();
    expect(element.querySelector('.alerts-summary')?.textContent).toContain('Tienes 1 alerta sin leer');
  });

  it('marca todas como leídas y desactiva el botón', async () => {
    await render();

    const markAll = element.querySelector<HTMLButtonElement>('.mark-all-button');
    markAll?.click();
    await refresh();

    expect(markAllAsRead).toHaveBeenCalledOnce();
    expect(items().some((item) => item.classList.contains('is-unread'))).toBe(false);
    expect(element.querySelector<HTMLButtonElement>('.mark-all-button')?.disabled).toBe(true);
    expect(element.querySelector('.alerts-summary')?.textContent).toContain('Has leído todas tus alertas');
  });

  it('elimina una alerta de la lista', async () => {
    await render();

    button(items()[1], 'Eliminar')?.click();
    await refresh();

    expect(deleteAlert).toHaveBeenCalledWith('media');
    expect(items()).toHaveLength(2);
    expect(element.textContent).not.toContain('Producto media');
  });

  it('los botones nombran el producto de su alerta para los lectores de pantalla', async () => {
    await render();

    expect(button(items()[0], 'Eliminar')?.getAttribute('aria-label')).toBe('Eliminar la alerta de Teclado');
    expect(button(items()[0], 'Marcar como leída')?.getAttribute('aria-label'))
      .toBe('Marcar como leída la alerta de Teclado');
  });

  it('al pulsar una alerta la marca como leída y lleva al producto', async () => {
    await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    const link = items()[0].querySelector<HTMLAnchorElement>('.alert-product');
    expect(link?.getAttribute('href')).toBe('/products/p-reciente');
    link?.click();
    await refresh();

    expect(markAsRead).toHaveBeenCalledWith('reciente');
    expect(navigate).toHaveBeenCalled();
  });

  it('al pulsar una alerta ya leída no la vuelve a marcar', async () => {
    await render();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    items()[2].querySelector<HTMLAnchorElement>('.alert-product')?.click();
    await refresh();

    expect(markAsRead).not.toHaveBeenCalled();
  });

  it('no repite la acción mientras la anterior está en curso', async () => {
    const pending = new Subject<void>();
    markAsRead.mockReturnValue(pending.asObservable());
    await render();

    button(items()[0], 'Marcar como leída')?.click();
    await refresh();
    expect(button(items()[0], 'Marcar como leída')?.disabled).toBe(true);
    expect(button(items()[0], 'Eliminar')?.disabled).toBe(true);

    fixture.componentInstance.markAsRead(fixture.componentInstance.sortedAlerts()[0]);
    expect(markAsRead).toHaveBeenCalledOnce();
  });

  it('si una acción falla lo avisa y deja la alerta como estaba', async () => {
    deleteAlert.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    await render();

    button(items()[0], 'Eliminar')?.click();
    await refresh();

    expect(element.querySelector('.alerts-action-error')?.textContent).toContain('No se ha podido eliminar la alerta.');
    expect(items()).toHaveLength(3);
    expect(button(items()[0], 'Eliminar')?.disabled).toBe(false);
  });

  it('si la alerta ya no existe la quita de la lista sin dar error', async () => {
    deleteAlert.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await render();

    button(items()[0], 'Eliminar')?.click();
    await refresh();

    expect(element.querySelector('.alerts-action-error')).toBeNull();
    expect(items()).toHaveLength(2);
  });

  it('sin alertas lo explica y no enseña el botón de marcar todas', async () => {
    getAlerts.mockReturnValue(of([]));
    await render();

    expect(element.querySelector('.alerts-empty')?.textContent).toContain('No tienes alertas');
    expect(element.querySelector('.mark-all-button')).toBeNull();
    expect(element.querySelector('.alerts-empty a')?.getAttribute('href')).toBe('/client/catalog');
  });

  it('si no se pueden cargar avisa y permite reintentar', async () => {
    getAlerts.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('No se han podido cargar tus alertas.');
    element.querySelector<HTMLButtonElement>('.retry-button')?.click();
    await refresh();

    expect(getAlerts).toHaveBeenCalledTimes(2);
    expect(items()).toHaveLength(3);
  });

  it('sin conexión lo dice claramente', async () => {
    getAlerts.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('No hay conexión con el servidor');
  });
});
