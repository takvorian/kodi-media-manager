import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs';

import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { MovieDetails, TrailerOptions } from './movie';
import { MovieService } from './movie.service';

const SNACKBAR_DURATION_MS = 5000;
const YOUTUBE_VIDEO_ID = /[?&]videoid=([A-Za-z0-9_-]+)/;
const WEB_LINK = /^https?:\/\//i;
const YOUTUBE_WATCH = 'https://www.youtube.com/watch?v=';

/** Trailer-Link der NFO, den Kodi abspielt: anzeigen, aus Vorschlägen wählen, selbst eintragen oder entfernen. */
@Component({
  selector: 'app-movie-trailer',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './movie-trailer.html',
  styleUrl: './movie-trailer.scss',
})
export class MovieTrailer {
  private readonly movieService = inject(MovieService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  readonly movieId = input.required<number>();
  readonly trailer = input<string | null>(null);
  /** Der Film nach der Änderung. */
  readonly changed = output<MovieDetails>();

  protected readonly busy = signal(false);
  protected readonly options = signal<TrailerOptions | null>(null);
  protected readonly manualLink = signal('');

  /** Adresse zum Ansehen im Browser, sofern sich eine aus dem Link ableiten lässt. */
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
    this.movieService
      .trailerCandidates(this.movieId())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (options) => this.options.set(options),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected apply(url: string): void {
    this.busy.set(true);
    this.movieService
      .applyTrailer(this.movieId(), url.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (details) => {
          this.options.set(null);
          this.manualLink.set('');
          this.changed.emit(details);
          this.notify('Trailer übernommen und in die NFO geschrieben.');
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
    if (!this.confirm('Trailer-Link aus Bibliothek und NFO entfernen?')) {
      return;
    }
    this.busy.set(true);
    this.movieService
      .removeTrailer(this.movieId())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (details) => {
          this.changed.emit(details);
          this.notify('Trailer entfernt.');
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
