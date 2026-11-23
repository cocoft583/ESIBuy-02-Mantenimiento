import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ProductRating,
  ProductSales,
  SellerProductSummary,
  SellerRatingStatistics,
  SellerSalesStatistics,
  StatisticsService,
} from '../../../core/services/statistics.service';
import { ChartRenderer } from '../../../shared/statistics-ranking/chart-renderer';
import { SellerStatistics } from './seller-statistics';

function sale(productId: string, productName: string, unitsSold: number, revenue: number): ProductSales {
  return { productId, productName, sellerId: 's-1', sellerName: 'Tienda Ana', unitsSold, revenue };
}

const SALES: SellerSalesStatistics = {
  topSold: [sale('p-1', 'Teclado', 8, 160), sale('p-2', 'Auriculares', 3, 90)],
  leastSold: [sale('p-3', 'Funda', 0, 0), sale('p-4', 'Cable', 0, 0), sale('p-2', 'Auriculares', 3, 90)],
};

function rated(productId: string, productName: string, rating: number, reviewCount: number): ProductRating {
  return { productId, productName, sellerId: 's-1', sellerName: 'Tienda Ana', rating, reviewCount };
}

const RATINGS: SellerRatingStatistics = {
  topRated: [rated('p-1', 'Teclado', 4.67, 3), rated('p-2', 'Ratón', 2, 1)],
  worstRated: [rated('p-2', 'Ratón', 2, 1), rated('p-1', 'Teclado', 4.67, 3)],
  averageRating: 3.33,
  ratedProducts: 2,
  platformAverage: 4.1,
};

const SUMMARY: SellerProductSummary = {
  totalProducts: 12,
  outOfStockProducts: 2,
  lowStockProducts: 0,
  lowStockThreshold: 5,
};

describe('SellerStatistics', () => {
  let fixture: ComponentFixture<SellerStatistics>;
  let element: HTMLElement;
  let getSellerSales: ReturnType<typeof vi.fn>;
  let getSellerProductSummary: ReturnType<typeof vi.fn>;
  let getSellerRatings: ReturnType<typeof vi.fn>;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(SellerStatistics);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function rankingRows(index: number): string[] {
    const ranking = element.querySelectorAll('app-statistics-ranking')[index];
    return Array.from(ranking.querySelectorAll('table.ranking-table tbody tr'))
      .map((row) => row.textContent?.replace(/\s+/g, ' ').trim() ?? '');
  }

  async function openRatings(): Promise<void> {
    (element.querySelector('#statistics-tab-ratings') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function cardLabels(): string[] {
    return Array.from(element.querySelectorAll('.kpi-card .kpi-label')).map((label) => label.textContent?.trim() ?? '');
  }

  beforeEach(async () => {
    getSellerSales = vi.fn(() => of(SALES));
    getSellerProductSummary = vi.fn(() => of(SUMMARY));
    getSellerRatings = vi.fn(() => of(RATINGS));
    await TestBed.configureTestingModule({
      imports: [SellerStatistics],
      providers: [
        provideRouter([]),
        { provide: StatisticsService, useValue: { getSellerSales, getSellerProductSummary, getSellerRatings } },
        { provide: ChartRenderer, useValue: { create: vi.fn(() => ({ update: vi.fn(), destroy: vi.fn(), replay: vi.fn() })) } },
      ],
    }).compileComponents();
  });

  it('uses the seller sidebar with the statistics entry enabled', async () => {
    await render();

    expect(element.querySelector('app-shared-sidebar[variant="seller"]')).not.toBeNull();
    expect(element.querySelector('app-shared-sidebar a[href="/seller/estadisticas"]')?.textContent?.trim())
      .toBe('Estadísticas');
    expect(element.querySelector('h1')?.textContent).toContain('Estadísticas');
  });

  it('shows the quick metrics of the catalog and flags products out of stock', async () => {
    await render();

    expect(getSellerProductSummary).toHaveBeenCalledOnce();
    expect(cardLabels().slice(0, 3)).toEqual(['Productos', 'Sin stock', 'Poco stock']);
    const cards = element.querySelectorAll('.quick-metrics .kpi-card');
    expect(cards[1].classList).toContain('is-warning');
    expect(cards[2].classList).not.toContain('is-warning');
    expect(cards[2].textContent).toContain('5 unidades o menos');
  });

  it('shows the top sold ranking from most to least units with the average price', async () => {
    await render();

    expect(getSellerSales).toHaveBeenCalledOnce();
    const rows = rankingRows(0);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('Teclado');
    expect(rows[0]).toContain('20,00 € de media');
    expect(rows[1]).toContain('Auriculares');
  });

  it('shows the least sold ranking with the products never sold first', async () => {
    await render();

    const rows = rankingRows(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Funda');
    expect(rows[0]).toContain('Sin ventas');
    expect(rows[1]).toContain('Cable');
    expect(rows[2]).toContain('Auriculares');
  });

  it('sums up the sales and counts the products never sold', async () => {
    await render();

    const component = fixture.componentInstance;
    expect(component.totalUnits()).toBe(11);
    expect(component.totalRevenue()).toBe(250);
    expect(component.topProduct()?.productName).toBe('Teclado');
    expect(component.neverSoldCount()).toBe(2);
    expect(cardLabels()).toContain('Sin vender');
  });

  it('keeps showing the rankings when the quick metrics fail', async () => {
    getSellerProductSummary.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    await render();

    expect(element.querySelector('.quick-metrics')).toBeNull();
    expect(element.querySelectorAll('app-statistics-ranking')).toHaveLength(2);
  });

  it('shows the products never sold even if the seller has not sold anything yet', async () => {
    getSellerSales.mockReturnValue(of({ topSold: [], leastSold: [sale('p-3', 'Funda', 0, 0)] }));

    await render();

    expect(element.querySelector('.statistics-empty')).toBeNull();
    expect(element.querySelectorAll('app-statistics-ranking')[0].textContent)
      .toContain('Todavía no has vendido ningún producto.');
    expect(rankingRows(1)[0]).toContain('Sin ventas');
    expect(element.querySelector('.kpi-card.is-highlight')?.textContent).toContain('Aún no has vendido nada');
  });

  it('invites the seller to add products when it has none', async () => {
    getSellerSales.mockReturnValue(of({ topSold: [], leastSold: [] }));

    await render();

    expect(element.querySelector('.statistics-empty')?.textContent).toContain('Todavía no tienes productos');
    expect(element.querySelector('app-statistics-ranking')).toBeNull();
  });

  it('shows an error and retries when the sales cannot be loaded', async () => {
    getSellerSales.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

    await render();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    (element.querySelector('.retry-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getSellerSales).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelectorAll('app-statistics-ranking')).toHaveLength(2);
  });

  it('explains that only sellers can see their statistics when the server forbids it', async () => {
    getSellerSales.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Solo un vendedor puede ver sus estadísticas.');
  });

  describe('valoraciones (HU25)', () => {
    it('shows sales first and does not ask for the ratings until their tab is opened', async () => {
      await render();

      expect(element.querySelector('#statistics-tab-sales')?.getAttribute('aria-selected')).toBe('true');
      expect(element.querySelector('#statistics-tab-ratings')?.getAttribute('aria-selected')).toBe('false');
      expect(getSellerRatings).not.toHaveBeenCalled();
    });

    it('shows the seller average next to the platform one, apart from the sales', async () => {
      await render();
      await openRatings();

      expect(getSellerRatings).toHaveBeenCalledOnce();
      expect(element.querySelector('#statistics-panel-ratings')).not.toBeNull();
      expect(element.querySelector('#statistics-panel-sales')).toBeNull();
      expect(cardLabels()).toEqual(expect.arrayContaining(['Tu valoración media', 'Media de la plataforma', 'Productos valorados']));
      const highlight = element.querySelector('#statistics-panel-ratings .kpi-card.is-highlight');
      expect(highlight?.textContent).toContain('Tu valoración media');
      expect(fixture.componentInstance.sellerReference()).toEqual({ value: 3.33, label: 'Tu valoración media' });
    });

    it('shows the best and the worst rated products with how many reviews each one has', async () => {
      await render();
      await openRatings();

      const best = rankingRows(0);
      expect(best).toHaveLength(2);
      expect(best[0]).toContain('Teclado');
      expect(best[0]).toContain('3 valoraciones');
      const worst = rankingRows(1);
      expect(worst[0]).toContain('Ratón');
      expect(worst[0]).toContain('1 valoración');
    });

    it('asks for the ratings only once when the tab is opened again', async () => {
      await render();
      await openRatings();
      (element.querySelector('#statistics-tab-sales') as HTMLButtonElement).click();
      fixture.detectChanges();
      await openRatings();

      expect(getSellerRatings).toHaveBeenCalledOnce();
    });

    it('explains that there are no ratings yet and still shows the platform average', async () => {
      getSellerRatings.mockReturnValue(of({
        topRated: [], worstRated: [], averageRating: null, ratedProducts: 0, platformAverage: 4.1,
      }));

      await render();
      await openRatings();

      expect(element.querySelector('#statistics-panel-ratings .statistics-empty')?.textContent)
        .toContain('Todavía no tienes valoraciones');
      expect(element.querySelector('#statistics-panel-ratings app-statistics-ranking')).toBeNull();
      expect(fixture.componentInstance.sellerReference()).toBeNull();
    });

    it('shows an error and retries when the ratings cannot be loaded', async () => {
      getSellerRatings.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

      await render();
      await openRatings();

      expect(element.querySelector('#statistics-panel-ratings [role="alert"]')).not.toBeNull();
      (element.querySelector('#statistics-panel-ratings .retry-button') as HTMLButtonElement).click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(getSellerRatings).toHaveBeenCalledTimes(2);
      expect(element.querySelectorAll('#statistics-panel-ratings app-statistics-ranking')).toHaveLength(2);
    });

    it('moves between the tabs with the arrow keys', async () => {
      await render();

      const salesTab = element.querySelector('#statistics-tab-sales') as HTMLButtonElement;
      salesTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.componentInstance.section()).toBe('ratings');
      expect(getSellerRatings).toHaveBeenCalledOnce();
    });
  });
});
