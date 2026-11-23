import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ALERTS_NOTIFICATION_ID, AlertNotifications, DEVICE_NOTIFIER, DeviceNotifier } from './alert-notifications';
import { AlertsService } from './alerts.service';
import { AppPlatform } from './app-platform';
import { AuthService } from './auth.service';

describe('AlertNotifications', () => {
  let nativeApp: boolean;
  let currentUser: ReturnType<typeof signal<{ id: string; role: string } | null>>;
  let counts: Subject<number>;
  let alertsService: { unreadCountUpdates: Subject<number>; startWatching: ReturnType<typeof vi.fn>; refreshUnreadCount: ReturnType<typeof vi.fn> };
  let notifier: {
    requestPermission: ReturnType<typeof vi.fn<DeviceNotifier['requestPermission']>>;
    notify: ReturnType<typeof vi.fn<DeviceNotifier['notify']>>;
    onOpened: ReturnType<typeof vi.fn<DeviceNotifier['onOpened']>>;
    onResume: ReturnType<typeof vi.fn<DeviceNotifier['onResume']>>;
  };
  let navigateByUrl: ReturnType<typeof vi.fn>;

  function start(): AlertNotifications {
    const service = TestBed.inject(AlertNotifications);
    service.start();
    return service;
  }

  /** Llega una cifra del servidor y se espera a que termine de decidir si avisa. */
  async function serverSays(count: number): Promise<void> {
    counts.next(count);
    await new Promise((resolve) => setTimeout(resolve, 0));
  }

  beforeEach(() => {
    localStorage.clear();
    nativeApp = true;
    currentUser = signal<{ id: string; role: string } | null>({ id: 'ana', role: 'CLIENT' });
    counts = new Subject<number>();
    alertsService = { unreadCountUpdates: counts, startWatching: vi.fn(), refreshUnreadCount: vi.fn() };
    notifier = {
      requestPermission: vi.fn(async () => true),
      notify: vi.fn(async () => undefined),
      onOpened: vi.fn(),
      onResume: vi.fn(),
    };
    navigateByUrl = vi.fn(async () => true);
    TestBed.configureTestingModule({
      providers: [
        { provide: AppPlatform, useValue: { isNativeApp: () => nativeApp } },
        { provide: DEVICE_NOTIFIER, useValue: notifier },
        { provide: AlertsService, useValue: alertsService },
        { provide: AuthService, useValue: { currentUser } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
  });

  afterEach(() => localStorage.clear());

  it('en el navegador no hace nada', async () => {
    nativeApp = false;
    start();
    await serverSays(3);

    expect(alertsService.startWatching).not.toHaveBeenCalled();
    expect(notifier.onOpened).not.toHaveBeenCalled();
    expect(notifier.notify).not.toHaveBeenCalled();
  });

  it('en la app sigue el contador aunque no se vea la campana', () => {
    start();

    expect(alertsService.startWatching).toHaveBeenCalledOnce();
  });

  it('empezar varias veces no duplica los avisos', async () => {
    const service = start();
    service.start();
    await serverSays(0);
    await serverSays(1);

    expect(alertsService.startWatching).toHaveBeenCalledOnce();
    expect(notifier.notify).toHaveBeenCalledOnce();
  });

  it('la primera vez en el móvil no avisa de las alertas que ya había, pero pide el permiso', async () => {
    start();
    await serverSays(4);

    expect(notifier.notify).not.toHaveBeenCalled();
    expect(notifier.requestPermission).toHaveBeenCalledOnce();
  });

  it('con la app abierta avisa en cuanto llega una alerta nueva y lleva a la bandeja', async () => {
    start();
    await serverSays(2);
    await serverSays(3);

    expect(notifier.notify).toHaveBeenCalledWith({
      id: ALERTS_NOTIFICATION_ID,
      title: 'Tienes una alerta nueva',
      body: '3 alertas sin leer en ESIBuy.',
      route: '/client/alerts',
    });
  });

  it('cuenta cuántas alertas han llegado desde el último aviso', async () => {
    start();
    await serverSays(0);
    await serverSays(2);

    expect(notifier.notify).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Tienes 2 alertas nuevas',
      body: '2 alertas sin leer en ESIBuy.',
    }));
  });

  it('al volver a la app avisa de las alertas que llegaron mientras no estaba', async () => {
    // La última vez que estuvo en la app tenía 1 sin leer
    localStorage.setItem('esibuy-alerts-notified-ana', '1');
    start();

    await serverSays(4);

    expect(notifier.notify).toHaveBeenCalledOnce();
    expect(notifier.notify).toHaveBeenCalledWith(expect.objectContaining({ title: 'Tienes 3 alertas nuevas' }));
  });

  it('no repite el aviso si la cifra no cambia', async () => {
    start();
    await serverSays(1);
    await serverSays(2);
    await serverSays(2);
    await serverSays(2);

    expect(notifier.notify).toHaveBeenCalledOnce();
  });

  it('después de leerlas vuelve a avisar de la siguiente que llegue', async () => {
    start();
    await serverSays(2);
    await serverSays(0);
    await serverSays(1);

    expect(notifier.notify).toHaveBeenCalledOnce();
    expect(notifier.notify).toHaveBeenCalledWith(expect.objectContaining({ title: 'Tienes una alerta nueva' }));
  });

  it('sin permiso no muestra nada y no lo vuelve a pedir cada vez', async () => {
    notifier.requestPermission.mockResolvedValue(false);
    start();
    await serverSays(0);
    await serverSays(1);
    await serverSays(2);

    expect(notifier.notify).not.toHaveBeenCalled();
    expect(notifier.requestPermission).toHaveBeenCalledOnce();
  });

  it('si pedir el permiso falla no rompe nada', async () => {
    notifier.requestPermission.mockRejectedValue(new Error('No disponible'));
    start();
    await serverSays(0);
    await serverSays(1);

    expect(notifier.notify).not.toHaveBeenCalled();
  });

  it('solo avisa a los clientes', async () => {
    currentUser.set({ id: 'vendedor', role: 'SELLER' });
    start();
    await serverSays(0);
    await serverSays(5);

    expect(notifier.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem('esibuy-alerts-notified-vendedor')).toBeNull();
  });

  it('cada cliente del móvil tiene su propia cuenta de avisos', async () => {
    localStorage.setItem('esibuy-alerts-notified-ana', '5');
    currentUser.set({ id: 'luis', role: 'CLIENT' });
    start();
    await serverSays(1);

    expect(notifier.notify).not.toHaveBeenCalled();
    expect(localStorage.getItem('esibuy-alerts-notified-luis')).toBe('1');
    expect(localStorage.getItem('esibuy-alerts-notified-ana')).toBe('5');
  });

  it('al pulsar la notificación abre la bandeja de alertas', () => {
    start();
    const opened = notifier.onOpened.mock.calls[0][0];

    opened('/client/alerts');
    opened(null);

    expect(navigateByUrl).toHaveBeenNthCalledWith(1, '/client/alerts');
    expect(navigateByUrl).toHaveBeenNthCalledWith(2, '/client/alerts');
  });

  it('al volver a la app vuelve a pedir el contador', () => {
    start();
    const resumed = notifier.onResume.mock.calls[0][0];

    resumed();

    expect(alertsService.refreshUnreadCount).toHaveBeenCalledOnce();
  });
});
