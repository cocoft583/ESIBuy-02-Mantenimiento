import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChartRenderer, RankingChartData } from './chart-renderer';
import { RankingMetric, RankingRow } from './ranking.model';
import { StatisticsRanking } from './statistics-ranking';

const METRICS: RankingMetric[] = [
  { key: 'units', label: 'Unidades vendidas', format: 'number' },
  { key: 'revenue', label: 'Ingresos', format: 'currency' },
];

const ROWS: RankingRow[] = [
  { id: 'p-1', label: 'Auriculares', detail: 'Tienda Uno', values: { units: 8, revenue: 160 } },
  { id: 'p-2', label: 'Teclado', detail: null, values: { units: 4, revenue: 200.5 } },
];

describe('StatisticsRanking', () => {
  let fixture: ComponentFixture<StatisticsRanking>;
  let element: HTMLElement;
  let chart: { update: ReturnType<typeof vi.fn>; destroy: ReturnType<typeof vi.fn> };
  let create: ReturnType<typeof vi.fn>;

  async function render(inputs: Partial<{ rows: RankingRow[]; initialView: string; chartTypes: string[] }> = {}) {
    fixture = TestBed.createComponent(StatisticsRanking);
    fixture.componentRef.setInput('heading', 'Productos más vendidos');
    fixture.componentRef.setInput('metrics', METRICS);
    fixture.componentRef.setInput('rows', inputs.rows ?? ROWS);
    fixture.componentRef.setInput('emptyMessage', 'Todavía no hay ventas.');
    if (inputs.initialView) fixture.componentRef.setInput('initialView', inputs.initialView);
    if (inputs.chartTypes) fixture.componentRef.setInput('chartTypes', inputs.chartTypes);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  function lastChartData(): RankingChartData {
    const updates = chart.update.mock.calls;
    return (updates.length ? updates[updates.length - 1][0] : create.mock.calls[create.mock.calls.length - 1][1]) as RankingChartData;
  }

  function button(container: string, text: string): HTMLButtonElement {
    const found = Array.from(element.querySelectorAll<HTMLButtonElement>(`${container} button`))
      .find((candidate) => candidate.textContent?.trim() === text);
    expect(found, text).toBeDefined();
    return found!;
  }

  beforeEach(async () => {
    chart = { update: vi.fn(), destroy: vi.fn() };
    create = vi.fn(() => chart);
    await TestBed.configureTestingModule({
      imports: [StatisticsRanking],
      providers: [{ provide: ChartRenderer, useValue: { create } }],
    }).compileComponents();
  });

  afterEach(() => {
    Reflect.deleteProperty(window, 'matchMedia');
  });

  it('shows every row in the table with its position, name, detail and formatted values', async () => {
    await render();

    expect(element.querySelector('h2')?.textContent).toContain('Productos más vendidos');
    const rows = Array.from(element.querySelectorAll('table.ranking-table tbody tr'));
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('1');
    expect(rows[0].textContent).toContain('Auriculares');
    expect(rows[0].textContent).toContain('Tienda Uno');
    expect(rows[0].textContent).toContain('8');
    expect(rows[0].textContent).toContain('160,00');
    expect(rows[1].textContent).toContain('Teclado');
    expect(rows[1].textContent).toContain('—');
    expect(rows[1].textContent).toContain('200,50');
  });

  it('draws a bar chart of the first metric by default', async () => {
    await render();

    expect(create).toHaveBeenCalledOnce();
    const [canvas, data] = create.mock.calls[0] as [HTMLCanvasElement, RankingChartData];
    expect(canvas).toBe(element.querySelector('canvas.ranking-canvas'));
    expect(data.type).toBe('bar');
    expect(data.labels).toEqual(['Auriculares', 'Teclado']);
    expect(data.values).toEqual([8, 4]);
    expect(data.metricLabel).toBe('Unidades vendidas');
    expect(data.formatValue(1234)).toBe('1234');
  });

  it('describes the chart for screen readers', async () => {
    await render();

    const canvas = element.querySelector('canvas.ranking-canvas');
    expect(canvas?.getAttribute('role')).toBe('img');
    expect(canvas?.getAttribute('aria-label')).toContain('Unidades vendidas');
    expect(canvas?.getAttribute('aria-label')).toContain('Auriculares: 8');
  });

  it('switches the chart to another metric and keeps the table showing its share', async () => {
    await render();

    const select = element.querySelector('select.ranking-metric') as HTMLSelectElement;
    select.value = 'revenue';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const data = lastChartData();
    expect(data.values).toEqual([160, 200.5]);
    expect(data.metricLabel).toBe('Ingresos');
    expect(data.formatValue(160)).toContain('160,00');
    const shares = Array.from(element.querySelectorAll<HTMLElement>('.share-fill')).map((fill) => fill.style.width);
    expect(shares[1]).toBe('100%');
    expect(Number.parseFloat(shares[0])).toBeCloseTo(79.8, 1);
  });

  it('changes the kind of chart from the chart type buttons', async () => {
    await render();

    button('.ranking-chart-type', 'Anillo').click();
    fixture.detectChanges();
    expect(lastChartData().type).toBe('doughnut');
    expect(button('.ranking-chart-type', 'Anillo').getAttribute('aria-pressed')).toBe('true');

    button('.ranking-chart-type', 'Polar').click();
    fixture.detectChanges();
    expect(lastChartData().type).toBe('polarArea');
  });

  it('offers only the chart types it is given', async () => {
    await render({ chartTypes: ['bar', 'doughnut'] });

    const labels = Array.from(element.querySelectorAll('.ranking-chart-type button')).map((b) => b.textContent?.trim());
    expect(labels).toEqual(['Barras', 'Anillo']);
  });

  it('shows the table, the chart or both (RF-EST-06)', async () => {
    await render();
    expect(element.querySelector('table.ranking-table')).not.toBeNull();
    expect(element.querySelector('canvas.ranking-canvas')).not.toBeNull();
    expect(button('.ranking-view-toggle', 'Tabla y gráfico').getAttribute('aria-pressed')).toBe('true');

    button('.ranking-view-toggle', 'Tabla').click();
    fixture.detectChanges();
    expect(element.querySelector('table.ranking-table')).not.toBeNull();
    expect(element.querySelector('canvas.ranking-canvas')).toBeNull();
    expect(element.querySelector('.ranking-chart-type')).toBeNull();
    expect(chart.destroy).toHaveBeenCalledOnce();

    button('.ranking-view-toggle', 'Gráfico').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element.querySelector('table.ranking-table')).toBeNull();
    expect(element.querySelector('canvas.ranking-canvas')).not.toBeNull();
    expect(create).toHaveBeenCalledTimes(2);
  });

  it('can start on a given view', async () => {
    await render({ initialView: 'table' });

    expect(element.querySelector('canvas.ranking-canvas')).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it('shows the empty message instead of the table and the chart when there are no rows', async () => {
    await render({ rows: [] });

    expect(element.querySelector('.ranking-empty')?.textContent).toContain('Todavía no hay ventas.');
    expect(element.querySelector('table.ranking-table')).toBeNull();
    expect(element.querySelector('canvas.ranking-canvas')).toBeNull();
    expect(create).not.toHaveBeenCalled();
  });

  it('redraws the chart when the rows change', async () => {
    await render();

    fixture.componentRef.setInput('rows', [ROWS[1]]);
    fixture.detectChanges();

    expect(lastChartData().labels).toEqual(['Teclado']);
  });

  it('does not animate the chart when the user prefers reduced motion', async () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn((query: string) => ({ matches: query.includes('prefers-reduced-motion: reduce'), media: query })),
    });

    await render();

    expect(lastChartData().animate).toBe(false);
  });

  it('frees the chart when the component goes away', async () => {
    await render();

    fixture.destroy();

    expect(chart.destroy).toHaveBeenCalledOnce();
  });
});
