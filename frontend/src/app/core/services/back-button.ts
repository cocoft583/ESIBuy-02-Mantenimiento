import { Injectable, InjectionToken } from '@angular/core';
import { App } from '@capacitor/app';

/** Lo que necesita el botón atrás de la capa nativa: el evento del botón, volver y minimizar. */
export interface BackButtonSource {
  onBackButton(listener: (canGoBack: boolean) => void): void;
  goBack(): void;
  minimize(): Promise<void>;
}

export const BACK_BUTTON_SOURCE = new InjectionToken<BackButtonSource>('BACK_BUTTON_SOURCE', {
  providedIn: 'root',
  factory: () => ({
    onBackButton: (listener) => {
      void App.addListener('backButton', ({ canGoBack }) => listener(canGoBack));
    },
    goBack: () => window.history.back(),
    minimize: () => App.minimizeApp(),
  }),
});

/** Botón atrás de Android dentro de la app (HU30, T30.2). */
@Injectable({ providedIn: 'root' })
export class BackButton {
  start(): void {
    // Ciclo rojo: todavía no escucha el botón
  }
}
