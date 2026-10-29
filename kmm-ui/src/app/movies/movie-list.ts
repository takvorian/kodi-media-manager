import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter as where, tap } from 'rxjs';

import {
  ALL_MOVIES,
  MOVIE_FILTER_FIELDS,
  MovieFilter,
  PREDEFINED_MOVIE_FILTERS,
} from '../filters/movie-filter';
import { BatchChanges, batchSummary } from '../filters/batch';
import { BatchEditor, KEEP_FIELD } from '../filters/batch-editor';
import { ListFilter, completeConditions } from '../filters/filter';
import { FilterEditor } from '../filters/filter-editor';
import { SavedFilter, SavedFilterService } from '../filters/saved-filter.service';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { MediaSourceService } from '../sources/media-source.service';
import { DiscFormat } from './movie';
import { MOVIE_BATCH_FIELDS, MOVIE_BATCH_JOB_KEY } from './movie-batch';
import { MovieService } from './movie.service';

export const SEARCH_DEBOUNCE_MS = 300;
export const DEFAULT_PAGE_SIZE = 50;

const DISC_FORMAT_LABELS: Record<DiscFormat, string> = {
  FILE: 'Datei',
  DVD: 'DVD',
  BLURAY: 'Blu-ray',
};
const SNACKBAR_DURATION_MS = 5000;
const ALL = 'all';
const PREDEFINED = 'predefined:';
const SAVED = 'saved:';

/** Filmliste mit Titelsuche und Filtern (mitgelieferte, gespeicherte und eigene Bedingungen). */
@Component({
  selector: 'app-movie-list',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTableModule,
    BatchEditor,
    FilterEditor,
    RouterLink,
  ],
  templateUrl: './movie-list.html',
  styleUrl: './movie-list.scss',
})
export class MovieList {
  private readonly movieService = inject(MovieService);
  private readonly savedFilterService = inject(SavedFilterService);
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);

  protected readonly pageSizes = [25, DEFAULT_PAGE_SIZE, 100];
  protected readonly columns = ['select', 'title', 'year', 'discFormat', 'filePath'];
  protected readonly predefined = PREDEFINED_MOVIE_FILTERS;
  protected readonly filterFields = MOVIE_FILTER_FIELDS;
  protected readonly batchFields = MOVIE_BATCH_FIELDS;
  protected readonly searchInput = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly filter = signal<MovieFilter>(ALL_MOVIES);
  /** Gewählter Eintrag der Filterauswahl: `all`, `predefined:<Index>` oder `saved:<ID>`. */
  protected readonly selection = signal(ALL);
  protected readonly editing = signal(false);
  protected readonly filterName = signal('');
  /** Für die Stapel-Bearbeitung ausgewählte Filme, auch über Seiten hinweg. */
  protected readonly chosen = signal<ReadonlySet<number>>(new Set());
  protected readonly batchEditing = signal(false);
  protected readonly batchJob = computed(() => this.jobStore.runningJob(MOVIE_BATCH_JOB_KEY));

  protected readonly savedFilters = rxResource({
    stream: () => this.savedFilterService.findAll('MOVIE'),
  });
  protected readonly filterValues = rxResource({
    params: () => (this.editing() ? true : undefined),
    stream: () => this.movieService.filterValues(),
  });
  protected readonly sources = rxResource({
    params: () => (this.editing() ? true : undefined),
    stream: () => this.sourceService.findAll(),
  });

  private readonly search = toSignal(
    toObservable(this.searchInput).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      tap(() => this.pageIndex.set(0)),
    ),
    { initialValue: '' },
  );

  /** Neu geladen wird nur, wenn sich die Abfrage inhaltlich ändert, nicht schon bei einer leeren neuen Zeile. */
  private readonly query = computed(
    () => ({
      filter: { ...completeConditions(this.filter()), search: this.search().trim() || null },
      page: this.pageIndex(),
      size: this.pageSize(),
    }),
    { equal: (previous, next) => JSON.stringify(previous) === JSON.stringify(next) },
  );

  protected readonly movies = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.movieService.queryMovies(params.filter, params.page, params.size),
  });

  /** Ob die Liste eingeschränkt ist; dann heißt eine leere Liste „nichts gefunden“ statt „Bibliothek leer“. */
  protected readonly restricted = computed(
    () =>
      this.searchInput().trim() !== '' || completeConditions(this.filter()).conditions.length > 0,
  );

  protected readonly selectedSaved = computed<SavedFilter | null>(() => {
    const selection = this.selection();
    if (!selection.startsWith(SAVED) || !this.savedFilters.hasValue()) {
      return null;
    }
    const id = Number(selection.substring(SAVED.length));
    return this.savedFilters.value().find((saved) => saved.id === id) ?? null;
  });

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        where((job) => job.key === MOVIE_BATCH_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(batchSummary(job));
        this.movies.reload();
      });
  }

  protected isChosen(movieId: number): boolean {
    return this.chosen().has(movieId);
  }

  protected toggleChosen(movieId: number): void {
    this.chosen.update((current) => {
      const next = new Set(current);
      if (!next.delete(movieId)) {
        next.add(movieId);
      }
      return next;
    });
  }

  /** Wählt alle Filme aus, auf die der aktuelle Filter samt Suche passt, nicht nur die sichtbare Seite. */
  protected chooseAllFiltered(): void {
    this.movieService.queryMovieIds(this.query().filter).subscribe({
      next: (ids) => this.chosen.set(new Set(ids)),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected clearChosen(): void {
    this.chosen.set(new Set());
    this.batchEditing.set(false);
  }

  protected toggleBatchEditing(): void {
    this.batchEditing.update((editing) => !editing);
  }

  protected chosenIds(): number[] {
    return [...this.chosen()];
  }

  protected startBatch(changes: BatchChanges): void {
    const movieIds = this.chosenIds();
    if (!this.confirm(`${movieIds.length} Filme ändern? Die NFOs werden neu geschrieben.`)) {
      return;
    }
    const edit = {
      movieIds,
      genres: changes.genres,
      tags: changes.tags,
      mpaa: changes.fields['mpaa'] ?? KEEP_FIELD,
      edition: changes.fields['edition'] ?? KEEP_FIELD,
      videoSource: changes.fields['videoSource'] ?? KEEP_FIELD,
    };
    this.movieService.batchEdit(edit).subscribe({
      next: (job) => {
        this.jobStore.update(job);
        this.batchEditing.set(false);
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Aktualisiert die Bewertungen der Auswahl (etwa Trakt, OMDb) und füllt leere Felder; vorhandene Angaben bleiben. */
  protected refreshRatings(): void {
    const ids = this.chosenIds();
    if (
      !this.confirm(
        `Bewertungen für ${ids.length} Filme aktualisieren und leere Felder füllen (Trakt, OMDb)? Die NFOs werden dabei geschrieben.`,
      )
    ) {
      return;
    }
    this.movieService.refreshRatings(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Holt für die Auswahl fehlende Titelmusik (theme.mp3), nur bei gleichem Titel beim Anbieter. */
  protected fillMissingThemes(): void {
    const ids = this.chosenIds();
    if (
      !this.confirm(
        `Für ${ids.length} Filme fehlende Titelmusik holen? Sie wird als theme.mp3 im Ordner gespeichert.`,
      )
    ) {
      return;
    }
    this.movieService.fillMissingThemes(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Trägt für die ausgewählten Filme ohne Trailer den besten Vorschlag ein; vorhandene bleiben. */
  protected fillMissingTrailers(): void {
    const movieIds = this.chosenIds();
    if (
      !this.confirm(
        `Für ${movieIds.length} Filme fehlende Trailer holen? Die NFOs werden dabei geschrieben.`,
      )
    ) {
      return;
    }
    this.movieService.fillMissingTrailers(movieIds).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected discFormatLabel(format: DiscFormat): string {
    return DISC_FORMAT_LABELS[format];
  }

  protected onSearch(event: Event): void {
    this.searchInput.set((event.target as HTMLInputElement).value);
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected select(selection: string): void {
    this.selection.set(selection);
    this.filterName.set('');
    if (selection.startsWith(PREDEFINED)) {
      this.applyFilter(this.predefined[Number(selection.substring(PREDEFINED.length))].filter);
    } else if (selection.startsWith(SAVED)) {
      const saved = this.selectedSaved();
      this.applyFilter(saved ? (saved.definition as MovieFilter) : ALL_MOVIES);
      this.filterName.set(saved?.name ?? '');
    } else {
      this.applyFilter(ALL_MOVIES);
    }
  }

  protected toggleEditing(): void {
    this.editing.update((editing) => !editing);
  }

  protected onFilterChange(filter: ListFilter): void {
    this.applyFilter(filter as MovieFilter);
  }

  /** Speichert unter dem eingegebenen Namen: einen gewählten gespeicherten Filter ändern, sonst neu anlegen. */
  protected saveFilter(): void {
    const name = this.filterName().trim();
    if (!name) {
      this.notify('Bitte einen Namen für den Filter eingeben.');
      return;
    }
    const definition = completeConditions(this.filter());
    const saved = this.selectedSaved();
    const request =
      saved && saved.name.toLowerCase() === name.toLowerCase()
        ? this.savedFilterService.update(saved.id, 'MOVIE', name, definition)
        : this.savedFilterService.create('MOVIE', name, definition);
    request.subscribe({
      next: (stored) => {
        this.notify(`Filter „${stored.name}“ gespeichert.`);
        this.savedFilters.reload();
        this.selection.set(SAVED + stored.id);
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected deleteFilter(): void {
    const saved = this.selectedSaved();
    if (!saved || !this.confirm(`Filter „${saved.name}“ löschen?`)) {
      return;
    }
    this.savedFilterService.delete(saved.id).subscribe({
      next: () => {
        this.notify(`Filter „${saved.name}“ gelöscht.`);
        this.savedFilters.reload();
        this.select(ALL);
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected savedKey(saved: SavedFilter): string {
    return SAVED + saved.id;
  }

  protected predefinedKey(index: number): string {
    return PREDEFINED + index;
  }

  private applyFilter(filter: MovieFilter): void {
    this.filter.set(filter);
    this.pageIndex.set(0);
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
