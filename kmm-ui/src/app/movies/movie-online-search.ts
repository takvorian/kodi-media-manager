import { Component, computed, inject, input, output, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { LocalizedPipe } from '../i18n/localized.pipe';
import { problemMessage } from '../shared/problem-message';
import { MovieMetadata } from './movie';
import { MovieService } from './movie.service';
import { CollectionReference, ScrapeCandidate, ScraperInfo } from './scraper';

/** Adopted data including the collection and where it comes from. */
export interface ScrapedProposal {
  readonly metadata: MovieMetadata;
  readonly collection: CollectionReference | null;
  readonly scraperName: string;
}

/**
 * Searches a movie with a metadata scraper and delivers the data of the chosen result. Nothing is
 * saved; the proposal goes into the form of the detail page.
 */
@Component({
  selector: 'app-movie-online-search',
  imports: [
    LocalizedPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslocoPipe,
  ],
  templateUrl: './movie-online-search.html',
  styleUrl: './movie-online-search.scss',
})
export class MovieOnlineSearch {
  private readonly movieService = inject(MovieService);

  readonly movieId = input.required<number>();
  readonly title = input.required<string>();
  readonly year = input<number | null>(null);
  readonly proposed = output<ScrapedProposal>();

  protected readonly scrapers = rxResource({ stream: () => this.movieService.metadataScrapers() });
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

  /** The chosen scraper, otherwise the first one available. */
  protected readonly selectedScraper = computed<ScraperInfo | undefined>(() => {
    const scrapers = this.scrapers.hasValue() ? this.scrapers.value() : [];
    return scrapers.find((scraper) => scraper.id === this.chosenScraperId()) ?? scrapers[0];
  });

  protected search(): void {
    const scraper = this.selectedScraper();
    if (!scraper) {
      return;
    }
    const { query, year } = this.form.getRawValue();
    const title = query.trim() || this.title();
    const searchYear = query.trim() ? year : this.year();
    this.run(
      this.movieService.searchOnline(this.movieId(), scraper.id, title, searchYear),
      (results) => this.candidates.set(results),
    );
  }

  protected adopt(candidate: ScrapeCandidate): void {
    const scraper = this.selectedScraper();
    if (!scraper) {
      return;
    }
    this.run(
      this.movieService.proposal(this.movieId(), scraper.id, candidate.providerId),
      (proposal) => this.proposed.emit({ ...proposal, scraperName: scraper.displayName }),
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
