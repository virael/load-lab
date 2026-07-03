import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { form, FormField, min, required, validate } from '@angular/forms/signals';
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

      <button type="submit" [disabled]="testForm().invalid() || submitting()">
        {{ submitting() ? 'Running…' : 'Start test' }}
      </button>
    </form>

    @if (result(); as r) {
      <h2>Result</h2>
      <table>
        <tbody>
          <tr>
            <th>Status</th>
            <td>{{ r.status }}</td>
          </tr>
          <tr>
            <th>Total requests</th>
            <td>{{ r.totalRequests }}</td>
          </tr>
          <tr>
            <th>Avg latency (ms)</th>
            <td>{{ r.avgLatencyMs | number: '1.0-1' }}</td>
          </tr>
          <tr>
            <th>Errors</th>
            <td>{{ r.errors }}</td>
          </tr>
        </tbody>
      </table>
    }
  `,
})
export class TestRunnerComponent {
  private api = inject(LoadTestService);

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
  protected submitting = signal(false);

  start(event: Event): void {
    event.preventDefault();
    if (this.testForm().invalid()) return;
    this.submitting.set(true);

    this.api.startTest(this.testForm().value()).subscribe((created) => {
      this.result.set(created);
      this.pollUntilDone(created.id);
    });
  }

  private pollUntilDone(id: string): void {
    const timer = setInterval(() => {
      this.api.getResult(id).subscribe((r) => {
        this.result.set(r);
        if (r.status === 'DONE') {
          clearInterval(timer);
          this.submitting.set(false);
        }
      });
    }, 1000);
  }
}
