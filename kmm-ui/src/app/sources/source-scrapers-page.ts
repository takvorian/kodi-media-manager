import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ScraperAdminService } from '../settings/scrapers/scraper-admin.service';
import { ScraperPriorityChange } from '../settings/scrapers/scraper-plugin';
import { ScraperPriorityEditor } from '../settings/scrapers/scraper-priority-editor';
import { HelpLink } from '../shared/help-link';
import { problemMessage } from '../shared/problem-message';
import { MediaSourceService } from './media-source.service';

const SNACKBAR_DURATION_MS = 5000;

/** The order of the scrapers for one source; each kind can follow the order from the settings. */
@Component({
  selector: 'app-source-scrapers-page',
  imports: [HelpLink, MatCardModule, RouterLink, ScraperPriorityEditor, TranslocoPipe],
  template: `
    <h2 class="page-title">
      {{ 'sources.scrapers.title' | transloco: { name: source()?.name ?? '' } }}
    </h2>
    <p class="back">
      <a routerLink="/sources">{{ 'sources.scrapers.back' | transloco }}</a>
    </p>
    <mat-card appearance="outlined" class="source-scrapers">
      <mat-card-content>
        <p class="hint">
          {{ 'sources.scrapers.hint' | transloco }}
          <app-help-link chapter="sources" anchor="source-scrapers">{{
            'sources.scrapers.help' | transloco
          }}</app-help-link>
        </p>
        @if (priorities.error()) {
          <p class="error">{{ 'settings.scrapers.priority.loadError' | transloco }}</p>
        } @else if (priorities.hasValue()) {
          <app-scraper-priority-editor
            [priorities]="priorities.value()"
            [forSource]="true"
            [busy]="saving()"
            (saved)="save($event)"
          />
        } @else {
          <p class="loading">{{ 'settings.scrapers.loading' | transloco }}</p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .source-scrapers {
      max-width: 900px;
    }

    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SourceScrapersPage {
  private readonly adminService = inject(ScraperAdminService);
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  /** ID of the source, from the route. */
  readonly id = input.required<string>();

  private readonly sourceId = computed(() => Number(this.id()));
  private readonly sources = rxResource({ stream: () => this.sourceService.findAll() });
  protected readonly source = computed(() =>
    (this.sources.value() ?? []).find((source) => source.id === this.sourceId()),
  );
  protected readonly priorities = rxResource({
    params: () => this.sourceId(),
    stream: ({ params }) => this.adminService.sourcePriorities(params),
  });
  protected readonly saving = signal(false);

  protected save(changes: ScraperPriorityChange[]): void {
    this.saving.set(true);
    this.adminService.changeSourcePriorities(this.sourceId(), changes).subscribe({
      next: (priorities) => {
        this.saving.set(false);
        this.priorities.set(priorities);
        this.notify(this.transloco.translate('settings.scrapers.priority.saved'));
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}
