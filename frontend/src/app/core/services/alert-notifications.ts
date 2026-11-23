import { DestroyRef, Injectable, InjectionToken, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { LocalNotifications } from '@capacitor/local-notifications';
import { AppPlatform } from './app-platform';
import { AlertsService } from './alerts.service';
import { AuthService } from './auth.service';

/** Lo que necesita el aviso del móvil de la capa nativa: permiso, notificación local y eventos de la app. */
export interface DeviceNotifier {
  requestPermission(): Promise<boolean>;
  notify(notification: { id: number; title: string; body: string; route: string }): Promise<void>;
  onOpened(listener: (route: string | null) => void): void;
  onResume(listener: () => void): void;
}

export const DEVICE_NOTIFIER = new InjectionToken<DeviceNotifier>('DEVICE_NOTIFIER', {
  providedIn: 'root',
  factory: () => ({
    requestPermission: async () => {
      const current = await LocalNotifications.checkPermissions();
      if (current.display === 'granted') {
        return true;
      }
      return (await LocalNotifications.requestPermissions()).display === 'granted';
    },
    notify: async ({ id, title, body, route }) => {
      await LocalNotifications.schedule({ notifications: [{ id, title, body, extra: { route } }] });
    },
    onOpened: (listener) => {
      void LocalNotifications.addListener('localNotificationActionPerformed', (action) => {
        const route = action.notification.extra?.route;
        listener(typeof route === 'string' ? route : null);
      });
    },
    onResume: (listener) => {
      void App.addListener('resume', () => listener());
    },
  }),
});

/** Siempre la misma: una alerta nueva sustituye al aviso anterior en lugar de acumularse. */
export const ALERTS_NOTIFICATION_ID = 2900;
const ALERTS_ROUTE = '/client/alerts';
const STORAGE_PREFIX = 'esibuy-alerts-notified-';

/**
 * Notificación en el móvil cuando el cliente tiene alertas nuevas (HU30, RF-MOV-05).
 * Es local: la app avisa cuando sube el contador de alertas sin leer (T29.2), que se consulta
 * al abrirla, al volver a ella, al cambiar de página y cada minuto mientras está abierta.
 * Con la app cerrada no llega nada (eso necesitaría push con Firebase). Solo funciona dentro
 * de la app; en el navegador no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class AlertNotifications {
  private readonly platform = inject(AppPlatform);
  private readonly notifier = inject(DEVICE_NOTIFIER);
  private readonly alertsService = inject(AlertsService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private started = false;
  private permission: Promise<boolean> | null = null;

  start(): void {
    if (this.started || !this.platform.isNativeApp()) {
      return;
    }
    this.started = true;

    this.notifier.onOpened((route) => void this.router.navigateByUrl(route ?? ALERTS_ROUTE));
    this.notifier.onResume(() => this.alertsService.refreshUnreadCount());
    // La campana solo sigue el contador si se ve el menú; aquí se sigue en toda la app
    this.alertsService.startWatching();

    // Cada respuesta del servidor, aunque repita la cifra anterior
    this.alertsService.unreadCountUpdates
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((count) => {
        const user = this.authService.currentUser();
        if (user?.role === 'CLIENT' && user.id) {
          void this.onCount(user.id, count);
        }
      });
  }

  private async onCount(userId: string, count: number): Promise<void> {
    const previous = this.readNotified(userId);
    if (previous === null) {
      // La primera vez en este móvil no se avisa de lo que ya había: solo de lo que llegue después
      this.writeNotified(userId, count);
      void this.ensurePermission();
      return;
    }

    if (count <= previous) {
      // Ha leído o borrado alertas: la próxima que llegue vuelve a avisar
      this.writeNotified(userId, count);
      return;
    }

    this.writeNotified(userId, count);
    if (await this.ensurePermission()) {
      await this.notifier.notify({
        id: ALERTS_NOTIFICATION_ID,
        title: count - previous === 1 ? 'Tienes una alerta nueva' : `Tienes ${count - previous} alertas nuevas`,
        body: count === 1 ? '1 alerta sin leer en ESIBuy.' : `${count} alertas sin leer en ESIBuy.`,
        route: ALERTS_ROUTE,
      });
    }
  }

  private ensurePermission(): Promise<boolean> {
    this.permission ??= this.notifier.requestPermission().catch(() => false);
    return this.permission;
  }

  private readNotified(userId: string): number | null {
    const stored = localStorage.getItem(STORAGE_PREFIX + userId);
    const value = stored === null ? Number.NaN : Number(stored);
    return Number.isFinite(value) && value >= 0 ? value : null;
  }

  private writeNotified(userId: string, count: number): void {
    localStorage.setItem(STORAGE_PREFIX + userId, String(count));
  }
}
