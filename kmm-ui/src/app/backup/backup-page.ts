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
import { filter } from 'rxjs';

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
const DRY_RUN_TITLE = 'Probelauf';
const FALLBACK_FILE_NAME = 'kmm-sicherung.json.gz';
const MERGE_QUESTION =
  'Sicherung einspielen? Gleiche Einträge werden mit den Angaben aus der Datei überschrieben, alles andere bleibt.';

export const REPLACE_WORD = 'ERSETZEN';

type MappingForm = FormGroup<{ from: FormControl<string>; to: FormControl<string> }>;

/** Exportiert die KMM-Daten als Datei und spielt eine Sicherung wieder ein. */
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

  protected readonly tableLabel = tableLabel;
  protected readonly replaceWord = REPLACE_WORD;
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
  /** Das Einspielen lässt sich nur mit dem Bestätigungswort starten, wenn es alles ersetzt. */
  protected readonly canImport = computed(
    () => this.chosenMode() !== 'REPLACE' || this.confirmation() === REPLACE_WORD,
  );
  protected readonly contents = computed(() => {
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
    // Sonst löst dieselbe Auswahl beim nächsten Mal kein Ereignis aus.
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
    if (!dryRun && this.mode.value === 'MERGE' && !this.confirm(MERGE_QUESTION)) {
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

  protected formatted(timestamp: string | null): string {
    return timestamp ? new Date(timestamp).toLocaleString('de-DE') : 'unbekannt';
  }

  /** Vorschlag: jede Quelle mit unverändertem Pfad; unter Docker z. B. /media/filme. */
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

  /** Unveränderte Pfade brauchen keine Regel, auf einem anderen System wären sie auch keine gültigen Ziele. */
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
      // Nach dem echten Import löscht der Server die hochgeladene Datei.
      this.forgetUpload();
    }
    if (job.status === 'SUCCEEDED') {
      this.report.set(job.result as BackupImportReport);
      this.notify(`${job.title}: fertig.`);
    } else if (job.status === 'CANCELLED') {
      this.notify(`${job.title} abgebrochen – es wurde nichts geändert.`);
    } else {
      this.notify(`${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`);
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
