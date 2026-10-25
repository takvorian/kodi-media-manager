import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';

import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { MediaSource, MediaType, SOURCE_MEDIA_TYPES, ScanResult } from './media-source';
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
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './source-list.html',
  styleUrl: './source-list.scss',
})
export class SourceList {
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  protected readonly mediaTypes = SOURCE_MEDIA_TYPES;
  protected readonly columns = ['name', 'path', 'mediaType', 'lastScannedAt', 'actions'];
  protected readonly sources = rxResource({ stream: () => this.sourceService.findAll() });
  protected readonly scanningSourceId = signal<number | null>(null);
  protected readonly saving = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', Validators.required],
    path: ['', Validators.required],
    mediaType: ['MOVIE' as MediaType, Validators.required],
  });

  protected mediaTypeLabel(mediaType: MediaType): string {
    return this.mediaTypes.find((type) => type.value === mediaType)?.label ?? mediaType;
  }

  protected isScannable(source: MediaSource): boolean {
    return source.mediaType === 'MOVIE';
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
    this.scanningSourceId.set(source.id);
    this.sourceService.scan(source.id).subscribe({
      next: (result) => {
        this.scanningSourceId.set(null);
        this.notify(scanSummary(source, result));
        this.sources.reload();
      },
      error: (error: unknown) => {
        this.scanningSourceId.set(null);
        this.notify(problemMessage(error));
      },
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

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function scanSummary(source: MediaSource, result: ScanResult): string {
  return `„${source.name}“ gescannt: ${result.added} neu, ${result.removed} entfernt, ${result.unchanged} unverändert.`;
}
