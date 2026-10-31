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
import { MatSortModule } from '@angular/material/sort';
import { RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { Job, progressPercent } from '../jobs/job';
import { JobStore } from '../jobs/job-store';
import { SettingsService } from '../settings/settings.service';
import { ListSort, listSortOf, sortedBy } from '../shared/list-sort';
import { problemMessage } from '../shared/problem-message';
import {
  MovieRenamePatterns,
  MovieRenamePlan,
  RENAME_STATUS_LABELS,
  movesFolder,
  movieRenameJobKey,
  movieRenameSummary,
} from './movie-rename';
import { MovieRenameService } from './movie-rename.service';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 8000;

/**
 * Filme einer Quelle nach Mustern umbenennen: Vorschau mit allen Änderungen und Konflikten, Auswahl, dann
 * Umbenennen als Hintergrund-Job. Die Vorschau ändert nichts.
 */
@Component({
  selector: 'app-movie-rename-page',
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
    MatSortModule,
  ],
  templateUrl: './movie-rename-page.html',
  styleUrl: './movie-rename-page.scss',
})
export class MovieRenamePage {
  private readonly renameService = inject(MovieRenameService);
  private readonly jobStore = inject(JobStore);
  private readonly snackBar = inject(MatSnackBar);

  /** Quelle aus der Route. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly statusLabels = RENAME_STATUS_LABELS;
  protected readonly form = inject(NonNullableFormBuilder).group({
    folder: ['', Validators.required],
    file: ['', Validators.required],
  });
  protected readonly plans = signal<readonly MovieRenamePlan[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly onlyChanges = signal(true);
  protected readonly selected = signal<ReadonlySet<number>>(new Set());

  protected readonly sort = signal<ListSort>({ active: 'title', direction: 'asc' });
  protected readonly listSortOf = listSortOf;
  protected readonly visiblePlans = computed(() =>
    sortedBy(
      (this.plans() ?? []).filter((plan) => !this.onlyChanges() || plan.status !== 'UNCHANGED'),
      this.sort(),
      (plan, column) => this.sortValue(plan, column),
    ),
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
    this.jobStore.runningJob(movieRenameJobKey(this.id())),
  );

  constructor() {
    this.jobStore.connect();
    inject(SettingsService)
      .load()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (settings) =>
          this.form.setValue({
            folder: settings.rename.movieFolder,
            file: settings.rename.movieFile,
          }),
        error: (error: unknown) => this.notify(problemMessage(error)),
      });
    this.jobStore.completed
      .pipe(
        filter((job) => job.key === movieRenameJobKey(this.id())),
        takeUntilDestroyed(),
      )
      .subscribe((job) => {
        this.notify(movieRenameSummary(job));
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
          new Set(plans.filter((plan) => plan.status === 'RENAME').map((plan) => plan.movieId)),
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
    const movieIds = [...this.selected()];
    if (movieIds.length === 0) {
      return;
    }
    this.renameService.renameSource(this.id(), this.patterns(), movieIds).subscribe({
      next: (job) => this.jobStore.update(job),
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected isSelected(plan: MovieRenamePlan): boolean {
    return this.selected().has(plan.movieId);
  }

  protected toggle(plan: MovieRenamePlan): void {
    this.selected.update((selected) => {
      const next = new Set(selected);
      if (!next.delete(plan.movieId)) {
        next.add(plan.movieId);
      }
      return next;
    });
  }

  protected selectAll(selectAll: boolean): void {
    const renamable = (this.plans() ?? [])
      .filter((plan) => plan.status === 'RENAME')
      .map((plan) => plan.movieId);
    this.selected.set(new Set(selectAll ? renamable : []));
  }

  protected movesFolder(plan: MovieRenamePlan): boolean {
    return movesFolder(plan);
  }

  protected percent(job: Job): number | null {
    return progressPercent(job);
  }

  private patterns(): MovieRenamePatterns {
    const value = this.form.getRawValue();
    return { folder: value.folder.trim(), file: value.file.trim() };
  }

  private sortValue(plan: MovieRenamePlan, column: string): string {
    switch (column) {
      case 'folder':
        return plan.currentDirectory;
      case 'status':
        return this.statusLabels[plan.status];
      default:
        return plan.title;
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
