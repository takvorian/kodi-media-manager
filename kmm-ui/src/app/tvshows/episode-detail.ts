import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { problemMessage } from '../shared/problem-message';
import { StreamDetailsView } from '../shared/stream-details';
import { TvPeople } from './tv-people';
import { EpisodeDetails, episodeCode } from './tvshow';
import { createEpisodeMetadataForm, toEpisodeFormValue, toEpisodeMetadata } from './tvshow-forms';
import { TvShowService } from './tvshow.service';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-episode-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    StreamDetailsView,
    TvPeople,
  ],
  templateUrl: './episode-detail.html',
  styleUrl: './tvshow-detail.scss',
})
export class EpisodeDetail {
  private readonly tvShowService = inject(TvShowService);
  private readonly snackBar = inject(MatSnackBar);

  /** Episoden-ID aus der Route `/episodes/:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly form = createEpisodeMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly episode = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.tvShowService.findEpisode(params),
  });

  constructor() {
    effect(() => {
      if (this.episode.hasValue()) {
        this.form.reset(toEpisodeFormValue(this.episode.value().metadata));
      }
    });
  }

  protected code(details: EpisodeDetails): string {
    return episodeCode(details.season, details.episode);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.tvShowService.updateEpisode(this.id(), toEpisodeMetadata(this.form)).subscribe({
      next: (details) => {
        this.saving.set(false);
        this.episode.set(details);
        this.notify(`Gespeichert und in ${details.nfo.path} geschrieben.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    if (this.episode.hasValue()) {
      this.form.reset(toEpisodeFormValue(this.episode.value().metadata));
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
