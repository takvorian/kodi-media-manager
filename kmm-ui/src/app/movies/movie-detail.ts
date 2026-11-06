import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TranslocoDatePipe } from '@jsverse/transloco-locale';
import { EMPTY, filter, finalize, map, of, switchMap } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import {
  KodiPlaybackResult,
  kodiPlaybackMessage,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
} from '../kodi/kodi';
import { KodiService } from '../kodi/kodi.service';
import { MediaInfoService } from '../media-info/media-info.service';
import { SettingsService } from '../settings/settings.service';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { CONFIRM } from '../shared/confirm';
import { batchSummary } from '../filters/batch';
import { LanguageSelect, languageChangeQuestion } from '../shared/language-select';
import { problemMessage } from '../shared/problem-message';
import { StreamDetails, StreamDetailsView } from '../shared/stream-details';
import { ThemeEditor } from '../shared/theme-editor';
import { TrailerEditor } from '../shared/trailer-editor';
import { MovieDetails } from './movie';
import { MovieArtwork } from './movie-artwork';
import { MOVIE_BATCH_JOB_KEY, movieScrapeJobKey } from './movie-batch';
import {
  MAX_YEAR,
  MIN_YEAR,
  createMovieMetadataForm,
  toFormValue,
  toMetadata,
} from './movie-metadata-form';
import { MovieOnlineSearch, ScrapedProposal } from './movie-online-search';
import { MovieSupplementView } from './movie-supplement';
import { MOVIES_URL, MovieService } from './movie.service';
import { MovieRenamePlan, renameConfirmation } from './movie-rename';
import { MovieRenameService } from './movie-rename.service';
import { localize } from '../i18n/localized-text';
import { CollectionReference } from './scraper';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 5000;

/** Named arguments of a translated message. */
type TranslationParams = Record<string, unknown>;

@Component({
  selector: 'app-movie-detail',
  imports: [
    HelpLink,
    LanguageSelect,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MovieArtwork,
    MovieOnlineSearch,
    MovieSupplementView,
    ScrapeProgress,
    ScrapeReport,
    StreamDetailsView,
    ThemeEditor,
    TrailerEditor,
    TranslocoDatePipe,
    TranslocoPipe,
  ],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.scss',
})
export class MovieDetail {
  private readonly movieService = inject(MovieService);
  private readonly kodiService = inject(KodiService);
  private readonly mediaInfoService = inject(MediaInfoService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly renameService = inject(MovieRenameService);
  private readonly settingsService = inject(SettingsService);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);
  private readonly transloco = inject(TranslocoService);

  /** Movie ID from the route {@code /movies/:id}. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly minYear = MIN_YEAR;
  protected readonly maxYear = MAX_YEAR;
  protected readonly form = createMovieMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly searchingOnline = signal(false);
  protected readonly refreshingKodi = signal(false);
  protected readonly importingPlayback = signal(false);
  protected readonly probingStreams = signal(false);
  protected readonly renaming = signal(false);
  /** Keeps running when the user leaves the page and comes back. */
  protected readonly scrapeJob = computed(() =>
    this.jobStore.runningJob(movieScrapeJobKey(this.id())),
  );
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Counts finished runs; theme music and artwork load by themselves and are rebuilt with it. */
  protected readonly scrapeRuns = signal(0);
  private readonly changingLanguage = signal(false);
  /** The language change of this view; the form is reloaded afterwards. */
  protected readonly languageJob = computed(() =>
    this.changingLanguage() ? this.jobStore.runningJob(MOVIE_BATCH_JOB_KEY) : undefined,
  );
  /** Whether the form holds a result of the online search; the technical details are read after saving. */
  private readonly adoptedProposal = signal(false);
  /** Collection of the adopted result; after saving the movie is assigned to its set, if configured. */
  private readonly adoptedCollection = signal<CollectionReference | null>(null);
  protected readonly trailerEndpoint = computed(() => `${MOVIES_URL}/${this.id()}/trailer`);
  protected readonly themeEndpoint = computed(() => `${MOVIES_URL}/${this.id()}/theme`);
  protected readonly movie = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.movieService.findMovie(params),
  });

  /** A signal of its own, so changes outside the metadata (watched status) do not reset the form. */
  private readonly metadata = computed(() =>
    this.movie.hasValue() ? this.movie.value().metadata : null,
  );

  constructor() {
    this.jobStore.connect();
    effect(() => {
      const metadata = this.metadata();
      if (metadata) {
        this.form.reset(toFormValue(metadata));
      }
    });
    // The form is reloaded at the end; input made during the run would be lost.
    effect(() => {
      if (this.scrapeJob() || this.languageJob()) {
        this.form.disable();
      } else {
        this.form.enable();
      }
    });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === MOVIE_BATCH_JOB_KEY && this.changingLanguage()),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.changingLanguage.set(false);
        this.notify(batchSummary(job));
        this.movie.reload();
      });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === movieScrapeJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.scrapeReport.set(job);
        this.scrapeRuns.update((runs) => runs + 1);
        this.movie.reload();
      });
  }

  /** Adds everything missing like scraping a source does, only for this movie. */
  protected scrape(): void {
    if (this.form.dirty) {
      this.notifyTranslated('movies.actions.saveFirst');
      return;
    }
    this.searchingOnline.set(false);
    this.scrapeReport.set(null);
    this.movieService.autoScrape(this.id()).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Asks whether the texts are fetched again in the new language; the language changes either way. */
  protected changeLanguage(language: string | null): void {
    if (this.form.dirty) {
      this.notifyTranslated('movies.actions.saveFirst');
      return;
    }
    const texts = this.transloco.translate('movies.language.textsOfMovie');
    const refetchTexts = this.confirm(languageChangeQuestion(language, texts));
    this.changingLanguage.set(true);
    this.movieService.changeLanguage([this.id()], language, refetchTexts).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => {
        this.changingLanguage.set(false);
        this.notify(problemMessage(error));
      },
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
        this.notifyTranslated('movies.detail.savedTo', { path: details.nfo.path });
        if (this.adoptedProposal()) {
          this.adoptedProposal.set(false);
          this.assignCollectionThenProbe(this.adoptedCollection());
          this.adoptedCollection.set(null);
        }
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

  /** Puts a proposal into the form; it is only saved with “Save”. */
  protected adoptProposal(proposal: ScrapedProposal): void {
    this.form.patchValue(toFormValue(proposal.metadata));
    this.form.markAsDirty();
    this.adoptedProposal.set(true);
    this.adoptedCollection.set(proposal.collection);
    this.searchingOnline.set(false);
    this.notifyTranslated('movies.detail.adopted', { scraper: proposal.scraperName });
  }

  /** Makes Kodi read the movie's NFO and artwork again, independently of the real-time sync. */
  protected refreshInKodi(): void {
    this.refreshingKodi.set(true);
    this.kodiService.refreshMovie(this.id()).subscribe({
      next: (results) => {
        this.refreshingKodi.set(false);
        this.snackBar.open(kodiSyncSummary(results), 'OK', kodiSyncSnackBarConfig(results));
      },
      error: (error: unknown) => {
        this.refreshingKodi.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /** Fetches play count and last playback from Kodi and writes them to the NFO and the library. */
  protected importPlayback(): void {
    this.importingPlayback.set(true);
    this.kodiService.importMoviePlayback(this.id()).subscribe({
      next: (result) => {
        this.importingPlayback.set(false);
        this.showPlayback(result);
        this.notify(kodiPlaybackMessage(result));
      },
      error: (error: unknown) => {
        this.importingPlayback.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /**
   * Renames folder and files following the patterns from the settings; the new names are shown for
   * confirmation first. Unsaved changes must be saved before, because the names are built from the saved data.
   */
  protected renameMovie(): void {
    if (this.form.dirty) {
      this.notifyTranslated('movies.actions.saveFirst');
      return;
    }
    this.renaming.set(true);
    this.settingsService
      .load()
      .pipe(
        map((settings) => ({
          folder: settings.rename.movieFolder,
          file: settings.rename.movieFile,
        })),
        switchMap((patterns) =>
          this.renameService
            .previewMovie(this.id(), patterns)
            .pipe(map((plan) => ({ patterns, plan }))),
        ),
        switchMap(({ patterns, plan }) => {
          if (plan.status !== 'RENAME') {
            return of(plan);
          }
          return this.confirm(renameConfirmation(plan))
            ? this.renameService.renameMovie(this.id(), patterns)
            : EMPTY;
        }),
        finalize(() => this.renaming.set(false)),
      )
      .subscribe({
        next: (plan) => this.showRenamed(plan),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  /** Reads codecs, resolution and tracks with ffprobe and writes them to the NFO and the library. */
  protected probeStreams(): void {
    this.probingStreams.set(true);
    this.mediaInfoService.probeMovie(this.id()).subscribe({
      next: (streams) => {
        this.probingStreams.set(false);
        this.showStreams(streams);
        this.notifyTranslated('movies.detail.streamsRead');
      },
      error: (error: unknown) => {
        this.probingStreams.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    this.adoptedProposal.set(false);
    this.adoptedCollection.set(null);
    if (this.movie.hasValue()) {
      this.showInForm(this.movie.value());
    }
  }

  /**
   * Like after automatic scraping, unless switched off in the settings. One after the other, because both
   * write the NFO.
   */
  private assignCollectionThenProbe(collection: CollectionReference | null): void {
    if (!collection) {
      this.probeStreamsAfterScrape();
      return;
    }
    this.movieService.assignCollection(this.id(), collection).subscribe({
      next: (result) => {
        if (result.assigned) {
          this.notifyTranslated('movies.detail.assignedToSet', { name: collection.name });
        }
        this.probeStreamsAfterScrape();
      },
      error: (error: unknown) => {
        this.notify(problemMessage(error));
        this.probeStreamsAfterScrape();
      },
    });
  }

  private probeStreamsAfterScrape(): void {
    this.probingStreams.set(true);
    this.mediaInfoService.probeMovieAfterScrape(this.id()).subscribe({
      next: (streams) => {
        this.probingStreams.set(false);
        if (streams) {
          this.showStreams(streams);
          this.notifyTranslated('movies.detail.savedAndStreamsRead');
        }
      },
      error: (error: unknown) => {
        this.probingStreams.set(false);
        this.notifyTranslated('movies.detail.savedButStreamsFailed', {
          problem: problemMessage(error),
        });
      },
    });
  }

  private showInForm(details: MovieDetails): void {
    this.form.reset(toFormValue(details.metadata));
  }

  private showPlayback(result: KodiPlaybackResult): void {
    this.movie.update(
      (details) =>
        details && {
          ...details,
          supplement: {
            ...details.supplement,
            playCount: result.playCount,
            lastPlayed: result.lastPlayedAt,
            userRating: result.userRating,
          },
        },
    );
  }

  /** Takes over only the trailer, so unsaved changes in the form are kept. */
  protected showTrailer(trailer: string | null): void {
    this.movie.update((details) => details && { ...details, trailer });
  }

  private showStreams(streams: StreamDetails): void {
    this.movie.update((details) => details && { ...details, streams });
  }

  private showRenamed(plan: MovieRenamePlan): void {
    switch (plan.status) {
      case 'RENAME':
        this.notifyTranslated('movies.detail.renamed', { title: plan.title });
        this.movie.reload();
        break;
      case 'UNCHANGED':
        this.notifyTranslated('movies.detail.alreadyNamed', { title: plan.title });
        break;
      default:
        this.notifyTranslated('movies.detail.notRenamed', {
          title: plan.title,
          reason: localize(plan.message),
        });
    }
  }

  private notifyTranslated(key: string, params?: TranslationParams): void {
    this.notify(this.transloco.translate(key, params));
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
