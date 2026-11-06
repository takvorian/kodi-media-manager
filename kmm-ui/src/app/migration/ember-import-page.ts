import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translate, TranslocoPipe } from '@jsverse/transloco';
import { filter } from 'rxjs';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import {
  EMBER_IMPORT_JOB_KEY,
  emberProfileFiles,
  EmberImportReport,
  EmberInspection,
  PathMapping,
} from './ember-migration';
import { EmberMigrationService } from './ember-migration.service';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 8000;
/** Part of the job title the server gives a dry run; it is German until the server delivers keys. */
const DRY_RUN_TITLE = 'Probelauf';

type MappingForm = FormGroup<{ emberPath: FormControl<string>; kmmPath: FormControl<string> }>;

/** Wizard for taking over an Ember profile: upload, map paths, dry run, import. */
@Component({
  selector: 'app-ember-import-page',
  imports: [
    HelpLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    TranslocoPipe,
  ],
  templateUrl: './ember-import-page.html',
  styleUrl: './ember-import-page.scss',
})
export class EmberImportPage {
  private readonly migrationService = inject(EmberMigrationService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly mappings = this.formBuilder.array<MappingForm>([]);
  protected readonly importSettings = this.formBuilder.control(true);
  protected readonly importKodiHosts = this.formBuilder.control(true);
  protected readonly importTvShows = this.formBuilder.control(true);
  protected readonly inspection = signal<EmberInspection | null>(null);
  protected readonly uploading = signal(false);
  protected readonly report = signal<EmberImportReport | null>(null);

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === EMBER_IMPORT_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.onImportFinished(job));
  }

  protected runningJob(): Job | undefined {
    return this.jobStore.runningJob(EMBER_IMPORT_JOB_KEY);
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  /** Progress appended to the job message, e.g. " (25 %)"; empty without a known total. */
  protected percentSuffix(job: Job): string {
    const percent = progressPercent(job);
    return percent === null ? '' : ` (${percent} %)`;
  }

  /** Uploads only the files from the chosen folder that the import needs. */
  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = emberProfileFiles(Array.from(input.files ?? []));
    // Otherwise the same selection triggers no event next time.
    input.value = '';
    if (files.length === 0) {
      this.notify(translate('migration.noProfile'));
      return;
    }
    this.uploading.set(true);
    this.report.set(null);
    this.migrationService.upload(files).subscribe({
      next: (inspection) => {
        this.uploading.set(false);
        this.inspection.set(inspection);
        this.showMappings(inspection);
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.forgetProfile();
        this.notify(problemMessage(error));
      },
    });
  }

  protected discard(): void {
    this.migrationService.discardUpload().subscribe({
      next: () => this.forgetProfile(),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected start(dryRun: boolean): void {
    const inspection = this.inspection();
    if (!inspection || this.mappings.invalid) {
      this.mappings.markAllAsTouched();
      return;
    }
    if (!dryRun && !this.confirm(confirmation(inspection, this.importTvShows.value))) {
      return;
    }
    this.report.set(null);
    this.migrationService
      .startImport({
        pathMappings: this.mappings.getRawValue().map(trimmed),
        importSettings: this.importSettings.value,
        importKodiHosts: this.importKodiHosts.value,
        importTvShows: this.importTvShows.value,
        dryRun,
      })
      .subscribe({
        next: (job) => this.jobStore.update(job),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected hasTvShows(report: EmberImportReport): boolean {
    const tv = report.tvShows;
    return tv.showsCreated + tv.showsUpdated + tv.showsSkipped + tv.episodesMissing > 0;
  }

  protected cancel(job: Job): void {
    this.jobStore.cancel(job.id).subscribe({
      next: (updated) => this.jobStore.update(updated),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Suggestion: every movie and TV show source with an unchanged path; under Docker e.g. /media/filme. */
  private showMappings(inspection: EmberInspection): void {
    this.mappings.clear();
    const paths = new Set<string>();
    for (const source of [...inspection.sources, ...inspection.tvSources]) {
      if (source.path) {
        paths.add(source.path);
      }
    }
    for (const path of paths) {
      this.mappings.push(
        this.formBuilder.group({
          emberPath: [path, Validators.required],
          kmmPath: [path, Validators.required],
        }),
      );
    }
  }

  private forgetProfile(): void {
    this.inspection.set(null);
    this.mappings.clear();
  }

  private onImportFinished(job: Job): void {
    if (!job.title.includes(DRY_RUN_TITLE)) {
      // After the real import the server deletes the uploaded profile.
      this.forgetProfile();
    }
    if (job.status === 'SUCCEEDED') {
      const report = job.result as EmberImportReport;
      this.report.set(report);
      this.notify(summary(job.title, report));
    } else if (job.status === 'CANCELLED') {
      this.notify(translate('migration.cancelled', { title: job.title }));
    } else {
      this.notify(
        translate('migration.failed', {
          title: job.title,
          message: job.message ?? translate('migration.unknownError'),
        }),
      );
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

function trimmed(mapping: PathMapping): PathMapping {
  return { emberPath: mapping.emberPath.trim(), kmmPath: mapping.kmmPath.trim() };
}

function confirmation(inspection: EmberInspection, withTvShows: boolean): string {
  const tvShows = withTvShows ? inspection.library.tvShows : 0;
  return translate('migration.confirm', { movies: inspection.library.movies, tvShows });
}

function summary(title: string, report: EmberImportReport): string {
  return translate('migration.summary', {
    title,
    dryRun: String(report.dryRun),
    created: report.moviesCreated,
    updated: report.moviesUpdated,
    episodes: report.tvShows.episodesCreated + report.tvShows.episodesUpdated,
  });
}
