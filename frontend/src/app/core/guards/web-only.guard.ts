import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AppPlatform } from '../services/app-platform';

/** La app móvil es solo para clientes: las pantallas de admin y vendedor se usan desde la web. */
export const webOnlyGuard: CanActivateFn = () =>
  inject(AppPlatform).isNativeApp() ? inject(Router).createUrlTree(['/solo-web']) : true;
