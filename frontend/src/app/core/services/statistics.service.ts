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

/** Ranking de ventas del vendedor de la sesión (GET /api/seller/statistics/sales). */
export interface SellerSalesStatistics {
  /** Sus 10 productos más vendidos, de más a menos unidades. */
  topSold: ProductSales[];
  /** Sus 10 productos menos vendidos, de menos a más, incluidos los que no ha vendido nunca. */
  leastSold: ProductSales[];
}

interface SellerSalesStatisticsResponse {
  topSold?: ProductSalesResponse[] | null;
  leastSold?: ProductSalesResponse[] | null;
}

/** Valoraciones del vendedor de la sesión (GET /api/seller/statistics/ratings). */
export interface SellerRatingStatistics {
  /** Sus 10 productos mejor valorados, de más a menos nota. */
  topRated: ProductRating[];
  /** Sus 10 productos peor valorados, de menos a más nota. */
  worstRated: ProductRating[];
  /** Su valoración media, la misma que ve el administrador; null si no tiene ninguna. */
  averageRating: number | null;
  ratedProducts: number;
  platformAverage: number | null;
}

type ProductRatingResponse = Omit<ProductRating, 'sellerId' | 'sellerName' | 'rating'> & {
  sellerId?: string | null;
  sellerName?: string | null;
  rating: number | string;
};

interface SellerRatingStatisticsResponse {
  topRated?: ProductRatingResponse[] | null;
  worstRated?: ProductRatingResponse[] | null;
  averageRating?: number | null;
  ratedProducts?: number;
  platformAverage?: number | null;
}

function toProductRating(row: ProductRatingResponse): ProductRating {
  return {
    productId: row.productId,
    productName: row.productName,
    sellerId: row.sellerId ?? null,
    sellerName: row.sellerName ?? null,
    rating: Number(row.rating),
    reviewCount: Number(row.reviewCount),
  };
}

/** Métricas rápidas del catálogo del vendedor (GET /api/products/summary). */
export interface SellerProductSummary {
  totalProducts: number;
  outOfStockProducts: number;
  lowStockProducts: number;
  lowStockThreshold: number;
}

function toProductSales(row: ProductSalesResponse): ProductSales {
  return {
    productId: row.productId,
    productName: row.productName,
    sellerId: row.sellerId ?? null,
    sellerName: row.sellerName ?? null,
    unitsSold: Number(row.unitsSold),
    // El backend manda un BigDecimal: se acepta como número o como texto
    revenue: Number(row.revenue),
  };
}

@Injectable({ providedIn: 'root' })
export class StatisticsService {
  private readonly http = inject(HttpClient);

  /** Los 10 productos más vendidos de la plataforma; solo cuentan los pedidos pagados y no cancelados. */
  getSales(): Observable<ProductSales[]> {
    return this.http
      .get<ProductSalesResponse[]>('/api/admin/statistics/sales', { withCredentials: true })
      .pipe(map((rows) => rows.map(toProductSales)));
  }

  /**
   * Los 10 productos más vendidos y los 10 menos vendidos del vendedor de la sesión. El backend
   * saca el vendedor de la sesión: no hace falta (ni se puede) indicarlo.
   */
  getSellerSales(): Observable<SellerSalesStatistics> {
    return this.http
      .get<SellerSalesStatisticsResponse>('/api/seller/statistics/sales', { withCredentials: true })
      .pipe(map((response) => ({
        topSold: (response.topSold ?? []).map(toProductSales),
        leastSold: (response.leastSold ?? []).map(toProductSales),
      })));
  }

  /**
   * Los 10 productos mejor valorados y los 10 peor valorados del vendedor de la sesión, su
   * valoración media y la de la plataforma. Como en las ventas, el vendedor sale de la sesión.
   */
  getSellerRatings(): Observable<SellerRatingStatistics> {
    return this.http
      .get<SellerRatingStatisticsResponse>('/api/seller/statistics/ratings', { withCredentials: true })
      .pipe(map((response) => ({
        topRated: (response.topRated ?? []).map(toProductRating),
        worstRated: (response.worstRated ?? []).map(toProductRating),
        averageRating: response.averageRating ?? null,
        ratedProducts: Number(response.ratedProducts ?? 0),
        platformAverage: response.platformAverage ?? null,
      })));
  }

  /** Cuántos productos tiene el vendedor, cuántos sin stock y cuántos con poco stock. */
  getSellerProductSummary(): Observable<SellerProductSummary> {
    return this.http
      .get<SellerProductSummary>('/api/products/summary', { withCredentials: true })
      .pipe(map((summary) => ({
        totalProducts: Number(summary.totalProducts),
        outOfStockProducts: Number(summary.outOfStockProducts),
        lowStockProducts: Number(summary.lowStockProducts),
        lowStockThreshold: Number(summary.lowStockThreshold),
      })));
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
