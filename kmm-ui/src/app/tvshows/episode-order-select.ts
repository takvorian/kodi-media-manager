import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { problemMessage } from '../shared/problem-message';
import { ScraperEpisodeOrders, TvEpisodeOrdering } from './tvshow';
import { TvShowService } from './tvshow.service';

/** Stands for “the order from the settings”; an option with `null` would only clear the selection. */
const DEFAULT_ORDER = '';
const SEPARATOR = ':';

function keyOf(ordering: Pick<TvEpisodeOrdering, 'scraperId' | 'orderId'>): string {
  return ordering.scraperId + SEPARATOR + ordering.orderId;
}

/**
 * Selection of the episode order of a TV show. The orders the providers know for the TV show are loaded when the
 * list opens, since that costs requests at the providers. Like the language selection it always shows `value` and
 * only reports a choice.
 */
@Component({
  selector: 'app-episode-order-select',
  imports: [MatFormFieldModule, MatSelectModule, TranslocoPipe],
  template: `
    <mat-form-field subscriptSizing="dynamic" class="ordering-field">
      <mat-label>{{ 'tvshows.ordering.label' | transloco }}</mat-label>
      <mat-select
        #select
        [value]="selected()"
        [disabled]="disabled()"
        (openedChange)="$event && load()"
        (selectionChange)="choose(select, $event.value)"
      >
        <mat-option value="">{{ 'tvshows.ordering.default' | transloco }}</mat-option>
        @if (current(); as current) {
          <mat-option [value]="current.key">{{ current.name }}</mat-option>
        }
        @for (group of groups(); track group.scraperId) {
          <mat-optgroup [label]="group.scraperName">
            @for (order of group.orders; track order.id) {
              <mat-option [value]="group.scraperId + ':' + order.id">{{ order.name }}</mat-option>
            }
          </mat-optgroup>
        }
        @if (loading()) {
          <mat-option class="ordering-loading" disabled>{{
            'tvshows.ordering.loading' | transloco
          }}</mat-option>
        } @else if (failure(); as failure) {
          <mat-option class="ordering-failed" disabled>{{ failure }}</mat-option>
        } @else if (groups()?.length === 0) {
          <mat-option class="ordering-none" disabled>{{
            'tvshows.ordering.none' | transloco
          }}</mat-option>
        }
      </mat-select>
      <mat-hint>{{ 'tvshows.ordering.hint' | transloco }}</mat-hint>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: inline-block;
      width: 22rem;
      max-width: 100%;
    }

    .ordering-field {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EpisodeOrderSelect {
  private readonly tvShowService = inject(TvShowService);

  readonly showId = input.required<number>();
  /** The TV show's own order, `null` for the one from the settings. */
  readonly value = input<TvEpisodeOrdering | null>(null);
  readonly disabled = input(false);
  /** The chosen order, `null` for the one from the settings. */
  readonly chosen = output<TvEpisodeOrdering | null>();

  protected readonly groups = signal<readonly ScraperEpisodeOrders[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly selected = computed(() => {
    const value = this.value();
    return value ? keyOf(value) : DEFAULT_ORDER;
  });

  /** The own order as long as the providers' list does not contain it, so the field never stays empty. */
  protected readonly current = computed(() => {
    const value = this.value();
    if (!value) {
      return null;
    }
    const key = keyOf(value);
    const listed = (this.groups() ?? []).some((group) =>
      group.orders.some(
        (order) => keyOf({ scraperId: group.scraperId, orderId: order.id }) === key,
      ),
    );
    return listed ? null : { key, name: value.name };
  });

  protected load(): void {
    if (this.groups() !== null || this.loading()) {
      return;
    }
    this.loading.set(true);
    this.failure.set(null);
    this.tvShowService.episodeOrders(this.showId()).subscribe({
      next: (groups) => {
        this.groups.set(groups);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failure.set(problemMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected choose(select: MatSelect, key: string): void {
    select.value = this.selected();
    if (key === DEFAULT_ORDER) {
      this.chosen.emit(null);
      return;
    }
    const separator = key.indexOf(SEPARATOR);
    const scraperId = key.substring(0, separator);
    const orderId = key.substring(separator + 1);
    const name =
      this.groups()
        ?.find((group) => group.scraperId === scraperId)
        ?.orders.find((order) => order.id === orderId)?.name ??
      this.value()?.name ??
      orderId;
    this.chosen.emit({ scraperId, orderId, name });
  }
}
