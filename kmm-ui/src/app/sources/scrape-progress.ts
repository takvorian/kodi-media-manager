import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe } from '@jsverse/transloco';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { problemMessage } from '../shared/problem-message';

const SNACKBAR_DURATION_MS = 5000;

/** Progress of a running scrape job with cancel; with a large selection it can take a long time. */
@Component({
  selector: 'app-scrape-progress',
  imports: [MatButtonModule, MatProgressBarModule, TranslocoPipe],
  template: `
    <div class="scrape-progress">
      <mat-progress-bar mode="determinate" [value]="percent()" />
      <div class="scrape-progress-row">
        <span class="scrape-message">
          {{
            'sources.scrapeProgress'
              | transloco: { title: job().title, done: job().done, total: job().total }
          }}
          @if (job().message) {
            ({{ job().message }})
          }
        </span>
        <button matButton type="button" class="cancel-scrape-button" (click)="cancel()">
          {{ 'common.stop' | transloco }}
        </button>
      </div>
    </div>
  `,
  styles: `
    .scrape-progress {
      margin: 8px 0;
    }

    .scrape-progress-row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      justify-content: space-between;
    }
  `,
})
export class ScrapeProgress {
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);

  readonly job = input.required<Job>();

  protected readonly percent = computed(() => progressPercent(this.job()) ?? 0);

  protected cancel(): void {
    this.jobStore.cancel(this.job().id).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) =>
        this.snackBar.open(problemMessage(error), 'OK', { duration: SNACKBAR_DURATION_MS }),
    });
  }
}
