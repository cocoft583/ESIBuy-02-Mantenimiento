import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AlertType, AlertsService, CustomerAlert } from '../../../core/services/alerts.service';
import { AppSidebarComponent } from '../../../shared/app-sidebar/app-sidebar';

const TYPE_LABELS: Record<AlertType, string> = {
  PRICE_DROP: 'Bajada de precio',
  AVAILABILITY: 'Vuelve a estar disponible',
  NEW_PRODUCT: 'Producto nuevo',
};

const DATE_FORMAT = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' });

/**
 * Bandeja de alertas del cliente (HU29): de la más reciente a la más antigua, con las no
 * leídas resaltadas. Se pueden marcar como leídas una a una o todas, y eliminar cada una.
 * Al abrir una alerta se marca como leída y se va al producto.
 */
@Component({
  selector: 'app-client-alerts',
  standalone: true,
  imports: [AppSidebarComponent, RouterLink],
  templateUrl: './client-alerts.html',
  styleUrl: './client-alerts.css',
})
export class ClientAlerts implements OnInit {
  private readonly alertsService = inject(AlertsService);

  readonly sidebarCollapsed = signal(
    typeof localStorage !== 'undefined' && localStorage.getItem('client-sidebar-collapsed') === 'true',
  );
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly busyIds = signal<ReadonlySet<string>>(new Set());
  readonly markingAll = signal(false);
  private readonly alerts = signal<CustomerAlert[]>([]);

  readonly sortedAlerts = computed(() =>
    [...this.alerts()].sort((a, b) => this.time(b.createdAt) - this.time(a.createdAt)),
  );
  readonly unreadCount = computed(() => this.alerts().filter((alert) => !alert.read).length);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.alertsService.getAlerts().subscribe({
      next: (alerts) => {
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(this.messageFor(error, 'No se han podido cargar tus alertas.'));
        this.loading.set(false);
      },
    });
  }

  typeLabel(type: AlertType): string {
    return TYPE_LABELS[type] ?? 'Alerta';
  }

  formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : DATE_FORMAT.format(date);
  }

  isBusy(alertId: string): boolean {
    return this.busyIds().has(alertId);
  }

  /** Abrir una alerta la da por leída; la navegación al producto la hace el enlace. */
  open(alert: CustomerAlert): void {
    if (!alert.read) {
      this.markAsRead(alert);
    }
  }

  markAsRead(alert: CustomerAlert): void {
    if (alert.read || this.isBusy(alert.id)) {
      return;
    }
    this.startAction(alert.id);
    this.alertsService.markAsRead(alert.id).subscribe({
      next: () => {
        this.update(alert.id, (current) => ({ ...current, read: true }));
        this.endAction(alert.id);
      },
      error: (error: unknown) => {
        this.failAction(alert.id, error, 'No se ha podido marcar la alerta como leída.');
      },
    });
  }

  markAllAsRead(): void {
    if (this.unreadCount() === 0 || this.markingAll()) {
      return;
    }
    this.markingAll.set(true);
    this.actionError.set(null);
    this.alertsService.markAllAsRead().subscribe({
      next: () => {
        this.alerts.update((alerts) => alerts.map((alert) => ({ ...alert, read: true })));
        this.markingAll.set(false);
      },
      error: (error: unknown) => {
        this.actionError.set(this.messageFor(error, 'No se han podido marcar las alertas como leídas.'));
        this.markingAll.set(false);
      },
    });
  }

  delete(alert: CustomerAlert): void {
    if (this.isBusy(alert.id)) {
      return;
    }
    this.startAction(alert.id);
    this.alertsService.delete(alert.id).subscribe({
      next: () => {
        this.alerts.update((alerts) => alerts.filter((current) => current.id !== alert.id));
        this.endAction(alert.id);
      },
      error: (error: unknown) => {
        // Si ya no existe (borrada en otra pestaña) se quita igualmente de la lista
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.alerts.update((alerts) => alerts.filter((current) => current.id !== alert.id));
          this.endAction(alert.id);
          return;
        }
        this.failAction(alert.id, error, 'No se ha podido eliminar la alerta.');
      },
    });
  }

  private update(alertId: string, change: (alert: CustomerAlert) => CustomerAlert): void {
    this.alerts.update((alerts) => alerts.map((alert) => (alert.id === alertId ? change(alert) : alert)));
  }

  private startAction(alertId: string): void {
    this.actionError.set(null);
    this.busyIds.update((ids) => new Set(ids).add(alertId));
  }

  private endAction(alertId: string): void {
    this.busyIds.update((ids) => {
      const next = new Set(ids);
      next.delete(alertId);
      return next;
    });
  }

  private failAction(alertId: string, error: unknown, fallback: string): void {
    this.actionError.set(this.messageFor(error, fallback));
    this.endAction(alertId);
  }

  private messageFor(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'No hay conexión con el servidor. Inténtalo de nuevo.';
    }
    return fallback;
  }

  private time(value: string): number {
    const time = new Date(value).getTime();
    return Number.isNaN(time) ? 0 : time;
  }
}
