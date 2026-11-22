export type RankingValueFormat = 'number' | 'currency' | 'rating';

/** Una magnitud que se puede ver en el ranking (unidades, ingresos, nota media…). */
export interface RankingMetric {
  key: string;
  label: string;
  format: RankingValueFormat;
}

/** Una fila del ranking: lo que se nombra, un dato secundario y sus valores por métrica. */
export interface RankingRow {
  id: string;
  label: string;
  detail?: string | null;
  values: Record<string, number>;
}

export type RankingView = 'table' | 'chart' | 'both';
export type RankingChartType = 'bar' | 'doughnut' | 'polarArea';

export const RANKING_VIEW_LABELS: Record<RankingView, string> = {
  both: 'Tabla y gráfico',
  table: 'Tabla',
  chart: 'Gráfico',
};

export const RANKING_CHART_LABELS: Record<RankingChartType, string> = {
  bar: 'Barras',
  doughnut: 'Anillo',
  polarArea: 'Polar',
};
