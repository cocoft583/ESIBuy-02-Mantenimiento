import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Chart.js necesita un canvas de verdad: aquí se sustituye por un doble que registra las llamadas
const charts: FakeChart[] = [];

class FakeChart {
  static register = vi.fn();
  static overrides = { doughnut: { plugins: { legend: { labels: { generateLabels: () => [] } } } } };
  readonly reset = vi.fn();
  readonly update = vi.fn();
  readonly destroy = vi.fn();
  data: unknown;
  options: unknown;

  constructor(readonly canvas: HTMLCanvasElement, readonly config: { type: string; data: unknown; options: unknown }) {
    this.data = config.data;
    this.options = config.options;
    charts.push(this);
  }
}

vi.mock('chart.js', () => ({
  Chart: FakeChart,
  ArcElement: {},
  BarController: {},
  BarElement: {},
  CategoryScale: {},
  DoughnutController: {},
  Legend: {},
  LinearScale: {},
  PolarAreaController: {},
  RadialLinearScale: {},
  Tooltip: {},
}));

const { ChartRenderer } = await import('./chart-renderer');
type ChartData = Parameters<InstanceType<typeof ChartRenderer>['create']>[1];

function data(overrides: Partial<ChartData> = {}): ChartData {
  return {
    type: 'bar',
    labels: ['Auriculares', 'Teclado'],
    values: [8, 4],
    metricLabel: 'Unidades vendidas',
    formatValue: (value: number) => String(value),
    animate: true,
    ...overrides,
  };
}

describe('ChartRenderer', () => {
  let frames: FrameRequestCallback[];
  let canvas: HTMLCanvasElement;

  function nextFrame(): void {
    const pending = frames;
    frames = [];
    pending.forEach((callback) => callback(performance.now()));
  }

  beforeEach(() => {
    charts.length = 0;
    frames = [];
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => frames.push(callback));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    canvas = document.createElement('canvas');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('plays the entrance animation once Chart.js has settled the size of the new chart', () => {
    new ChartRenderer().create(canvas, data());
    const chart = charts[0];

    // Chart.js redimensiona en el primer fotograma y eso cortaría la animación: hasta entonces no se ve
    expect(canvas.style.visibility).toBe('hidden');
    nextFrame();
    expect(chart.reset).not.toHaveBeenCalled();
    nextFrame();

    expect(chart.reset).toHaveBeenCalledOnce();
    expect(chart.update).toHaveBeenCalledOnce();
    expect(canvas.style.visibility).toBe('');
  });

  it('shows the chart straight away and without replaying anything when it must not animate', () => {
    new ChartRenderer().create(canvas, data({ animate: false }));

    expect(canvas.style.visibility).toBe('');
    nextFrame();
    nextFrame();
    expect(charts[0].reset).not.toHaveBeenCalled();
    expect(charts[0].config.options).toMatchObject({ animation: false });
  });

  it('plays the entrance animation again when the chart type changes', () => {
    const rendered = new ChartRenderer().create(canvas, data());
    nextFrame();
    nextFrame();

    rendered.update(data({ type: 'doughnut' }));

    expect(charts[0].destroy).toHaveBeenCalledOnce();
    expect(charts[1].config.type).toBe('doughnut');
    expect(canvas.style.visibility).toBe('hidden');
    nextFrame();
    nextFrame();
    expect(charts[1].reset).toHaveBeenCalledOnce();
    expect(canvas.style.visibility).toBe('');
  });

  it('animates new data from the current chart without starting over', () => {
    const rendered = new ChartRenderer().create(canvas, data());
    nextFrame();
    nextFrame();
    charts[0].reset.mockClear();
    charts[0].update.mockClear();

    rendered.update(data({ values: [160, 200] }));

    expect(charts).toHaveLength(1);
    expect(charts[0].update).toHaveBeenCalledOnce();
    expect(charts[0].reset).not.toHaveBeenCalled();
  });

  it('replays the entrance animation on demand once the new size has settled', () => {
    const rendered = new ChartRenderer().create(canvas, data());
    nextFrame();
    nextFrame();
    charts[0].reset.mockClear();

    rendered.replay();

    expect(canvas.style.visibility).toBe('hidden');
    nextFrame();
    nextFrame();
    expect(charts[0].reset).toHaveBeenCalledOnce();
    expect(canvas.style.visibility).toBe('');
  });

  it('does not touch a chart that was destroyed before its animation started', () => {
    const rendered = new ChartRenderer().create(canvas, data());

    rendered.destroy();
    nextFrame();
    nextFrame();

    expect(charts[0].destroy).toHaveBeenCalledOnce();
    expect(charts[0].reset).not.toHaveBeenCalled();
  });
});
