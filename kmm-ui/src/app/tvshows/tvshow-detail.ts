import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { problemMessage } from '../shared/problem-message';
import { TvPeople } from './tv-people';
import { EpisodeSummary, Season, TvShowDetails, episodeCode, seasonLabel } from './tvshow';
import { MAX_YEAR, MIN_YEAR, createTvShowMetadataForm, toTvShowFormValue, toTvShowMetadata } from './tvshow-forms';
import { TvShowService } from './tvshow.service';

const SNACKBAR_DURATION_MS = 5000;

/** Staffel mit ihren Episoden für die Übersicht. */
export interface SeasonWithEpisodes {
  readonly season: Season;
  readonly episodes: readonly EpisodeSummary[];
}

@Component({
  selector: 'app-tvshow-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    TvPeople,
  ],
  templateUrl: './tvshow-detail.html',
  styleUrl: './tvshow-detail.scss',
})
export class TvShowDetail {
  private readonly tvShowService = inject(TvShowService);
  private readonly snackBar = inject(MatSnackBar);

  /** Serien-ID aus der Route `/tvshows/:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly minYear = MIN_YEAR;
  protected readonly maxYear = MAX_YEAR;
  protected readonly form = createTvShowMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly show = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.tvShowService.findShow(params),
  });
  protected readonly seasons = computed<SeasonWithEpisodes[]>(() =>
    this.show.hasValue() ? groupBySeason(this.show.value()) : [],
  );

  constructor() {
    effect(() => {
      if (this.show.hasValue()) {
        this.form.reset(toTvShowFormValue(this.show.value().metadata));
      }
    });
  }

  protected code(episode: EpisodeSummary): string {
    return episodeCode(episode.season, episode.episode);
  }

  protected label(season: Season): string {
    return seasonLabel(season);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.tvShowService.updateShow(this.id(), toTvShowMetadata(this.form)).subscribe({
      next: (details) => {
        this.saving.set(false);
        this.show.set(details);
        this.notify(`Gespeichert und in ${details.nfo.path} geschrieben.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    if (this.show.hasValue()) {
      this.form.reset(toTvShowFormValue(this.show.value().metadata));
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function groupBySeason(details: TvShowDetails): SeasonWithEpisodes[] {
  return details.seasons
    .filter((season) => season.episodeCount > 0)
    .map((season) => ({
      season,
      episodes: details.episodes.filter((episode) => episode.season === season.number),
    }));
}
