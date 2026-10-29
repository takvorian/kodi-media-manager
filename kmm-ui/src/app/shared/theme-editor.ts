import { DecimalPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { CONFIRM } from './confirm';
import { problemMessage } from './problem-message';
import { ThemeInfo, ThemeOption, ThemeOptions, ThemeService } from './theme';

const SNACKBAR_DURATION_MS = 5000;
const BYTES_PER_KB = 1024;

/**
 * Titelmusik eines Films oder einer Serie als `theme.mp3` im Ordner: anhören, aus Vorschlägen wählen oder
 * entfernen. Kodi spielt sie nur mit einem Add-on wie TvTunes ab.
 */
@Component({
  selector: 'app-theme-editor',
  imports: [DecimalPipe, MatButtonModule],
  templateUrl: './theme-editor.html',
  styleUrl: './theme-editor.scss',
})
export class ThemeEditor {
  private readonly themeService = inject(ThemeService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  /** Titelmusik-Endpunkt, z. B. `/api/v1/tvshows/3/theme`. */
  readonly endpoint = input.required<string>();

  protected readonly theme = rxResource({
    params: () => this.endpoint(),
    stream: ({ params }) => this.themeService.info(params),
  });
  protected readonly busy = signal(false);
  protected readonly options = signal<ThemeOptions | null>(null);

  protected fileUrl(info: ThemeInfo): string {
    return this.themeService.fileUrl(this.endpoint(), info);
  }

  protected kilobytes(info: ThemeInfo): number {
    return (info.sizeBytes ?? 0) / BYTES_PER_KB;
  }

  protected findCandidates(): void {
    this.busy.set(true);
    this.themeService
      .candidates(this.endpoint())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (options) => this.options.set(options),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected apply(option: ThemeOption): void {
    if (
      this.theme.value()?.exists &&
      !this.confirm('Die vorhandene theme.mp3 durch diese Titelmusik ersetzen?')
    ) {
      return;
    }
    this.busy.set(true);
    this.themeService
      .apply(this.endpoint(), option)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (info) => {
          this.theme.set(info);
          this.options.set(null);
          this.notify('Titelmusik als theme.mp3 gespeichert.');
        },
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected remove(): void {
    if (!this.confirm('theme.mp3 löschen?')) {
      return;
    }
    this.busy.set(true);
    this.themeService
      .remove(this.endpoint())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (info) => {
          this.theme.set(info);
          this.notify('Titelmusik gelöscht.');
        },
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected closeCandidates(): void {
    this.options.set(null);
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
