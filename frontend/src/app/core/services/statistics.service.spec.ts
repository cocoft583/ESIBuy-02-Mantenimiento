import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  ProductSales,
  SellerProductSummary,
  SellerSalesStatistics,
  StatisticsService,
} from './statistics.service';

describe('StatisticsService', () => {
  let service: StatisticsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StatisticsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('asks the admin endpoint for the sales ranking with the session cookie', () => {
    let sales: ProductSales[] | undefined;
    service.getSales().subscribe((value) => (sales = value));

    const request = httpTesting.expectOne('/api/admin/statistics/sales');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush([{
      productId: 'p-1',
      productName: 'Auriculares',
      sellerId: 's-1',
      sellerName: 'Tienda Uno',
      unitsSold: 7,
      revenue: 139.93,
    }]);

    expect(sales).toEqual([{
      productId: 'p-1',
      productName: 'Auriculares',
      sellerId: 's-1',
      sellerName: 'Tienda Uno',
      unitsSold: 7,
      revenue: 139.93,
    }]);
  });

  it('turns revenue sent as text into a number and keeps a missing seller as null', () => {
    let sales: ProductSales[] | undefined;
    service.getSales().subscribe((value) => (sales = value));

    httpTesting.expectOne('/api/admin/statistics/sales').flush([{
      productId: 'p-2',
      productName: 'Teclado',
      unitsSold: 2,
      revenue: '45.50',
    }]);

    expect(sales?.[0].revenue).toBe(45.5);
    expect(sales?.[0].sellerId).toBeNull();
    expect(sales?.[0].sellerName).toBeNull();
  });

  it('asks the seller endpoint for its top and least sold products with the session cookie', () => {
    let sales: SellerSalesStatistics | undefined;
    service.getSellerSales().subscribe((value) => (sales = value));

    const request = httpTesting.expectOne('/api/seller/statistics/sales');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.params.keys()).toEqual([]);
    request.flush({
      scope: 'VENDEDOR',
      topSold: [{ productId: 'p-1', productName: 'Teclado', sellerId: 's-1', sellerName: 'Tienda Ana', unitsSold: 7, revenue: '140.50' }],
      leastSold: [{ productId: 'p-2', productName: 'Ratón', sellerId: 's-1', sellerName: 'Tienda Ana', unitsSold: 0, revenue: 0 }],
    });

    expect(sales?.topSold).toEqual([
      { productId: 'p-1', productName: 'Teclado', sellerId: 's-1', sellerName: 'Tienda Ana', unitsSold: 7, revenue: 140.5 },
    ]);
    expect(sales?.leastSold[0].unitsSold).toBe(0);
    expect(sales?.leastSold[0].revenue).toBe(0);
  });

  it('treats missing seller rankings as empty lists', () => {
    let sales: SellerSalesStatistics | undefined;
    service.getSellerSales().subscribe((value) => (sales = value));

    httpTesting.expectOne('/api/seller/statistics/sales').flush({ scope: 'VENDEDOR' });

    expect(sales).toEqual({ topSold: [], leastSold: [] });
  });

  it('asks for the quick metrics of the seller catalog', () => {
    let summary: SellerProductSummary | undefined;
    service.getSellerProductSummary().subscribe((value) => (summary = value));

    const request = httpTesting.expectOne('/api/products/summary');
    expect(request.request.method).toBe('GET');
    request.flush({ totalProducts: 12, outOfStockProducts: 2, lowStockProducts: 3, lowStockThreshold: 5 });

    expect(summary).toEqual({ totalProducts: 12, outOfStockProducts: 2, lowStockProducts: 3, lowStockThreshold: 5 });
  });
});
