export interface TestRequest {
  targetUrl: string;
  virtualUsers: number;
  durationSeconds: number;
}

export interface TestResult {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  totalRequests: number;
  avgLatencyMs: number;
  errors: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
}

export interface TestSummary {
  id: string;
  targetUrl: string;
  virtualUsers: number;
  durationSeconds: number;
  status: string;
  totalRequests: number;
  avgLatencyMs: number;
  errors: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  createdAt: string;
}