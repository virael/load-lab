import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TestSummary } from './test.model';

export interface Window {
  testId: string;
  timestamp: string;
  requestsInWindow: number;
  errorsInWindow: number;
}

@Injectable({ providedIn: 'root' })
export class HistoryService {
  private http = inject(HttpClient);

  listRecent(): Observable<TestSummary[]> {
    return this.http.get<TestSummary[]>('/tests');
  }

  getWindows(testId: string): Observable<Window[]> {
    return this.http.get<Window[]>(`/windows/${testId}`);
  }
}
