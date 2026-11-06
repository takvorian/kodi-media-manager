import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, filter as where, tap } from 'rxjs';

import { BatchChanges, batchSummary } from '../filters/batch';
import { BatchEditor, KEEP_FIELD } from '../filters/batch-editor';
import { ListFilter, completeConditions } from '../filters/filter';
import { FilterEditor } from '../filters/filter-editor';
import { SavedFilter, SavedFilterService } from '../filters/saved-filter.service';
import {
  ALL_TVSHOWS,
  PREDEFINED_TVSHOW_FILTERS,
  TVSHOW_FILTER_FIELDS,
  TvShowFilter,
} from '../filters/tvshow-filter';
import { Job, isRunning } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { localDateTime } from '../shared/date-time';
import { ExportMenu } from '../shared/export-menu';
import { ListSort, SORT_BY_TITLE, listSortOf } from '../shared/list-sort';
import { KodiService } from '../kodi/kodi.service';
import { LanguageSelect, languageChangeQuestion } from '../shared/language-select';
import { problemMessage } from '../shared/problem-message';
import { MediaSourceService } from '../sources/media-source.service';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { TVSHOWS_SCRAPE_JOB_KEY, TVSHOW_BATCH_FIELDS, TVSHOW_BATCH_JOB_KEY } from './tvshow-batch';
import { TVSHOWS_URL, TvShowService } from './tvshow.service';
import { LocalizedPipe } from '../i18n/localized.pipe';

export const SEARCH_DEBOUNCE_MS = 300;
export const DEFAULT_PAGE_SIZE = 50;

const SNACKBAR_DURATION_MS = 5000;
const ALL = 'all';
const PREDEFINED = 'predefined:';
const SAVED = 'saved:';

/** TV show list with title search and filters, also across the episodes. */
@Component({
  selector: 'app-tvshow-list',
  imports: [
    LocalizedPipe,
    LanguageSelect,
    BatchEditor,
    ExportMenu,
    FilterEditor,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    NgTemplateOutlet,
    RouterLink,
    ScrapeProgress,
    ScrapeReport,
    TranslocoPipe,
  ],
  templateUrl: './tvshow-list.html',
  styleUrl: './tvshow-list.scss',
})
export class TvShowList {
  private readonly tvShowService = inject(TvShowService);
  private readonly kodiService = inject(KodiService);
  private readonly savedFilterService = inject(SavedFilterService);
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly pageSizes = [25, DEFAULT_PAGE_SIZE, 100];
  protected readonly columns = [
    'select',
    'title',
    'year',
    'episodeCount',
    'addedAt',
    'directoryPath',
  ];
  protected readonly batchFields = TVSHOW_BATCH_FIELDS;
  protected readonly predefined = PREDEFINED_TVSHOW_FILTERS;
  protected readonly filterFields = TVSHOW_FILTER_FIELDS;
  protected readonly searchInput = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly sort = signal<ListSort>(SORT_BY_TITLE);
  protected readonly localDateTime = localDateTime;
  protected readonly filter = signal<TvShowFilter>(ALL_TVSHOWS);
  /** Chosen entry of the filter selection: `all`, `predefined:<index>` or `saved:<ID>`. */
  protected readonly selection = signal(ALL);
  protected readonly editing = signal(false);
  protected readonly filterName = signal('');
  /** TV shows selected for the batch edit, also across pages. */
  protected readonly chosen = signal<ReadonlySet<number>>(new Set());
  protected readonly batchEditing = signal(false);
  protected readonly batchJob = computed(() => this.jobStore.runningJob(TVSHOW_BATCH_JOB_KEY));
  protected readonly scrapeJob = computed(() => this.jobStore.runningJob(TVSHOWS_SCRAPE_JOB_KEY));
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Finished run while its report is not open: it may end while one is on another page. */
  protected readonly lastScrape = computed(() =>
    this.scrapeReport()
      ? undefined
      : this.jobStore.jobs().find((job) => job.key === TVSHOWS_SCRAPE_JOB_KEY && !isRunning(job)),
  );

  protected readonly savedFilters = rxResource({
    stream: () => this.savedFilterService.findAll('TV_SHOW'),
  });
  protected readonly filterValues = rxResource({
    params: () => (this.editing() ? true : undefined),
    stream: () => this.tvShowService.filterValues(),
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

  protected readonly exportEndpoint = `${TVSHOWS_URL}/export`;
  /** The chosen filter including the search, as the list shows it. */
  protected readonly exportFilter = computed(() => ({
    ...completeConditions(this.filter()),
    search: this.search().trim() || null,
  }));

  /** Reloads only when the content of the query changes. */
  private readonly query = computed(
    () => ({
      filter: { ...completeConditions(this.filter()), search: this.search().trim() || null },
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sort(),
    }),
    { equal: (previous, next) => JSON.stringify(previous) === JSON.stringify(next) },
  );

  protected readonly shows = rxResource({
    params: () => this.query(),
    stream: ({ params }) =>
      this.tvShowService.queryShows(params.filter, params.page, params.size, params.sort),
  });

  /** Whether the list is restricted; an empty list then means “nothing found”. */
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
        where((job) => job.key === TVSHOW_BATCH_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(batchSummary(job));
        this.shows.reload();
      });
    this.jobStore.completed
      .pipe(
        where((job) => job.key === TVSHOWS_SCRAPE_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.scrapeReport.set(job);
        this.shows.reload();
      });
  }

  protected isChosen(showId: number): boolean {
    return this.chosen().has(showId);
  }

  protected toggleChosen(showId: number): void {
    this.chosen.update((current) => {
      const next = new Set(current);
      if (!next.delete(showId)) {
        next.add(showId);
      }
      return next;
    });
  }

  /** Selects all TV shows the current filter and search match, not only the visible page. */
  protected chooseAllFiltered(): void {
    this.tvShowService.queryShowIds(this.query().filter).subscribe({
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

  protected startBatch(changes: BatchChanges): void {
    const showIds = [...this.chosen()];
    if (
      !this.confirm(
        this.transloco.translate('tvshows.list.confirmBatch', { count: showIds.length }),
      )
    ) {
      return;
    }
    const edit = {
      showIds,
      genres: changes.genres,
      tags: changes.tags,
      mpaa: changes.fields['mpaa'] ?? KEEP_FIELD,
      status: changes.fields['status'] ?? KEEP_FIELD,
    };
    this.tvShowService.batchEdit(edit).subscribe({
      next: (job) => {
        this.jobStore.update(job);
        this.batchEditing.set(false);
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Adds everything missing for the selection like scraping a source does; existing data stays. */
  protected scrapeChosen(): void {
    const ids = [...this.chosen()];
    if (
      !this.confirm(this.transloco.translate('tvshows.list.confirmScrape', { count: ids.length }))
    ) {
      return;
    }
    this.scrapeReport.set(null);
    this.tvShowService.autoScrapeAll(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Updates the ratings of the selection (such as Trakt, OMDb) and fills empty fields; existing details stay. */
  protected refreshRatings(): void {
    const ids = [...this.chosen()];
    if (
      !this.confirm(this.transloco.translate('tvshows.list.confirmRatings', { count: ids.length }))
    ) {
      return;
    }
    this.tvShowService.refreshRatings(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Gets missing theme music (theme.mp3) for the selection, only if the provider has the same title. */
  protected fillMissingThemes(): void {
    const ids = [...this.chosen()];
    if (
      !this.confirm(this.transloco.translate('tvshows.list.confirmThemes', { count: ids.length }))
    ) {
      return;
    }
    this.tvShowService.fillMissingThemes(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Asks whether the texts are refetched in the new language; the language changes in any case. */
  protected changeChosenLanguage(language: string | null): void {
    const refetchTexts = this.confirm(
      languageChangeQuestion(language, this.transloco.translate('tvshows.languageTexts')),
    );
    this.tvShowService.changeLanguage([...this.chosen()], language, refetchTexts).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected refreshChosenInKodi(): void {
    const ids = [...this.chosen()];
    if (
      !this.confirm(this.transloco.translate('tvshows.list.confirmKodi', { count: ids.length }))
    ) {
      return;
    }
    this.kodiService.refreshTvShows(ids).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Enters the best proposal for the selected TV shows without a trailer; existing ones stay. */
  protected fillMissingTrailers(): void {
    const showIds = [...this.chosen()];
    if (
      !this.confirm(
        this.transloco.translate('tvshows.list.confirmTrailers', { count: showIds.length }),
      )
    ) {
      return;
    }
    this.tvShowService.fillMissingTrailers(showIds).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
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
      this.applyFilter(saved ? (saved.definition as TvShowFilter) : ALL_TVSHOWS);
      this.filterName.set(saved?.name ?? '');
    } else {
      this.applyFilter(ALL_TVSHOWS);
    }
  }

  protected toggleEditing(): void {
    this.editing.update((editing) => !editing);
  }

  protected onFilterChange(filter: ListFilter): void {
    this.applyFilter(filter as TvShowFilter);
  }

  /** Saves under the entered name: updates a chosen saved filter, otherwise creates a new one. */
  protected saveFilter(): void {
    const name = this.filterName().trim();
    if (!name) {
      this.notify(this.transloco.translate('tvshows.list.filterNameRequired'));
      return;
    }
    const definition = completeConditions(this.filter());
    const saved = this.selectedSaved();
    const request =
      saved && saved.name.toLowerCase() === name.toLowerCase()
        ? this.savedFilterService.update(saved.id, 'TV_SHOW', name, definition)
        : this.savedFilterService.create('TV_SHOW', name, definition);
    request.subscribe({
      next: (stored) => {
        this.notify(this.transloco.translate('tvshows.list.filterSaved', { name: stored.name }));
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
      !this.confirm(
        this.transloco.translate('tvshows.list.confirmDeleteFilter', { name: saved.name }),
      )
    ) {
      return;
    }
    this.savedFilterService.delete(saved.id).subscribe({
      next: () => {
        this.notify(this.transloco.translate('tvshows.list.filterDeleted', { name: saved.name }));
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

  private applyFilter(filter: TvShowFilter): void {
    this.filter.set(filter);
    this.pageIndex.set(0);
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
