import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProductSales, RatingStatistics, StatisticsService } from './statistics.service';

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

  it('asks the admin endpoint for the rating statistics with the session cookie', () => {
    let ratings: RatingStatistics | undefined;
    service.getRatings().subscribe((value) => (ratings = value));

    const request = httpTesting.expectOne('/api/admin/statistics/ratings');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      topProducts: [{ productId: 'p-1', productName: 'Auriculares', sellerId: 's-1', sellerName: 'Tienda Uno', rating: 4.8, reviewCount: 12 }],
      topSellers: [{ sellerId: 's-1', sellerName: 'Tienda Uno', averageRating: 4.6, ratedProducts: 4 }],
      platformAverage: 4.25,
      minimumReviews: 3,
    });

    expect(ratings).toEqual({
      topProducts: [{ productId: 'p-1', productName: 'Auriculares', sellerId: 's-1', sellerName: 'Tienda Uno', rating: 4.8, reviewCount: 12 }],
      topSellers: [{ sellerId: 's-1', sellerName: 'Tienda Uno', averageRating: 4.6, ratedProducts: 4 }],
      platformAverage: 4.25,
      minimumReviews: 3,
    });
  });

  it('keeps a missing seller and a missing platform average as null', () => {
    let ratings: RatingStatistics | undefined;
    service.getRatings().subscribe((value) => (ratings = value));

    httpTesting.expectOne('/api/admin/statistics/ratings').flush({
      topProducts: [{ productId: 'p-2', productName: 'Teclado', rating: 4, reviewCount: 3 }],
      topSellers: [],
      minimumReviews: 5,
    });

    expect(ratings?.topProducts[0].sellerId).toBeNull();
    expect(ratings?.topProducts[0].sellerName).toBeNull();
    expect(ratings?.platformAverage).toBeNull();
    expect(ratings?.minimumReviews).toBe(5);
  });
});
