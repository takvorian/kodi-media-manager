import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { SettingsService } from '../settings/settings.service';
import { problemMessage } from '../shared/problem-message';
import {
  TV_RENAME_STATUS_LABELS,
  TvRenamePatterns,
  TvShowRenamePlan,
  folderLabel,
  tvRenameJobKey,
  tvRenameSummary,
} from './tv-rename';
import { TvRenameService } from './tv-rename.service';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 8000;

/**
 * Serien einer Quelle nach Mustern umbenennen: Vorschau mit Serienordnern, Staffelordnern, Episodendateien und
 * Konflikten, Auswahl, dann Umbenennen als Hintergrund-Job. Die Vorschau ändert nichts.
 */
@Component({
  selector: 'app-tv-rename-page',
  imports: [
    HelpLink,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './tv-rename-page.html',
  styleUrl: './tv-rename-page.scss',
})
export class TvRenamePage {
  private readonly renameService = inject(TvRenameService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);

  /** Quelle aus der Route. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly statusLabels = TV_RENAME_STATUS_LABELS;
  protected readonly folderLabel = folderLabel;
  protected readonly form = inject(NonNullableFormBuilder).group({
    showFolder: ['', Validators.required],
    seasonFolder: ['', Validators.required],
    episodeFile: ['', Validators.required],
  });
  protected readonly plans = signal<readonly TvShowRenamePlan[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly onlyChanges = signal(true);
  protected readonly selected = signal<ReadonlySet<number>>(new Set());

  protected readonly visiblePlans = computed(() =>
    (this.plans() ?? []).filter((plan) => !this.onlyChanges() || plan.status !== 'UNCHANGED'),
  );
  protected readonly counts = computed(() => {
    const plans = this.plans() ?? [];
    const count = (status: string) => plans.filter((plan) => plan.status === status).length;
    return {
      rename: count('RENAME'),
      unchanged: count('UNCHANGED'),
      conflict: count('CONFLICT'),
      failed: count('FAILED'),
    };
  });
  protected readonly runningJob = computed(() =>
    this.jobStore.runningJob(tvRenameJobKey(this.id())),
  );

  constructor() {
    this.jobStore.connect();
    inject(SettingsService)
      .load()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (settings) =>
          this.form.setValue({
            showFolder: settings.rename.tvShowFolder,
            seasonFolder: settings.rename.seasonFolder,
            episodeFile: settings.rename.episodeFile,
          }),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === tvRenameJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(tvRenameSummary(job));
        this.preview();
      });
  }

  protected preview(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.renameService.previewSource(this.id(), this.patterns()).subscribe({
      next: (plans) => {
        this.loading.set(false);
        this.plans.set(plans);
        this.selected.set(
          new Set(plans.filter((plan) => plan.status === 'RENAME').map((plan) => plan.showId)),
        );
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.plans.set(null);
        this.notify(problemMessage(error));
      },
    });
  }

  protected rename(): void {
    const showIds = [...this.selected()];
    if (showIds.length === 0) {
      return;
    }
    this.renameService.renameSource(this.id(), this.patterns(), showIds).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected isSelected(plan: TvShowRenamePlan): boolean {
    return this.selected().has(plan.showId);
  }

  protected toggle(plan: TvShowRenamePlan): void {
    this.selected.update((selected) => {
      const next = new Set(selected);
      if (!next.delete(plan.showId)) {
        next.add(plan.showId);
      }
      return next;
    });
  }

  protected selectAll(selectAll: boolean): void {
    const renamable = (this.plans() ?? [])
      .filter((plan) => plan.status === 'RENAME')
      .map((plan) => plan.showId);
    this.selected.set(new Set(selectAll ? renamable : []));
  }

  protected movesFolder(plan: TvShowRenamePlan): boolean {
    return plan.currentDirectory !== plan.targetDirectory;
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  private patterns(): TvRenamePatterns {
    const value = this.form.getRawValue();
    return {
      showFolder: value.showFolder.trim(),
      seasonFolder: value.seasonFolder.trim(),
      episodeFile: value.episodeFile.trim(),
    };
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
