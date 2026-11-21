import { Injectable, InjectionToken, signal } from '@angular/core';

/** De dónde sale el estado de la red: el plugin Network de Capacitor (en la app y en la web). */
export interface NetworkSource {
  isConnected(): Promise<boolean>;
  onChange(listener: (connected: boolean) => void): void;
}

export const NETWORK_SOURCE = new InjectionToken<NetworkSource>('NETWORK_SOURCE');

@Injectable({ providedIn: 'root' })
export class NetworkStatus {
  readonly online = signal(true);

  async start(): Promise<void> {}
}
