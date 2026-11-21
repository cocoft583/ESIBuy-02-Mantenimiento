import { Component, inject } from '@angular/core';
import { NetworkStatus } from '../../core/services/network-status';

/** Franja fija bajo la cabecera mientras no hay conexión. */
@Component({
  selector: 'app-offline-banner',
  standalone: true,
  template: `
    @if (!network.online()) {
      <div class="offline-banner" role="status">
        <strong>Sin conexión.</strong> Revisa tu red: cuando vuelva, recargaremos la página.
      </div>
    }
  `,
  styles: `
    .offline-banner {
      position: fixed;
      inset: var(--app-header-height) 0 auto;
      z-index: 49;
      padding: 0.6rem 1rem;
      background: var(--color-warning-background);
      border-bottom: 1px solid var(--color-warning);
      color: var(--color-text-primary);
      font-size: 0.85rem;
      line-height: 1.4;
      text-align: center;
    }
  `,
})
export class OfflineBanner {
  readonly network = inject(NetworkStatus);
}
