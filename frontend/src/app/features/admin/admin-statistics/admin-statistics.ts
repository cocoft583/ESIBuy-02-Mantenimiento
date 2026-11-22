import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ProductSales, StatisticsService } from '../../../core/services/statistics.service';
import { httpErrorMessage } from '../../../core/utils/http-error-message';
import { AppSidebarComponent } from '../../../shared/app-sidebar/app-sidebar';
import { CountUp } from '../../../shared/statistics-ranking/count-up';
import { RankingMetric, RankingRow } from '../../../shared/statistics-ranking/ranking.model';
import { StatisticsRanking } from '../../../shared/statistics-ranking/statistics-ranking';

const UNKNOWN_SELLER = 'Vendedor no disponible';
const UNKNOWN_SELLER_ID = '__sin-vendedor__';
const GENERIC_ERROR = 'No se pudieron cargar las estadísticas. Inténtalo de nuevo.';

const SALES_METRICS: RankingMetric[] = [
  { key: 'units', label: 'Unidades vendidas', format: 'number' },
  { key: 'revenue', label: 'Ingresos', format: 'currency' },
];

@Component({
  selector: 'app-admin-statistics',
  standalone: true,
  imports: [AppSidebarComponent, StatisticsRanking, CountUp],
  templateUrl: './admin-statistics.html',
  styleUrl: './admin-statistics.css',
})
export class AdminStatistics implements OnInit {
  private readonly statisticsService = inject(StatisticsService);

  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly sales = signal<ProductSales[]>([]);

  readonly salesMetrics = SALES_METRICS;

  /** Ranking de productos tal como lo ordena el backend (de más a menos unidades). */
  readonly productRows = computed<RankingRow[]>(() => this.sales().map((sale) => ({
    id: sale.productId,
    label: sale.productName,
    detail: sale.sellerName ?? UNKNOWN_SELLER,
    values: { units: sale.unitsSold, revenue: sale.revenue },
  })));

  /** Los mismos 10 productos agrupados por vendedor, de más a menos unidades. */
  readonly sellerRows = computed<RankingRow[]>(() => {
    const groups = new Map<string, { name: string; products: number; units: number; revenue: number }>();
    for (const sale of this.sales()) {
      const id = sale.sellerId ?? UNKNOWN_SELLER_ID;
      const group = groups.get(id) ?? { name: sale.sellerName ?? UNKNOWN_SELLER, products: 0, units: 0, revenue: 0 };
      group.products += 1;
      group.units += sale.unitsSold;
      group.revenue += sale.revenue;
      groups.set(id, group);
    }
    return Array.from(groups.entries())
      .sort(([, a], [, b]) => b.units - a.units || b.revenue - a.revenue)
      .map(([id, group]) => ({
        id,
        label: group.name,
        detail: `${group.products} ${group.products === 1 ? 'producto' : 'productos'}`,
        values: { units: group.units, revenue: Math.round(group.revenue * 100) / 100 },
      }));
  });

  readonly totalUnits = computed(() => this.sales().reduce((total, sale) => total + sale.unitsSold, 0));
  readonly totalRevenue = computed(() =>
    Math.round(this.sales().reduce((total, sale) => total + sale.revenue, 0) * 100) / 100);
  readonly topProduct = computed(() => this.sales()[0] ?? null);
  readonly sellerCount = computed(() => this.sellerRows().length);

  ngOnInit(): void {
    this.loadSales();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  loadSales(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.statisticsService.getSales().subscribe({
      next: (sales) => {
        this.sales.set(sales);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.getErrorMessage(error));
      },
    });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Solo un administrador puede ver las estadísticas.';
    }
    return httpErrorMessage(error) ?? GENERIC_ERROR;
  }
}
