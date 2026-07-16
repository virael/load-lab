import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { HistoryService } from './history.service';
import { TestSummary } from './test.model';

@Component({
  selector: 'app-test-history',
  imports: [DatePipe, DecimalPipe],
  template: `
    <h1>Test History</h1>

    <table>
      <thead>
        <tr>
          <th></th>
          <th>Started</th>
          <th>Target</th>
          <th>Status</th>
          <th>Requests</th>
          <th>p99 (ms)</th>
        </tr>
      </thead>
      <tbody>
        @for (t of tests(); track t.id) {
          <tr>
            <td>
              <input
                type="checkbox"
                [checked]="isSelected(t.id)"
                (change)="toggle(t.id)"
                [disabled]="!isSelected(t.id) && selected().length >= 2"
              />
            </td>
            <td>{{ t.createdAt | date: 'short' }}</td>
            <td>{{ t.targetUrl }}</td>
            <td>{{ t.status }}</td>
            <td>{{ t.totalRequests }}</td>
            <td>{{ t.p99Ms | number: '1.0-0' }}</td>
          </tr>
        }
      </tbody>
    </table>

    <button [disabled]="selected().length !== 2" (click)="compare()">
      Compare selected ({{ selected().length }}/2)
    </button>
  `,
})
export class TestHistoryComponent {
  private history = inject(HistoryService);
  private router = inject(Router);

  protected tests = signal<TestSummary[]>([]);
  protected selected = signal<string[]>([]);

  constructor() {
    this.history.listRecent().subscribe((tests) => this.tests.set(tests));
  }

  protected isSelected(id: string): boolean {
    return this.selected().includes(id);
  }

  protected toggle(id: string): void {
    this.selected.update((ids) =>
      ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id],
    );
  }

  protected compare(): void {
    const [a, b] = this.selected();
    this.router.navigate(['/compare'], { queryParams: { a, b } });
  }
}
