import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProductSales, StatisticsService } from './statistics.service';

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
});
