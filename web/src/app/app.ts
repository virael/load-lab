import { Component } from '@angular/core';
import { TestRunnerComponent } from './test-runner.component';

@Component({
  selector: 'app-root',
  imports: [TestRunnerComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
