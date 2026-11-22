import { Injectable } from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartConfiguration,
  DoughnutController,
  Legend,
  LinearScale,
  PolarAreaController,
  RadialLinearScale,
  Tooltip,
} from 'chart.js';
import { RankingChartType } from './ranking.model';

// Solo se registran las piezas que usa el ranking, para que el resto de Chart.js no entre en el bundle
Chart.register(
  BarController,
  BarElement,
  CategoryScale,
  LinearScale,
  DoughnutController,
  ArcElement,
  PolarAreaController,
  RadialLinearScale,
  Tooltip,
  Legend,
);

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
  replay(): void;
  destroy(): void;
}

// Colores de la marca (verde menta sobre azul petróleo) y tonos que se distinguen entre sí
const PALETTE = [
  '#22b892', '#1c5360', '#4f8fd6', '#f2a33a', '#8e6bd8',
  '#e0607e', '#3fb6c9', '#94b83d', '#d9822b', '#6b7f8e',
];
const GRID_COLOR = 'rgb(16 44 64 / 8%)';
const TEXT_COLOR = '#496477';
const LABEL_MAX_LENGTH = 22;

function shortLabel(label: string): string {
  return label.length > LABEL_MAX_LENGTH ? `${label.slice(0, LABEL_MAX_LENGTH - 1)}…` : label;
}

function withAlpha(hex: string, alpha: number): string {
  const value = Number.parseInt(hex.slice(1), 16);
  return `rgb(${(value >> 16) & 255} ${(value >> 8) & 255} ${value & 255} / ${alpha})`;
}

export function buildChartConfiguration(data: RankingChartData): ChartConfiguration {
  const colors = data.values.map((_, index) => PALETTE[index % PALETTE.length]);
  const animation = data.animate
    ? {
        duration: 900,
        easing: 'easeOutQuart' as const,
        // Cada elemento entra un poco después del anterior
        delay: (context: { type: string; mode: string; dataIndex: number }) =>
          context.type === 'data' && context.mode === 'default' ? context.dataIndex * 70 : 0,
      }
    : false;
  const tooltip = {
    callbacks: {
      label: (item: { raw: unknown }) => ` ${data.metricLabel}: ${data.formatValue(Number(item.raw))}`,
    },
  };

  if (data.type === 'bar') {
    return {
      type: 'bar',
      data: {
        labels: data.labels,
        datasets: [{
          label: data.metricLabel,
          data: data.values,
          backgroundColor: data.values.map((_, index) => (index === 0 ? PALETTE[0] : withAlpha(PALETTE[1], 0.82))),
          hoverBackgroundColor: PALETTE[0],
          borderRadius: 8,
          borderSkipped: false,
          maxBarThickness: 26,
        }],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        animation,
        plugins: { legend: { display: false }, tooltip },
        scales: {
          x: {
            beginAtZero: true,
            grid: { color: GRID_COLOR },
            border: { display: false },
            ticks: { color: TEXT_COLOR, callback: (value) => data.formatValue(Number(value)) },
          },
          y: {
            grid: { display: false },
            border: { display: false },
            ticks: {
              color: '#173d52',
              font: { weight: 600 },
              callback: (_value, index) => shortLabel(data.labels[index] ?? ''),
            },
          },
        },
      },
    };
  }

  const common = {
    responsive: true,
    maintainAspectRatio: false,
    animation: data.animate ? { ...animation, animateRotate: true, animateScale: true } : false,
    plugins: {
      legend: {
        position: 'right' as const,
        labels: {
          color: '#173d52',
          boxWidth: 12,
          boxHeight: 12,
          usePointStyle: true,
          generateLabels: (chart: Chart) =>
            Chart.overrides.doughnut.plugins.legend.labels.generateLabels(chart).map((item) => ({
              ...item,
              text: shortLabel(item.text),
            })),
        },
      },
      tooltip,
    },
  };

  if (data.type === 'doughnut') {
    const doughnut: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: data.labels,
        datasets: [{
          label: data.metricLabel,
          data: data.values,
          backgroundColor: colors,
          borderColor: '#fff',
          borderWidth: 3,
          hoverOffset: 12,
        }],
      },
      options: { ...common, cutout: '62%' },
    };
    return doughnut as unknown as ChartConfiguration;
  }

  const polar: ChartConfiguration<'polarArea'> = {
    type: 'polarArea',
    data: {
      labels: data.labels,
      datasets: [{
        label: data.metricLabel,
        data: data.values,
        backgroundColor: colors.map((color) => withAlpha(color, 0.72)),
        borderColor: '#fff',
        borderWidth: 2,
      }],
    },
    options: {
      ...common,
      scales: {
        r: {
          beginAtZero: true,
          grid: { color: GRID_COLOR },
          ticks: { display: false },
        },
      },
    },
  };
  return polar as unknown as ChartConfiguration;
}

/** Pinta los gráficos del ranking con Chart.js. Se inyecta para poder sustituirlo en los tests. */
@Injectable({ providedIn: 'root' })
export class ChartRenderer {
  create(canvas: HTMLCanvasElement, data: RankingChartData): RenderedChart {
    let currentType = data.type;
    let chart = new Chart(canvas, buildChartConfiguration(data));

    return {
      update: (next) => {
        const configuration = buildChartConfiguration(next);
        if (next.type !== currentType) {
          // Chart.js no cambia de tipo en caliente: se vuelve a crear sobre el mismo canvas
          chart.destroy();
          chart = new Chart(canvas, configuration);
          currentType = next.type;
          return;
        }
        chart.data = configuration.data;
        chart.options = configuration.options ?? {};
        chart.update();
      },
      replay: () => undefined,
      destroy: () => chart.destroy(),
    };
  }
}
