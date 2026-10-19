import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, tap } from 'rxjs';

import { MonthStats, YearlyReport } from '../../models/report';
import { ReportService } from '../../services/report.service';
import { problemMessage } from '../../shared/problem';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Axis ticks: pick a clean step (1, 2, 2.5 or 5 × a power of ten) that splits the range into about
 * 4 parts, then stop at the first tick above the data. 28,000 → step 10,000 → 0, 10K, 20K, 30K.
 */
function niceTicks(maxValue: number): number[] {
  if (maxValue <= 0) {
    return [0, 1000];
  }
  const rough = maxValue / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step = ([1, 2, 2.5, 5, 10].find(s => s * power >= rough) ?? 10) * power;
  const top = Math.ceil(maxValue / step) * step;
  const ticks: number[] = [];
  for (let t = 0; t <= top + step / 2; t += step) {
    ticks.push(t);
  }
  return ticks;
}

/** ₱0, ₱500, ₱5K, ₱12.5K, ₱1.2M — short labels for the axis */
export function compactPeso(value: number): string {
  if (value >= 1_000_000) {
    return `₱${+(value / 1_000_000).toFixed(1)}M`;
  }
  if (value >= 1_000) {
    return `₱${+(value / 1_000).toFixed(1)}K`;
  }
  return `₱${value}`;
}

@Component({
  selector: 'app-admin-reports',
  imports: [CurrencyPipe],
  templateUrl: './admin-reports.component.html',
  styleUrl: './admin-reports.component.scss'
})
export class AdminReportsComponent {
  private readonly reportService = inject(ReportService);

  readonly currentYear = new Date().getFullYear();
  readonly year = signal(this.currentYear);
  readonly report = signal<YearlyReport | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showTable = signal(false);
  /** index (0–11) of the month column under the pointer / keyboard focus */
  readonly hovered = signal<number | null>(null);

  readonly monthNames = MONTHS;
  readonly compact = compactPeso;

  /** Axis ticks (bottom-up) and the top of the axis; bars are drawn as a % of axisMax */
  private readonly tickValues = computed(() =>
    niceTicks(Math.max(0, ...(this.report()?.months.map(m => m.revenue) ?? [0]))));
  readonly axisMax = computed(() => this.tickValues()[this.tickValues().length - 1]);
  readonly ticks = computed(() => [...this.tickValues()].reverse());

  /** The best month gets the only direct label (never a number on every bar) */
  readonly peakIndex = computed(() => {
    const months = this.report()?.months ?? [];
    let best = -1;
    months.forEach((m, i) => {
      if (m.revenue > 0 && (best < 0 || m.revenue > months[best].revenue)) {
        best = i;
      }
    });
    return best;
  });

  constructor() {
    // Reload whenever the year changes. toObservable turns the signal into a stream.
    toObservable(this.year).pipe(
      tap(() => {
        this.loading.set(true); // the old report stays visible (dimmed) while loading
        this.error.set(null);
      }),
      switchMap(year => this.reportService.yearly(year).pipe(
        catchError(err => {
          this.error.set(problemMessage(err));
          return of(null);
        })
      )),
      takeUntilDestroyed()
    ).subscribe(report => {
      if (report) {
        this.report.set(report);
      }
      this.loading.set(false);
    });
  }

  changeYear(delta: number): void {
    this.hovered.set(null);
    this.year.update(y => y + delta);
  }

  barHeight(month: MonthStats): number {
    return (month.revenue / this.axisMax()) * 100; // percent of the plot height
  }

  barLabel(month: MonthStats, index: number): string {
    return `${MONTHS[index]} ${this.year()}: revenue ${compactPeso(month.revenue)}, ${month.bookings} bookings, pending ${compactPeso(month.pendingValue)}`;
  }
}
