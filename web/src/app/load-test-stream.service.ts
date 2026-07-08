import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { TestResult } from './test.model';

@Injectable({ providedIn: 'root' })
export class LoadTestStreamService {
  connect(id: string): Observable<TestResult> {
    return new Observable<TestResult>((subscriber) => {
      const source = new EventSource(`/tests/${id}/stream`);

      source.addEventListener('snapshot', (event: MessageEvent) => {
        const result = JSON.parse(event.data) as TestResult;
        subscriber.next(result);
        if (result.status === 'DONE') {
          source.close();
          subscriber.complete();
        }
      });

      source.onerror = () => {
        if (source.readyState === EventSource.CLOSED) {
          subscriber.error(new Error('Stream connection closed unexpectedly'));
        }
      };

      return () => source.close();
    });
  }
}
