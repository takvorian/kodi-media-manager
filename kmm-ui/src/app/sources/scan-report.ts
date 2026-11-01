import { Job } from '../jobs/job';
import { ScanEntry, ScanResult } from './media-source';

/** Eine Zeile des Berichts; ohne Ziel, wenn der Eintrag nicht mehr in der Bibliothek steht. */
export interface ScanReportRow {
  readonly label: string;
  readonly link: readonly (string | number)[] | null;
}

/** Ein Abschnitt des Berichts. Die Liste kann kürzer sein als die Gesamtzahl. */
export interface ScanReportSection {
  readonly title: string;
  readonly hint: string | null;
  readonly total: number;
  readonly rows: readonly ScanReportRow[];
}

const ROUTES: Partial<Record<ScanEntry['type'], string>> = {
  MOVIE: '/movies',
  TV_SHOW: '/tvshows',
  TV_EPISODE: '/episodes',
};
const UPDATED_HINT = 'Ihre NFO wurde außerhalb von KMM geändert und neu eingelesen.';
const REMOVED_HINT = 'Die Datei ist nicht mehr da. Entfernt wurde nur der Eintrag in KMM.';
const UNREADABLE_HINT = 'Der Eintrag bleibt erhalten, die Angaben aus der NFO fehlen aber.';
const UNRECOGNIZED_HINT =
  'KMM hat diese Dateien nicht aufgenommen, weil der Name keine Staffel und Episode wie S01E05 enthält. ' +
  'Benenne sie um und scanne erneut. Die Pfade gelten ab dem Ordner der Quelle.';

/** Abschnitte des Berichts zu einem erfolgreichen Scan; nur solche, in denen etwas passiert ist. */
export function scanReportSections(job: Job): readonly ScanReportSection[] {
  if (job.status !== 'SUCCEEDED') {
    return [];
  }
  const result = job.result as ScanResult;
  const details = result.details;
  const items = result.showsAdded === undefined ? 'Filme' : 'Episoden';
  const unrecognized = result.unrecognizedFiles ?? [];
  const sections: ScanReportSection[] = [
    section('Neue Serien', null, result.showsAdded ?? 0, entries(details?.addedShows)),
    section(`Neue ${items}`, null, result.added, entries(details?.added)),
    section(`Aktualisierte ${items}`, UPDATED_HINT, result.updated, entries(details?.updated)),
    section(
      'Entfernte Serien',
      REMOVED_HINT,
      result.showsRemoved ?? 0,
      entries(details?.removedShows),
    ),
    section(`Entfernte ${items}`, REMOVED_HINT, result.removed, entries(details?.removed)),
    section('NFO nicht lesbar', UNREADABLE_HINT, result.nfoErrors, plain(details?.unreadableNfos)),
    section(
      'Dateien ohne erkennbare Episode',
      UNRECOGNIZED_HINT,
      unrecognized.length,
      plain(unrecognized),
    ),
  ];
  return sections.filter((candidate) => candidate.total > 0);
}

function section(
  title: string,
  hint: string | null,
  total: number,
  rows: readonly ScanReportRow[],
): ScanReportSection {
  return { title, hint, total, rows };
}

function entries(list: readonly ScanEntry[] | undefined): readonly ScanReportRow[] {
  return (list ?? []).map((entry) => ({ label: entry.label, link: scanEntryLink(entry) }));
}

function plain(list: readonly string[] | undefined): readonly ScanReportRow[] {
  return (list ?? []).map((label) => ({ label, link: null }));
}

/** Ziel eines Eintrags in der Bibliothek; {@code null}, wenn er nicht mehr dort steht. */
export function scanEntryLink(entry: ScanEntry): readonly (string | number)[] | null {
  const route = ROUTES[entry.type];
  return route !== undefined && entry.id !== null ? [route, entry.id] : null;
}
