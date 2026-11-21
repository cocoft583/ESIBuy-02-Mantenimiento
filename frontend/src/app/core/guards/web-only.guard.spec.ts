import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AppPlatform } from '../services/app-platform';
import { webOnlyGuard } from './web-only.guard';

describe('webOnlyGuard', () => {
  function runGuard(nativeApp: boolean) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AppPlatform, useValue: { isNativeApp: () => nativeApp } },
      ],
    });

    return TestBed.runInInjectionContext(() =>
      webOnlyGuard({} as ActivatedRouteSnapshot, { url: '/seller/catalog' } as RouterStateSnapshot));
  }

  afterEach(() => TestBed.resetTestingModule());

  it('en la web deja entrar', () => {
    expect(runGuard(false)).toBe(true);
  });

  it('en la app no deja entrar y lleva a la portada', () => {
    const result = runGuard(true);

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/');
  });
});
