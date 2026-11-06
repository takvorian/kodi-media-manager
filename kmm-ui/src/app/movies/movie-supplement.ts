import { Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { TranslocoDatePipe, TranslocoDecimalPipe } from '@jsverse/transloco-locale';

import { MovieRating, MovieSupplement } from './movie';

/** This many actors are visible before “Show all” appears. */
export const VISIBLE_ACTORS = 15;

/** Source of the rating Kodi shows when no other one is chosen; its label is translated. */
const DEFAULT_RATING_SOURCE = 'default';

/** Proper names of the rating sources; unknown sources are shown as they are. */
const RATING_SOURCE_LABELS: Readonly<Record<string, string>> = {
  imdb: 'IMDb',
  themoviedb: 'TMDB',
  tmdb: 'TMDB',
  trakt: 'Trakt',
};

/** Shows cast and crew, assignments, ratings and user data of a movie (read-only). */
@Component({
  selector: 'app-movie-supplement',
  imports: [MatButtonModule, TranslocoDatePipe, TranslocoDecimalPipe, TranslocoPipe],
  templateUrl: './movie-supplement.html',
  styleUrl: './movie-supplement.scss',
})
export class MovieSupplementView {
  readonly supplement = input.required<MovieSupplement>();
  readonly addedAt = input.required<string>();

  protected readonly visibleActorLimit = VISIBLE_ACTORS;
  protected readonly oneDecimal: Intl.NumberFormatOptions = {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  };
  protected readonly showAllActors = signal(false);
  protected readonly visibleActors = computed(() => {
    const actors = this.supplement().actors;
    return this.showAllActors() ? actors : actors.slice(0, VISIBLE_ACTORS);
  });
  protected readonly hiddenActorCount = computed(
    () => this.supplement().actors.length - VISIBLE_ACTORS,
  );

  protected isDefaultSource(rating: MovieRating): boolean {
    return rating.source.toLowerCase() === DEFAULT_RATING_SOURCE;
  }

  protected ratingLabel(rating: MovieRating): string {
    return RATING_SOURCE_LABELS[rating.source.toLowerCase()] ?? rating.source;
  }

  protected toggleActors(): void {
    this.showAllActors.update((showAll) => !showAll);
  }
}
