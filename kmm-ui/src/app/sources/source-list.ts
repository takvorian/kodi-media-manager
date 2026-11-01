import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Observable, filter } from 'rxjs';

import { Job, isRunning, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import {
  KODI_PLAYBACK_JOB_KEY_PREFIX,
  kodiPlaybackJobKey,
  kodiPlaybackSummary,
  kodiSyncSummary,
} from '../kodi/kodi';
import { KodiService } from '../kodi/kodi.service';
import {
  NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX,
  STREAM_DETAILS_JOB_KEY_PREFIX,
  StreamDetailsScope,
  newFilesStreamDetailsJobKey,
  streamDetailsJobKey,
  streamDetailsSummary,
} from '../media-info/media-info';
import { MediaInfoService } from '../media-info/media-info.service';
import { movieRenameJobKey } from '../movies/movie-rename';
import { tvRenameJobKey } from '../tvshows/tv-rename';
import { CONFIRM } from '../shared/confirm';
import { localDateTime } from '../shared/date-time';
import { ListSort, listSortOf, sortedBy } from '../shared/list-sort';
import { LanguageSelect } from '../shared/language-select';
import { problemMessage } from '../shared/problem-message';
import {
  AUTO_SCRAPE_JOB_KEY_PREFIX,
  AutoScrapeResult,
  MediaSource,
  MediaType,
  SCAN_JOB_KEY_PREFIX,
  SOURCE_MEDIA_TYPES,
  ScanResult,
  autoScrapeJobKey,
  scanJobKey,
} from './media-source';
import { MediaSourceService } from './media-source.service';
import { scanReportSections } from './scan-report';
import { ScrapeReport, autoScrapeSummary, hasScrapeDetails } from './scrape-report';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-source-list',
  imports: [
    HelpLink,
    LanguageSelect,
    ReactiveFormsModule,
    RouterLink,
    ScrapeReport,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './source-list.html',
  styleUrl: './source-list.scss',
})
export class SourceList {
  private readonly sourceService = inject(MediaSourceService);
  private readonly jobStore = inject(JobStore);
  private readonly kodiService = inject(KodiService);
  private readonly mediaInfoService = inject(MediaInfoService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);

  protected readonly mediaTypes = SOURCE_MEDIA_TYPES;
  protected readonly columns = [
    'name',
    'path',
    'mediaType',
    'language',
    'lastScannedAt',
    'actions',
  ];
  protected readonly sources = rxResource({ stream: () => this.sourceService.findAll() });
  protected readonly sort = signal<ListSort>({ active: 'name', direction: 'asc' });
  protected readonly sortedSources = computed(() =>
    this.sources.hasValue()
      ? sortedBy(this.sources.value(), this.sort(), (source, column) =>
          this.sortValue(source, column),
        )
      : [],
  );
  protected readonly saving = signal(false);
  protected readonly newLanguage = signal<string | null>(null);
  /** Letzter Scrape-Lauf, solange er Titel ohne Treffer, Fehler oder Hinweise enthält. */
  protected readonly scrapeReport = signal<Job | null>(null);
  private readonly reportedScanId = signal<string | null>(null);
  /** Geöffneter Bericht eines Scans; er kommt aus dem Job-Verlauf des Servers. */
  protected readonly scanReport = computed(() => {
    const job = this.jobStore.jobs().find((candidate) => candidate.id === this.reportedScanId());
    return job ? { title: job.title, sections: scanReportSections(job) } : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', Validators.required],
    path: ['', Validators.required],
    mediaType: ['MOVIE' as MediaType, Validators.required],
  });

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        filter((job) => job.key.startsWith(SCAN_JOB_KEY_PREFIX)),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.onScanFinished(job));
    this.jobStore.completed
      .pipe(
        filter((job) => job.key.startsWith(AUTO_SCRAPE_JOB_KEY_PREFIX)),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.onAutoScrapeFinished(job));
    this.jobStore.completed
      .pipe(
        filter((job) => job.key.startsWith(KODI_PLAYBACK_JOB_KEY_PREFIX)),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.notify(kodiPlaybackSummary(job)));
    this.jobStore.completed
      .pipe(
        filter(
          (job) =>
            job.key.startsWith(STREAM_DETAILS_JOB_KEY_PREFIX) ||
            job.key.startsWith(NEW_FILES_STREAM_DETAILS_JOB_KEY_PREFIX),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.notify(streamDetailsSummary(job)));
  }

  protected onSort(sort: Sort): void {
    this.sort.set(listSortOf(sort));
  }

  protected mediaTypeLabel(mediaType: MediaType): string {
    return this.mediaTypes.find((type) => type.value === mediaType)?.label ?? mediaType;
  }

  /** Film- und Serienquellen lassen sich scannen. */
  protected canScan(source: MediaSource): boolean {
    return source.mediaType === 'MOVIE' || source.mediaType === 'TV_SHOW';
  }

  /** Film- und Serienquellen lassen sich automatisch scrapen. */
  protected canScrape(source: MediaSource): boolean {
    return this.canScan(source);
  }

  /**
   * Laufender Job der Quelle (Scan, Scrapen, Gesehen-Status, technische Angaben, Umbenennen); sie schließen sich in der
   * Oberfläche aus.
   */
  protected runningJob(source: MediaSource): Job | undefined {
    return (
      this.jobStore.runningJob(scanJobKey(source.id)) ??
      this.jobStore.runningJob(autoScrapeJobKey(source.id)) ??
      this.jobStore.runningJob(kodiPlaybackJobKey(source.id)) ??
      this.jobStore.runningJob(streamDetailsJobKey(source.id)) ??
      this.jobStore.runningJob(newFilesStreamDetailsJobKey(source.id)) ??
      this.jobStore.runningJob(movieRenameJobKey(source.id)) ??
      this.jobStore.runningJob(tvRenameJobKey(source.id))
    );
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  protected lastScannedAt(source: MediaSource): string {
    return localDateTime(source.lastScannedAt);
  }

  /** Ergebnis des letzten beendeten Scans; bleibt sichtbar, bis der Server neu startet. */
  protected lastScanResult(source: MediaSource): string | null {
    const lastScan = this.lastScan(source);
    if (!lastScan) {
      return null;
    }
    return lastScan.status === 'SUCCEEDED' ? scanCounts(lastScan) : unsuccessfulSummary(lastScan);
  }

  /** Ob der letzte Scan etwas geändert oder gemeldet hat, das sich einzeln ansehen lässt. */
  protected hasScanReport(source: MediaSource): boolean {
    const lastScan = this.lastScan(source);
    return !!lastScan && scanReportSections(lastScan).length > 0;
  }

  protected openScanReport(source: MediaSource): void {
    this.reportedScanId.set(this.lastScan(source)?.id ?? null);
  }

  protected closeScanReport(): void {
    this.reportedScanId.set(null);
  }

  protected create(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    const source = { ...this.form.getRawValue(), metadataLanguage: this.newLanguage() };
    this.sourceService.create(source).subscribe({
      next: (source) => {
        this.saving.set(false);
        this.form.reset();
        this.newLanguage.set(null);
        this.notify(`Quelle „${source.name}“ angelegt.`);
        this.sources.reload();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /** Gilt für künftiges Scrapen; vorhandene Texte bleiben. */
  protected changeLanguage(source: MediaSource, language: string | null): void {
    this.sourceService.changeLanguage(source.id, language).subscribe({
      next: () => this.sources.reload(),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected scan(source: MediaSource): void {
    this.track(this.sourceService.scan(source.id));
  }

  protected autoScrape(source: MediaSource): void {
    this.track(this.sourceService.autoScrape(source.id));
  }

  /** Lässt alle Kodi-Geräte das Verzeichnis der Quelle nach neuen Filmen durchsuchen. */
  protected scanInKodi(source: MediaSource): void {
    this.kodiService.scanSource(source.id).subscribe({
      next: (results) => this.notify(kodiSyncSummary(results)),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Holt den Gesehen-Status aller Filme bzw. Episoden der Quelle aus Kodi. */
  protected importPlayback(source: MediaSource): void {
    this.track(this.kodiService.importSourcePlayback(source.id));
  }

  /** Liest die technischen Angaben der Filme bzw. Episoden mit ffprobe aus. */
  protected probeStreams(source: MediaSource, scope: StreamDetailsScope): void {
    this.track(this.mediaInfoService.probeSource(source.id, scope));
  }

  protected closeScrapeReport(): void {
    this.scrapeReport.set(null);
  }

  protected cancelJob(job: Job): void {
    this.jobStore.cancel(job.id).subscribe({
      next: (updated) => this.jobStore.update(updated),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected delete(source: MediaSource): void {
    if (
      !this.confirm(`Quelle „${source.name}“ und alle ihre Filme aus der Bibliothek entfernen?`)
    ) {
      return;
    }
    this.sourceService.delete(source.id).subscribe({
      next: () => {
        this.notify(`Quelle „${source.name}“ entfernt.`);
        this.sources.reload();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private lastScan(source: MediaSource): Job | undefined {
    return this.jobStore.jobs().find((job) => job.key === scanJobKey(source.id) && !isRunning(job));
  }

  private sortValue(source: MediaSource, column: string): string | null {
    switch (column) {
      case 'mediaType':
        return this.mediaTypeLabel(source.mediaType);
      case 'path':
        return source.path;
      case 'lastScannedAt':
        return source.lastScannedAt;
      default:
        return source.name;
    }
  }

  private track(start: Observable<Job>): void {
    start.subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  private onAutoScrapeFinished(job: Job): void {
    this.notify(autoScrapeSummary(job));
    const result = job.status === 'SUCCEEDED' ? (job.result as AutoScrapeResult) : null;
    this.scrapeReport.set(result && hasScrapeDetails(result) ? job : null);
  }

  private onScanFinished(job: Job): void {
    this.notify(scanSummary(job));
    this.reportedScanId.set(scanReportSections(job).length > 0 ? job.id : null);
    this.sources.reload();
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function scanSummary(job: Job): string {
  return job.status === 'SUCCEEDED'
    ? `${job.title} abgeschlossen: ${scanCounts(job)}.`
    : unsuccessfulSummary(job);
}

function scanCounts(job: Job): string {
  const result = job.result as ScanResult;
  const errors = result.nfoErrors > 0 ? `, ${result.nfoErrors} NFO-Fehler` : '';
  const unrecognizedCount = result.unrecognizedFiles?.length ?? 0;
  const unrecognized = unrecognizedCount
    ? `, ${unrecognizedCount} ${unrecognizedCount === 1 ? 'Datei' : 'Dateien'} ohne erkennbare Episode`
    : '';
  const newShows = result.showsAdded ? `, ${result.showsAdded} neue Serien` : '';
  return (
    `${result.added} neu, ${result.updated} aktualisiert, ` +
    `${result.removed} entfernt, ${result.unchanged} unverändert${newShows}${errors}${unrecognized}`
  );
}

function unsuccessfulSummary(job: Job): string {
  return job.status === 'CANCELLED'
    ? `${job.title} abgebrochen.`
    : `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
}
