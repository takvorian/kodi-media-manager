import { Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormsModule,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, filter } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { ArtworkGallery } from '../movies/artwork-gallery';
import { MovieSummary } from '../movies/movie';
import { MovieService } from '../movies/movie.service';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import {
  MOVIE_SET_JOB_KEY,
  MovieSetCandidate,
  MovieSetData,
  MovieSetSummary,
  SORT_METHOD_KEYS,
  SetSortMethod,
  movieSetJobSummary,
} from './movie-set';
import { MovieSetService } from './movie-set.service';
import { HelpLink } from '../shared/help-link';
import { LocalizedPipe } from '../i18n/localized.pipe';

const SNACKBAR_DURATION_MS = 8000;
const SEARCH_SIZE = 20;
const TMDB_ID_PATTERN = /^\s*(\d{1,10})?\s*$/;

/**
 * Movie set manager: creates, edits and deletes sets, chooses artwork, adds and removes movies. Whatever changes
 * movie NFOs runs as a background job.
 */
@Component({
  selector: 'app-movie-set-manager',
  imports: [
    LocalizedPipe,
    HelpLink,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    ArtworkGallery,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    TranslocoPipe,
  ],
  templateUrl: './movie-set-manager.html',
  styleUrl: './movie-set-manager.scss',
})
export class MovieSetManager {
  private readonly setService = inject(MovieSetService);
  private readonly movieService = inject(MovieService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly transloco = inject(TranslocoService);

  protected readonly sortMethods = Object.entries(SORT_METHOD_KEYS) as [SetSortMethod, string][];
  protected readonly sets = rxResource({ stream: () => this.setService.findAll() });
  protected readonly selectedId = signal<number | null>(null);
  protected readonly set = rxResource({
    params: () => this.selectedId() ?? undefined,
    stream: ({ params }) => this.setService.find(params),
  });
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', Validators.required],
    overview: [''],
    tmdbCollectionId: ['', Validators.pattern(TMDB_ID_PATTERN)],
    sortMethod: ['YEAR' as SetSortMethod],
  });
  protected readonly filterText = signal('');
  protected readonly visibleSets = computed(() => {
    const text = this.filterText().trim().toLowerCase();
    const sets = this.sets.hasValue() ? this.sets.value() : [];
    return text ? sets.filter((set) => set.name.toLowerCase().includes(text)) : sets;
  });
  protected readonly removal = signal<ReadonlySet<number>>(new Set());
  protected readonly search = signal('');
  protected readonly candidates = signal<readonly MovieSummary[]>([]);
  protected readonly chosen = signal<ReadonlySet<number>>(new Set());
  protected readonly collectionQuery = signal('');
  protected readonly collections = signal<readonly MovieSetCandidate[] | null>(null);
  /** New each time the set is loaded, so the gallery shows the moved images after a rename. */
  protected readonly artworkSource = computed(() =>
    this.set.hasValue() ? this.setService.artwork(this.set.value().id) : null,
  );
  protected readonly runningJob = computed(() => this.jobStore.runningJob(MOVIE_SET_JOB_KEY));

  constructor() {
    this.jobStore.connect();
    effect(() => {
      if (this.set.hasValue()) {
        const data = this.set.value().data;
        this.collectionQuery.set(data.name);
        this.collections.set(null);
        this.form.reset({
          name: data.name,
          overview: data.overview ?? '',
          tmdbCollectionId: data.tmdbCollectionId ?? '',
          sortMethod: data.sortMethod,
        });
      }
    });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === MOVIE_SET_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(movieSetJobSummary(job));
        this.reload();
      });
  }

  protected select(set: MovieSetSummary): void {
    this.selectedId.set(set.id);
    this.removal.set(new Set());
    this.chosen.set(new Set());
  }

  protected create(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) {
      return;
    }
    this.setService
      .create({ name: trimmed, overview: null, tmdbCollectionId: null, sortMethod: 'YEAR' })
      .subscribe({
        next: (created) => {
          this.notify(this.transloco.translate('moviesets.created', { name: created.data.name }));
          this.sets.reload();
          this.selectedId.set(created.id);
        },
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected save(): void {
    const setId = this.selectedId();
    if (setId === null || this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    const data: MovieSetData = {
      name: value.name.trim(),
      overview: value.overview.trim() || null,
      tmdbCollectionId: value.tmdbCollectionId.trim() || null,
      sortMethod: value.sortMethod,
    };
    this.track(this.setService.update(setId, data), () => this.form.markAsPristine());
  }

  protected delete(): void {
    const setId = this.selectedId();
    const name = this.set.hasValue() ? this.set.value().data.name : '';
    if (
      setId !== null &&
      this.confirm(this.transloco.translate('moviesets.confirmDelete', { name }))
    ) {
      this.track(this.setService.delete(setId), () => this.selectedId.set(null));
    }
  }

  protected searchCollections(): void {
    const title = this.collectionQuery().trim();
    if (!title) {
      return;
    }
    this.setService.searchCollections(title).subscribe({
      next: (found) => this.collections.set(found),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Puts title, description and TMDB ID into the form; it is saved with “Save”. */
  protected takeOver(candidate: MovieSetCandidate): void {
    const setId = this.selectedId();
    if (setId === null) {
      return;
    }
    this.setService.proposal(setId, candidate.providerId).subscribe({
      next: (data) => {
        this.form.patchValue({
          name: data.name,
          overview: data.overview ?? '',
          tmdbCollectionId: data.tmdbCollectionId ?? '',
        });
        this.form.markAsDirty();
        this.collections.set(null);
        this.notify(this.transloco.translate('moviesets.adopted', { name: data.name }));
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected completeAll(): void {
    this.track(this.setService.completeAll(), () => undefined);
  }

  protected toggleRemoval(movieId: number): void {
    this.removal.update((current) => toggled(current, movieId));
  }

  protected removeMarked(): void {
    const setId = this.selectedId();
    if (setId !== null && this.removal().size > 0) {
      this.track(this.setService.removeMovies(setId, [...this.removal()]), () =>
        this.removal.set(new Set()),
      );
    }
  }

  protected find(): void {
    const search = this.search().trim();
    if (!search) {
      this.candidates.set([]);
      return;
    }
    this.movieService.findMovies({ search, page: 0, size: SEARCH_SIZE }).subscribe({
      next: (page) => this.candidates.set(page.content),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected toggleChosen(movieId: number): void {
    this.chosen.update((current) => toggled(current, movieId));
  }

  protected addChosen(): void {
    const setId = this.selectedId();
    if (setId !== null && this.chosen().size > 0) {
      this.track(this.setService.addMovies(setId, [...this.chosen()]), () =>
        this.chosen.set(new Set()),
      );
    }
  }

  protected label(movie: { title: string; year: number | null }): string {
    return movie.year ? `${movie.title} (${movie.year})` : movie.title;
  }

  private reload(): void {
    this.sets.reload();
    if (this.selectedId() !== null) {
      this.set.reload();
    }
  }

  private track(start: Observable<Job>, onStarted: () => void): void {
    start.subscribe({
      next: (job) => {
        this.jobStore.update(job);
        onStarted();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function toggled(current: ReadonlySet<number>, id: number): ReadonlySet<number> {
  const next = new Set(current);
  if (!next.delete(id)) {
    next.add(id);
  }
  return next;
}
