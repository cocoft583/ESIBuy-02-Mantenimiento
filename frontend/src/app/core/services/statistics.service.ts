import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';

/** Una fila del ranking de ventas (GET /api/admin/statistics/sales). */
export interface ProductSales {
  productId: string;
  productName: string;
  sellerId: string | null;
  sellerName: string | null;
  unitsSold: number;
  revenue: number;
}

@Injectable({ providedIn: 'root' })
export class StatisticsService {
  private readonly http = inject(HttpClient);

  getSales(): Observable<ProductSales[]> {
    return of([]);
  }
}
