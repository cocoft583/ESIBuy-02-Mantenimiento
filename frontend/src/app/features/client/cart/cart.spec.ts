import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CartService } from '../../../core/services/cart.service';
import { PedidoService } from '../../../core/services/pedido.service';
import { ClientCart } from './cart';

describe('ClientCart', () => {
  it('should render cart item names as text and show the backend total', async () => {
    const cart = signal({
      id: 'cart-1',
      clienteId: 'client-1',
      items: [{
        productoId: 'product-1',
        nombre: '<script>alert(1)</script>',
        precioUnitario: 12.5,
        cantidad: 2,
        subtotal: 25,
      }],
      precioTotal: 25,
    });

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        {
          provide: CartService,
          useValue: {
            cart,
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.item-information h2')?.textContent).toBe('<script>alert(1)</script>');
    expect(element.querySelector('.item-information h2 script')).toBeNull();
    expect(element.querySelector('.total-line')?.textContent).toContain('0,00 €');

    fixture.componentInstance.setSelectedProductIds(['product-1']);
    fixture.detectChanges();
    expect(element.querySelector('.total-line')?.textContent).toContain('25,00 €');
    await TestBed.inject(Router).navigateByUrl('/client/cart');
    fixture.detectChanges();
    const navigationLinks = Array.from(element.querySelectorAll('.app-navigation .navigation-link'));
    expect(navigationLinks).toHaveLength(5);
    expect(navigationLinks[2].textContent).toContain('Mis pedidos');
    expect(navigationLinks[3].getAttribute('href')).toBe('/client/cart');
    expect(navigationLinks[3].getAttribute('aria-current')).toBe('page');
    const orderIcon = navigationLinks[2].querySelector('svg');
    expect(orderIcon?.querySelector('path')?.getAttribute('d')).toContain('M21 8');
  });

  it('should submit quantity changes and prevent concurrent updates for the same item', async () => {
    const update = new Subject<never>();
    const updateItemQuantity = vi.fn(() => update.asObservable());

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: signal({
              id: 'cart-1',
              clienteId: 'client-1',
              items: [{
                productoId: 'product-1',
                nombre: 'Auriculares',
                precioUnitario: 12.5,
                cantidad: 2,
                subtotal: 25,
              }],
              precioTotal: 25,
            }),
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
            updateItemQuantity,
            removeItem: vi.fn(() => update.asObservable()),
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('.quantity-action') as NodeListOf<HTMLButtonElement>;

    buttons[1].click();
    fixture.detectChanges();
    buttons[1].click();

    expect(updateItemQuantity).toHaveBeenCalledTimes(1);
    expect(updateItemQuantity).toHaveBeenCalledWith('product-1', 3);
    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(true);

    update.complete();
    fixture.detectChanges();
    expect(buttons[0].disabled).toBe(false);
  });

  it('should call the remove endpoint action from the delete control', async () => {
    const removeItem = vi.fn(() => new Subject<never>().asObservable());

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: signal({
              id: 'cart-1',
              clienteId: 'client-1',
              items: [{
                productoId: 'product-1',
                nombre: 'Auriculares',
                precioUnitario: 12.5,
                cantidad: 2,
                subtotal: 25,
              }],
              precioTotal: 25,
            }),
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
            updateItemQuantity: vi.fn(),
            removeItem,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const removeButton = fixture.nativeElement.querySelector('.remove-action') as HTMLButtonElement;
    removeButton.click();

    expect(removeItem).toHaveBeenCalledWith('product-1');
  });

  it('should show the generated order identifier and clear cart state on success', async () => {
    const clear = vi.fn();
    const summary = {
      direcciones: [{
        id: 'address-1',
        recipientName: 'Ana López',
        street: 'Calle Mayor 1',
        additionalInfo: null,
        postalCode: '13001',
        city: 'Ciudad Real',
        province: 'Ciudad Real',
        phone: '600123456',
        isDefault: true,
      }],
      metodosPago: [{
        id: 'payment-1',
        type: 'CARD',
        cardBrand: 'VISA',
        lastFourDigits: '1234',
        defaultMethod: true,
      }],
      items: [{
        productoId: 'product-1',
        nombre: 'Auriculares',
        precioUnitario: 19.99,
        cantidad: 2,
        subtotal: 39.98,
      }],
      subtotal: 39.98,
      ivaPorcentaje: 21,
      ivaImporte: 8.4,
      gastosEnvio: 4.99,
      precioTotal: 53.37,
    };
    const obtenerCheckoutResumen = vi.fn(() => of(summary));
    const crearPedido = vi.fn(() => of({
      pedidoId: 'pedido-123',
      precioTotal: 53.37,
      estado: 'PENDIENTE' as const,
      estadoPago: 'PAGADO' as const,
      fechaCreacion: '2026-10-17T12:00:00',
    }));

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: signal({
              id: 'cart-1',
              clienteId: 'client-1',
              items: summary.items,
              precioTotal: summary.precioTotal,
            }),
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
            clear,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen, crearPedido } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();
    fixture.componentInstance.setSelectedProductIds(['product-1']);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.checkout-action') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(obtenerCheckoutResumen).toHaveBeenCalledOnce();
    expect((fixture.nativeElement.querySelector('#shipping-address') as HTMLSelectElement).value)
      .toBe('address-1');
    (fixture.nativeElement.querySelector('.checkout-actions .primary-action') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(crearPedido).toHaveBeenCalledWith('address-1', 'payment-1');
    expect(clear).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelector('.order-number')?.textContent).toContain('pedido-123');
    expect(fixture.nativeElement.querySelector('.checkout-panel')).toBeNull();
  });

  it('should keep checkout visible and show safe backend errors when order creation fails', async () => {
    const summary = {
      direcciones: [{
        id: 'address-1',
        recipientName: 'Ana',
        street: 'Calle Mayor 1',
        additionalInfo: null,
        postalCode: '13001',
        city: 'Ciudad Real',
        province: 'Ciudad Real',
        phone: '',
        isDefault: true,
      }],
      metodosPago: [{
        id: 'payment-1',
        type: 'CARD',
        cardBrand: 'VISA',
        lastFourDigits: '1234',
        defaultMethod: true,
      }],
      items: [{
        productoId: 'product-1',
        nombre: 'Auriculares',
        precioUnitario: 19.99,
        cantidad: 2,
        subtotal: 39.98,
      }],
      subtotal: 39.98,
      ivaPorcentaje: 21,
      ivaImporte: 8.4,
      gastosEnvio: 4.99,
      precioTotal: 53.37,
    };
    const crearPedido = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 402,
      error: { message: 'El pago fue rechazado. No se ha realizado ningún cargo ni modificado tu carrito' },
    })));

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: signal({
              id: 'cart-1',
              clienteId: 'client-1',
              items: summary.items,
              precioTotal: summary.precioTotal,
            }),
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
            clear: vi.fn(),
          },
        },
        {
          provide: PedidoService, useValue: {
            obtenerCheckoutResumen: vi.fn(() => of(summary)),
            crearPedido,
          }
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();
    fixture.componentInstance.setSelectedProductIds(['product-1']);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.checkout-action') as HTMLButtonElement).click();
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.checkout-actions .primary-action') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('El pago fue rechazado');
    expect(fixture.nativeElement.querySelector('.checkout-panel')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.order-confirmation')).toBeNull();
  });

  it('should render product image when imageUrl is provided, and fallback when missing', async () => {
    const cart = signal({
      id: 'cart-1',
      clienteId: 'client-1',
      items: [
        {
          productoId: 'p-1',
          nombre: 'Auriculares Pro',
          precioUnitario: 50,
          cantidad: 1,
          subtotal: 50,
          imageUrl: 'https://example.com/auriculares.jpg',
        },
        {
          productoId: 'p-2',
          nombre: 'Cable USB',
          precioUnitario: 10,
          cantidad: 1,
          subtotal: 10,
        },
      ],
      precioTotal: 60,
    });

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart,
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const items = element.querySelectorAll('.cart-item');
    expect(items.length).toBe(2);

    const img = items[0].querySelector('.product-thumb') as HTMLImageElement;
    expect(img).toBeTruthy();
    expect(img.getAttribute('src')).toBe('https://example.com/auriculares.jpg');

    const fallback = items[1].querySelector('.thumb-fallback');
    expect(fallback).toBeTruthy();
  });

  it('should group cart items by vendor and calculate total based on selected vendor items', async () => {
    const cart = signal({
      id: 'cart-1',
      clienteId: 'client-1',
      items: [
        {
          productoId: 'p-1',
          nombre: 'Whopper',
          precioUnitario: 8.5,
          cantidad: 2,
          subtotal: 17,
          vendedorId: 'seller-bk',
          vendedorNombre: 'Burger King',
        },
        {
          productoId: 'p-2',
          nombre: 'Patatas',
          precioUnitario: 3.5,
          cantidad: 1,
          subtotal: 3.5,
          vendedorId: 'seller-bk',
          vendedorNombre: 'Burger King',
        },
      ],
      precioTotal: 20.5,
    });

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart,
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 3,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.seller-summary-name')?.textContent).toContain('Burger King');
    expect(element.querySelector('.seller-warning-alert')).toBeNull();
    // Por defecto inicia en 0,00 € y checkout deshabilitado
    expect(element.querySelector('.total-line')?.textContent).toContain('0,00');
    expect(element.querySelector('.seller-summary-subtotal')?.textContent).toContain('0,00');
    const checkoutBtn = element.querySelector('.checkout-action') as HTMLButtonElement;
    expect(checkoutBtn.disabled).toBe(true);

    // Al seleccionar el vendedor, se seleccionan todos sus productos y se calcula el total
    fixture.componentInstance.toggleSellerSelection(fixture.componentInstance.sellerGroups()[0]);
    fixture.detectChanges();

    expect(element.querySelector('.total-line')?.textContent).toContain('20,50');
    expect(element.querySelector('.seller-summary-subtotal')?.textContent).toContain('20,50');
    expect(checkoutBtn.disabled).toBe(false);
  });

  it('should only sum selected vendor items and prevent selecting products from another vendor', async () => {
    const items = [
      {
        productoId: 'p-1',
        nombre: 'Whopper',
        precioUnitario: 8.5,
        cantidad: 1,
        subtotal: 8.5,
        vendedorId: 'seller-bk',
        vendedorNombre: 'Burger King',
      },
      {
        productoId: 'p-2',
        nombre: 'Big Mac',
        precioUnitario: 8.0,
        cantidad: 1,
        subtotal: 8.0,
        vendedorId: 'seller-mcd',
        vendedorNombre: 'McDonalds',
      },
    ];
    const cart = signal({
      id: 'cart-1',
      clienteId: 'client-1',
      items,
      precioTotal: 16.5,
    });

    const obtenerCheckoutResumen = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart,
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 2,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen, crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    // When multiple sellers are in the cart, client selects a vendor first
    const firstGroup = fixture.componentInstance.sellerGroups()[0];
    fixture.componentInstance.toggleSellerSelection(firstGroup);
    fixture.detectChanges();

    // Total only includes selected vendor (Burger King: 8,50 €), NOT the entire cart (16,50 €)
    expect(element.querySelector('.total-line')?.textContent).toContain('8,50');
    expect(element.querySelector('.total-line')?.textContent).not.toContain('16,50');

    // McDonalds seller is disabled because Burger King has active selection
    expect(fixture.componentInstance.isSellerDisabled('seller-mcd')).toBe(true);
    const mcdCheckboxes = element.querySelectorAll('.seller-accordion')[1].querySelectorAll('input[type="checkbox"]');
    expect((mcdCheckboxes[0] as HTMLInputElement).disabled).toBe(true);
    expect((mcdCheckboxes[1] as HTMLInputElement).disabled).toBe(true);

    // Attempting to select product from McDonalds while Burger King is selected
    const secondGroup = fixture.componentInstance.sellerGroups()[1];
    fixture.componentInstance.toggleProductSelection(items[1], secondGroup);
    fixture.detectChanges();

    expect(fixture.componentInstance.isProductSelected('p-2')).toBe(false);
    expect(fixture.componentInstance.actionError()).toContain('No puedes realizar pedidos de diferente vendedor a la vez');

    // If multiple sellers are selected simultaneously, total price is removed (displays —) and checkout is blocked
    fixture.componentInstance.setSelectedProductIds(['p-1', 'p-2']);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTotal()).toBeNull();
    expect(element.querySelector('.no-price-indicator')?.textContent).toContain('—');

    const checkoutBtn = element.querySelector('.checkout-action') as HTMLButtonElement;
    expect(checkoutBtn.disabled).toBe(true);
  });
  it('should only show and order selected products in checkout when subset is chosen', async () => {
    const summary = {
      direcciones: [{
        id: 'address-1',
        recipientName: 'Pepe',
        street: 'Pepito, 12',
        additionalInfo: null,
        postalCode: '12222',
        city: 'PEPELAND',
        province: 'PEPELAND',
        phone: '',
        isDefault: true,
      }],
      metodosPago: [{
        id: 'payment-1',
        type: 'CARD',
        cardBrand: 'VISA',
        lastFourDigits: '1236',
        defaultMethod: true,
      }],
      items: [
        { productoId: 'p-1', nombre: 'Auriculares', precioUnitario: 25, cantidad: 2, subtotal: 50, vendedorId: 'v-1', vendedorNombre: 'cc' },
        { productoId: 'p-2', nombre: 'Batería Yamaha', precioUnitario: 300, cantidad: 1, subtotal: 300, vendedorId: 'v-1', vendedorNombre: 'cc' },
        { productoId: 'p-3', nombre: 'Casita de juguete', precioUnitario: 60, cantidad: 1, subtotal: 60, vendedorId: 'v-2', vendedorNombre: 'Messi' },
      ],
      subtotal: 410,
      ivaPorcentaje: 21,
      ivaImporte: 86.1,
      gastosEnvio: 0,
      precioTotal: 496.1,
    };

    const obtenerCheckoutResumen = vi.fn(() => of({
      ...summary,
      items: [summary.items[0]],
      subtotal: 50,
      ivaImporte: 10.5,
      gastosEnvio: 0,
      precioTotal: 60.5,
    }));
    const crearPedido = vi.fn(() => of({
      pedidoId: 'pedido-auriculares',
      precioTotal: 60.5,
      estado: 'PENDIENTE' as const,
      estadoPago: 'PAGADO' as const,
      fechaCreacion: '2026-10-27T18:00:00',
    }));

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart: signal({
              id: 'cart-1',
              clienteId: 'client-1',
              items: summary.items,
              precioTotal: 410,
            }),
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 4,
            clear: vi.fn(),
            load: vi.fn(),
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen, crearPedido } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    // Client explicitly selects ONLY Auriculares (p-1)
    fixture.componentInstance.setSelectedProductIds(['p-1']);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedItemCount()).toBe(2);
    expect(fixture.componentInstance.selectedTotal()).toBe(50);

    // Click checkout
    (fixture.nativeElement.querySelector('.checkout-action') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    // Checkout panel summary should only display Auriculares (not Batería or Casita)
    const rows = element.querySelectorAll('.checkout-items tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].querySelector('th')?.textContent).toBe('Auriculares');
    expect(rows[0].querySelectorAll('td')[0].textContent).toContain('2');
    expect(rows[0].querySelectorAll('td')[1].textContent).toContain('50,00');

    expect(element.querySelector('.checkout-total strong')?.textContent).toContain('60,50');

    // Confirm order
    (element.querySelector('.checkout-actions .primary-action') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(crearPedido).toHaveBeenCalledWith('address-1', 'payment-1', 'v-1', ['p-1']);
  });
  it('should render discount pills, strikethrough price and total savings when discount is present', async () => {
    const cart = signal({
      id: 'cart-1',
      clienteId: 'client-1',
      items: [
        {
          productoId: 'p-1',
          nombre: 'Auriculares con Descuento',
          precioUnitario: 80,
          precioOriginal: 100,
          descuento: 20,
          esDescuentoPremium: false,
          cantidad: 1,
          subtotal: 80,
        },
      ],
      totalSinDescuento: 100,
      descuentoTotal: 20,
      precioTotal: 80,
    });

    await TestBed.configureTestingModule({
      imports: [ClientCart],
      providers: [
        provideRouter([]),
        {
          provide: CartService,
          useValue: {
            cart,
            loading: signal(false),
            loadError: signal(null),
            itemCount: () => 1,
          },
        },
        { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ClientCart);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.item-original-price')?.textContent).toContain('100,00');
    expect(element.querySelector('.item-discount-tag')?.textContent).toContain('-20%');
    // Inicialmente sin seleccionar no hay línea de descuento y total es 0,00
    expect(element.querySelector('.total-line')?.textContent).toContain('0,00');
    expect(element.querySelector('.discount-saving-line')).toBeNull();

    // Al seleccionar el producto con descuento
    fixture.componentInstance.setSelectedProductIds(['p-1']);
    fixture.detectChanges();

    expect(element.querySelector('.discount-saving-line')?.textContent).toContain('20,00');
    expect(element.querySelector('.total-line')?.textContent).toContain('80,00');
  });

  describe('stock disponible', () => {
    async function createCart(
      item: { cantidad: number; stockDisponible?: number | null },
      updateItemQuantity = vi.fn(() => new Subject<never>().asObservable()),
    ) {
      await TestBed.configureTestingModule({
        imports: [ClientCart],
        providers: [
          provideRouter([]),
          {
            provide: CartService,
            useValue: {
              cart: signal({
                id: 'cart-1',
                clienteId: 'client-1',
                items: [{
                  productoId: 'product-1',
                  nombre: 'Auriculares',
                  precioUnitario: 10,
                  subtotal: 10 * item.cantidad,
                  ...item,
                }],
                precioTotal: 10 * item.cantidad,
              }),
              loading: signal(false),
              loadError: signal(null),
              itemCount: () => item.cantidad,
              updateItemQuantity,
              removeItem: vi.fn(() => new Subject<never>().asObservable()),
            },
          },
          { provide: PedidoService, useValue: { obtenerCheckoutResumen: vi.fn(), crearPedido: vi.fn() } },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(ClientCart);
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      const increase = element.querySelector('[aria-label="Aumentar cantidad de Auriculares"]') as HTMLButtonElement;
      const decrease = element.querySelector('[aria-label="Reducir cantidad de Auriculares"]') as HTMLButtonElement;
      return { fixture, element, increase, decrease, updateItemQuantity };
    }

    it('should show the backend stock message when the quantity exceeds the stock', async () => {
      const updateItemQuantity = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 409,
      error: { message: 'No hay stock suficiente para la cantidad solicitada', code: 'INSUFFICIENT_STOCK' },
    })));
      const { fixture, element, increase } = await createCart({ cantidad: 1, stockDisponible: 5 }, updateItemQuantity);

      increase.click();
      fixture.detectChanges();

      expect(updateItemQuantity).toHaveBeenCalledWith('product-1', 2);
      expect(element.querySelector('[role="alert"]')?.textContent)
        .toContain('No hay stock suficiente para la cantidad solicitada');
    });

    it('should keep the generic message when the backend sends no message', async () => {
      const updateItemQuantity = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 500 })));
      const { fixture, element, increase } = await createCart({ cantidad: 1, stockDisponible: 5 }, updateItemQuantity);

      increase.click();
      fixture.detectChanges();

      expect(element.querySelector('[role="alert"]')?.textContent)
        .toContain('No se pudo actualizar el carrito. Inténtalo de nuevo.');
    });

    it('should disable the increase button and show the limit once the cart holds all the stock', async () => {
      const { element, increase, decrease, updateItemQuantity } = await createCart({ cantidad: 3, stockDisponible: 3 });

      expect(increase.disabled).toBe(true);
      expect(decrease.disabled).toBe(false);
      expect(element.querySelector('.stock-limit')?.textContent).toContain('Máximo disponible: 3');

      increase.click();
      expect(updateItemQuantity).not.toHaveBeenCalled();
    });

    it('should warn when the cart holds more units than the stock left and still allow reducing them', async () => {
      const { fixture, element, increase, decrease, updateItemQuantity } =
        await createCart({ cantidad: 4, stockDisponible: 2 });

      expect(increase.disabled).toBe(true);
      expect(element.querySelector('.stock-limit')?.textContent).toContain('Solo quedan 2 unidades');

      decrease.click();
      fixture.detectChanges();
      expect(updateItemQuantity).toHaveBeenCalledWith('product-1', 3);
    });

    it('should not ask the server for more units than the stock allows', async () => {
      const { fixture, element, updateItemQuantity } = await createCart({ cantidad: 3, stockDisponible: 3 });

      fixture.componentInstance.changeQuantity('product-1', 4);
      fixture.detectChanges();

      expect(updateItemQuantity).not.toHaveBeenCalled();
      expect(element.querySelector('[role="alert"]')?.textContent)
        .toContain('No hay stock suficiente para la cantidad solicitada');
    });

    it('should keep the increase button enabled below the stock or when the stock is unknown', async () => {
      const belowStock = await createCart({ cantidad: 2, stockDisponible: 3 });
      expect(belowStock.increase.disabled).toBe(false);
      expect(belowStock.element.querySelector('.stock-limit')).toBeNull();

      TestBed.resetTestingModule();
      const unknownStock = await createCart({ cantidad: 2, stockDisponible: null });
      expect(unknownStock.increase.disabled).toBe(false);
      expect(unknownStock.element.querySelector('.stock-limit')).toBeNull();
    });
  });

  describe('resultado del pago (HU11)', () => {
    async function placeOrder(order$: ReturnType<PedidoService['crearPedido']>) {
      const items = [{
        productoId: 'product-1',
        nombre: 'Auriculares',
        precioUnitario: 19.99,
        cantidad: 2,
        subtotal: 39.98,
      }];
      const summary = {
        direcciones: [{
          id: 'address-1',
          recipientName: 'Ana',
          street: 'Calle Mayor 1',
          additionalInfo: null,
          postalCode: '13001',
          city: 'Ciudad Real',
          province: 'Ciudad Real',
          phone: '',
          isDefault: true,
        }],
        metodosPago: [{
          id: 'payment-1',
          type: 'CARD',
          cardBrand: 'VISA',
          lastFourDigits: '1234',
          defaultMethod: true,
        }],
        items,
        subtotal: 39.98,
        ivaPorcentaje: 21,
        ivaImporte: 6.94,
        gastosEnvio: 0,
        precioTotal: 39.98,
      };

      await TestBed.configureTestingModule({
        imports: [ClientCart],
        providers: [
          provideRouter([]),
          {
            provide: CartService,
            useValue: {
              cart: signal({ id: 'cart-1', clienteId: 'client-1', items, precioTotal: 39.98 }),
              loading: signal(false),
              loadError: signal(null),
              itemCount: () => 2,
              clear: vi.fn(),
            },
          },
          {
            provide: PedidoService,
            useValue: {
              obtenerCheckoutResumen: vi.fn(() => of(summary)),
              crearPedido: vi.fn(() => order$),
            },
          },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(ClientCart);
      fixture.detectChanges();
      fixture.componentInstance.setSelectedProductIds(['product-1']);
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('.checkout-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('.checkout-actions .primary-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    function failWith(status: number, message?: string) {
      return throwError(() => new HttpErrorResponse({
        status,
        error: message ? { message } : null,
      }));
    }

    it('should say the order was not placed and nothing was charged when the backend could not complete it', async () => {
      const element = await placeOrder(failWith(
        503,
        'No se ha podido completar el pedido. No se te ha cobrado nada ni se ha modificado tu carrito. Inténtalo de nuevo más tarde.',
      ));

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('No se te ha cobrado nada');
      expect(element.querySelector('.checkout-panel')).not.toBeNull();
      expect(element.querySelector('.order-confirmation')).toBeNull();
    });

    it('should say nothing was charged instead of the generic internal error when the server fails', async () => {
      const element = await placeOrder(failWith(
        500,
        'Se produjo un error interno. Consulte el identificador de correlación.',
      ));

      const alert = element.querySelector('[role="alert"]')?.textContent ?? '';
      expect(alert).toContain('El pedido no se ha completado y no se ha realizado ningún cargo');
      expect(alert).not.toContain('error interno');
      expect(element.querySelector('.checkout-panel')).not.toBeNull();
    });

    it('should ask to check the orders before retrying when the connection is lost while paying', async () => {
      const element = await placeOrder(failWith(0));

      const alert = element.querySelector('[role="alert"]')?.textContent ?? '';
      expect(alert).toContain('No hemos podido saber si tu pedido se ha completado');
      expect(alert).toContain('revisa Mis pedidos');
      expect(element.querySelector('.checkout-panel')).not.toBeNull();
    });

    it('should confirm the charge and link to the order history when the order is placed', async () => {
      const element = await placeOrder(of({
        pedidoId: 'pedido-123',
        precioTotal: 39.98,
        estado: 'PENDIENTE' as const,
        estadoPago: 'PAGADO' as const,
        fechaCreacion: '2026-11-22T12:00:00',
      }));

      const confirmation = element.querySelector('.order-confirmation');
      expect(confirmation?.textContent).toContain('Se ha realizado el cargo');
      const historyLink = confirmation?.querySelector('a[href="/client/orders"]');
      expect(historyLink?.textContent).toContain('Mis pedidos');
      expect(element.querySelector('[role="alert"]')).toBeNull();
    });
  });

  describe('aviso de lo que falta para tramitar el pedido', () => {
    const address = {
      id: 'address-1',
      recipientName: 'Ana',
      street: 'Calle Mayor 1',
      additionalInfo: null,
      postalCode: '13001',
      city: 'Ciudad Real',
      province: 'Ciudad Real',
      phone: '',
      isDefault: true,
    };
    const paymentMethod = {
      id: 'payment-1',
      type: 'CARD',
      cardBrand: 'VISA',
      lastFourDigits: '1234',
      defaultMethod: true,
    };

    async function openCheckout(direcciones: unknown[], metodosPago: unknown[]): Promise<string> {
      const items = [{
        productoId: 'product-1',
        nombre: 'Auriculares',
        precioUnitario: 19.99,
        cantidad: 1,
        subtotal: 19.99,
      }];
      const summary = {
        direcciones,
        metodosPago,
        items,
        subtotal: 19.99,
        ivaPorcentaje: 21,
        ivaImporte: 3.47,
        gastosEnvio: 0,
        precioTotal: 19.99,
      };

      await TestBed.configureTestingModule({
        imports: [ClientCart],
        providers: [
          provideRouter([]),
          {
            provide: CartService,
            useValue: {
              cart: signal({ id: 'cart-1', clienteId: 'client-1', items, precioTotal: 19.99 }),
              loading: signal(false),
              loadError: signal(null),
              itemCount: () => 1,
            },
          },
          {
            provide: PedidoService,
            useValue: { obtenerCheckoutResumen: vi.fn(() => of(summary)), crearPedido: vi.fn() },
          },
        ],
      }).compileComponents();

      const fixture = TestBed.createComponent(ClientCart);
      fixture.detectChanges();
      fixture.componentInstance.setSelectedProductIds(['product-1']);
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('.checkout-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      return (fixture.nativeElement as HTMLElement).querySelector('.checkout-notice')?.textContent?.trim() ?? '';
    }

    it('si solo falta el método de pago, pide solo el método de pago', async () => {
      const notice = await openCheckout([address], []);

      expect(notice).toBe('Guarda un método de pago en tu perfil para continuar.');
    });

    it('si solo falta la dirección, pide solo la dirección de envío', async () => {
      const notice = await openCheckout([], [paymentMethod]);

      expect(notice).toBe('Guarda una dirección de envío en tu perfil para continuar.');
    });

    it('si faltan los dos, pide la dirección y el método de pago', async () => {
      const notice = await openCheckout([], []);

      expect(notice).toBe('Guarda una dirección de envío y un método de pago en tu perfil para continuar.');
    });
  });
});
