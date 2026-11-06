import { Component, computed, inject } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { progressPercent } from './job';
import { JobStore } from './job-store';
import { LocalizedPipe } from '../i18n/localized.pipe';

/** Shows below the toolbar which job is running and how far it has got. */
@Component({
  selector: 'app-job-indicator',
  imports: [LocalizedPipe, MatProgressBarModule, TranslocoPipe],
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
