import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { MovieRating, MovieSupplement } from './movie';

/** So viele Darsteller sind sichtbar, bevor „Alle anzeigen“ erscheint. */
export const VISIBLE_ACTORS = 15;

const RATING_SOURCE_LABELS: Readonly<Record<string, string>> = {
  imdb: 'IMDb',
  themoviedb: 'TMDB',
  tmdb: 'TMDB',
  trakt: 'Trakt',
  default: 'Standard',
};

/** Zeigt Mitwirkende, Zuordnungen, Bewertungen und Nutzerdaten eines Films (nur lesend). */
@Component({
  selector: 'app-movie-supplement',
  imports: [DatePipe, DecimalPipe, MatButtonModule],
  templateUrl: './movie-supplement.html',
  styleUrl: './movie-supplement.scss',
})
export class MovieSupplementView {
  readonly supplement = input.required<MovieSupplement>();
  readonly addedAt = input.required<string>();

  protected readonly visibleActorLimit = VISIBLE_ACTORS;
  protected readonly showAllActors = signal(false);
  protected readonly visibleActors = computed(() => {
    const actors = this.supplement().actors;
    return this.showAllActors() ? actors : actors.slice(0, VISIBLE_ACTORS);
  });
  protected readonly hiddenActorCount = computed(() => this.supplement().actors.length - VISIBLE_ACTORS);

  protected ratingLabel(rating: MovieRating): string {
    return RATING_SOURCE_LABELS[rating.source.toLowerCase()] ?? rating.source;
  }

  protected toggleActors(): void {
    this.showAllActors.update((showAll) => !showAll);
  }
}
