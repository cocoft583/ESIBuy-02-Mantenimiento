import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductSales, StatisticsService } from '../../../core/services/statistics.service';
import { ChartRenderer } from '../../../shared/statistics-ranking/chart-renderer';
import { AdminStatistics } from './admin-statistics';

const SALES: ProductSales[] = [
  { productId: 'p-1', productName: 'Auriculares', sellerId: 's-1', sellerName: 'Tienda Uno', unitsSold: 10, revenue: 200 },
  { productId: 'p-2', productName: 'Teclado', sellerId: 's-2', sellerName: 'Tienda Dos', unitsSold: 6, revenue: 300 },
  { productId: 'p-3', productName: 'Ratón', sellerId: 's-1', sellerName: 'Tienda Uno', unitsSold: 4, revenue: 40 },
  { productId: 'p-4', productName: 'Funda', sellerId: null, sellerName: null, unitsSold: 2, revenue: 10.5 },
];

describe('AdminStatistics', () => {
  let fixture: ComponentFixture<AdminStatistics>;
  let element: HTMLElement;
  let getSales: ReturnType<typeof vi.fn>;

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
    await TestBed.configureTestingModule({
      imports: [AdminStatistics],
      providers: [
        provideRouter([]),
        { provide: StatisticsService, useValue: { getSales } },
        { provide: ChartRenderer, useValue: { create: vi.fn(() => ({ update: vi.fn(), destroy: vi.fn() })) } },
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
});
