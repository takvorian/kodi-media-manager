import { translate } from '@jsverse/transloco';

import { Job } from '../jobs/job';
import { ScanEntry, ScanResult } from './media-source';

/** A line of the report; without a target if the entry is no longer in the library. */
export interface ScanReportRow {
  readonly label: string;
  readonly link: readonly (string | number)[] | null;
}

/** A section of the report. The list may be shorter than the total. */
export interface ScanReportSection {
  readonly title: string;
  readonly hint: string | null;
  readonly total: number;
  readonly rows: readonly ScanReportRow[];
}

/** Kind of the counted items, the ICU `select` argument of the section titles. */
type ScanItems = 'movies' | 'episodes';

const ROUTES: Partial<Record<ScanEntry['type'], string>> = {
  MOVIE: '/movies',
  TV_SHOW: '/tvshows',
  TV_EPISODE: '/episodes',
};
const SECTION = 'sources.scanReport.section';
const HINT = 'sources.scanReport.hint';

/**
 * Sections of the report on a successful scan, translated into the active language; only those in which something
 * happened.
 */
export function scanReportSections(job: Job): readonly ScanReportSection[] {
  if (job.status !== 'SUCCEEDED') {
    return [];
  }
  const result = job.result as ScanResult;
  const details = result.details;
  const kind: ScanItems = result.showsAdded === undefined ? 'movies' : 'episodes';
  const unrecognized = result.unrecognizedFiles ?? [];
  const sections: ScanReportSection[] = [
    section('newShows', null, result.showsAdded ?? 0, entries(details?.addedShows), kind),
    section('added', null, result.added, entries(details?.added), kind),
    section('updated', 'updated', result.updated, entries(details?.updated), kind),
    section(
      'removedShows',
      'removed',
      result.showsRemoved ?? 0,
      entries(details?.removedShows),
      kind,
    ),
    section('removed', 'removed', result.removed, entries(details?.removed), kind),
    section('unreadable', 'unreadable', result.nfoErrors, plain(details?.unreadableNfos), kind),
    section('unrecognized', 'unrecognized', unrecognized.length, plain(unrecognized), kind),
  ];
  return sections.filter((candidate) => candidate.total > 0);
}

function section(
  title: string,
  hint: string | null,
  total: number,
  rows: readonly ScanReportRow[],
  kind: ScanItems,
): ScanReportSection {
  return {
    title: translate(`${SECTION}.${title}`, { kind }),
    hint: hint === null ? null : translate(`${HINT}.${hint}`),
    total,
    rows,
  };
}

function entries(list: readonly ScanEntry[] | undefined): readonly ScanReportRow[] {
  return (list ?? []).map((entry) => ({ label: entry.label, link: scanEntryLink(entry) }));
}

function plain(list: readonly string[] | undefined): readonly ScanReportRow[] {
  return (list ?? []).map((label) => ({ label, link: null }));
}

/** Target of an entry in the library; {@code null} if it is no longer there. */
export function scanEntryLink(entry: ScanEntry): readonly (string | number)[] | null {
  const route = ROUTES[entry.type];
  return route !== undefined && entry.id !== null ? [route, entry.id] : null;
}
