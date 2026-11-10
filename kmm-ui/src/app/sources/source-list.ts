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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Observable, filter } from 'rxjs';

import { LanguageService } from '../i18n/language.service';
import { Job, isRunning, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import {
  KODI_PLAYBACK_JOB_KEY_PREFIX,
  kodiPlaybackJobKey,
  kodiPlaybackSummary,
  kodiSyncSnackBarConfig,
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
  autoScrapeJobKey,
  scanJobKey,
} from './media-source';
import { joinParts, unsuccessfulJobSummary } from './job-summary';
import { scanCounts, scanSummary } from './scan-summary';
import { MediaSourceService } from './media-source.service';
import { scanReportSections } from './scan-report';
import { ScrapeReport, autoScrapeSummary, hasScrapeDetails } from './scrape-report';
import { HelpLink } from '../shared/help-link';
import { localize } from '../i18n/localized-text';
import { LocalizedPipe } from '../i18n/localized.pipe';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-source-list',
  imports: [
    LocalizedPipe,
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
    TranslocoPipe,
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
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);

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
  /** Depends on the UI language, because the content column sorts by its translated label. */
  protected readonly sortedSources = computed(() => {
    this.language.current();
    return this.sources.hasValue()
      ? sortedBy(this.sources.value(), this.sort(), (source, column) =>
          this.sortValue(source, column),
        )
      : [];
  });
  protected readonly saving = signal(false);
  protected readonly newLanguage = signal<string | null>(null);
  /** Last scrape run, as long as it contains titles without a match, errors or notes. */
  protected readonly scrapeReport = signal<Job | null>(null);
  private readonly reportedScanId = signal<string | null>(null);
  /** Open report of a scan; it comes from the job history of the server and follows the UI language. */
  protected readonly scanReport = computed(() => {
    this.language.current();
    const job = this.jobStore.jobs().find((candidate) => candidate.id === this.reportedScanId());
    return job ? { title: localize(job.title), sections: scanReportSections(job) } : null;
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
    const labelKey = this.mediaTypes.find((type) => type.value === mediaType)?.labelKey;
    return labelKey ? this.transloco.translate(labelKey) : mediaType;
  }

  /** Movie and TV show sources can be scanned. */
  protected canScan(source: MediaSource): boolean {
    return source.mediaType === 'MOVIE' || source.mediaType === 'TV_SHOW';
  }

  /** Movie and TV show sources can be auto-scraped. */
  protected canScrape(source: MediaSource): boolean {
    return this.canScan(source);
  }

  /**
   * Running job of the source (scan, scrape, watched status, technical details, rename); they exclude each other in
   * the UI.
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

  /** Progress appended to the job message, e.g. " (25 %)"; empty without a known total. */
  protected percentSuffix(job: Job): string {
    const percent = progressPercent(job);
    return percent === null ? '' : ` (${percent} %)`;
  }

  protected lastScannedAt(source: MediaSource): string {
    return localDateTime(source.lastScannedAt);
  }

  /** Result of the last finished scan; stays visible until the server restarts. */
  protected lastScanResult(source: MediaSource): string | null {
    const lastScan = this.lastScan(source);
    if (!lastScan) {
      return null;
    }
    return lastScan.status === 'SUCCEEDED'
      ? joinParts(scanCounts(lastScan))
      : unsuccessfulJobSummary(lastScan);
  }

  /** Whether the last scan changed or reported something that can be looked at one by one. */
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
        this.notify(this.transloco.translate('sources.created', { name: source.name }));
        this.sources.reload();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  /** Applies to future scraping; existing texts stay. */
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

  /** Lets all Kodi devices scan the directory of the source for new movies. */
  protected scanInKodi(source: MediaSource): void {
    this.kodiService.scanSource(source.id).subscribe({
      next: (results) =>
        this.snackBar.open(kodiSyncSummary(results), 'OK', kodiSyncSnackBarConfig(results)),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Fetches the watched status of all movies or episodes of the source from Kodi. */
  protected importPlayback(source: MediaSource): void {
    this.track(this.kodiService.importSourcePlayback(source.id));
  }

  /** Reads the technical details of the movies or episodes with ffprobe. */
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
    if (!this.confirm(this.transloco.translate('sources.deleteConfirm', { name: source.name }))) {
      return;
    }
    this.sourceService.delete(source.id).subscribe({
      next: () => {
        this.notify(this.transloco.translate('sources.deleted', { name: source.name }));
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
