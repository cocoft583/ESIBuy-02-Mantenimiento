import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AlertsService } from '../../core/services/alerts.service';

/**
 * Campana con el número de alertas sin leer (HU29). Va dentro del enlace a la bandeja del
 * menú del cliente y, al aparecer, empieza a seguir el contador.
 */
@Component({
  selector: 'app-alerts-bell',
  standalone: true,
  templateUrl: './alerts-bell.html',
  styleUrl: './alerts-bell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsBell {
  private readonly alertsService = inject(AlertsService);

  readonly count = this.alertsService.unreadCount;
  readonly badge = computed(() => (this.count() > 99 ? '99+' : String(this.count())));

  constructor() {
    this.alertsService.startWatching();
  }
}
