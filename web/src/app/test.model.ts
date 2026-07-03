export interface TestRequest {
  targetUrl: string;
  virtualUsers: number;
  durationSeconds: number;
}

export interface TestResult {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'DONE';
  totalRequests: number;
  avgLatencyMs: number;
  errors: number;
}