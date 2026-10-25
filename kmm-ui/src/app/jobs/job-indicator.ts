import { Component, computed, inject } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { progressPercent } from './job';
import { JobStore } from './job-store';

/** Zeigt unter der Toolbar an, welcher Job gerade läuft und wie weit er ist. */
@Component({
  selector: 'app-job-indicator',
  imports: [MatProgressBarModule],
  templateUrl: './job-indicator.html',
  styleUrl: './job-indicator.scss',
})
export class JobIndicator {
  private readonly store = inject(JobStore);

  protected readonly firstRunning = computed(() => this.store.runningJobs()[0]);
  protected readonly percent = computed(() => {
    const job = this.firstRunning();
    return job ? progressPercent(job) : null;
  });
  protected readonly otherCount = computed(() => Math.max(this.store.runningJobs().length - 1, 0));

  constructor() {
    this.store.connect();
  }
}
