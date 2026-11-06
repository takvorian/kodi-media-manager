import { Component, computed, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { NumberFormatOptions, TranslocoDecimalPipe } from '@jsverse/transloco-locale';

import { TvActor, TvRating } from './tvshow';

export const VISIBLE_ACTORS = 15;

/** Ratings are shown with exactly one decimal, e.g. 7.0. */
const ONE_DECIMAL: NumberFormatOptions = {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
};

const RATING_LABELS: Record<string, string> = {
  imdb: 'IMDb',
  tmdb: 'TMDB',
  themoviedb: 'TMDB',
  tvdb: 'TVDB',
  trakt: 'Trakt',
};

/** Ratings and cast of a TV show or episode, read-only. */
@Component({
  selector: 'app-tv-people',
  imports: [MatButtonModule, TranslocoDecimalPipe, TranslocoPipe],
  // Line breaks would put spaces between a rating and its maximum or between a name and its role.
  // prettier-ignore
  template: `
    @if (ratings().length > 0) {
      <h3>{{ 'tvshows.people.ratings' | transloco }}</h3>
      <ul class="inline-list ratings">
        @for (rating of ratings(); track $index) {
          <li [class.default-rating]="rating.defaultRating">
            {{ ratingLabel(rating) }} {{ rating.value | translocoDecimal: oneDecimal }}@if (rating.max) {/{{ rating.max }}}
            @if (rating.votes) {
              <span class="hint"> {{ 'tvshows.people.votes' | transloco: { votes: (rating.votes | translocoDecimal) } }}</span>
            }
          </li>
        }
      </ul>
    }
    @if (actors().length > 0) {
      <h3>{{ 'fields.cast' | transloco }}</h3>
      <ul class="actors">
        @for (actor of visibleActors(); track $index) {
          <li>
            {{ actor.name }}@if (actor.role) {<span class="hint"> {{ 'tvshows.people.asRole' | transloco: { role: actor.role } }}</span>}
          </li>
        }
      </ul>
      @if (actors().length > visibleCount) {
        <button matButton type="button" class="toggle-actors" (click)="showAll.set(!showAll())">
          {{
            showAll()
              ? ('tvshows.people.showLess' | transloco)
              : ('tvshows.people.showAll' | transloco: { count: actors().length })
          }}
        </button>
      }
    }
  `,
  styles: `
    h3 {
      margin: 8px 0 4px;
      font: var(--mat-sys-title-small);
    }

    .inline-list {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      padding: 0;
      margin: 0;
      list-style: none;
    }

    .default-rating {
      font-weight: 500;
    }

    .actors {
      columns: 2 280px;
      margin: 0;
      padding-left: 20px;
    }

    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TvPeople {
  readonly actors = input<readonly TvActor[]>([]);
  readonly ratings = input<readonly TvRating[]>([]);

  protected readonly visibleCount = VISIBLE_ACTORS;
  protected readonly oneDecimal = ONE_DECIMAL;
  protected readonly showAll = signal(false);
  protected readonly visibleActors = computed(() =>
    this.showAll() ? this.actors() : this.actors().slice(0, VISIBLE_ACTORS),
  );

  protected ratingLabel(rating: TvRating): string {
    return RATING_LABELS[rating.source.toLowerCase()] ?? rating.source;
  }
}
