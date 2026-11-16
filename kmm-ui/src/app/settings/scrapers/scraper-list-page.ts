import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LocalizedPipe } from '../../i18n/localized.pipe';
import { HelpLink } from '../../shared/help-link';
import { SettingsTabs } from '../settings-tabs';
import { ScraperAdminService } from './scraper-admin.service';
import { ScraperStatus } from './scraper-status';

/** Overview of the scraper plugins: what each delivers and whether it is ready. */
@Component({
  selector: 'app-scraper-list-page',
  imports: [
    HelpLink,
    LocalizedPipe,
    MatCardModule,
    RouterLink,
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

  protected readonly plugins = rxResource({ stream: () => this.adminService.plugins() });
}
