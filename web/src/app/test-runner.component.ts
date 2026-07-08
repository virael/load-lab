import { DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { form, FormField, min, required, validate } from '@angular/forms/signals';
import { LoadTestStreamService } from './load-test-stream.service';
import { LoadTestService } from './load-test.service';
import { TestRequest, TestResult } from './test.model';

@Component({
  selector: 'app-test-runner',
  imports: [FormField, DecimalPipe],
  template: `
    <h1>Load test</h1>

    <form (submit)="start($event)">
      <label>
        Target URL
        <input type="text" [formField]="testForm.targetUrl" />
      </label>
      @if (testForm.targetUrl().touched() && testForm.targetUrl().errors().length) {
        <small class="error">{{ testForm.targetUrl().errors()[0].message }}</small>
      }
      <label>
        Virtual users
        <input type="number" [formField]="testForm.virtualUsers" />
      </label>
      <label>
        Duration (s)
        <input type="number" [formField]="testForm.durationSeconds" />
      </label>
      <button type="submit" [disabled]="testForm().invalid() || running()">
        {{ running() ? 'Running…' : 'Start test' }}
      </button>
    </form>

    @if (result(); as r) {
      <h2>Latency over time</h2>
      <div class="chart-row">
        <div class="y-axis">
          <span>{{ sharedMax() }} ms</span>
          <span>{{ sharedMax() / 2 | number: '1.0-0' }} ms</span>
          <span>0 ms</span>
        </div>
        <svg viewBox="0 0 400 150" class="chart" preserveAspectRatio="none">
          <line x1="0" y1="10" x2="400" y2="10" class="gridline" />
          <line x1="0" y1="80" x2="400" y2="80" class="gridline" />
          <line x1="0" y1="140" x2="400" y2="140" class="gridline" />
          <polyline [attr.points]="p50Points()" class="line line-p50" />
          <polyline [attr.points]="p95Points()" class="line line-p95" />
          <polyline [attr.points]="p99Points()" class="line line-p99" />
        </svg>
      </div>
      <div class="x-axis">
        <span>0s</span>
        <span>{{ elapsedSeconds() }}s elapsed</span>
      </div>

      <div class="legend">
        <span class="legend-item">
          <span class="legend-swatch swatch-p50"></span>
          p50 — typical request
        </span>
        <span class="legend-item">
          <span class="legend-swatch swatch-p95"></span>
          p95 — slower than 95% of requests
        </span>
        <span class="legend-item">
          <span class="legend-swatch swatch-p99"></span>
          p99 — worst-case tail
        </span>
      </div>
    }
  `,
  styles: `
    .chart-row {
      display: flex;
      gap: 8px;
    }
    .y-axis {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      font-size: 11px;
      color: #666;
      text-align: right;
      padding: 4px 0;
    }
    .chart {
      width: 100%;
      height: 150px;
      background: #f7f7f7;
      flex: 1;
    }
    .gridline {
      stroke: #ddd;
      stroke-width: 1;
    }
    .line {
      fill: none;
      stroke-width: 2;
    }
    .line-p50 {
      stroke: #2e7d32;
    }
    .line-p95 {
      stroke: #f9a825;
    }
    .line-p99 {
      stroke: #c62828;
    }

    .x-axis {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #666;
      margin-left: 48px;
      margin-top: 2px;
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
    .swatch-p50 {
      background: #2e7d32;
    }
    .swatch-p95 {
      background: #f9a825;
    }
    .swatch-p99 {
      background: #c62828;
    }
  `,
})
export class TestRunnerComponent {
  private api = inject(LoadTestService);
  private streamApi = inject(LoadTestStreamService);
  private destroyRef = inject(DestroyRef);

  private model = signal<TestRequest>({
    targetUrl: 'http://www.example.com',
    virtualUsers: 10,
    durationSeconds: 5,
  });

  protected testForm = form(this.model, (path) => {
    required(path.targetUrl, { message: 'Target URL is required' });
    validate(path.targetUrl, ({ value }) =>
      value().startsWith('http') ? null : { kind: 'url', message: 'URL must start with http' },
    );
    required(path.virtualUsers);
    min(path.virtualUsers, 1, { message: 'At least 1 virtual user' });
    required(path.durationSeconds);
    min(path.durationSeconds, 1, { message: 'At least 1 second' });
  });

  protected result = signal<TestResult | null>(null);
  protected running = signal(false);
  private history = signal<TestResult[]>([]);

  protected p50Points = computed(() => this.toPoints((r) => r.p50Ms));
  protected p95Points = computed(() => this.toPoints((r) => r.p95Ms));
  protected p99Points = computed(() => this.toPoints((r) => r.p99Ms));
  protected elapsedSeconds = computed(() => Math.max(this.history().length - 1, 0));

  start(event: Event): void {
    event.preventDefault();
    if (this.testForm().invalid()) return;

    this.running.set(true);
    this.history.set([]);

    this.api.startTest(this.testForm().value()).subscribe({
      next: (created) => {
        this.result.set(created);
        this.streamApi
          .connect(created.id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (snapshot) => {
              this.result.set(snapshot);
              this.history.update((h) => [...h, snapshot]);
              if (snapshot.status === 'DONE') this.running.set(false);
            },
            error: () => this.running.set(false),
          });
      },
      error: () => this.running.set(false),
    });
  }

  protected sharedMax = computed(() => {
    const points = this.history();
    if (points.length === 0) return 1;
    return Math.max(...points.map((r) => r.p99Ms), 1);
  });

  private toPoints(pick: (r: TestResult) => number): string {
    const points = this.history();
    if (points.length === 0) return '';
    const max = this.sharedMax();
    const stepX = 400 / Math.max(points.length - 1, 1);
    return points.map((r, i) => `${i * stepX},${150 - (pick(r) / max) * 140}`).join(' ');
  }
}
