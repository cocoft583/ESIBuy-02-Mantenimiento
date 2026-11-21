import { Route } from '@angular/router';
import { routes } from './app.routes';
import { webOnlyGuard } from './core/guards/web-only.guard';

describe('rutas de la app móvil', () => {
  const pagesWithComponent = routes.filter((route): route is Route & { path: string } =>
    !!route.component && route.path !== undefined);

  const isWebOnly = (path: string) =>
    path.startsWith('admin/') || path.startsWith('seller/') || path === 'products/new' || path === 'products/:id/edit';

  it('las pantallas de administración y de vendedor solo se abren en la web', () => {
    const webOnly = pagesWithComponent.filter((route) => isWebOnly(route.path));

    expect(webOnly.length).toBeGreaterThan(0);
    for (const route of webOnly) {
      expect(route.canActivate, route.path).toContain(webOnlyGuard);
    }
  });

  it('las pantallas de cliente y las públicas también se abren en la app', () => {
    const forEveryone = pagesWithComponent.filter((route) => !isWebOnly(route.path));

    expect(forEveryone.map((route) => route.path)).toEqual(expect.arrayContaining([
      '', 'login', 'register', 'products/:id', 'client/catalog', 'client/cart', 'solo-web',
    ]));
    for (const route of forEveryone) {
      expect(route.canActivate ?? [], route.path).not.toContain(webOnlyGuard);
    }
  });
});
