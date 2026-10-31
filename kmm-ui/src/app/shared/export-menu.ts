import { HttpClient, HttpResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { problemMessage } from './problem-message';
import { saveDownload } from './save-download';

const SNACKBAR_DURATION_MS = 5000;

/** Formate des Exports; HTML ist eine Liste mit Postern als ZIP. */
export type ExportFormat = 'HTML' | 'CSV' | 'JSON';

/** Bietet den Export der gefilterten Liste als Download an. */
@Component({
  selector: 'app-export-menu',
  imports: [MatButtonModule, MatMenuModule],
  template: `
    <button
      matButton
      type="button"
      class="export-button"
      [disabled]="busy()"
      [matMenuTriggerFor]="formats"
    >
      {{ busy() ? 'Exportiere …' : 'Exportieren' }}
    </button>
    <mat-menu #formats="matMenu">
      <button mat-menu-item type="button" class="export-html" (click)="export('HTML')">
        HTML-Liste mit Postern (ZIP)
      </button>
      <button mat-menu-item type="button" class="export-csv" (click)="export('CSV')">
        CSV für Excel
      </button>
      <button mat-menu-item type="button" class="export-json" (click)="export('JSON')">JSON</button>
    </mat-menu>
  `,
})
export class ExportMenu {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  /** Export-Endpunkt, z. B. `/api/v1/movies/export`. */
  readonly endpoint = input.required<string>();
  /** Der gerade gewählte Filter der Liste. */
  readonly filter = input.required<object>();
  /** Name der Datei, wenn der Server keinen nennt. */
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
