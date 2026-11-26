import { Injectable, InjectionToken, inject } from '@angular/core';
import { App } from '@capacitor/app';
import { AppPlatform } from './app-platform';

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

/**
 * Botón atrás de Android dentro de la app (HU30, T30.2). Sin escucharlo, Capacitor cierra la app.
 * Vuelve a la página anterior y, si no la hay, minimiza la app sin cerrar la sesión.
 * En iOS el evento no llega (allí se vuelve deslizando) y en el navegador no hace nada.
 */
@Injectable({ providedIn: 'root' })
export class BackButton {
  private readonly platform = inject(AppPlatform);
  private readonly source = inject(BACK_BUTTON_SOURCE);
  private started = false;

  start(): void {
    if (this.started || !this.platform.isNativeApp()) {
      return;
    }
    this.started = true;
    this.source.onBackButton((canGoBack) => {
      if (canGoBack) {
        this.source.goBack();
      } else {
        void this.source.minimize();
      }
    });
  }
}
