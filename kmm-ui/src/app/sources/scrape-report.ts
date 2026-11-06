import { Component, computed, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { translate, TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../i18n/language.service';
import { Job } from '../jobs/job';
import { finishedJobSummary, unsuccessfulJobSummary } from './job-summary';
import { AutoScrapeResult } from './media-source';
import { scanEntryLink } from './scan-report';

const SUMMARY = 'sources.scrapeSummary';

/** Stays until it is closed, because titles without a match have to be edited one by one afterwards. */
@Component({
  selector: 'app-scrape-report',
  imports: [MatButtonModule, MatCardModule, RouterLink, TranslocoPipe],
  templateUrl: './scrape-report.html',
  styleUrl: './scrape-report.scss',
})
export class ScrapeReport {
  private readonly language = inject(LanguageService);

  /** The finished scrape job. */
  readonly job = input.required<Job>();
  readonly closed = output<void>();

  protected readonly summary = computed(() => {
    this.language.current();
    return autoScrapeSummary(this.job());
  });
  protected readonly result = computed(() => {
    const job = this.job();
    return job.status === 'SUCCEEDED' ? (job.result as AutoScrapeResult) : null;
  });
  protected readonly link = scanEntryLink;
}

/** Summary of a finished scrape job in the active language. */
export function autoScrapeSummary(job: Job): string {
  if (job.status !== 'SUCCEEDED') {
    return unsuccessfulJobSummary(job);
  }
  const result = job.result as AutoScrapeResult;
  return finishedJobSummary(job, [
    ...items(result),
    translate(`${SUMMARY}.scraped`, { count: result.scraped }),
    translate(`${SUMMARY}.unmatched`, { count: result.unmatched }),
    translate(`${SUMMARY}.images`, { count: result.imagesSaved }),
    ...optional('trailers', result.trailersSaved),
    ...optional('themes', result.themesSaved),
    ...optional('enriched', result.enriched),
    ...optional('failures', result.failures.length),
  ]);
}

/** Whether something is left that needs attention. */
export function hasScrapeDetails(result: AutoScrapeResult): boolean {
  return result.unmatched + result.failures.length + result.warnings.length > 0;
}

function items(result: AutoScrapeResult): string[] {
  if (result.shows === undefined) {
    return [translate(`${SUMMARY}.movies`, { count: result.movies ?? 0 })];
  }
  return [
    translate(`${SUMMARY}.shows`, { count: result.shows }),
    translate(`${SUMMARY}.episodes`, { count: result.episodesUpdated ?? 0 }),
  ];
}

/** A count that is only mentioned if it is not zero. */
function optional(key: string, count: number | undefined): string[] {
  return count ? [translate(`${SUMMARY}.${key}`, { count })] : [];
}
