import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { ScraperChoice } from '../movies/scraper';

/** Stands for “the order of the source”; an option with `null` would only clear the selection. */
const SOURCE_ORDER = '';

/**
 * Selection of the metadata scraper of a movie or TV show. Like the language selection it always shows the current
 * choice and only reports a new one.
 */
@Component({
  selector: 'app-scraper-select',
  imports: [MatFormFieldModule, MatSelectModule, TranslocoPipe],
  template: `
    <mat-form-field subscriptSizing="dynamic" class="scraper-field">
      <mat-label>{{ 'scraperSelect.label' | transloco }}</mat-label>
      <mat-select
        #select
        [value]="selected()"
        [disabled]="disabled() || !choice()"
        (selectionChange)="choose(select, $event.value)"
      >
        <mat-option value="">{{
          (defaultName() ? 'scraperSelect.default' : 'scraperSelect.defaultNone')
            | transloco: { name: defaultName() }
        }}</mat-option>
        @for (scraper of choice()?.scrapers ?? []; track scraper.id) {
          <mat-option [value]="scraper.id" [disabled]="!!scraper.missingConfiguration">{{
            scraper.displayName
          }}</mat-option>
        }
      </mat-select>
      <mat-hint>{{ 'scraperSelect.hint' | transloco }}</mat-hint>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: inline-block;
      width: 22rem;
      max-width: 100%;
    }

    .scraper-field {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScraperSelect {
  readonly choice = input<ScraperChoice | null>(null);
  readonly disabled = input(false);
  /** The chosen scraper, `null` for the order of the source. */
  readonly chosen = output<string | null>();

  protected readonly selected = computed(() => this.choice()?.scraperId ?? SOURCE_ORDER);
  protected readonly defaultName = computed(() => {
    const choice = this.choice();
    return (
      choice?.scrapers.find((scraper) => scraper.id === choice.defaultScraperId)?.displayName ??
      null
    );
  });

  protected choose(select: MatSelect, scraperId: string): void {
    select.value = this.selected();
    this.chosen.emit(scraperId === SOURCE_ORDER ? null : scraperId);
  }
}
