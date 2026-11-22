import {
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  linkedSignal,
  viewChild,
} from '@angular/core';
import { ChartRenderer, RankingChartData, RenderedChart } from './chart-renderer';
import {
  RANKING_CHART_LABELS,
  RANKING_VIEW_LABELS,
  RankingChartType,
  RankingMetric,
  RankingReference,
  RankingRow,
  RankingValueFormat,
  RankingView,
} from './ranking.model';

const VIEWS: RankingView[] = ['both', 'table', 'chart'];

const FORMATTERS: Record<RankingValueFormat, Intl.NumberFormat> = {
  number: new Intl.NumberFormat('es-ES', { maximumFractionDigits: 0 }),
  currency: new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }),
  rating: new Intl.NumberFormat('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 2 }),
};

export function formatRankingValue(value: number, format: RankingValueFormat): string {
  return FORMATTERS[format].format(value);
}

const DELTA_FORMATTER = new Intl.NumberFormat('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const RATING_SCALE_MAX = 5;

/** Diferencia con signo: «+0,35», «−0,40» o «0,00». */
function formatDelta(delta: number): string {
  const rounded = Math.round(delta * 100) / 100;
  if (rounded === 0) {
    return DELTA_FORMATTER.format(0);
  }
  return `${rounded > 0 ? '+' : '−'}${DELTA_FORMATTER.format(Math.abs(rounded))}`;
}

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches === true;
}

let nextId = 0;

/**
 * Ranking reutilizable de las estadísticas (HU22 a HU25): tabla, gráfico o los dos (RF-EST-06),
 * con varias métricas y tipos de gráfico. El orden de las filas lo decide quien lo usa.
 */
@Component({
  selector: 'app-statistics-ranking',
  standalone: true,
  templateUrl: './statistics-ranking.html',
  styleUrl: './statistics-ranking.css',
})
export class StatisticsRanking {
  readonly heading = input.required<string>();
  readonly description = input<string | null>(null);
  readonly rows = input.required<RankingRow[]>();
  readonly metrics = input.required<RankingMetric[]>();
  readonly labelHeader = input('Nombre');
  readonly detailHeader = input('Detalle');
  readonly chartTypes = input<RankingChartType[]>(['bar', 'doughnut', 'polarArea']);
  readonly initialView = input<RankingView>('both');
  readonly emptyMessage = input('Todavía no hay datos.');
  readonly reference = input<RankingReference | null>(null);

  private readonly renderer = inject(ChartRenderer);
  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart: RenderedChart | null = null;
  private chartCanvas: HTMLCanvasElement | null = null;
  private chartView: RankingView | null = null;

  readonly headingId = `statistics-ranking-${++nextId}`;
  readonly views = VIEWS;
  readonly viewLabels = RANKING_VIEW_LABELS;
  readonly chartLabels = RANKING_CHART_LABELS;

  readonly view = linkedSignal(() => this.initialView());
  readonly chartType = linkedSignal<RankingChartType>(() => this.chartTypes()[0] ?? 'bar');
  readonly selectedMetricKey = linkedSignal(() => this.metrics()[0]?.key ?? '');

  readonly selectedMetric = computed<RankingMetric | undefined>(() =>
    this.metrics().find((metric) => metric.key === this.selectedMetricKey()) ?? this.metrics()[0]);
  readonly showTable = computed(() => this.view() !== 'chart');
  readonly showChart = computed(() => this.view() !== 'table');

  /** Porcentaje de cada fila respecto a la mayor, para la barra de la tabla. */
  readonly shares = computed(() => {
    const key = this.selectedMetric()?.key ?? '';
    const values = this.rows().map((row) => row.values[key] ?? 0);
    const max = Math.max(0, ...values);
    return values.map((value) => (max > 0 ? Math.round((value / max) * 1000) / 10 : 0));
  });

  /** La referencia solo tiene sentido con la métrica que mide lo mismo (una valoración). */
  readonly activeReference = computed<RankingReference | null>(() => {
    const reference = this.reference();
    return reference && this.selectedMetric()?.format === 'rating' ? reference : null;
  });

  readonly referenceNote = computed(() => {
    const reference = this.activeReference();
    return reference ? `${reference.label}: ${formatRankingValue(reference.value, 'rating')}` : null;
  });

  /** Distancia de cada fila a la referencia, para la tabla. */
  readonly deltas = computed(() => {
    const reference = this.activeReference();
    const key = this.selectedMetric()?.key ?? '';
    return this.rows().map((row) => {
      if (!reference) {
        return null;
      }
      const delta = Math.round(((row.values[key] ?? 0) - reference.value) * 100) / 100;
      return { text: formatDelta(delta), direction: delta > 0 ? 'above' : delta < 0 ? 'below' : 'equal' };
    });
  });

  readonly chartSummary = computed(() => {
    const metric = this.selectedMetric();
    if (!metric) {
      return this.heading();
    }
    const items = this.rows()
      .map((row) => `${row.label}: ${this.format(row.values[metric.key] ?? 0, metric)}`)
      .join(', ');
    return `${this.heading()}. ${metric.label}. ${items}`;
  });

  private readonly chartData = computed<RankingChartData | null>(() => {
    const metric = this.selectedMetric();
    const rows = this.rows();
    if (!metric || rows.length === 0) {
      return null;
    }
    return {
      type: this.chartType(),
      labels: rows.map((row) => row.label),
      values: rows.map((row) => row.values[metric.key] ?? 0),
      metricLabel: metric.label,
      formatValue: (value: number) => formatRankingValue(value, metric.format),
      animate: !prefersReducedMotion(),
      reference: this.activeReference() ?? undefined,
      // Las valoraciones van de 0 a 5: con la escala fija, las barras se comparan de verdad
      scaleMax: metric.format === 'rating' ? RATING_SCALE_MAX : undefined,
    };
  });

  constructor() {
    // Se ejecuta tras pintar: el canvas puede aparecer o desaparecer al cambiar de vista
    afterRenderEffect(() => {
      const canvas = this.canvasRef()?.nativeElement ?? null;
      this.syncChart(canvas, this.chartData(), this.view());
    });
    inject(DestroyRef).onDestroy(() => this.releaseChart());
  }

  selectMetric(key: string): void {
    this.selectedMetricKey.set(key);
  }

  format(value: number, metric: RankingMetric): string {
    return formatRankingValue(value, metric.format);
  }

  private syncChart(canvas: HTMLCanvasElement | null, data: RankingChartData | null, view: RankingView): void {
    if (!canvas || !data) {
      this.releaseChart();
      return;
    }
    if (this.chart && this.chartCanvas === canvas) {
      this.chart.update(data);
      if (view !== this.chartView) {
        // Al pasar entre «Gráfico» y «Tabla y gráfico» el canvas cambia de ancho: se vuelve a animar
        this.chart.replay();
        this.chartView = view;
      }
      return;
    }
    this.releaseChart();
    this.chart = this.renderer.create(canvas, data);
    this.chartCanvas = canvas;
    this.chartView = view;
  }

  private releaseChart(): void {
    this.chart?.destroy();
    this.chart = null;
    this.chartCanvas = null;
    this.chartView = null;
  }
}
