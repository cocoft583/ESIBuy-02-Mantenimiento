import { Injectable } from '@angular/core';
import { Capacitor } from '@capacitor/core';

/** Dice si el frontend corre dentro de la app móvil (Capacitor) o en el navegador. */
@Injectable({ providedIn: 'root' })
export class AppPlatform {
  isNativeApp(): boolean {
    return Capacitor.isNativePlatform();
  }
}
