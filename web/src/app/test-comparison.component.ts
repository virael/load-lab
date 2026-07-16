import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HistoryService, Window } from './history.service';
import { TestSummary } from './test.model';

@Component({
  selector: 'app-test-comparison',
  imports: [DecimalPipe],
  template: `
    <h1>Compare Runs</h1>

    @if (summaryA(); as a) {
      @if (summaryB(); as b) {
        <table>
          <thead>
            <tr><th></th><th>Run A</th><th>Run B</th></tr>
          </thead>
          <tbody>
            <tr><th>Started</th><td>{{ a.createdAt }}</td><td>{{ b.createdAt }}</td></tr>
            <tr><th>Total requests</th><td>{{ a.totalRequests }}</td><td>{{ b.totalRequests }}</td></tr>
            <tr>
              <th>Avg latency (ms)</th>
              <td>{{ a.avgLatencyMs | number: '1.0-1' }}</td>
              <td>{{ b.avgLatencyMs | number: '1.0-1' }}</td>
            </tr>
            <tr><th>p50 (ms)</th><td>{{ a.p50Ms }}</td><td>{{ b.p50Ms }}</td></tr>
            <tr><th>p95 (ms)</th><td>{{ a.p95Ms }}</td><td>{{ b.p95Ms }}</td></tr>
            <tr><th>p99 (ms)</th><td>{{ a.p99Ms }}</td><td>{{ b.p99Ms }}</td></tr>
            <tr><th>Errors</th><td>{{ a.errors }}</td><td>{{ b.errors }}</td></tr>
          </tbody>
        </table>

        <h2>Requests per window over time</h2>
        <svg viewBox="0 0 400 150" class="chart" preserveAspectRatio="none">
          <polyline [attr.points]="pointsA()" class="line line-a" />
          <polyline [attr.points]="pointsB()" class="line line-b" />
        </svg>
        <div class="legend">
          <span class="legend-item"><span class="legend-swatch swatch-a"></span>Run A</span>
          <span class="legend-item"><span class="legend-swatch swatch-b"></span>Run B</span>
        </div>
      }
    }
  `,
  styles: `
    .chart {
      width: 100%;
      height: 150px;
      background: #f7f7f7;
    }
    .line {
      fill: none;
      stroke-width: 2;
    }
    .line-a {
      stroke: #1565c0;
    }
    .line-b {
      stroke: #ef6c00;
    }
    .legend {
      display: flex;
      gap: 20px;
      margin-top: 8px;
      font-size: 13px;
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .legend-swatch {
      width: 12px;
      height: 12px;
      border-radius: 2px;
      display: inline-block;
    }
    .swatch-a {
      background: #1565c0;
    }
    .swatch-b {
      background: #ef6c00;
    }
  `,
})
export class TestComparisonComponent {
  private route = inject(ActivatedRoute);
  private history = inject(HistoryService);

  protected summaryA = signal<TestSummary | null>(null);
  protected summaryB = signal<TestSummary | null>(null);
  private windowsA = signal<Window[]>([]);
  private windowsB = signal<Window[]>([]);

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const a = params.get('a') ?? '';
    const b = params.get('b') ?? '';

    // Re-fetch the list rather than passing state between components, so a direct
    // link to /compare?a=…&b=… still resolves after a page refresh.
    this.history.listRecent().subscribe((tests) => {
      this.summaryA.set(tests.find((t) => t.id === a) ?? null);
      this.summaryB.set(tests.find((t) => t.id === b) ?? null);
    });
    this.history.getWindows(a).subscribe((w) => this.windowsA.set(w));
    this.history.getWindows(b).subscribe((w) => this.windowsB.set(w));
  }

  // Shared max across BOTH series (the E2.3 lesson): a per-series max would make a
  // 3× busier run look "similar" to a quiet one.
  private sharedMax = computed(() => {
    const all = [...this.windowsA(), ...this.windowsB()].map((w) => w.requestsInWindow);
    return Math.max(...all, 1);
  });

  protected pointsA = computed(() => this.toPoints(this.windowsA()));
  protected pointsB = computed(() => this.toPoints(this.windowsB()));

  private toPoints(windows: Window[]): string {
    if (windows.length === 0) return '';
    const max = this.sharedMax();
    const stepX = 400 / Math.max(windows.length - 1, 1);
    return windows.map((w, i) => `${i * stepX},${150 - (w.requestsInWindow / max) * 140}`).join(' ');
  }
}
