import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductSales, RatingStatistics, StatisticsService } from '../../../core/services/statistics.service';
import { ChartRenderer } from '../../../shared/statistics-ranking/chart-renderer';
import { AdminStatistics } from './admin-statistics';

const SALES: ProductSales[] = [
  { productId: 'p-1', productName: 'Auriculares', sellerId: 's-1', sellerName: 'Tienda Uno', unitsSold: 10, revenue: 200 },
  { productId: 'p-2', productName: 'Teclado', sellerId: 's-2', sellerName: 'Tienda Dos', unitsSold: 6, revenue: 300 },
  { productId: 'p-3', productName: 'Ratón', sellerId: 's-1', sellerName: 'Tienda Uno', unitsSold: 4, revenue: 40 },
  { productId: 'p-4', productName: 'Funda', sellerId: null, sellerName: null, unitsSold: 2, revenue: 10.5 },
];

const RATINGS: RatingStatistics = {
  topProducts: [
    { productId: 'p-1', productName: 'Auriculares', sellerId: 's-1', sellerName: 'Tienda Uno', rating: 4.8, reviewCount: 12 },
    { productId: 'p-2', productName: 'Teclado', sellerId: null, sellerName: null, rating: 4.5, reviewCount: 3 },
  ],
  topSellers: [
    { sellerId: 's-1', sellerName: 'Tienda Uno', averageRating: 4.6, ratedProducts: 4 },
    { sellerId: 's-2', sellerName: 'Tienda Dos', averageRating: 3.9, ratedProducts: 1 },
  ],
  platformAverage: 4.25,
  minimumReviews: 3,
};

describe('AdminStatistics', () => {
  let fixture: ComponentFixture<AdminStatistics>;
  let element: HTMLElement;
  let getSales: ReturnType<typeof vi.fn>;
  let getRatings: ReturnType<typeof vi.fn>;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(AdminStatistics);
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

  beforeEach(async () => {
    getSales = vi.fn(() => of(SALES));
    getRatings = vi.fn(() => of(RATINGS));
    await TestBed.configureTestingModule({
      imports: [AdminStatistics],
      providers: [
        provideRouter([]),
        { provide: StatisticsService, useValue: { getSales, getRatings } },
        { provide: ChartRenderer, useValue: { create: vi.fn(() => ({ update: vi.fn(), destroy: vi.fn(), replay: vi.fn() })) } },
      ],
    }).compileComponents();
  });

  it('uses the administration sidebar with the statistics entry active', async () => {
    await render();

    expect(element.querySelector('app-shared-sidebar[variant="admin"]')).not.toBeNull();
    expect(element.querySelector('app-shared-sidebar a[href="/admin/estadisticas"]')?.textContent?.trim())
      .toBe('Estadísticas');
    expect(element.querySelector('h1')?.textContent).toContain('Estadísticas');
  });

  it('loads the sales ranking and sums it up in the summary cards', async () => {
    await render();

    expect(getSales).toHaveBeenCalledOnce();
    const cards = Array.from(element.querySelectorAll('.kpi-card')).map((card) => card.textContent?.replace(/\s+/g, ' ') ?? '');
    expect(cards).toHaveLength(4);
    expect(cards[0]).toContain('Unidades vendidas');
    expect(cards[0]).toContain('22');
    expect(cards[1]).toContain('Ingresos');
    expect(cards[1]).toContain('550,50');
    expect(cards[2]).toContain('Más vendido');
    expect(cards[2]).toContain('Auriculares');
    expect(cards[3]).toContain('Vendedores');
    expect(cards[3]).toContain('3');
  });

  it('shows the 10 best sellers with their seller, units and revenue (RF-EST-02)', async () => {
    await render();

    const rows = rankingRows(0);
    expect(rows).toHaveLength(4);
    expect(rows[0]).toContain('Auriculares');
    expect(rows[0]).toContain('Tienda Uno');
    expect(rows[0]).toContain('10');
    expect(rows[0]).toContain('200,00');
    expect(rows[3]).toContain('Funda');
    expect(rows[3]).toContain('Vendedor no disponible');
  });

  it('groups the best sellers by seller in a second view', async () => {
    await render();

    const headings = Array.from(element.querySelectorAll('app-statistics-ranking h2')).map((h) => h.textContent?.trim());
    expect(headings).toEqual(['Productos más vendidos', 'Reparto por vendedor']);
    const rows = rankingRows(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Tienda Uno');
    expect(rows[0]).toContain('14');
    expect(rows[0]).toContain('240,00');
    expect(rows[0]).toContain('2 productos');
    expect(rows[1]).toContain('Tienda Dos');
    expect(rows[2]).toContain('Vendedor no disponible');
    expect(rows[2]).toContain('1 producto');
  });

  it('shows a single empty state when nothing has been sold yet', async () => {
    getSales.mockReturnValue(of([]));
    await render();

    expect(element.querySelector('.statistics-empty')?.textContent).toContain('Todavía no hay ventas pagadas');
    expect(element.querySelector('.kpi-card')).toBeNull();
    expect(element.querySelector('app-statistics-ranking')).toBeNull();
  });

  it('shows the loading state while the request is in flight', async () => {
    getSales.mockReturnValue(new Subject<ProductSales[]>().asObservable());
    await render();

    expect(element.querySelector('output.state-message')?.textContent).toContain('Cargando');
  });

  it('shows an error and loads again from the retry button', async () => {
    getSales.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('No se pudieron cargar las estadísticas');
    (element.querySelector('.retry-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(getSales).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(rankingRows(0)).toHaveLength(4);
  });

  it('explains that only an administrator can see the statistics on a 403 (RNF-01)', async () => {
    getSales.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    await render();

    expect(element.querySelector('[role="alert"]')?.textContent)
      .toContain('Solo un administrador puede ver las estadísticas');
  });

  describe('valoraciones (HU23)', () => {
    function tab(text: string): HTMLButtonElement {
      const found = Array.from(element.querySelectorAll<HTMLButtonElement>('.statistics-tabs [role="tab"]'))
        .find((candidate) => candidate.textContent?.trim() === text);
      expect(found, text).toBeDefined();
      return found!;
    }

    async function openRatings(): Promise<void> {
      await render();
      tab('Valoraciones').click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }

    function cards(): string[] {
      return Array.from(element.querySelectorAll('.kpi-card')).map((card) => card.textContent?.replace(/\s+/g, ' ') ?? '');
    }

    it('keeps sales and ratings in separate tabs and only loads ratings when asked', async () => {
      await render();

      expect(tab('Ventas').getAttribute('aria-selected')).toBe('true');
      expect(tab('Valoraciones').getAttribute('aria-selected')).toBe('false');
      expect(getRatings).not.toHaveBeenCalled();

      tab('Valoraciones').click();
      fixture.detectChanges();

      expect(getRatings).toHaveBeenCalledOnce();
      expect(tab('Valoraciones').getAttribute('aria-selected')).toBe('true');
      const headings = Array.from(element.querySelectorAll('app-statistics-ranking h2')).map((h) => h.textContent?.trim());
      expect(headings).toEqual(['Productos mejor valorados', 'Vendedores mejor valorados']);
    });

    it('does not ask for the ratings again when going back to the tab', async () => {
      await openRatings();

      tab('Ventas').click();
      fixture.detectChanges();
      tab('Valoraciones').click();
      fixture.detectChanges();

      expect(getRatings).toHaveBeenCalledOnce();
      expect(getSales).toHaveBeenCalledOnce();
    });

    it('sums up the platform average, the best product and seller and the minimum of reviews', async () => {
      await openRatings();

      const summary = cards();
      expect(summary).toHaveLength(4);
      expect(summary[0]).toContain('Media de la plataforma');
      expect(summary[0]).toContain('4,25');
      expect(summary[1]).toContain('Mejor producto');
      expect(summary[1]).toContain('Auriculares');
      expect(summary[1]).toContain('4,8');
      expect(summary[2]).toContain('Mejor vendedor');
      expect(summary[2]).toContain('Tienda Uno');
      expect(summary[3]).toContain('Mínimo de valoraciones');
      expect(summary[3]).toContain('3');
      expect(element.querySelector('.kpi-card a[href="/admin/configuracion"]')).not.toBeNull();
    });

    it('lists the best rated products with their seller, rating and number of reviews (RF-EST-03)', async () => {
      await openRatings();

      const rows = rankingRows(0);
      expect(rows).toHaveLength(2);
      expect(rows[0]).toContain('Auriculares');
      expect(rows[0]).toContain('Tienda Uno');
      expect(rows[0]).toContain('4,8');
      expect(rows[0]).toContain('12');
      expect(rows[1]).toContain('Vendedor no disponible');
      expect(element.querySelectorAll('app-statistics-ranking')[0].textContent).toContain('al menos 3 valoraciones');
    });

    it('compares each seller with the platform average (RF-EST-04, RF-EST-05)', async () => {
      await openRatings();

      const rows = rankingRows(1);
      expect(rows).toHaveLength(2);
      expect(rows[0]).toContain('Tienda Uno');
      expect(rows[0]).toContain('4 productos valorados');
      expect(rows[0]).toContain('4,6');
      expect(rows[0]).toContain('+0,35');
      expect(rows[1]).toContain('1 producto valorado');
      expect(rows[1]).toContain('−0,35');
    });

    it('explains that no product reaches the minimum yet', async () => {
      getRatings.mockReturnValue(of({ ...RATINGS, topProducts: [] }));
      await openRatings();

      expect(element.querySelectorAll('app-statistics-ranking')[0].querySelector('.ranking-empty')?.textContent)
        .toContain('Ningún producto tiene todavía al menos 3 valoraciones');
    });

    it('shows a single empty state when there are no ratings at all', async () => {
      getRatings.mockReturnValue(of({ topProducts: [], topSellers: [], platformAverage: null, minimumReviews: 3 }));
      await openRatings();

      expect(element.querySelector('.statistics-empty')?.textContent).toContain('Todavía no hay valoraciones');
      expect(element.querySelector('app-statistics-ranking')).toBeNull();
      expect(element.querySelector('.kpi-card')).toBeNull();
    });

    it('shows an error and loads the ratings again from the retry button', async () => {
      getRatings.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
      await openRatings();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain('No se pudieron cargar las estadísticas');
      (element.querySelector('.retry-button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(getRatings).toHaveBeenCalledTimes(2);
      expect(rankingRows(0)).toHaveLength(2);
    });

    it('reloads the tab that is open from the refresh button', async () => {
      await openRatings();

      (element.querySelector('.refresh-button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(getRatings).toHaveBeenCalledTimes(2);
      expect(getSales).toHaveBeenCalledOnce();
    });
  });
});
