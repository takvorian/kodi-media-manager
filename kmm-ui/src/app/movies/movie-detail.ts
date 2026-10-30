import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { EMPTY, finalize, map, of, switchMap } from 'rxjs';

import { KodiPlaybackResult, kodiSyncSummary } from '../kodi/kodi';
import { KodiService } from '../kodi/kodi.service';
import { MediaInfoService } from '../media-info/media-info.service';
import { SettingsService } from '../settings/settings.service';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { StreamDetails, StreamDetailsView } from '../shared/stream-details';
import { ThemeEditor } from '../shared/theme-editor';
import { TrailerEditor } from '../shared/trailer-editor';
import { MovieDetails } from './movie';
import { MovieArtwork } from './movie-artwork';
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
import { CollectionReference } from './scraper';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-movie-detail',
  imports: [
    HelpLink,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MovieArtwork,
    MovieOnlineSearch,
    MovieSupplementView,
    StreamDetailsView,
    ThemeEditor,
    TrailerEditor,
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

  /** Film-ID aus der Route {@code /movies/:id}. */
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
  /** Ob das Formular einen Treffer der Online-Suche enthält; nach dem Speichern werden die Angaben ausgelesen. */
  private readonly adoptedProposal = signal(false);
  /** Sammlung des übernommenen Treffers; nach dem Speichern wird der Film ihrem Set zugeordnet, wenn eingestellt. */
  private readonly adoptedCollection = signal<CollectionReference | null>(null);
  protected readonly trailerEndpoint = computed(() => `${MOVIES_URL}/${this.id()}/trailer`);
  protected readonly themeEndpoint = computed(() => `${MOVIES_URL}/${this.id()}/theme`);
  protected readonly movie = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.movieService.findMovie(params),
  });

  /** Eigenes Signal, damit Änderungen außerhalb der Metadaten (Gesehen-Status) das Formular nicht zurücksetzen. */
  private readonly metadata = computed(() =>
    this.movie.hasValue() ? this.movie.value().metadata : null,
  );

  constructor() {
    effect(() => {
      const metadata = this.metadata();
      if (metadata) {
        this.form.reset(toFormValue(metadata));
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

  /** Übernimmt einen Vorschlag ins Formular; gespeichert wird erst mit „Speichern“. */
  protected adoptProposal(proposal: ScrapedProposal): void {
    this.form.patchValue(toFormValue(proposal.metadata));
    this.form.markAsDirty();
    this.adoptedProposal.set(true);
    this.adoptedCollection.set(proposal.collection);
    this.searchingOnline.set(false);
    this.notify(`Daten von ${proposal.scraperName} übernommen – bitte prüfen und speichern.`);
  }

  /** Lässt Kodi NFO und Bilder des Films neu einlesen, unabhängig vom Echtzeit-Sync. */
  protected refreshInKodi(): void {
    this.refreshingKodi.set(true);
    this.kodiService.refreshMovie(this.id()).subscribe({
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

  /** Holt Wiedergaben und letzte Wiedergabe aus Kodi und schreibt sie in NFO und Bibliothek. */
  protected importPlayback(): void {
    this.importingPlayback.set(true);
    this.kodiService.importMoviePlayback(this.id()).subscribe({
      next: (result) => {
        this.importingPlayback.set(false);
        this.showPlayback(result);
        this.notify(`${result.host}: ${result.message}`);
      },
      error: (error: unknown) => {
        this.importingPlayback.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /**
   * Benennt Ordner und Dateien nach den Mustern aus den Einstellungen um; vorher werden die neuen Namen zur
   * Bestätigung gezeigt. Ungespeicherte Änderungen müssen erst gespeichert werden, denn die Namen entstehen aus
   * den gespeicherten Angaben.
   */
  protected renameMovie(): void {
    if (this.form.dirty) {
      this.notify('Bitte die Änderungen zuerst speichern.');
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

  /** Liest Codecs, Auflösung und Spuren mit ffprobe aus und schreibt sie in NFO und Bibliothek. */
  protected probeStreams(): void {
    this.probingStreams.set(true);
    this.mediaInfoService.probeMovie(this.id()).subscribe({
      next: (streams) => {
        this.probingStreams.set(false);
        this.showStreams(streams);
        this.notify('Technische Angaben ausgelesen.');
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

  /** Wie nach dem automatischen Scrapen, sofern in den Einstellungen nicht abgeschaltet. */
  /** Nacheinander, weil beides die NFO schreibt. */
  private assignCollectionThenProbe(collection: CollectionReference | null): void {
    if (!collection) {
      this.probeStreamsAfterScrape();
      return;
    }
    this.movieService.assignCollection(this.id(), collection).subscribe({
      next: (result) => {
        if (result.assigned) {
          this.notify(`Dem Filmset „${collection.name}“ zugeordnet.`);
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
          this.notify('Gespeichert; technische Angaben ausgelesen.');
        }
      },
      error: (error: unknown) => {
        this.probingStreams.set(false);
        this.notify(`Gespeichert, technische Angaben nicht ausgelesen: ${problemMessage(error)}`);
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

  /** Übernimmt nur den Trailer, damit ungespeicherte Änderungen im Formular bleiben. */
  protected showTrailer(trailer: string | null): void {
    this.movie.update((details) => details && { ...details, trailer });
  }

  private showStreams(streams: StreamDetails): void {
    this.movie.update((details) => details && { ...details, streams });
  }

  private showRenamed(plan: MovieRenamePlan): void {
    switch (plan.status) {
      case 'RENAME':
        this.notify(`„${plan.title}“ umbenannt.`);
        this.movie.reload();
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
