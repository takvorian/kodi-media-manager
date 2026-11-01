import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { EMPTY, filter, finalize, map, of, switchMap } from 'rxjs';

import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { KodiService } from '../kodi/kodi.service';
import { kodiSyncSummary } from '../kodi/kodi';
import { ArtworkSource } from '../movies/artwork';
import { ArtworkGallery } from '../movies/artwork-gallery';
import { Attribution, ScraperInfo } from '../movies/scraper';
import { showStreamDetailsSummary } from '../media-info/media-info';
import { MediaInfoService } from '../media-info/media-info.service';
import { SettingsService } from '../settings/settings.service';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
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
import { TvShowArtworkService } from './tvshow-artwork.service';
import { tvShowScrapeJobKey } from './tvshow-batch';
import { TvShowOnlineSearch, TvShowScrapedProposal } from './tvshow-online-search';
import { TVSHOWS_URL, TvShowService } from './tvshow.service';

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
    TvShowOnlineSearch,
    ArtworkGallery,
    ScrapeProgress,
    ScrapeReport,
    ThemeEditor,
    TrailerEditor,
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
  /** Eigenes Signal, damit Änderungen außerhalb der Metadaten (Trailer) das Formular nicht zurücksetzen. */
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
  /** Hinweise der Anbieter, deren IDs die Serie trägt (z. B. TheTVDB). */
  protected readonly attributions = computed<Attribution[]>(() =>
    this.show.hasValue() && this.scrapers.hasValue()
      ? attributionsFor(this.show.value().metadata, this.scrapers.value())
      : [],
  );
  protected readonly searchingOnline = signal(false);
  protected readonly refreshingKodi = signal(false);
  protected readonly renaming = signal(false);
  /** Läuft auch weiter, wenn man die Seite verlässt und zurückkehrt. */
  protected readonly scrapeJob = computed(() =>
    this.jobStore.runningJob(tvShowScrapeJobKey(this.id())),
  );
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Zählt beendete Läufe; Titelmusik und Bilder laden selbst und werden damit neu aufgebaut. */
  protected readonly scrapeRuns = signal(0);
  /** Übernommener Treffer, dessen Episodendaten beim nächsten Speichern ergänzt werden. */
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
    // Am Ende wird das Formular neu geladen; Eingaben während des Laufs gingen dabei verloren.
    effect(() => {
      if (this.scrapeJob()) {
        this.form.disable();
      } else {
        this.form.enable();
      }
    });
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
  }

  /** Ergänzt alles Fehlende wie das Scrapen einer Quelle, nur für diese Serie. */
  protected scrape(): void {
    if (this.form.dirty) {
      this.notify('Bitte die Änderungen zuerst speichern.');
      return;
    }
    this.searchingOnline.set(false);
    this.scrapeReport.set(null);
    this.tvShowService.autoScrape(this.id()).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Übernimmt nur den Trailer, damit ungespeicherte Änderungen im Formular bleiben. */
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

  /** Lässt Kodi NFO und Bilder der Serie neu einlesen, unabhängig vom Echtzeit-Sync. */
  protected refreshInKodi(): void {
    this.refreshingKodi.set(true);
    this.kodiService.refreshTvShow(this.id()).subscribe({
      next: (results) => {
        this.refreshingKodi.set(false);
        this.notify(kodiSyncSummary(results));
      },
      error: (error: unknown) => {
        this.refreshingKodi.set(false);
        this.notify(problemMessage(error));
      },
    });
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
      `Daten von ${scraped.scraperName} übernommen – bitte prüfen und speichern. ` +
        `Beim Speichern werden ${scraped.proposal.matchedEpisodes} Episoden ergänzt.`,
    );
  }

  /** Fragt beim bevorzugten Serien-Scraper, welche Episoden ohne Datei es gibt. */
  protected showMissingEpisodes(): void {
    const scraper = this.scrapers.hasValue()
      ? defaultTvShowScraper(this.scrapers.value())
      : undefined;
    if (!scraper) {
      this.notify('Es ist kein Serien-Scraper installiert oder aktiv.');
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
        this.notify(`Gespeichert und in ${details.nfo.path} geschrieben.`);
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
        this.notify(`Serie gespeichert, Episoden nicht ergänzt: ${problemMessage(error)}`);
      },
    });
  }

  /** Liest die Episoden ohne technische Angaben aus, wie nach dem automatischen Scrapen. */
  private probeStreamsAfterScrape(): void {
    this.mediaInfoService.probeShowAfterScrape(this.id()).subscribe({
      next: (result) => {
        const summary = showStreamDetailsSummary(result);
        if (summary) {
          this.notify(summary);
        }
      },
      error: (error: unknown) =>
        this.notify(`Technische Angaben nicht ausgelesen: ${problemMessage(error)}`),
    });
  }

  /**
   * Benennt Serienordner, Staffelordner und Episodendateien nach den Mustern aus den Einstellungen um; vorher
   * werden die Änderungen zur Bestätigung zusammengefasst. Ungespeicherte Änderungen müssen erst gespeichert werden.
   */
  protected renameShow(): void {
    if (this.form.dirty) {
      this.notify('Bitte die Änderungen zuerst speichern.');
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
        this.notify(`„${plan.title}“ umbenannt.`);
        this.show.reload();
        break;
      case 'UNCHANGED':
        this.notify(`„${plan.title}“ heißt schon so.`);
        break;
      default:
        this.notify(`„${plan.title}“ nicht umbenannt: ${plan.message}`);
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function fillSummary(scraperName: string, result: EpisodeFillResult): string {
  const failed =
    result.failedFiles.length > 0
      ? ` ${result.failedFiles.length} NFOs konnten nicht geschrieben werden.`
      : '';
  return `Gespeichert; ${result.updated} Episoden mit Daten von ${scraperName} ergänzt.${failed}`;
}

/** TheTVDB verlangt den Hinweis, wo seine Daten genutzt werden; die Serie trägt dann eine TVDB-ID. */
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
