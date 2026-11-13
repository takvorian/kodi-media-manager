import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TranslocoDatePipe } from '@jsverse/transloco-locale';
import { filter, finalize } from 'rxjs';

import {
  KodiPlaybackResult,
  kodiPlaybackMessage,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
} from '../kodi/kodi';
import { Job } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { KodiService } from '../kodi/kodi.service';
import { ArtworkGallery } from '../movies/artwork-gallery';
import { MediaInfoService } from '../media-info/media-info.service';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { RemovalService } from '../shared/removal';
import { StreamDetails, StreamDetailsView } from '../shared/stream-details';
import { ScrapeProgress } from '../sources/scrape-progress';
import { ScrapeReport } from '../sources/scrape-report';
import { TvPeople } from './tv-people';
import { EpisodeDetails, episodeCode } from './tvshow';
import { createEpisodeMetadataForm, toEpisodeFormValue, toEpisodeMetadata } from './tvshow-forms';
import { SHORT_DATE_TIME } from './tv-date-formats';
import { TvShowArtworkService } from './tvshow-artwork.service';
import { tvShowScrapeJobKey } from './tvshow-batch';
import { TvShowService } from './tvshow.service';
import { ListReturn } from '../shared/list-state';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-episode-detail',
  imports: [
    ArtworkGallery,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ScrapeProgress,
    ScrapeReport,
    StreamDetailsView,
    TranslocoDatePipe,
    TranslocoPipe,
    TvPeople,
  ],
  templateUrl: './episode-detail.html',
  styleUrl: './tvshow-detail.scss',
})
export class EpisodeDetail {
  private readonly tvShowService = inject(TvShowService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly artworkService = inject(TvShowArtworkService);
  private readonly kodiService = inject(KodiService);
  private readonly mediaInfoService = inject(MediaInfoService);
  private readonly transloco = inject(TranslocoService);
  private readonly removal = inject(RemovalService);
  private readonly router = inject(Router);
  private readonly confirm = inject(CONFIRM);
  private readonly jobStore = inject(JobStore);
  /** Back to the list as it was left: page, filter and sorting. */
  protected readonly listParams = inject(ListReturn).queryParams('tvshows');

  /** Episode ID from the route `/episodes/:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly shortDateTime = SHORT_DATE_TIME;
  protected readonly form = createEpisodeMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly refreshingKodi = signal(false);
  protected readonly importingPlayback = signal(false);
  protected readonly probingStreams = signal(false);
  protected readonly removing = signal(false);
  protected readonly artwork = computed(() => this.artworkService.forEpisode(this.id()));
  protected readonly episode = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.tvShowService.findEpisode(params),
  });

  /** Scraping shares the job of the TV show, so the season or the whole TV show blocks it, too. */
  protected readonly scrapeJob = computed(() =>
    this.episode.hasValue()
      ? this.jobStore.runningJob(tvShowScrapeJobKey(this.episode.value().tvShowId))
      : undefined,
  );
  protected readonly scrapeReport = signal<Job | null>(null);
  /** Counts finished scrapes so the artwork is loaded again. */
  protected readonly scrapeRuns = signal(0);
  /** Only a scrape started here reloads the episode; the TV show may have been scraped in another tab. */
  private readonly scraping = signal(false);

  /** Own signal, so changes outside the metadata (watched status) do not reset the form. */
  private readonly metadata = computed(() =>
    this.episode.hasValue() ? this.episode.value().metadata : null,
  );

  constructor() {
    this.jobStore.connect();
    effect(() => {
      const metadata = this.metadata();
      if (metadata) {
        this.form.reset(toEpisodeFormValue(metadata));
      }
    });
    // The form is reloaded at the end; input made during the run would be lost.
    effect(() => {
      if (this.scrapeJob()) {
        this.form.disable();
      } else {
        this.form.enable();
      }
    });
    this.jobStore.completed
      .pipe(
        filter(
          (job) =>
            this.scraping() &&
            this.episode.hasValue() &&
            job.key === tvShowScrapeJobKey(this.episode.value().tvShowId),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.scraping.set(false);
        this.scrapeReport.set(job);
        this.scrapeRuns.update((runs) => runs + 1);
        this.episode.reload();
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
        this.notify(this.transloco.translate('tvshows.savedTo', { path: details.nfo.path }));
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /**
   * Scrapes the episode like a season: empty fields and a missing still, with `overwrite` after confirmation also a
   * new title and texts.
   */
  protected scrape(details: EpisodeDetails, overwrite: boolean): void {
    if (this.form.dirty) {
      this.notify(this.transloco.translate('tvshows.saveFirst'));
      return;
    }
    if (
      overwrite &&
      !this.confirm(
        this.transloco.translate('tvshows.episode.confirmRefetch', { code: this.code(details) }),
      )
    ) {
      return;
    }
    this.scrapeReport.set(null);
    this.scraping.set(true);
    this.tvShowService.autoScrapeEpisode(this.id(), overwrite).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => {
        this.scraping.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /** Reads codecs, resolution and tracks with ffprobe and writes them to the NFO and the library. */
  protected probeStreams(): void {
    this.probingStreams.set(true);
    this.mediaInfoService.probeEpisode(this.id()).subscribe({
      next: (streams) => {
        this.probingStreams.set(false);
        this.showStreams(streams);
        this.notify(this.transloco.translate('tvshows.episode.probed'));
      },
      error: (error: unknown) => {
        this.probingStreams.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    if (this.episode.hasValue()) {
      this.form.reset(toEpisodeFormValue(this.episode.value().metadata));
    }
  }

  /**
   * Removes the episode after asking how, with a multi-episode file all its episodes; then goes back to the TV show,
   * which reloads when the job is done.
   */
  protected remove(details: EpisodeDetails): void {
    this.removing.set(true);
    this.removal
      .remove({
        title: this.transloco.translate('removal.title.episode'),
        helpChapter: 'tv-shows',
        plan: this.tvShowService.episodeRemovalPlan(this.id()),
        remove: (deleteFiles) => this.tvShowService.removeEpisode(this.id(), deleteFiles),
      })
      .pipe(finalize(() => this.removing.set(false)))
      .subscribe(() => void this.router.navigate(['/tvshows', details.tvShowId]));
  }

  /** Makes Kodi re-read the NFO and thumbnail of the episode, independent of the real-time sync. */
  protected refreshInKodi(): void {
    this.refreshingKodi.set(true);
    this.kodiService.refreshEpisode(this.id()).subscribe({
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

  /** Gets the play count and last playback from Kodi and writes them to the NFO and the library. */
  protected importPlayback(): void {
    this.importingPlayback.set(true);
    this.kodiService.importEpisodePlayback(this.id()).subscribe({
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

  private showPlayback(result: KodiPlaybackResult): void {
    this.episode.update(
      (details) =>
        details && {
          ...details,
          extras: {
            ...details.extras,
            playCount: result.playCount,
            lastPlayed: result.lastPlayedAt,
          },
        },
    );
    // Set only the field: new metadata would reset the form including unsaved changes.
    this.form.controls.userRating.setValue(result.userRating);
  }

  private showStreams(streams: StreamDetails): void {
    this.episode.update(
      (details) => details && { ...details, extras: { ...details.extras, streams } },
    );
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
