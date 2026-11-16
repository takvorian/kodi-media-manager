import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LocalizedPipe } from '../../i18n/localized.pipe';
import { HelpLink } from '../../shared/help-link';
import { problemMessage } from '../../shared/problem-message';
import { SettingsTabs } from '../settings-tabs';
import { ScraperAdminService } from './scraper-admin.service';
import { PriorityMediaType, ScraperPriorityChange } from './scraper-plugin';
import { ScraperPriorityEditor } from './scraper-priority-editor';
import { ScraperStatus } from './scraper-status';

const SNACKBAR_DURATION_MS = 5000;

/** Overview of the scraper plugins: what each delivers, whether it is ready, and their order per kind. */
@Component({
  selector: 'app-scraper-list-page',
  imports: [
    HelpLink,
    LocalizedPipe,
    MatButtonToggleModule,
    MatCardModule,
    RouterLink,
    ScraperPriorityEditor,
    ScraperStatus,
    SettingsTabs,
    TranslocoPipe,
  ],
  templateUrl: './scraper-list-page.html',
  styleUrl: './scrapers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScraperListPage {
  private readonly adminService = inject(ScraperAdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly plugins = rxResource({ stream: () => this.adminService.plugins() });
  protected readonly priorities = rxResource({ stream: () => this.adminService.priorities() });
  protected readonly mediaType = signal<PriorityMediaType>('MOVIE');
  protected readonly shown = computed(() =>
    (this.priorities.value() ?? []).filter((priority) => priority.mediaType === this.mediaType()),
  );
  protected readonly saving = signal(false);

  protected savePriorities(changes: ScraperPriorityChange[]): void {
    this.saving.set(true);
    this.adminService.changePriorities(changes).subscribe({
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
