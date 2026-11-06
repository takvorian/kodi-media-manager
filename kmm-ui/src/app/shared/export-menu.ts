import { HttpClient, HttpResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';

import { problemMessage } from './problem-message';
import { saveDownload } from './save-download';

const SNACKBAR_DURATION_MS = 5000;

/** Export formats; HTML is a list with posters as a ZIP. */
export type ExportFormat = 'HTML' | 'CSV' | 'JSON';

/** Offers the export of the filtered list as a download. */
@Component({
  selector: 'app-export-menu',
  imports: [MatButtonModule, MatMenuModule, TranslocoPipe],
  template: `
    <button
      matButton
      type="button"
      class="export-button"
      [disabled]="busy()"
      [matMenuTriggerFor]="formats"
    >
      {{ (busy() ? 'shared.export.busy' : 'shared.export.button') | transloco }}
    </button>
    <mat-menu #formats="matMenu">
      <button mat-menu-item type="button" class="export-html" (click)="export('HTML')">
        {{ 'shared.export.html' | transloco }}
      </button>
      <button mat-menu-item type="button" class="export-csv" (click)="export('CSV')">
        {{ 'shared.export.csv' | transloco }}
      </button>
      <button mat-menu-item type="button" class="export-json" (click)="export('JSON')">JSON</button>
    </mat-menu>
  `,
})
export class ExportMenu {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  /** Export endpoint, e.g. `/api/v1/movies/export`. */
  readonly endpoint = input.required<string>();
  /** The filter currently chosen in the list. */
  readonly filter = input.required<object>();
  /** Name of the file if the server names none. */
  readonly fallbackName = input('kmm-export');

  protected readonly busy = signal(false);

  protected export(format: ExportFormat): void {
    this.busy.set(true);
    this.http
      .post(this.endpoint(), this.filter(), {
        params: { format },
        observe: 'response',
        responseType: 'blob',
      })
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (response) => this.save(response, format),
        error: (error: unknown) =>
          this.snackBar.open(problemMessage(error), 'OK', { duration: SNACKBAR_DURATION_MS }),
      });
  }

  private save(response: HttpResponse<Blob>, format: ExportFormat): void {
    const extension = format === 'HTML' ? 'zip' : format.toLowerCase();
    saveDownload(response, `${this.fallbackName()}.${extension}`);
  }
}
