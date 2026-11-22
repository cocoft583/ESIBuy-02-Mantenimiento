import { Component, input } from '@angular/core';
import { RankingChartType, RankingMetric, RankingRow, RankingView } from './ranking.model';

@Component({
  selector: 'app-statistics-ranking',
  standalone: true,
  template: '',
})
export class StatisticsRanking {
  readonly heading = input.required<string>();
  readonly rows = input.required<RankingRow[]>();
  readonly metrics = input.required<RankingMetric[]>();
  readonly chartTypes = input<RankingChartType[]>(['bar', 'doughnut', 'polarArea']);
  readonly initialView = input<RankingView>('both');
  readonly emptyMessage = input('Todavía no hay datos.');
}
