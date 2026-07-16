import { Routes } from '@angular/router';
import { TestRunnerComponent } from './test-runner.component';
import { TestHistoryComponent } from './test-history.component';
import { TestComparisonComponent } from './test-comparison.component';

export const routes: Routes = [
  { path: '', component: TestRunnerComponent },
  { path: 'history', component: TestHistoryComponent },
  { path: 'compare', component: TestComparisonComponent },
];
