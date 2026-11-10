import { NgTemplateOutlet } from '@angular/common';
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
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
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
import { Job, isRunning } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { localDateTime } from '../shared/date-time';
import { ExportMenu } from '../shared/export-menu';
import { KodiService } from '../kodi/kodi.service';
import { ListSort, SORT_BY_TITLE, listSortOf } from '../shared/list-sort';
import { LanguageSelect, languageChangeQuestion } from '../shared/language-select';
import { problemMessage } from '../shared/problem-message';
import { RemovalService, isRemovalJob } from '../shared/removal';
import { MediaSourceService } from '../sources/media-source.service';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { DiscFormat } from './movie';
import { MOVIES_SCRAPE_JOB_KEY, MOVIE_BATCH_FIELDS, MOVIE_BATCH_JOB_KEY } from './movie-batch';
import { MOVIES_URL, MovieService } from './movie.service';
import { LocalizedPipe } from '../i18n/localized.pipe';

export const SEARCH_DEBOUNCE_MS = 300;
export const DEFAULT_PAGE_SIZE = 50;

/** Translation key of the label of each disc format. */
const DISC_FORMAT_KEYS: Record<DiscFormat, string> = {
  FILE: 'movies.list.discFormat.FILE',
  DVD: 'movies.list.discFormat.DVD',
  BLURAY: 'movies.list.discFormat.BLURAY',
};
const SNACKBAR_DURATION_MS = 5000;

/** Named arguments of a translated message. */
type TranslationParams = Record<string, unknown>;
const ALL = 'all';
const PREDEFINED = 'predefined:';
const SAVED = 'saved:';

/** Movie list with title search and filters (built-in, saved and custom conditions). */
@Component({
  selector: 'app-movie-list',
  imports: [
    LocalizedPipe,
    LanguageSelect,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSortModule,
    MatTableModule,
    NgTemplateOutlet,
    BatchEditor,
    ExportMenu,
    FilterEditor,
    RouterLink,
    ScrapeProgress,
    ScrapeReport,
    TranslocoPipe,
  ],
  templateUrl: './movie-list.html',
  styleUrl: './movie-list.scss',
})
export class MovieList {
  private readonly movieService = inject(MovieService);
  private readonly kodiService = inject(KodiService);
  private readonly savedFilterService = inject(SavedFilterService);
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);
  private readonly removal = inject(RemovalService);
  private readonly transloco = inject(TranslocoService);

  protected readonly pageSizes = [25, DEFAULT_PAGE_SIZE, 100];
  protected readonly columns = ['select', 'title', 'year', 'discFormat', 'addedAt', 'filePath'];
  protected readonly predefined = PREDEFINED_MOVIE_FILTERS;
  protected readonly filterFields = MOVIE_FILTER_FIELDS;
  protected readonly batchFields = MOVIE_BATCH_FIELDS;
  protected readonly searchInput = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly sort = signal<ListSort>(SORT_BY_TITLE);
  protected readonly localDateTime = localDateTime;
  protected readonly filter = signal<MovieFilter>(ALL_MOVIES);
  /** Chosen entry of the filter selection: `all`, `predefined:<index>` or `saved:<ID>`. */
  protected readonly selection = signal(ALL);
  protected readonly editing = signal(false);
  protected readonly filterName = signal('');
  /** Movies chosen for the batch edit, also across pages. */
  protected readonly chosen = signal<ReadonlySet<number>>(new Set());
  protected readonly batchEditing = signal(false);
  protected readonly batchJob = computed(() => this.jobStore.runningJob(MOVIE_BATCH_JOB_KEY));
  protected readonly scrapeJob = computed(() => this.jobStore.runningJob(MOVIES_SCRAPE_JOB_KEY));
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Finished run while its report is not open: it may end while the user is on another page. */
  protected readonly lastScrape = computed(() =>
    this.scrapeReport()
      ? undefined
      : this.jobStore.jobs().find((job) => job.key === MOVIES_SCRAPE_JOB_KEY && !isRunning(job)),
  );

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

  protected readonly exportEndpoint = `${MOVIES_URL}/export`;
  /** The chosen filter including the search, as the list shows it. */
  protected readonly exportFilter = computed(() => ({
    ...completeConditions(this.filter()),
    search: this.search().trim() || null,
  }));

  /** Reloads only when the query changes in content, not already for a new empty row. */
  private readonly query = computed(
    () => ({
      filter: { ...completeConditions(this.filter()), search: this.search().trim() || null },
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sort(),
    }),
    { equal: (previous, next) => JSON.stringify(previous) === JSON.stringify(next) },
  );

  protected readonly movies = rxResource({
    params: () => this.query(),
    stream: ({ params }) =>
      this.movieService.queryMovies(params.filter, params.page, params.size, params.sort),
  });

  /** Whether the list is restricted; an empty list then means “nothing found” instead of “library empty”. */
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
    this.jobStore.completed
      .pipe(
        where((job) => job.key === MOVIES_SCRAPE_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.scrapeReport.set(job);
        this.movies.reload();
      });
    // Also movies removed on their detail page, which comes back here.
    this.jobStore.completed
      .pipe(where(isRemovalJob), takeUntilDestroyed())
      .subscribe(() => this.movies.reload());
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

  /** Chooses all movies matching the current filter and search, not only the visible page. */
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
    if (!this.confirmTranslated('movies.list.confirmBatch', { count: movieIds.length })) {
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

  /** Adds everything missing for the selection like scraping a source does; existing data is kept. */
  protected scrapeChosen(): void {
    const ids = this.chosenIds();
    if (!this.confirmTranslated('movies.list.confirmScrape', { count: ids.length })) {
      return;
    }
    this.scrapeReport.set(null);
    this.movieService.autoScrapeAll(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Refreshes the ratings of the selection (e.g. Trakt, OMDb) and fills empty fields; existing data is kept. */
  protected refreshRatings(): void {
    const ids = this.chosenIds();
    if (!this.confirmTranslated('movies.list.confirmRatings', { count: ids.length })) {
      return;
    }
    this.movieService.refreshRatings(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Fetches missing theme music (theme.mp3) for the selection, only if the provider has the same title. */
  protected fillMissingThemes(): void {
    const ids = this.chosenIds();
    if (!this.confirmTranslated('movies.list.confirmThemes', { count: ids.length })) {
      return;
    }
    this.movieService.fillMissingThemes(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Enters the best proposal for the chosen movies without a trailer; existing ones are kept. */
  protected fillMissingTrailers(): void {
    const movieIds = this.chosenIds();
    if (!this.confirmTranslated('movies.list.confirmTrailers', { count: movieIds.length })) {
      return;
    }
    this.movieService.fillMissingTrailers(movieIds).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Removes the chosen movies after asking how; the list reloads when the job is done. */
  protected removeChosen(): void {
    const ids = this.chosenIds();
    this.removal
      .remove({
        title: this.transloco.translate('removal.title.movies', { count: ids.length }),
        helpChapter: 'movies',
        plan: this.movieService.selectionRemovalPlan(ids),
        remove: (deleteFiles) => this.movieService.removeSelection(ids, deleteFiles),
      })
      .subscribe(() => this.clearChosen());
  }

  /** Asks whether the texts are fetched again in the new language; the language changes either way. */
  protected changeChosenLanguage(language: string | null): void {
    const texts = this.transloco.translate('movies.language.textsOfMovies');
    const refetchTexts = this.confirm(languageChangeQuestion(language, texts));
    this.movieService.changeLanguage(this.chosenIds(), language, refetchTexts).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Makes Kodi read the NFOs and artwork of the selection again, independently of the real-time sync. */
  protected refreshChosenInKodi(): void {
    const ids = this.chosenIds();
    if (!this.confirmTranslated('movies.list.confirmKodiRefresh', { count: ids.length })) {
      return;
    }
    this.kodiService.refreshMovies(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected discFormatKey(format: DiscFormat): string {
    return DISC_FORMAT_KEYS[format];
  }

  protected onSearch(event: Event): void {
    this.searchInput.set((event.target as HTMLInputElement).value);
  }

  protected onSort(sort: Sort): void {
    this.sort.set(listSortOf(sort));
    this.pageIndex.set(0);
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

  /** Saves under the entered name: changes a chosen saved filter, otherwise creates a new one. */
  protected saveFilter(): void {
    const name = this.filterName().trim();
    if (!name) {
      this.notifyTranslated('movies.list.enterFilterName');
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
        this.notifyTranslated('movies.list.filterSaved', { name: stored.name });
        this.savedFilters.reload();
        this.selection.set(SAVED + stored.id);
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected deleteFilter(): void {
    const saved = this.selectedSaved();
    if (
      !saved ||
      !this.confirmTranslated('movies.list.confirmDeleteFilter', { name: saved.name })
    ) {
      return;
    }
    this.savedFilterService.delete(saved.id).subscribe({
      next: () => {
        this.notifyTranslated('movies.list.filterDeleted', { name: saved.name });
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

  private confirmTranslated(key: string, params: TranslationParams): boolean {
    return this.confirm(this.transloco.translate(key, params));
  }

  private notifyTranslated(key: string, params?: TranslationParams): void {
    this.notify(this.transloco.translate(key, params));
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
