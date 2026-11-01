import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

import { Job } from '../jobs/job';
import { AutoScrapeResult } from './media-source';
import { scanEntryLink } from './scan-report';

/** Bleibt stehen, bis man ihn schließt, denn Titel ohne Treffer muss man danach einzeln bearbeiten. */
@Component({
  selector: 'app-scrape-report',
  imports: [MatButtonModule, MatCardModule, RouterLink],
  templateUrl: './scrape-report.html',
  styleUrl: './scrape-report.scss',
})
export class ScrapeReport {
  /** Der beendete Scrape-Job. */
  readonly job = input.required<Job>();
  readonly closed = output<void>();

  protected readonly summary = computed(() => autoScrapeSummary(this.job()));
  protected readonly result = computed(() => {
    const job = this.job();
    return job.status === 'SUCCEEDED' ? (job.result as AutoScrapeResult) : null;
  });
  protected readonly link = scanEntryLink;
}

/** Zusammenfassung eines beendeten Scrape-Jobs. */
export function autoScrapeSummary(job: Job): string {
  if (job.status === 'CANCELLED') {
    return `${job.title} abgebrochen.`;
  }
  if (job.status !== 'SUCCEEDED') {
    return `${job.title} fehlgeschlagen: ${job.message ?? 'Unbekannter Fehler'}`;
  }
  const result = job.result as AutoScrapeResult;
  const errors = result.failures.length > 0 ? `, ${result.failures.length} Fehler` : '';
  const trailers = result.trailersSaved ? `, ${result.trailersSaved} Trailer eingetragen` : '';
  const themes = result.themesSaved ? `, ${result.themesSaved} Titelmusik gespeichert` : '';
  const enriched = result.enriched ? `, ${result.enriched} ergänzt (Bewertungen, Angaben)` : '';
  return (
    `${job.title} abgeschlossen: ${items(result)}, ${result.scraped} neu zugeordnet, ` +
    `${result.unmatched} ohne Treffer, ${result.imagesSaved} Bilder gespeichert${trailers}${themes}${enriched}${errors}.`
  );
}

/** Ob etwas offen geblieben ist, um das man sich kümmern muss. */
export function hasScrapeDetails(result: AutoScrapeResult): boolean {
  return result.unmatched + result.failures.length + result.warnings.length > 0;
}

function items(result: AutoScrapeResult): string {
  if (result.shows === undefined) {
    return counted(result.movies ?? 0, 'Film', 'Filme');
  }
  const shows = counted(result.shows, 'Serie', 'Serien');
  return `${shows}, ${counted(result.episodesUpdated ?? 0, 'Episode', 'Episoden')} ergänzt`;
}

function counted(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}
