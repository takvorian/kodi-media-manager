import { Component, computed, inject, input, output, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { ScrapeCandidate, ScraperInfo } from '../movies/scraper';
import { problemMessage } from '../shared/problem-message';
import { defaultTvShowScraper } from './tv-scrapers';
import { TvShowProposal } from './tvshow';
import { TvShowService } from './tvshow.service';

/** Adopted match with its origin; the episodes are completed on saving. */
export interface TvShowScrapedProposal {
  readonly proposal: TvShowProposal;
  readonly scraperId: string;
  readonly scraperName: string;
  readonly providerId: string;
}

/**
 * Searches a TV show with a TV show scraper and delivers the data of the chosen match. Nothing is
 * saved; the proposal ends up in the form of the detail page.
 */
@Component({
  selector: 'app-tvshow-online-search',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslocoPipe,
  ],
  templateUrl: './tvshow-online-search.html',
  styleUrl: './tvshow-online-search.scss',
})
export class TvShowOnlineSearch {
  private readonly tvShowService = inject(TvShowService);

  readonly showId = input.required<number>();
  readonly title = input.required<string>();
  readonly year = input<number | null>(null);
  readonly proposed = output<TvShowScrapedProposal>();

  protected readonly scrapers = rxResource({ stream: () => this.tvShowService.tvShowScrapers() });
  protected readonly candidates = signal<readonly ScrapeCandidate[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    scraper: [''],
    query: [''],
    year: [null as number | null],
  });

  private readonly chosenScraperId = toSignal(this.form.controls.scraper.valueChanges, {
    initialValue: '',
  });

  /** Chosen scraper, otherwise the default. */
  protected readonly selectedScraper = computed<ScraperInfo | undefined>(() => {
    const scrapers = this.scrapers.hasValue() ? this.scrapers.value() : [];
    return (
      scrapers.find((scraper) => scraper.id === this.chosenScraperId()) ??
      defaultTvShowScraper(scrapers)
    );
  });

  protected search(): void {
    const scraper = this.selectedScraper();
    if (!scraper) {
      return;
    }
    const { query, year } = this.form.getRawValue();
    const title = query.trim() || this.title();
    const searchYear = query.trim() ? year : this.year();
    this.candidates.set(null);
    this.run(
      this.tvShowService.searchOnline(this.showId(), scraper.id, title, searchYear),
      (results) => this.candidates.set(results),
    );
  }

  protected adopt(candidate: ScrapeCandidate): void {
    const scraper = this.selectedScraper();
    if (!scraper) {
      return;
    }
    this.run(
      this.tvShowService.proposal(this.showId(), scraper.id, candidate.providerId),
      (proposal) =>
        this.proposed.emit({
          proposal,
          scraperId: scraper.id,
          scraperName: scraper.displayName,
          providerId: candidate.providerId,
        }),
    );
  }

  private run<T>(request: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        onSuccess(value);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
