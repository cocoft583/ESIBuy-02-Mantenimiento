import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { afterEach, describe, beforeEach, it, expect, vi } from 'vitest';
import { ClientCatalog } from './client-catalog';
import { ProductsService, ProductItem, Category, CatalogPage } from '../../../core/services/products.service';
import { WishlistService } from '../../../core/services/wishlist.service';
import { ToastService } from '../../../core/services/toast.service';
import { CartService } from '../../../core/services/cart.service';
import { AuthService, AuthenticatedUser } from '../../../core/services/auth.service';

function catalogPage(content: ProductItem[], page = 0, totalPages = 1, totalElements = content.length): CatalogPage {
  return { content, page, size: 12, totalElements, totalPages, maxPrice: 100 };
}

describe('ClientCatalog', () => {
  let component: ClientCatalog;
  let fixture: ComponentFixture<ClientCatalog>;
  let productsServiceMock: any;
  let wishlistServiceMock: any;
  let toastServiceMock: any;
  let cartServiceMock: any;
  let currentUserSignal = signal<AuthenticatedUser | null>(null);

  const mockCategories: Category[] = [
    { id: 'cat-1', name: 'Electrónica' },
    { id: 'cat-2', name: 'Hogar' },
  ];

  const mockProducts: ProductItem[] = [
    {
      id: 'p-1',
      name: 'Auriculares Bluetooth',
      categoryIds: ['cat-1'],
      stock: 10,
      description: 'Gran calidad de sonido',
      price: 29.99,
      available: true,
      sellerId: 's-1',
      rating: 4.8,
      discount: 15,
      premiumDiscount: 25,
      sellerName: 'AudioTech SL',
    },
    {
      id: 'p-2',
      name: 'Cafetera Express',
      categoryIds: ['cat-2'],
      stock: 2,
      description: 'Café rápido',
      price: 89.99,
      available: true,
      sellerId: 's-2',
      rating: 4.5,
    },
  ];

  beforeEach(async () => {
    localStorage.clear();
    productsServiceMock = {
      getCategories: vi.fn(() => of(mockCategories)),
      getPublicCatalog: vi.fn(() => of(catalogPage(mockProducts))),
    };

    wishlistServiceMock = {
      refreshIfClient: vi.fn(),
      isFavorite: vi.fn((id: string) => id === 'p-1'),
      toggleWishlist: vi.fn(() => of({ message: 'Lista actualizada', inWishlist: true })),
    };

    const toastSignal = signal<any>(null);
    toastServiceMock = {
      toast: toastSignal,
      show: vi.fn((text, type = 'success') => toastSignal.set({ text, type })),
      clear: vi.fn(() => toastSignal.set(null)),
    };

    currentUserSignal = signal<AuthenticatedUser | null>({
      id: 'client-1',
      email: 'client@example.com',
      role: 'CLIENT',
      active: true,
    });

    cartServiceMock = {
      itemCount: vi.fn(() => 2),
      addItem: vi.fn(() => of({})),
    };

    await TestBed.configureTestingModule({
      imports: [ClientCatalog],
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        { provide: ProductsService, useValue: productsServiceMock },
        { provide: WishlistService, useValue: wishlistServiceMock },
        { provide: ToastService, useValue: toastServiceMock },
        { provide: CartService, useValue: cartServiceMock },
        { provide: AuthService, useValue: { currentUser: currentUserSignal } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ClientCatalog);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('should create and render heading and navigation links in exact order', async () => {
    await TestBed.inject(Router).navigateByUrl('/client/catalog');
    fixture.detectChanges();
    expect(component).toBeTruthy();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('#catalog-title')?.textContent).toContain('Catálogo de productos');
    const navLinks = Array.from(el.querySelectorAll('.navigation-link'));
    expect(navLinks.length).toBe(4);
    expect(navLinks[0].textContent).toContain('Catálogo');
    expect(navLinks[0].classList.contains('is-active')).toBe(true);
    expect(navLinks[1].textContent).toContain('Lista de deseos');
    expect(navLinks[2].textContent).toContain('Mis pedidos');
    expect(navLinks[3].textContent).toContain('Carrito');
  });

  it('should toggle sidebar collapsed state', () => {
    const layout = fixture.nativeElement.querySelector('.seller-shell') as HTMLElement;
    const toggle = () => {
      (fixture.nativeElement.querySelector('.app-sidebar-toggle') as HTMLButtonElement).click();
      fixture.detectChanges();
    };

    expect(layout.classList.contains('app-sidebar-collapsed')).toBe(false);
    toggle();
    expect(layout.classList.contains('app-sidebar-collapsed')).toBe(true);
    toggle();
    expect(layout.classList.contains('app-sidebar-collapsed')).toBe(false);
  });

  it('asks the server for the first page of twelve products', () => {
    expect(productsServiceMock.getPublicCatalog).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 12 })
    );
  });

  it('asks the server for the next and previous page', () => {
    productsServiceMock.getPublicCatalog.mockReturnValue(of(catalogPage(mockProducts, 0, 3)));
    component.loadProducts();

    component.nextPage();
    expect(productsServiceMock.getPublicCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 })
    );

    component.previousPage();
    expect(productsServiceMock.getPublicCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0 })
    );
  });

  it('does not ask for a page after the last one', () => {
    productsServiceMock.getPublicCatalog.mockReturnValue(of(catalogPage(mockProducts, 0, 1)));
    component.loadProducts();
    const calls = productsServiceMock.getPublicCatalog.mock.calls.length;

    component.nextPage();

    expect(productsServiceMock.getPublicCatalog).toHaveBeenCalledTimes(calls);
  });

  it('sends the sort and the offers filter to the server and goes back to the first page', () => {
    productsServiceMock.getPublicCatalog.mockReturnValue(of(catalogPage(mockProducts, 0, 3)));
    component.loadProducts();
    component.nextPage();

    component.sortOrder.set('rating-desc');
    component.onlyOffers.set(true);
    component.onFilterChange();

    expect(productsServiceMock.getPublicCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'rating-desc', onlyOffers: true, page: 0 })
    );
  });

  it('shows the total of products of every page', () => {
    productsServiceMock.getPublicCatalog.mockReturnValue(of(catalogPage(mockProducts, 0, 3, 30)));
    component.loadProducts();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('30 productos disponibles');
    expect(fixture.nativeElement.textContent).toContain('Página 1 de 3');
  });

  it('should add product to cart and show toast', () => {
    const el: HTMLElement = fixture.nativeElement;
    const addBtn = el.querySelector('.add-to-cart-action-btn') as HTMLButtonElement;
    expect(addBtn).not.toBeNull();
    addBtn?.click();

    expect(cartServiceMock.addItem).toHaveBeenCalledWith('p-1', 1);
    expect(toastServiceMock.show).toHaveBeenCalledWith('Producto añadido al carrito', 'success');
  });

  it('should show the stock message when the cart already holds all the units', () => {
    cartServiceMock.addItem.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'No hay stock suficiente para la cantidad solicitada', code: 'INSUFFICIENT_STOCK' },
    })));
    const addBtn = fixture.nativeElement.querySelector('.add-to-cart-action-btn') as HTMLButtonElement;

    addBtn.click();

    expect(toastServiceMock.show).toHaveBeenCalledWith('No hay stock suficiente para la cantidad solicitada', 'error');
  });

  it('should ignore repeated add-to-cart clicks while the request is pending', () => {
    cartServiceMock.addItem.mockReturnValue(NEVER);
    const addBtn = fixture.nativeElement.querySelector('.add-to-cart-action-btn') as HTMLButtonElement;

    addBtn.click();
    addBtn.click();

    expect(cartServiceMock.addItem).toHaveBeenCalledTimes(1);
    expect(component.cartAdding()['p-1']).toBe(true);
  });

  it('should render products and allow toggling favorite with feedback', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const cards = el.querySelectorAll('.product-card');
    expect(cards.length).toBe(2);

    const firstTitle = cards[0].querySelector('.product-card-title')?.textContent;
    expect(firstTitle).toContain('Auriculares Bluetooth');

    // Click heart on first product
    const heartBtn = cards[0].querySelector('.heart-center-button') as HTMLButtonElement;
    expect(heartBtn).not.toBeNull();
    heartBtn?.click();

    expect(wishlistServiceMock.toggleWishlist).toHaveBeenCalledWith('p-1');
    expect(toastServiceMock.show).toHaveBeenCalledWith('Lista actualizada', 'success');
  });

  it('should render discount badges and provider name on product cards', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const firstCard = el.querySelector('.product-card');
    expect(firstCard).not.toBeNull();

    const standardBadge = firstCard?.querySelector('.standard-discount-badge');
    expect(standardBadge?.textContent).toContain('-15%');

    const premiumBadge = firstCard?.querySelector('.premium-discount-badge');
    expect(premiumBadge?.textContent).toContain('-25% Premium');

    const vendorInfo = firstCard?.querySelector('.product-vendor-info');
    expect(vendorInfo?.textContent).toContain('Proveedor:');
    expect(vendorInfo?.textContent).toContain('AudioTech SL');
  });

  it('should reload products when filters change', () => {
    component.searchQuery.set('Cafetera');
    component.onFilterChange();

    expect(productsServiceMock.getPublicCatalog).toHaveBeenCalledWith(
      expect.objectContaining({ query: 'Cafetera' })
    );
  });

  it('should render admin read-only mode without wishlist, add-to-cart or client sidebar for ADMIN', () => {
    currentUserSignal.set({
      id: 'admin-1',
      email: 'admin@example.com',
      role: 'ADMIN',
      active: true,
    });
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.admin-readonly-badge, .admin-mode-pill')?.textContent).toContain('Modo Administrador (Solo lectura)');
    expect(el.querySelector('.heart-center-button')).toBeNull();
    expect(el.querySelector('.add-to-cart-action-btn')).toBeNull();

    // The left sidebar must NOT be rendered for admin
    expect(el.querySelector('.sidebar')).toBeNull();
    expect(el.querySelector('.app-sidebar')).toBeNull();
    expect(el.querySelectorAll('.navigation-link').length).toBe(0);
  });

  it('should expose a category error and recover on retry', () => {
    productsServiceMock.getCategories.mockReturnValueOnce(
      throwError(() => new Error('categories unavailable')),
    );

    component.loadCategories();
    expect(component.categoryError()).toBe(true);

    productsServiceMock.getCategories.mockReturnValueOnce(of(mockCategories));
    component.loadCategories();

    expect(component.categoryError()).toBe(false);
    expect(component.categories()).toEqual(mockCategories);
  });
});
