import { Injectable } from '@angular/core';
import { RankingChartType } from './ranking.model';

/** Lo que necesita el ranking para pintar un gráfico, sin depender de la librería. */
export interface RankingChartData {
  type: RankingChartType;
  labels: string[];
  values: number[];
  metricLabel: string;
  formatValue: (value: number) => string;
  animate: boolean;
}

export interface RenderedChart {
  update(data: RankingChartData): void;
  destroy(): void;
}

@Injectable({ providedIn: 'root' })
export class ChartRenderer {
  create(_canvas: HTMLCanvasElement, _data: RankingChartData): RenderedChart {
    return { update: () => undefined, destroy: () => undefined };
  }
}
