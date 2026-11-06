import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import {
  ARTWORK_LABEL_KEYS,
  ArtworkCandidate,
  ArtworkCandidates,
  ArtworkInfo,
  ArtworkSource,
  ArtworkType,
} from './artwork';

const SNACKBAR_DURATION_MS = 5000;

/**
 * Artwork of a movie, a TV show, a season or an episode: shows the existing files, lets the user choose from
 * the scrapers' results and removes images. A chosen image is saved at once.
 */
@Component({
  selector: 'app-artwork-gallery',
  imports: [MatButtonModule, TranslocoPipe],
  templateUrl: './artwork-gallery.html',
  styleUrl: './artwork-gallery.scss',
})
export class ArtworkGallery {
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly transloco = inject(TranslocoService);

  readonly source = input.required<ArtworkSource>();

  protected readonly labelKeys = ARTWORK_LABEL_KEYS;
  protected readonly artwork = rxResource({
    params: () => this.source(),
    stream: ({ params }) => params.findAll(),
  });
  protected readonly selectedType = signal<ArtworkType | null>(null);
  protected readonly candidates = signal<ArtworkCandidates | null>(null);
  protected readonly candidatesError = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected imageUrl(artwork: ArtworkInfo): string {
    return this.source().imageUrl(artwork);
  }

  protected select(type: ArtworkType): void {
    if (this.selectedType() === type) {
      this.closeSelection();
      return;
    }
    this.selectedType.set(type);
    this.candidates.set(null);
    this.candidatesError.set(null);
    this.source()
      .candidates(type)
      .subscribe({
        next: (result) => this.candidates.set(result),
        error: (error: unknown) => this.candidatesError.set(problemMessage(error)),
      });
  }

  protected closeSelection(): void {
    this.selectedType.set(null);
    this.candidates.set(null);
    this.candidatesError.set(null);
  }

  protected apply(candidate: ArtworkCandidate): void {
    this.busy.set(true);
    this.source()
      .apply(candidate.type, candidate.url)
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.closeSelection();
          this.notify(
            this.transloco.translate('movies.artwork.saved', {
              type: this.label(candidate.type),
              scraper: candidate.scraperName,
            }),
          );
          this.artwork.reload();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.notify(problemMessage(error));
        },
      });
  }

  protected remove(artwork: ArtworkInfo): void {
    const type = this.label(artwork.type);
    if (!this.confirm(this.transloco.translate('movies.artwork.confirmDelete', { type }))) {
      return;
    }
    this.source()
      .remove(artwork.type)
      .subscribe({
        next: () => {
          this.notify(this.transloco.translate('movies.artwork.deleted', { type }));
          this.artwork.reload();
        },
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  private label(type: ArtworkType): string {
    return this.transloco.translate(ARTWORK_LABEL_KEYS[type]);
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
