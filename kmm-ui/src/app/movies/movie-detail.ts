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
import { MovieDetails } from './movie';
import { MAX_YEAR, MIN_YEAR, createMovieMetadataForm, toFormValue, toMetadata } from './movie-metadata-form';
import { MovieOnlineSearch, ScrapedProposal } from './movie-online-search';
import { MovieService } from './movie.service';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-movie-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MovieOnlineSearch,
  ],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.scss',
})
export class MovieDetail {
  private readonly movieService = inject(MovieService);
  private readonly snackBar = inject(MatSnackBar);

  /** Film-ID aus der Route {@code /movies/:id}. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly minYear = MIN_YEAR;
  protected readonly maxYear = MAX_YEAR;
  protected readonly form = createMovieMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly searchingOnline = signal(false);
  protected readonly movie = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.movieService.findMovie(params),
  });

  constructor() {
    effect(() => {
      if (this.movie.hasValue()) {
        this.showInForm(this.movie.value());
      }
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.movieService.updateMetadata(this.id(), toMetadata(this.form)).subscribe({
      next: (details) => {
        this.saving.set(false);
        this.movie.set(details);
        this.notify(`Gespeichert und in ${details.nfo.path} geschrieben.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected toggleOnlineSearch(): void {
    this.searchingOnline.update((open) => !open);
  }

  /** Übernimmt einen Vorschlag ins Formular; gespeichert wird erst mit „Speichern“. */
  protected adoptProposal(proposal: ScrapedProposal): void {
    this.form.patchValue(toFormValue(proposal.metadata));
    this.form.markAsDirty();
    this.searchingOnline.set(false);
    this.notify(`Daten von ${proposal.scraperName} übernommen – bitte prüfen und speichern.`);
  }

  protected reset(): void {
    if (this.movie.hasValue()) {
      this.showInForm(this.movie.value());
    }
  }

  private showInForm(details: MovieDetails): void {
    this.form.reset(toFormValue(details.metadata));
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
