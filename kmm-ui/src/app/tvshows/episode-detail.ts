import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TranslocoDatePipe } from '@jsverse/transloco-locale';

import {
  KodiPlaybackResult,
  kodiPlaybackMessage,
  kodiSyncSnackBarConfig,
  kodiSyncSummary,
} from '../kodi/kodi';
import { KodiService } from '../kodi/kodi.service';
import { ArtworkGallery } from '../movies/artwork-gallery';
import { MediaInfoService } from '../media-info/media-info.service';
import { problemMessage } from '../shared/problem-message';
import { StreamDetails, StreamDetailsView } from '../shared/stream-details';
import { TvPeople } from './tv-people';
import { EpisodeDetails, episodeCode } from './tvshow';
import { createEpisodeMetadataForm, toEpisodeFormValue, toEpisodeMetadata } from './tvshow-forms';
import { SHORT_DATE_TIME } from './tv-date-formats';
import { TvShowArtworkService } from './tvshow-artwork.service';
import { TvShowService } from './tvshow.service';

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

  /** Episode ID from the route `/episodes/:id`. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly shortDateTime = SHORT_DATE_TIME;
  protected readonly form = createEpisodeMetadataForm(inject(NonNullableFormBuilder));
  protected readonly saving = signal(false);
  protected readonly refreshingKodi = signal(false);
  protected readonly importingPlayback = signal(false);
  protected readonly probingStreams = signal(false);
  protected readonly artwork = computed(() => this.artworkService.forEpisode(this.id()));
  protected readonly episode = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.tvShowService.findEpisode(params),
  });

  /** Own signal, so changes outside the metadata (watched status) do not reset the form. */
  private readonly metadata = computed(() =>
    this.episode.hasValue() ? this.episode.value().metadata : null,
  );

  constructor() {
    effect(() => {
      const metadata = this.metadata();
      if (metadata) {
        this.form.reset(toEpisodeFormValue(metadata));
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
        this.notify(this.transloco.translate('tvshows.savedTo', { path: details.nfo.path }));
      },
      error: (error: unknown) => {
        this.saving.set(false);
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
