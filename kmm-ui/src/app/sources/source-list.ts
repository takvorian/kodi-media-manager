import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { filter } from 'rxjs';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { MediaSource, MediaType, SCAN_JOB_KEY_PREFIX, SOURCE_MEDIA_TYPES, ScanResult, scanJobKey } from './media-source';
import { MediaSourceService } from './media-source.service';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-source-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './source-list.html',
  styleUrl: './source-list.scss',
})
export class SourceList {
  private readonly sourceService = inject(MediaSourceService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  protected readonly mediaTypes = SOURCE_MEDIA_TYPES;
  protected readonly columns = ['name', 'path', 'mediaType', 'lastScannedAt', 'actions'];
  protected readonly sources = rxResource({ stream: () => this.sourceService.findAll() });
  protected readonly saving = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', Validators.required],
    path: ['', Validators.required],
    mediaType: ['MOVIE' as MediaType, Validators.required],
  });

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        filter((job) => job.key.startsWith(SCAN_JOB_KEY_PREFIX)),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.onScanFinished(job));
  }

  protected mediaTypeLabel(mediaType: MediaType): string {
    return this.mediaTypes.find((type) => type.value === mediaType)?.label ?? mediaType;
  }

  protected isScannable(source: MediaSource): boolean {
    return source.mediaType === 'MOVIE';
  }

  protected scanJob(source: MediaSource): Job | undefined {
    return this.jobStore.runningJob(scanJobKey(source.id));
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.sourceService.create(this.form.getRawValue()).subscribe({
      next: (source) => {
        this.saving.set(false);
        this.form.reset();
        this.notify(`Quelle „${source.name}“ angelegt.`);
        this.sources.reload();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected scan(source: MediaSource): void {
    this.sourceService.scan(source.id).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected cancelScan(job: Job): void {
    this.jobStore.cancel(job.id).subscribe({
      next: (updated) => this.jobStore.update(updated),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected delete(source: MediaSource): void {
    if (!this.confirm(`Quelle „${source.name}“ und alle ihre Filme aus der Bibliothek entfernen?`)) {
      return;
    }
    this.sourceService.delete(source.id).subscribe({
      next: () => {
        this.notify(`Quelle „${source.name}“ entfernt.`);
        this.sources.reload();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private onScanFinished(job: Job): void {
    this.notify(scanSummary(job));
    this.sources.reload();
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function scanSummary(job: Job): string {
  switch (job.status) {
    case 'SUCCEEDED': {
      const result = job.result as ScanResult;
      const errors = result.nfoErrors > 0 ? `, ${result.nfoErrors} NFO-Fehler` : '';
      return (
        `${job.title} abgeschlossen: ${result.added} neu, ${result.updated} aktualisiert, ` +
        `${result.removed} entfernt, ${result.unchanged} unverändert${errors}.`
      );
    }
    case 'CANCELLED':
      return `${job.title} abgebrochen.`;
    default:
      return `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
  }
}
