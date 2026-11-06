import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translate, TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';

import { LocalizedPipe } from '../i18n/localized.pipe';
import { CONFIRM } from './confirm';
import { problemMessage } from './problem-message';
import { TrailerOptions, TrailerService } from './trailer';

const SNACKBAR_DURATION_MS = 5000;
const YOUTUBE_VIDEO_ID = /[?&]videoid=([A-Za-z0-9_-]+)/;
const WEB_LINK = /^https?:\/\//i;
const YOUTUBE_WATCH = 'https://www.youtube.com/watch?v=';

/**
 * Trailer link in the NFO of a movie or TV show that Kodi plays: show it, choose from suggestions, enter one
 * yourself or remove it.
 */
@Component({
  selector: 'app-trailer-editor',
  imports: [LocalizedPipe, MatButtonModule, MatFormFieldModule, MatInputModule, TranslocoPipe],
  templateUrl: './trailer-editor.html',
  styleUrl: './trailer-editor.scss',
})
export class TrailerEditor {
  private readonly trailerService = inject(TrailerService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  /** Trailer endpoint, e.g. `/api/v1/movies/5/trailer`. */
  readonly endpoint = input.required<string>();
  readonly trailer = input<string | null>(null);
  /** The new trailer link, or `null` if it was removed. */
  readonly changed = output<string | null>();

  protected readonly busy = signal(false);
  protected readonly options = signal<TrailerOptions | null>(null);
  protected readonly manualLink = signal('');

  /** Address for watching in the browser, if one can be derived from the link. */
  protected readonly watchUrl = computed(() => {
    const link = this.trailer();
    if (!link) {
      return null;
    }
    const videoId = YOUTUBE_VIDEO_ID.exec(link)?.[1];
    if (videoId) {
      return YOUTUBE_WATCH + videoId;
    }
    return WEB_LINK.test(link) ? link : null;
  });

  protected findCandidates(): void {
    this.busy.set(true);
    this.trailerService
      .candidates(this.endpoint())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (options) => this.options.set(options),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected apply(url: string): void {
    this.busy.set(true);
    this.trailerService
      .apply(this.endpoint(), url.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (details) => {
          this.options.set(null);
          this.manualLink.set('');
          this.changed.emit(details.trailer);
          this.notify(translate('shared.trailer.applied'));
        },
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected applyManualLink(): void {
    if (this.manualLink().trim()) {
      this.apply(this.manualLink());
    }
  }

  protected remove(): void {
    if (!this.confirm(translate('shared.trailer.confirmRemove'))) {
      return;
    }
    this.busy.set(true);
    this.trailerService
      .remove(this.endpoint())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (details) => {
          this.changed.emit(details.trailer);
          this.notify(translate('shared.trailer.removed'));
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
