import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

import { LocalizedPipe } from '../../i18n/localized.pipe';
import { PriorityEntry, ScraperPriority, ScraperPriorityChange } from './scraper-plugin';

/**
 * Orders the scrapers of each kind and switches them on or off for movies or TV shows. For a source, each kind can
 * follow the order from the settings instead. Reports all kinds on saving.
 */
@Component({
  selector: 'app-scraper-priority-editor',
  imports: [LocalizedPipe, MatButtonModule, MatCheckboxModule, MatIconModule, TranslocoPipe],
  template: `
    @for (priority of draft(); track priority.kind; let kindIndex = $index) {
      <section class="priority" [attr.data-kind]="priority.kind">
        <h4 class="kind-title">{{ 'settings.scrapers.kind.' + priority.kind | transloco }}</h4>
        @if (forSource()) {
          <mat-checkbox
            class="inherit"
            [checked]="priority.inherited"
            (change)="setInherited(kindIndex, $event.checked)"
            >{{ 'settings.scrapers.priority.inherit' | transloco }}</mat-checkbox
          >
        }
        <ol class="entries">
          @for (
            entry of priority.entries;
            track entry.scraperId;
            let first = $first, last = $last
          ) {
            <li class="entry" [class.off]="!entry.enabled">
              <mat-checkbox
                class="enabled"
                [checked]="entry.enabled"
                [disabled]="priority.inherited"
                (change)="setEnabled(kindIndex, entry, $event.checked)"
                >{{ entry.displayName }}</mat-checkbox
              >
              @if (entry.missingConfiguration; as missing) {
                <span class="missing">{{ missing | localized }}</span>
              }
              <span class="moves">
                <button
                  matIconButton
                  type="button"
                  class="up"
                  [disabled]="priority.inherited || first"
                  [attr.aria-label]="
                    'settings.scrapers.priority.up' | transloco: { name: entry.displayName }
                  "
                  (click)="move(kindIndex, entry, -1)"
                >
                  <mat-icon>arrow_upward</mat-icon>
                </button>
                <button
                  matIconButton
                  type="button"
                  class="down"
                  [disabled]="priority.inherited || last"
                  [attr.aria-label]="
                    'settings.scrapers.priority.down' | transloco: { name: entry.displayName }
                  "
                  (click)="move(kindIndex, entry, 1)"
                >
                  <mat-icon>arrow_downward</mat-icon>
                </button>
              </span>
            </li>
          } @empty {
            <li class="none">{{ 'settings.scrapers.priority.none' | transloco }}</li>
          }
        </ol>
      </section>
    }
    <div class="actions">
      <button
        matButton="filled"
        type="button"
        class="save-priorities-button"
        [disabled]="!changed() || busy()"
        (click)="save()"
      >
        {{ (busy() ? 'settings.saving' : 'common.save') | transloco }}
      </button>
    </div>
  `,
  styles: `
    .kind-title {
      margin: 16px 0 4px;
    }

    .entries {
      margin: 0;
      padding-inline-start: 24px;
    }

    .entry {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0 8px;
    }

    .off {
      color: var(--mat-sys-outline);
    }

    .missing {
      color: var(--mat-sys-error);
      font-size: 0.875em;
    }

    .moves {
      margin-inline-start: auto;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScraperPriorityEditor {
  /** The orders of one media type, as the server has them. */
  readonly priorities = input.required<readonly ScraperPriority[]>();
  /** For a source: each kind may follow the order from the settings. */
  readonly forSource = input(false);
  readonly busy = input(false);
  readonly saved = output<ScraperPriorityChange[]>();

  protected readonly draft = signal<ScraperPriority[]>([]);
  protected readonly changed = computed(
    () => JSON.stringify(this.draft()) !== JSON.stringify(this.priorities()),
  );

  constructor() {
    effect(() => this.draft.set([...this.priorities()]));
  }

  protected setInherited(kindIndex: number, inherited: boolean): void {
    this.update(kindIndex, (priority) => ({ ...priority, inherited }));
  }

  protected setEnabled(kindIndex: number, entry: PriorityEntry, enabled: boolean): void {
    this.update(kindIndex, (priority) => ({
      ...priority,
      entries: priority.entries.map((each) => (each === entry ? { ...each, enabled } : each)),
    }));
  }

  protected move(kindIndex: number, entry: PriorityEntry, step: -1 | 1): void {
    this.update(kindIndex, (priority) => {
      const entries = [...priority.entries];
      const from = entries.indexOf(entry);
      entries.splice(from, 1);
      entries.splice(from + step, 0, entry);
      return { ...priority, entries };
    });
  }

  protected save(): void {
    this.saved.emit(
      this.draft().map((priority) => ({
        kind: priority.kind,
        mediaType: priority.mediaType,
        inherit: this.forSource() && priority.inherited,
        entries: priority.entries.map((entry) => ({
          scraperId: entry.scraperId,
          enabled: entry.enabled,
        })),
      })),
    );
  }

  private update(kindIndex: number, change: (priority: ScraperPriority) => ScraperPriority): void {
    this.draft.update((priorities) =>
      priorities.map((priority, index) => (index === kindIndex ? change(priority) : priority)),
    );
  }
}
