import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Una fila del ranking de ventas (GET /api/admin/statistics/sales). */
export interface ProductSales {
  productId: string;
  productName: string;
  sellerId: string | null;
  sellerName: string | null;
  unitsSold: number;
  revenue: number;
}

/** Un producto del ranking de mejor valorados. */
export interface ProductRating {
  productId: string;
  productName: string;
  sellerId: string | null;
  sellerName: string | null;
  rating: number;
  reviewCount: number;
}

/** Un vendedor del ranking: la media de las medias de sus productos valorados. */
export interface SellerRating {
  sellerId: string;
  sellerName: string | null;
  averageRating: number;
  ratedProducts: number;
}

/** GET /api/admin/statistics/ratings */
export interface RatingStatistics {
  topProducts: ProductRating[];
  topSellers: SellerRating[];
  platformAverage: number | null;
  minimumReviews: number;
}

type RatingStatisticsResponse = {
  topProducts?: (Omit<ProductRating, 'sellerId' | 'sellerName'> & { sellerId?: string | null; sellerName?: string | null })[];
  topSellers?: (Omit<SellerRating, 'sellerName'> & { sellerName?: string | null })[];
  platformAverage?: number | null;
  minimumReviews: number;
};

type ProductSalesResponse = Omit<ProductSales, 'sellerId' | 'sellerName' | 'revenue'> & {
  sellerId?: string | null;
  sellerName?: string | null;
  revenue: number | string;
};

@Injectable({ providedIn: 'root' })
export class StatisticsService {
  private readonly http = inject(HttpClient);

  /** Los 10 productos más vendidos de la plataforma; solo cuentan los pedidos pagados y no cancelados. */
  getSales(): Observable<ProductSales[]> {
    return this.http
      .get<ProductSalesResponse[]>('/api/admin/statistics/sales', { withCredentials: true })
      .pipe(map((rows) => rows.map((row) => ({
        productId: row.productId,
        productName: row.productName,
        sellerId: row.sellerId ?? null,
        sellerName: row.sellerName ?? null,
        unitsSold: Number(row.unitsSold),
        // El backend manda un BigDecimal: se acepta como número o como texto
        revenue: Number(row.revenue),
      }))));
  }

  /**
   * Los 10 productos mejor valorados (con el mínimo de valoraciones de la configuración),
   * los 10 vendedores con mejor media y la media de la plataforma.
   */
  getRatings(): Observable<RatingStatistics> {
    return this.http
      .get<RatingStatisticsResponse>('/api/admin/statistics/ratings', { withCredentials: true })
      .pipe(map((response) => ({
        topProducts: (response.topProducts ?? []).map((product) => ({
          productId: product.productId,
          productName: product.productName,
          sellerId: product.sellerId ?? null,
          sellerName: product.sellerName ?? null,
          rating: Number(product.rating),
          reviewCount: Number(product.reviewCount),
        })),
        topSellers: (response.topSellers ?? []).map((seller) => ({
          sellerId: seller.sellerId,
          sellerName: seller.sellerName ?? null,
          averageRating: Number(seller.averageRating),
          ratedProducts: Number(seller.ratedProducts),
        })),
        platformAverage: response.platformAverage ?? null,
        minimumReviews: Number(response.minimumReviews),
      })));
  }
}
