import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { ARTWORK_LABELS, ArtworkCandidate, ArtworkCandidates, ArtworkInfo, ArtworkSource, ArtworkType } from './artwork';

const SNACKBAR_DURATION_MS = 5000;

/**
 * Bilder eines Films, einer Serie, einer Staffel oder einer Episode: vorhandene Dateien anzeigen, aus
 * den Treffern der Scraper auswählen und entfernen. Ein gewähltes Bild wird sofort gespeichert.
 */
@Component({
  selector: 'app-artwork-gallery',
  imports: [MatButtonModule],
  templateUrl: './artwork-gallery.html',
  styleUrl: './artwork-gallery.scss',
})
export class ArtworkGallery {
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  readonly source = input.required<ArtworkSource>();

  protected readonly labels = ARTWORK_LABELS;
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

  protected languageLabel(candidate: ArtworkCandidate): string {
    return candidate.language ? candidate.language.toUpperCase() : 'ohne Text';
  }

  protected select(type: ArtworkType): void {
    if (this.selectedType() === type) {
      this.closeSelection();
      return;
    }
    this.selectedType.set(type);
    this.candidates.set(null);
    this.candidatesError.set(null);
    this.source().candidates(type).subscribe({
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
    this.source().apply(candidate.type, candidate.url).subscribe({
      next: () => {
        this.busy.set(false);
        this.closeSelection();
        this.notify(`${this.labels[candidate.type]} von ${candidate.scraperName} gespeichert.`);
        this.artwork.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected remove(artwork: ArtworkInfo): void {
    if (!this.confirm(`${this.labels[artwork.type]} löschen?`)) {
      return;
    }
    this.source().remove(artwork.type).subscribe({
      next: () => {
        this.notify(`${this.labels[artwork.type]} gelöscht.`);
        this.artwork.reload();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
