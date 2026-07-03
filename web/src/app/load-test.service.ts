import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TestRequest, TestResult } from './test.model';

@Injectable({ providedIn: 'root' })
export class LoadTestService {
  private http = inject(HttpClient);

  startTest(req: TestRequest): Observable<TestResult> {
    return this.http.post<TestResult>('/tests', req);
  }

  getResult(id: string): Observable<TestResult> {
    return this.http.get<TestResult>(`/tests/${id}/results`);
  }
}