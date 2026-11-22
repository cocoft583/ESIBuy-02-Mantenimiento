import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { Home } from './features/home/home';
import { Login } from './features/identity/login/login';
import { Register } from './features/identity/register/register';
import { AdminUserCreate } from './features/admin/admin-user-create/admin-user-create';
import { ProductCreate } from './features/products/product-create/product-create';
import { ProductDetail } from './features/products/product-detail/product-detail';
import { AdminUserList } from './features/admin/admin-user-list/admin-user-list';
import { AdminUserDetail } from './features/admin/admin-user-detail/admin-user-detail';
import { SellerCatalog } from './features/products/seller-catalog/seller-catalog';
import { ClientCatalog } from './features/client/client-catalog/client-catalog';
import { ClientWishlist } from './features/client/client-wishlist/client-wishlist';
import { ClientOrders } from './features/client/client-orders/client-orders';
import { ClientOrderDetail } from './features/client/client-order-detail/client-order-detail';
import { ClientProfile } from './features/client/client-profile/client-profile';
import { SellerProfile } from './features/products/seller-profile/seller-profile';
import { SellerOrders } from './features/products/seller-orders/seller-orders';
import { AdminProfile } from './features/admin/admin-profile/admin-profile';
import { AdminCategoryList } from './features/admin/admin-category-list/admin-category-list';
import { AdminProductList } from './features/admin/admin-product-list/admin-product-list';
import { ClientCart } from './features/client/cart/cart';
import { PasswordRecovery } from './features/identity/password-recovery/password-recovery';
import { GeneralConfigurationPage } from './features/admin/general-configuration/general-configuration';
import { LegalPage } from './features/legal/legal-page';
import { webOnlyGuard } from './core/guards/web-only.guard';
import { WebOnlyNotice } from './features/identity/web-only-notice/web-only-notice';
import { PendingAccount } from './features/identity/pending-account/pending-account';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login', component: Login },
  { path: 'recuperar-password', component: PasswordRecovery },
  { path: 'restablecer-password', component: PasswordRecovery },
  { path: 'register', component: Register },
  { path: 'legal/:document', component: LegalPage },
  { path: 'solo-web', component: WebOnlyNotice, canActivate: [authGuard] },
  { path: 'cuenta-pendiente', component: PendingAccount },

  { path: 'admin/users/new', component: AdminUserCreate, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  { path: 'admin/altas', redirectTo: 'admin/users/new', pathMatch: 'full' },
  { path: 'admin/usuarios', component: AdminUserList, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  { path: 'admin/usuarios/:id/editar', component: AdminUserDetail, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  { path: 'admin/categorias', component: AdminCategoryList, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  { path: 'admin/productos', component: AdminProductList, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  { path: 'admin/products', redirectTo: 'admin/productos', pathMatch: 'full' },
  { path: 'admin/configuracion', component: GeneralConfigurationPage, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },
  {
    // Bajo demanda: así Chart.js solo se descarga al abrir las estadísticas
    path: 'admin/estadisticas',
    loadComponent: () => import('./features/admin/admin-statistics/admin-statistics').then((m) => m.AdminStatistics),
    canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')],
  },

  { path: 'admin', redirectTo: 'admin/usuarios', pathMatch: 'full' },
  { path: 'admin/users', redirectTo: 'admin/usuarios', pathMatch: 'full' },
  { path: 'admin.html', redirectTo: 'admin/usuarios', pathMatch: 'full' },
  { path: 'admin/profile', component: AdminProfile, canActivate: [webOnlyGuard, authGuard, roleGuard('ADMIN')] },

  { path: 'seller/catalog', component: SellerCatalog, canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')] },
  { path: 'seller/orders', component: SellerOrders, canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')] },
  {
    // Bajo demanda, como las del administrador: Chart.js solo se descarga al abrir las estadísticas
    path: 'seller/estadisticas',
    loadComponent: () =>
      import('./features/products/seller-statistics/seller-statistics').then((m) => m.SellerStatistics),
    canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')],
  },
  { path: 'products/new', component: ProductCreate, canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')] },
  { path: 'products/:id', component: ProductDetail },
  { path: 'products/:id/edit', component: ProductCreate, canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')] },
  { path: 'seller/profile', component: SellerProfile, canActivate: [webOnlyGuard, authGuard, roleGuard('SELLER')] },

  { path: 'client/catalog', component: ClientCatalog, canActivate: [authGuard, roleGuard(['CLIENT', 'ADMIN'])] },
  { path: 'client/dashboard', redirectTo: 'client/catalog', pathMatch: 'full' },
  { path: 'client/wishlist', component: ClientWishlist, canActivate: [authGuard, roleGuard('CLIENT')] },
  { path: 'client/orders', component: ClientOrders, canActivate: [authGuard, roleGuard('CLIENT')] },
  { path: 'client/orders/:pedidoId', component: ClientOrderDetail, canActivate: [authGuard, roleGuard('CLIENT')] },
  { path: 'client/compras', redirectTo: 'client/orders', pathMatch: 'full' },
  { path: 'client/cart', component: ClientCart, canActivate: [authGuard, roleGuard('CLIENT')] },
  { path: 'client/profile', component: ClientProfile, canActivate: [authGuard, roleGuard('CLIENT')] },

  { path: '**', redirectTo: '' }
];
