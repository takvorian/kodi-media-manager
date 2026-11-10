import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService, translate } from '@jsverse/transloco';
import { TranslocoDatePipe } from '@jsverse/transloco-locale';
import { EMPTY, Observable, filter, finalize, map, of, switchMap } from 'rxjs';

import { Job, unsuccessfulJobSummary } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { KodiService } from '../kodi/kodi.service';
import {
  kodiJobResults,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
  kodiTvShowJobKey,
} from '../kodi/kodi';
import { ArtworkSource } from '../movies/artwork';
import { ArtworkGallery } from '../movies/artwork-gallery';
import { Attribution, ScraperInfo } from '../movies/scraper';
import {
  seasonStreamDetailsJobKey,
  showStreamDetailsSummary,
  streamDetailsSummary,
} from '../media-info/media-info';
import { MediaInfoService } from '../media-info/media-info.service';
import { SettingsService } from '../settings/settings.service';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { CONFIRM } from '../shared/confirm';
import { batchSummary } from '../filters/batch';
import { LanguageSelect, languageChangeQuestion } from '../shared/language-select';
import { problemMessage } from '../shared/problem-message';
import { RemovalPlan, RemovalService, isRemovalJob } from '../shared/removal';
import { ThemeEditor } from '../shared/theme-editor';
import { TrailerEditor } from '../shared/trailer-editor';
import { TvPeople } from './tv-people';
import { TvShowRenamePlan, tvRenameConfirmation } from './tv-rename';
import { TvRenameService } from './tv-rename.service';
import { defaultTvShowScraper } from './tv-scrapers';
import {
  EpisodeFillResult,
  EpisodeSummary,
  MissingEpisode,
  Season,
  TvShowDetails,
  TvShowMetadata,
  episodeCode,
  seasonLabel,
} from './tvshow';
import {
  MAX_YEAR,
  MIN_YEAR,
  createTvShowMetadataForm,
  toTvShowFormValue,
  toTvShowMetadata,
} from './tvshow-forms';
import { MEDIUM_DATE, SHORT_DATE_TIME } from './tv-date-formats';
import { TvShowArtworkService } from './tvshow-artwork.service';
import { TVSHOW_BATCH_JOB_KEY, tvShowScrapeJobKey } from './tvshow-batch';
import { TvShowOnlineSearch, TvShowScrapedProposal } from './tvshow-online-search';
import { TVSHOWS_URL, TvShowService } from './tvshow.service';
import { localize } from '../i18n/localized-text';
import { LocalizedPipe } from '../i18n/localized.pipe';

const SNACKBAR_DURATION_MS = 5000;

/** A season with its episodes for the overview. */
export interface SeasonWithEpisodes {
  readonly season: Season;
  readonly episodes: readonly EpisodeSummary[];
}

@Component({
  selector: 'app-tvshow-detail',
  imports: [
    LocalizedPipe,
    LanguageSelect,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    TvPeople,
    TvShowOnlineSearch,
    ArtworkGallery,
    ScrapeProgress,
    ScrapeReport,
    ThemeEditor,
    TrailerEditor,
    TranslocoDatePipe,
    TranslocoPipe,
  ],
  templateUrl: './tvshow-detail.html',
  styleUrl: './tvshow-detail.scss',
})
export class TvShowDetail {
  private readonly tvShowService = inject(TvShowService);
  private readonly mediaInfoService = inject(MediaInfoService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly artworkService = inject(TvShowArtworkService);
  private readonly kodiService = inject(KodiService);
  private readonly renameService = inject(TvRenameService);
  private readonly settingsService = inject(SettingsService);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);
  private readonly transloco = inject(TranslocoService);
  private readonly removal = inject(RemovalService);
  private readonly router = inject(Router);

  /** TV show ID from the route `/tvshows/:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly shortDateTime = SHORT_DATE_TIME;
  protected readonly mediumDate = MEDIUM_DATE;
  protected readonly minYear = MIN_YEAR;
  protected readonly maxYear = MAX_YEAR;
  protected readonly form = createTvShowMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly show = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.tvShowService.findShow(params),
  });
  /** Own signal, so changes outside the metadata (trailer) do not reset the form. */
  private readonly metadata = computed(() =>
    this.show.hasValue() ? this.show.value().metadata : null,
  );
  protected readonly trailerEndpoint = computed(() => `${TVSHOWS_URL}/${this.id()}/trailer`);
  protected readonly themeEndpoint = computed(() => `${TVSHOWS_URL}/${this.id()}/theme`);
  protected readonly seasons = computed<SeasonWithEpisodes[]>(() =>
    this.show.hasValue() ? groupBySeason(this.show.value()) : [],
  );
  protected readonly showArtwork = computed(() => this.artworkService.forShow(this.id(), null));
  protected readonly scrapers = rxResource({ stream: () => this.tvShowService.tvShowScrapers() });
  /** Attributions of the providers whose IDs the TV show carries (e.g. TheTVDB). */
  protected readonly attributions = computed<Attribution[]>(() =>
    this.show.hasValue() && this.scrapers.hasValue()
      ? attributionsFor(this.show.value().metadata, this.scrapers.value())
      : [],
  );
  protected readonly searchingOnline = signal(false);
  protected readonly renaming = signal(false);
  protected readonly removing = signal(false);
  /** Keeps running when leaving the page and coming back. */
  protected readonly scrapeJob = computed(() =>
    this.jobStore.runningJob(tvShowScrapeJobKey(this.id())),
  );
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Re-reading the technical details of a season; writes episode NFOs like scraping does. */
  protected readonly seasonStreamsJob = computed(() =>
    this.jobStore.runningJob(seasonStreamDetailsJobKey(this.id())),
  );
  /** Refreshing or scanning the TV show or one of its seasons in Kodi. */
  protected readonly kodiJob = computed(() =>
    this.jobStore.runningJob(kodiTvShowJobKey(this.id())),
  );
  /** Counts finished runs; theme music and artwork load by themselves and are rebuilt with it. */
  protected readonly scrapeRuns = signal(0);
  private readonly changingLanguage = signal(false);
  /** The language change of this view; the form is reloaded afterwards. */
  protected readonly languageJob = computed(() =>
    this.changingLanguage() ? this.jobStore.runningJob(TVSHOW_BATCH_JOB_KEY) : undefined,
  );
  /** Adopted match whose episode data is completed on the next save. */
  protected readonly pendingFill = signal<TvShowScrapedProposal | null>(null);
  protected readonly missingEpisodes = signal<readonly MissingEpisode[] | null>(null);
  protected readonly loadingMissing = signal(false);

  constructor() {
    this.jobStore.connect();
    effect(() => {
      const metadata = this.metadata();
      if (metadata) {
        this.form.reset(toTvShowFormValue(metadata));
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
        filter((job) => job.key === TVSHOW_BATCH_JOB_KEY && this.changingLanguage()),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.changingLanguage.set(false);
        this.notify(batchSummary(job));
        this.show.reload();
      });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === seasonStreamDetailsJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.notify(streamDetailsSummary(job)));
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === kodiTvShowJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.showKodiResults(job));
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === tvShowScrapeJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.scrapeReport.set(job);
        this.scrapeRuns.update((runs) => runs + 1);
        this.show.reload();
      });
    // Removed seasons and episodes, also of an episode page that came back here.
    this.jobStore.completed
      .pipe(filter(isRemovalJob), takeUntilDestroyed())
      .subscribe(() => this.show.reload());
  }

  /** Removes all episodes of the season after asking how; the TV show reloads when the job is done. */
  protected removeSeason(group: SeasonWithEpisodes): void {
    const season = group.season.number;
    this.startRemoval(
      this.transloco.translate('removal.title.season', {
        season,
        title: this.show.value()?.metadata.title ?? '',
      }),
      this.tvShowService.seasonRemovalPlan(this.id(), season),
      (deleteFiles) => this.tvShowService.removeSeason(this.id(), season, deleteFiles),
    ).subscribe();
  }

  /** Removes the TV show with all episodes after asking how, then goes back to the list. */
  protected removeShow(): void {
    this.startRemoval(
      this.transloco.translate('removal.title.tvShow', {
        title: this.show.value()?.metadata.title ?? '',
      }),
      this.tvShowService.showRemovalPlan(this.id()),
      (deleteFiles) => this.tvShowService.removeShow(this.id(), deleteFiles),
    ).subscribe(() => void this.router.navigate(['/tvshows']));
  }

  private startRemoval(
    title: string,
    plan: Observable<RemovalPlan>,
    remove: (deleteFiles: boolean) => Observable<Job>,
  ): Observable<Job> {
    this.removing.set(true);
    return this.removal
      .remove({ title, helpChapter: 'tv-shows', plan, remove })
      .pipe(finalize(() => this.removing.set(false)));
  }

  /** Adds everything missing like scraping a source does, only for this TV show. */
  protected scrape(): void {
    this.startScrape(() => this.tvShowService.autoScrape(this.id()));
  }

  /**
   * Scrapes only one season: empty fields and missing artwork, with `overwrite` after confirmation also new titles and
   * texts for its episodes.
   */
  protected scrapeSeason(group: SeasonWithEpisodes, overwrite: boolean): void {
    const season = group.season;
    this.startScrape(
      () => this.tvShowService.autoScrapeSeason(this.id(), season.number, overwrite),
      overwrite
        ? this.transloco.translate('tvshows.detail.confirmRefetchSeason', {
            count: group.episodes.length,
            season: this.label(season),
          })
        : null,
    );
  }

  /** Re-reads codecs, resolution and tracks of all episodes of the season, as a job. */
  protected probeSeason(group: SeasonWithEpisodes): void {
    this.mediaInfoService.probeSeason(this.id(), group.season.number).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private startScrape(start: () => Observable<Job>, confirmation: string | null = null): void {
    if (this.form.dirty) {
      this.notify(this.transloco.translate('tvshows.saveFirst'));
      return;
    }
    if (confirmation && !this.confirm(confirmation)) {
      return;
    }
    this.searchingOnline.set(false);
    this.scrapeReport.set(null);
    start().subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Takes over only the trailer, so unsaved changes in the form stay. */
  protected showTrailer(trailer: string | null): void {
    this.show.update((details) => details && { ...details, trailer });
  }

  protected seasonArtwork(season: Season): ArtworkSource {
    return this.artworkService.forShow(this.id(), season.number);
  }

  protected code(episode: EpisodeSummary): string {
    return episodeCode(episode.season, episode.episode);
  }

  protected label(season: Season): string {
    return seasonLabel(season);
  }

  protected missingCode(episode: MissingEpisode): string {
    return episodeCode(episode.season, episode.episode);
  }

  /** Makes Kodi re-read the TV show and all its episodes, independent of the real-time sync. */
  protected refreshInKodi(): void {
    this.startKodiJob(this.kodiService.refreshTvShow(this.id()));
  }

  /** Makes Kodi re-read the episodes of the season. */
  protected refreshSeasonInKodi(group: SeasonWithEpisodes): void {
    this.startKodiJob(this.kodiService.refreshSeason(this.id(), group.season.number));
  }

  /** Kodi looks for new episodes in the TV show folder and then removes entries whose files are gone. */
  protected scanInKodi(): void {
    this.startKodiJob(this.kodiService.scanTvShow(this.id()));
  }

  /** Like `scanInKodi` for the season folder. */
  protected scanSeasonInKodi(group: SeasonWithEpisodes): void {
    this.startKodiJob(this.kodiService.scanSeason(this.id(), group.season.number));
  }

  private startKodiJob(start: Observable<Job>): void {
    start.subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private showKodiResults(job: Job): void {
    if (job.status !== 'SUCCEEDED') {
      this.notify(unsuccessfulJobSummary(job));
      return;
    }
    const results = kodiJobResults(job);
    this.snackBar.open(kodiSyncSummary(results), 'OK', kodiSyncSnackBarConfig(results));
  }

  protected toggleOnlineSearch(): void {
    this.searchingOnline.update((open) => !open);
  }

  protected adoptProposal(scraped: TvShowScrapedProposal): void {
    this.form.patchValue(toTvShowFormValue(scraped.proposal.metadata));
    this.form.markAsDirty();
    this.searchingOnline.set(false);
    this.pendingFill.set(scraped);
    this.notify(
      this.transloco.translate('tvshows.detail.adopted', {
        scraper: scraped.scraperName,
        count: scraped.proposal.matchedEpisodes,
      }),
    );
  }

  /** Asks the preferred TV show scraper which episodes exist without a file. */
  protected showMissingEpisodes(): void {
    const scraper = this.scrapers.hasValue()
      ? defaultTvShowScraper(this.scrapers.value())
      : undefined;
    if (!scraper) {
      this.notify(this.transloco.translate('tvshows.noScraper'));
      return;
    }
    this.loadingMissing.set(true);
    this.tvShowService.missingEpisodes(this.id(), scraper.id).subscribe({
      next: (episodes) => {
        this.loadingMissing.set(false);
        this.missingEpisodes.set(episodes);
      },
      error: (error: unknown) => {
        this.loadingMissing.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /** Asks whether the texts are refetched in the new language; the language changes in any case. */
  protected changeLanguage(language: string | null): void {
    if (this.form.dirty) {
      this.notify(this.transloco.translate('tvshows.saveFirst'));
      return;
    }
    const refetchTexts = this.confirm(
      languageChangeQuestion(language, this.transloco.translate('tvshows.languageTexts')),
    );
    this.changingLanguage.set(true);
    this.tvShowService.changeLanguage([this.id()], language, refetchTexts).subscribe({
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
    this.tvShowService.updateShow(this.id(), toTvShowMetadata(this.form)).subscribe({
      next: (details) => {
        const pending = this.pendingFill();
        if (pending) {
          this.fillEpisodes(pending);
          return;
        }
        this.saving.set(false);
        this.show.set(details);
        this.notify(this.transloco.translate('tvshows.savedTo', { path: details.nfo.path }));
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    this.pendingFill.set(null);
    if (this.show.hasValue()) {
      this.form.reset(toTvShowFormValue(this.show.value().metadata));
    }
  }

  private fillEpisodes(pending: TvShowScrapedProposal): void {
    this.tvShowService.fillEpisodes(this.id(), pending.scraperId, pending.providerId).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.pendingFill.set(null);
        this.show.reload();
        this.notify(fillSummary(pending.scraperName, result));
        this.probeStreamsAfterScrape();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.show.reload();
        this.notify(
          this.transloco.translate('tvshows.detail.fillFailed', { problem: problemMessage(error) }),
        );
      },
    });
  }

  /** Reads the episodes without technical details, as after automatic scraping. */
  private probeStreamsAfterScrape(): void {
    this.mediaInfoService.probeShowAfterScrape(this.id()).subscribe({
      next: (result) => {
        const summary = showStreamDetailsSummary(result);
        if (summary) {
          this.notify(summary);
        }
      },
      error: (error: unknown) =>
        this.notify(
          this.transloco.translate('tvshows.detail.probeFailed', {
            problem: problemMessage(error),
          }),
        ),
    });
  }

  /**
   * Renames the show folder, season folders and episode files by the patterns from the settings; before that the
   * changes are summarized for confirmation. Unsaved changes have to be saved first.
   */
  protected renameShow(): void {
    if (this.form.dirty) {
      this.notify(this.transloco.translate('tvshows.saveFirst'));
      return;
    }
    this.renaming.set(true);
    this.settingsService
      .load()
      .pipe(
        map((settings) => ({
          showFolder: settings.rename.tvShowFolder,
          seasonFolder: settings.rename.seasonFolder,
          episodeFile: settings.rename.episodeFile,
        })),
        switchMap((patterns) =>
          this.renameService
            .previewShow(this.id(), patterns)
            .pipe(map((plan) => ({ patterns, plan }))),
        ),
        switchMap(({ patterns, plan }) => {
          if (plan.status !== 'RENAME') {
            return of(plan);
          }
          return this.confirm(tvRenameConfirmation(plan))
            ? this.renameService.renameShow(this.id(), patterns)
            : EMPTY;
        }),
        finalize(() => this.renaming.set(false)),
      )
      .subscribe({
        next: (plan) => this.showRenamed(plan),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  private showRenamed(plan: TvShowRenamePlan): void {
    switch (plan.status) {
      case 'RENAME':
        this.notify(this.transloco.translate('tvshows.detail.renamed', { title: plan.title }));
        this.show.reload();
        break;
      case 'UNCHANGED':
        this.notify(this.transloco.translate('tvshows.detail.alreadyNamed', { title: plan.title }));
        break;
      default:
        this.notify(
          this.transloco.translate('tvshows.detail.notRenamed', {
            title: plan.title,
            message: localize(plan.message),
          }),
        );
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function fillSummary(scraperName: string, result: EpisodeFillResult): string {
  const filled = translate('tvshows.detail.filled', {
    count: result.updated,
    scraper: scraperName,
  });
  return result.failedFiles.length > 0
    ? `${filled} ${translate('tvshows.detail.nfosFailed', { count: result.failedFiles.length })}`
    : filled;
}

/** TheTVDB requires the attribution wherever its data is used; the TV show then carries a TVDB ID. */
function attributionsFor(
  metadata: TvShowMetadata,
  scrapers: readonly ScraperInfo[],
): Attribution[] {
  const ids: Record<string, string | null> = { tvdb: metadata.tvdbId, tmdb: metadata.tmdbId };
  return scrapers.flatMap((scraper) =>
    scraper.attribution && ids[scraper.id] ? [scraper.attribution] : [],
  );
}

function groupBySeason(details: TvShowDetails): SeasonWithEpisodes[] {
  return details.seasons
    .filter((season) => season.episodeCount > 0)
    .map((season) => ({
      season,
      episodes: details.episodes.filter((episode) => episode.season === season.number),
    }));
}
