import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
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
import { MatRadioModule } from '@angular/material/radio';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translate, TranslocoPipe } from '@jsverse/transloco';
import { filter } from 'rxjs';

import { LanguageService } from '../i18n/language.service';
import { UI_LOCALES } from '../i18n/ui-language';
import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { CONFIRM } from '../shared/confirm';
import { HelpLink } from '../shared/help-link';
import { problemMessage } from '../shared/problem-message';
import { saveDownload } from '../shared/save-download';
import {
  BACKUP_IMPORT_JOB_KEY,
  BackupHeader,
  BackupImportMode,
  BackupImportReport,
  tableLabel,
  VISIBLE_TABLES,
} from './backup';
import { BackupService } from './backup.service';

const SNACKBAR_DURATION_MS = 8000;
/** Part of the job title the server gives a dry run; it is German until the server delivers keys. */
const DRY_RUN_TITLE = 'Probelauf';
/** Only used if the server sends no file name. */
const FALLBACK_FILE_NAME = 'kmm-sicherung.json.gz';

type MappingForm = FormGroup<{ from: FormControl<string>; to: FormControl<string> }>;

/** Exports the KMM data as a file and restores a backup. */
@Component({
  selector: 'app-backup-page',
  imports: [
    HelpLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatRadioModule,
    TranslocoPipe,
  ],
  templateUrl: './backup-page.html',
  styleUrl: './backup-page.scss',
})
export class BackupPage {
  private readonly backupService = inject(BackupService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly language = inject(LanguageService);

  protected readonly tableLabel = tableLabel;
  /** The confirmation word for replacing everything, in the active language. */
  protected readonly replaceWord = computed(() => {
    this.language.current();
    return translate('backup.import.replaceWord');
  });
  protected readonly includeCredentials = this.formBuilder.control(false);
  protected readonly mode = this.formBuilder.control<BackupImportMode>('REPLACE');
  protected readonly credentialsChosen = toSignal(this.includeCredentials.valueChanges, {
    initialValue: this.includeCredentials.value,
  });
  protected readonly chosenMode = toSignal(this.mode.valueChanges, {
    initialValue: this.mode.value,
  });
  protected readonly mappings = this.formBuilder.array<MappingForm>([]);
  protected readonly confirmation = signal('');
  protected readonly exporting = signal(false);
  protected readonly uploading = signal(false);
  protected readonly header = signal<BackupHeader | null>(null);
  protected readonly report = signal<BackupImportReport | null>(null);
  /** Restoring can only be started with the confirmation word if it replaces everything. */
  protected readonly canImport = computed(
    () => this.chosenMode() !== 'REPLACE' || this.confirmation() === this.replaceWord(),
  );
  protected readonly contents = computed(() => {
    this.language.current();
    const counts = this.header()?.counts ?? {};
    return VISIBLE_TABLES.map((table) => `${counts[table] ?? 0} ${tableLabel(table)}`).join(', ');
  });

  constructor() {
    this.jobStore.connect();
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === BACKUP_IMPORT_JOB_KEY),
        takeUntilDestroyed(),
      )
      .subscribe((job) => this.onImportFinished(job));
  }

  protected runningJob(): Job | undefined {
    return this.jobStore.runningJob(BACKUP_IMPORT_JOB_KEY);
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  protected export(): void {
    this.exporting.set(true);
    this.backupService.export(this.includeCredentials.value).subscribe({
      next: (response) => {
        this.exporting.set(false);
        saveDownload(response, FALLBACK_FILE_NAME);
      },
      error: (error: unknown) => {
        this.exporting.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected upload(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // Otherwise the same selection triggers no event next time.
    input.value = '';
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.report.set(null);
    this.backupService.upload(file).subscribe({
      next: (header) => {
        this.uploading.set(false);
        this.header.set(header);
        this.showMappings(header);
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        this.forgetUpload();
        this.notify(problemMessage(error));
      },
    });
  }

  protected discard(): void {
    this.backupService.discardUpload().subscribe({
      next: () => this.forgetUpload(),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected start(dryRun: boolean): void {
    if (this.mappings.invalid) {
      this.mappings.markAllAsTouched();
      return;
    }
    if (
      !dryRun &&
      this.mode.value === 'MERGE' &&
      !this.confirm(translate('backup.import.mergeQuestion'))
    ) {
      return;
    }
    this.report.set(null);
    this.backupService
      .startImport({ mode: this.mode.value, pathMappings: this.changedMappings(), dryRun })
      .subscribe({
        next: (job) => this.jobStore.update(job),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
  }

  protected cancel(job: Job): void {
    this.jobStore.cancel(job.id).subscribe({
      next: (updated) => this.jobStore.update(updated),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  /** Date and time in the locale of the UI language. */
  protected formatted(timestamp: string | null): string {
    return timestamp
      ? new Date(timestamp).toLocaleString(UI_LOCALES[this.language.current()])
      : translate('common.unknown');
  }

  /** Suggestion: every source with an unchanged path; under Docker e.g. /media/filme. */
  private showMappings(header: BackupHeader): void {
    this.mappings.clear();
    this.confirmation.set('');
    for (const path of new Set(header.sources.map((source) => source.path))) {
      this.mappings.push(
        this.formBuilder.group({
          from: [path, Validators.required],
          to: [path, Validators.required],
        }),
      );
    }
  }

  /** Unchanged paths need no rule; on another system they would not be valid targets either. */
  private changedMappings(): { from: string; to: string }[] {
    return this.mappings
      .getRawValue()
      .map((mapping) => ({ from: mapping.from.trim(), to: mapping.to.trim() }))
      .filter((mapping) => mapping.from !== mapping.to);
  }

  private forgetUpload(): void {
    this.header.set(null);
    this.mappings.clear();
    this.confirmation.set('');
  }

  private onImportFinished(job: Job): void {
    if (!job.title.includes(DRY_RUN_TITLE)) {
      // After the real import the server deletes the uploaded file.
      this.forgetUpload();
    }
    if (job.status === 'SUCCEEDED') {
      this.report.set(job.result as BackupImportReport);
      this.notify(translate('backup.job.finished', { title: job.title }));
    } else if (job.status === 'CANCELLED') {
      this.notify(translate('backup.job.cancelled', { title: job.title }));
    } else {
      this.notify(
        translate('backup.job.failed', {
          title: job.title,
          message: job.message ?? translate('backup.job.unknownError'),
        }),
      );
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
