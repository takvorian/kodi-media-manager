import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { ScraperPlugin } from './scraper-plugin';

type Status = 'off' | 'missing' | 'ready';

/** Whether a scraper plugin is switched off, misses its configuration (e.g. a key) or is ready. */
@Component({
  selector: 'app-scraper-status',
  imports: [TranslocoPipe],
  template: `<span class="scraper-status" [class]="status()">{{
    'settings.scrapers.status.' + status() | transloco
  }}</span>`,
  styles: `
    .scraper-status {
      font-weight: 500;
    }

    .ready {
      color: var(--mat-sys-primary);
    }

    .missing {
      color: var(--mat-sys-error);
    }

    .off {
      color: var(--mat-sys-outline);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScraperStatus {
  readonly plugin = input.required<ScraperPlugin>();

  protected readonly status = computed<Status>(() => {
    const plugin = this.plugin();
    if (!plugin.enabled) {
      return 'off';
    }
    return plugin.missingConfiguration ? 'missing' : 'ready';
  });
}
