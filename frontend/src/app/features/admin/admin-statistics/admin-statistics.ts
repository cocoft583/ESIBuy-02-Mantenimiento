import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductSales, RatingStatistics, StatisticsService } from '../../../core/services/statistics.service';
import { httpErrorMessage } from '../../../core/utils/http-error-message';
import { AppSidebarComponent } from '../../../shared/app-sidebar/app-sidebar';
import { CountUp } from '../../../shared/statistics-ranking/count-up';
import { RankingMetric, RankingReference, RankingRow } from '../../../shared/statistics-ranking/ranking.model';
import { StatisticsRanking } from '../../../shared/statistics-ranking/statistics-ranking';

export type StatisticsSection = 'sales' | 'ratings';

const SECTIONS: StatisticsSection[] = ['sales', 'ratings'];
const UNKNOWN_SELLER = 'Vendedor no disponible';
const UNKNOWN_SELLER_ID = '__sin-vendedor__';
const GENERIC_ERROR = 'No se pudieron cargar las estadísticas. Inténtalo de nuevo.';

const SALES_METRICS: RankingMetric[] = [
  { key: 'units', label: 'Unidades vendidas', format: 'number' },
  { key: 'revenue', label: 'Ingresos', format: 'currency' },
];

const PRODUCT_RATING_METRICS: RankingMetric[] = [
  { key: 'rating', label: 'Valoración media', format: 'rating' },
  { key: 'reviews', label: 'Valoraciones', format: 'number' },
];

const SELLER_RATING_METRICS: RankingMetric[] = [
  { key: 'rating', label: 'Valoración media', format: 'rating' },
  { key: 'products', label: 'Productos valorados', format: 'number' },
];

@Component({
  selector: 'app-admin-statistics',
  standalone: true,
  imports: [AppSidebarComponent, StatisticsRanking, CountUp, RouterLink],
  templateUrl: './admin-statistics.html',
  styleUrl: './admin-statistics.css',
})
export class AdminStatistics implements OnInit {
  private readonly statisticsService = inject(StatisticsService);

  readonly sidebarCollapsed = signal(false);
  readonly section = signal<StatisticsSection>('sales');
  readonly unknownSeller = UNKNOWN_SELLER;

  // Ventas (HU22)
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly sales = signal<ProductSales[]>([]);
  readonly salesMetrics = SALES_METRICS;

  // Valoraciones (HU23): se piden la primera vez que se abre su pestaña
  readonly ratingsLoading = signal(false);
  readonly ratingsError = signal<string | null>(null);
  readonly ratings = signal<RatingStatistics | null>(null);
  readonly productRatingMetrics = PRODUCT_RATING_METRICS;
  readonly sellerRatingMetrics = SELLER_RATING_METRICS;

  readonly refreshing = computed(() => (this.section() === 'sales' ? this.loading() : this.ratingsLoading()));

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

  /** Productos mejor valorados, en el orden del backend (RF-EST-03). */
  readonly ratedProductRows = computed<RankingRow[]>(() => (this.ratings()?.topProducts ?? []).map((product) => ({
    id: product.productId,
    label: product.productName,
    detail: product.sellerName ?? UNKNOWN_SELLER,
    values: { rating: product.rating, reviews: product.reviewCount },
  })));

  /** Vendedores con mejor media de las medias de sus productos (RF-EST-04). */
  readonly ratedSellerRows = computed<RankingRow[]>(() => (this.ratings()?.topSellers ?? []).map((seller) => ({
    id: seller.sellerId,
    label: seller.sellerName ?? UNKNOWN_SELLER,
    detail: `${seller.ratedProducts} ${seller.ratedProducts === 1 ? 'producto valorado' : 'productos valorados'}`,
    values: { rating: seller.averageRating, products: seller.ratedProducts },
  })));

  /** La media de la plataforma, para compararla con cada vendedor (RF-EST-05). */
  readonly platformReference = computed<RankingReference | null>(() => {
    const average = this.ratings()?.platformAverage;
    return average === null || average === undefined ? null : { value: average, label: 'Media de la plataforma' };
  });

  readonly bestProduct = computed(() => this.ratings()?.topProducts[0] ?? null);
  readonly bestSeller = computed(() => this.ratings()?.topSellers[0] ?? null);
  readonly hasNoRatings = computed(() => {
    const ratings = this.ratings();
    return !!ratings && ratings.platformAverage === null
      && ratings.topProducts.length === 0 && ratings.topSellers.length === 0;
  });

  ngOnInit(): void {
    this.loadSales();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed.update((collapsed) => !collapsed);
  }

  openSection(section: StatisticsSection): void {
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

  refresh(): void {
    if (this.section() === 'sales') {
      this.loadSales();
    } else {
      this.loadRatings();
    }
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

  loadRatings(): void {
    this.ratingsLoading.set(true);
    this.ratingsError.set(null);

    this.statisticsService.getRatings().subscribe({
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

  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Solo un administrador puede ver las estadísticas.';
    }
    return httpErrorMessage(error) ?? GENERIC_ERROR;
  }
}
