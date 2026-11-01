import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { problemMessage } from '../shared/problem-message';

const SNACKBAR_DURATION_MS = 5000;

/** Fortschritt eines laufenden Scrape-Jobs mit Abbrechen; bei einer großen Auswahl kann er lange dauern. */
@Component({
  selector: 'app-scrape-progress',
  imports: [MatButtonModule, MatProgressBarModule],
  template: `
    <div class="scrape-progress">
      <mat-progress-bar mode="determinate" [value]="percent()" />
      <div class="scrape-progress-row">
        <span class="scrape-message">
          {{ job().title }} – {{ job().done }} von {{ job().total }}
          @if (job().message) {
            ({{ job().message }})
          }
        </span>
        <button matButton type="button" class="cancel-scrape-button" (click)="cancel()">
          Abbrechen
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
