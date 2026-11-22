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
}
