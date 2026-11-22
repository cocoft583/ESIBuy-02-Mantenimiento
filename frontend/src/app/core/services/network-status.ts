import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Network } from '@capacitor/network';

/** De dónde sale el estado de la red: el plugin Network de Capacitor (en la app y en la web). */
export interface NetworkSource {
  isConnected(): Promise<boolean>;
  onChange(listener: (connected: boolean) => void): void;
}

export const NETWORK_SOURCE = new InjectionToken<NetworkSource>('NETWORK_SOURCE', {
  providedIn: 'root',
  factory: () => ({
    isConnected: async () => (await Network.getStatus()).connected,
    onChange: (listener) => {
      void Network.addListener('networkStatusChange', (status) => listener(status.connected));
    },
  }),
});

/** Sigue si hay red y, cuando vuelve después de perderse, recarga la página en la que estaba el usuario. */
@Injectable({ providedIn: 'root' })
export class NetworkStatus {
  private readonly source = inject(NETWORK_SOURCE);
  private readonly router = inject(Router);
  private readonly onlineSignal = signal(true);
  private started = false;

  readonly online = this.onlineSignal.asReadonly();

  async start(): Promise<void> {
    if (this.started) {
      return;
    }
    this.started = true;

    this.source.onChange((connected) => this.update(connected));
    this.onlineSignal.set(await this.source.isConnected());
  }

  private update(connected: boolean): void {
    const wasOffline = !this.onlineSignal();
    this.onlineSignal.set(connected);

    if (connected && wasOffline) {
      this.reloadCurrentPage();
    }
  }

  // Salir y volver a la misma URL crea otra vez la vista, que vuelve a pedir sus datos al servidor.
  private reloadCurrentPage(): void {
    const url = this.router.url;
    void this.router
      .navigateByUrl('/', { skipLocationChange: true })
      .then(() => this.router.navigateByUrl(url));
  }
}
