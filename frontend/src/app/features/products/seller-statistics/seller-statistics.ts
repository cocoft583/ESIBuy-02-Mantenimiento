import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  ProductSales,
  SellerProductSummary,
  StatisticsService,
} from '../../../core/services/statistics.service';
import { httpErrorMessage } from '../../../core/utils/http-error-message';
import { AppSidebarComponent } from '../../../shared/app-sidebar/app-sidebar';
import { CountUp } from '../../../shared/statistics-ranking/count-up';
import { RankingMetric, RankingRow } from '../../../shared/statistics-ranking/ranking.model';
import { StatisticsRanking } from '../../../shared/statistics-ranking/statistics-ranking';

const GENERIC_ERROR = 'No se pudieron cargar tus estadísticas. Inténtalo de nuevo.';
const NEVER_SOLD = 'Sin ventas';

const SALES_METRICS: RankingMetric[] = [
  { key: 'units', label: 'Unidades vendidas', format: 'number' },
  { key: 'revenue', label: 'Ingresos', format: 'currency' },
];

const currency = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });

function toRankingRow(sale: ProductSales): RankingRow {
  return {
    id: sale.productId,
    label: sale.productName,
    detail: sale.unitsSold > 0
      ? `${currency.format(Math.round((sale.revenue / sale.unitsSold) * 100) / 100)} de media`
      : NEVER_SOLD,
    values: { units: sale.unitsSold, revenue: sale.revenue },
  };
}

/**
 * Estadísticas del vendedor de la sesión: métricas rápidas de su catálogo y sus productos más y
 * menos vendidos, para decidir qué mejorar o retirar (HU24).
 */
@Component({
  selector: 'app-seller-statistics',
  standalone: true,
  imports: [AppSidebarComponent, StatisticsRanking, CountUp],
  templateUrl: './seller-statistics.html',
  // Mismo aspecto que las estadísticas del administrador, más lo propio del vendedor
  styleUrls: ['../../admin/admin-statistics/admin-statistics.css', './seller-statistics.css'],
})
export class SellerStatistics implements OnInit {
  private readonly statisticsService = inject(StatisticsService);

  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly topSold = signal<ProductSales[]>([]);
  readonly leastSold = signal<ProductSales[]>([]);
  /** Las métricas rápidas son un extra: si fallan, el ranking se sigue viendo sin ellas. */
  readonly summary = signal<SellerProductSummary | null>(null);

  readonly salesMetrics = SALES_METRICS;

  readonly topRows = computed<RankingRow[]>(() => this.topSold().map(toRankingRow));
  readonly leastRows = computed<RankingRow[]>(() => this.leastSold().map(toRankingRow));

  readonly hasProducts = computed(() => this.topSold().length > 0 || this.leastSold().length > 0);
  readonly totalUnits = computed(() => this.topSold().reduce((total, sale) => total + sale.unitsSold, 0));
  readonly totalRevenue = computed(() =>
    Math.round(this.topSold().reduce((total, sale) => total + sale.revenue, 0) * 100) / 100);
  readonly topProduct = computed(() => this.topSold()[0] ?? null);
  readonly neverSoldCount = computed(() => this.leastSold().filter((sale) => sale.unitsSold === 0).length);

  ngOnInit(): void {
    this.load();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  load(): void {
    this.loadSales();
    this.loadSummary();
  }

  private loadSales(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.statisticsService.getSellerSales().subscribe({
      next: (sales) => {
        this.topSold.set(sales.topSold);
        this.leastSold.set(sales.leastSold);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.getErrorMessage(error));
      },
    });
  }

  private loadSummary(): void {
    this.statisticsService.getSellerProductSummary().subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => this.summary.set(null),
    });
  }

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Solo un vendedor puede ver sus estadísticas.';
    }
    return httpErrorMessage(error) ?? GENERIC_ERROR;
  }
}
