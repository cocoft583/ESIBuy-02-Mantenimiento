import { DestroyRef, Directive, ElementRef, effect, inject, input } from '@angular/core';
import { RankingValueFormat } from './ranking.model';
import { formatRankingValue } from './statistics-ranking';

const DURATION_MS = 1100;

function canAnimate(): boolean {
  return typeof window !== 'undefined'
    && typeof window.requestAnimationFrame === 'function'
    && window.matchMedia?.('(prefers-reduced-motion: no-preference)')?.matches === true;
}

/** Escribe un número contando desde cero hasta su valor (o directamente, si no se puede animar). */
@Directive({
  selector: '[appCountUp]',
  standalone: true,
})
export class CountUp {
  readonly appCountUp = input.required<number>();
  readonly countUpFormat = input<RankingValueFormat>('number');

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private frame: number | null = null;

  constructor() {
    effect(() => this.run(this.appCountUp(), this.countUpFormat()));
    inject(DestroyRef).onDestroy(() => this.cancel());
  }

  private run(target: number, format: RankingValueFormat): void {
    this.cancel();
    const write = (value: number) => {
      this.element.nativeElement.textContent = formatRankingValue(value, format);
    };
    if (!canAnimate() || target === 0) {
      write(target);
      return;
    }

    const start = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / DURATION_MS);
      const eased = 1 - Math.pow(1 - progress, 3);
      const value = format === 'number' ? Math.round(target * eased) : target * eased;
      write(progress === 1 ? target : value);
      this.frame = progress < 1 ? window.requestAnimationFrame(step) : null;
    };
    this.frame = window.requestAnimationFrame(step);
  }

  private cancel(): void {
    if (this.frame !== null) {
      window.cancelAnimationFrame(this.frame);
      this.frame = null;
    }
  }
}
