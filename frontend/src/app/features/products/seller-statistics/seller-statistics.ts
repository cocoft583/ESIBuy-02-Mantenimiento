import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import {
  ProductRating,
  ProductSales,
  SellerProductSummary,
  SellerRatingStatistics,
  StatisticsService,
} from '../../../core/services/statistics.service';
import { httpErrorMessage } from '../../../core/utils/http-error-message';
import { AppSidebarComponent } from '../../../shared/app-sidebar/app-sidebar';
import { CountUp } from '../../../shared/statistics-ranking/count-up';
import { RankingMetric, RankingReference, RankingRow } from '../../../shared/statistics-ranking/ranking.model';
import { StatisticsRanking } from '../../../shared/statistics-ranking/statistics-ranking';

export type SellerStatisticsSection = 'sales' | 'ratings';

const SECTIONS: SellerStatisticsSection[] = ['sales', 'ratings'];
const GENERIC_ERROR = 'No se pudieron cargar tus estadísticas. Inténtalo de nuevo.';
const NEVER_SOLD = 'Sin ventas';

const SALES_METRICS: RankingMetric[] = [
  { key: 'units', label: 'Unidades vendidas', format: 'number' },
  { key: 'revenue', label: 'Ingresos', format: 'currency' },
];

const RATING_METRICS: RankingMetric[] = [
  { key: 'rating', label: 'Valoración media', format: 'rating' },
  { key: 'reviews', label: 'Valoraciones', format: 'number' },
];

function toRatingRow(product: ProductRating): RankingRow {
  return {
    id: product.productId,
    label: product.productName,
    detail: `${product.reviewCount} ${product.reviewCount === 1 ? 'valoración' : 'valoraciones'}`,
    values: { rating: product.rating, reviews: product.reviewCount },
  };
}

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
 * Estadísticas del vendedor de la sesión: métricas rápidas de su catálogo, sus productos más y
 * menos vendidos, para decidir qué mejorar o retirar (HU24), y en otra pestaña sus productos
 * mejor y peor valorados con su valoración media (HU25).
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
  readonly section = signal<SellerStatisticsSection>('sales');

  // Ventas (HU24)
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

  // Valoraciones (HU25): se piden la primera vez que se abre su pestaña
  readonly ratingsLoading = signal(false);
  readonly ratingsError = signal<string | null>(null);
  readonly ratings = signal<SellerRatingStatistics | null>(null);
  readonly ratingMetrics = RATING_METRICS;

  readonly refreshing = computed(() => (this.section() === 'sales' ? this.loading() : this.ratingsLoading()));

  readonly topRatedRows = computed<RankingRow[]>(() => (this.ratings()?.topRated ?? []).map(toRatingRow));
  readonly worstRatedRows = computed<RankingRow[]>(() => (this.ratings()?.worstRated ?? []).map(toRatingRow));
  readonly bestRated = computed(() => this.ratings()?.topRated[0] ?? null);
  readonly hasNoRatings = computed(() => (this.ratings()?.topRated.length ?? 0) === 0);

  /** Su media como línea de referencia: se ve qué productos la suben y cuáles la bajan. */
  readonly sellerReference = computed<RankingReference | null>(() => {
    const average = this.ratings()?.averageRating;
    return average === null || average === undefined ? null : { value: average, label: 'Tu valoración media' };
  });

  ngOnInit(): void {
    this.load();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  openSection(section: SellerStatisticsSection): void {
    this.section.set(section);
    if (section === 'ratings' && !this.ratings() && !this.ratingsLoading()) {
      this.loadRatings();
    }
  }

  /** Flechas, Inicio y Fin mueven entre pestañas, como pide el patrón de pestañas de ARIA. */
  onTabKeydown(event: KeyboardEvent): void {
    const index = SECTIONS.indexOf(this.section());
    const next = {
      ArrowRight: SECTIONS[(index + 1) % SECTIONS.length],
      ArrowLeft: SECTIONS[(index - 1 + SECTIONS.length) % SECTIONS.length],
      Home: SECTIONS[0],
      End: SECTIONS[SECTIONS.length - 1],
    }[event.key];
    if (!next) {
      return;
    }
    event.preventDefault();
    this.openSection(next);
    const tab = (event.currentTarget as HTMLElement | null)?.parentElement
      ?.querySelector<HTMLElement>(`#statistics-tab-${next}`);
    tab?.focus();
  }

  /** El botón Actualizar recarga la pestaña que se está viendo. */
  refresh(): void {
    if (this.section() === 'sales') {
      this.load();
    } else {
      this.loadRatings();
    }
  }

  loadRatings(): void {
    this.ratingsLoading.set(true);
    this.ratingsError.set(null);

    this.statisticsService.getSellerRatings().subscribe({
      next: (ratings) => {
        this.ratings.set(ratings);
        this.ratingsLoading.set(false);
      },
      error: (error: unknown) => {
        this.ratingsLoading.set(false);
        this.ratingsError.set(this.getErrorMessage(error));
      },
    });
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
