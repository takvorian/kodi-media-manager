import { HttpParams } from '@angular/common/http';
import { Sort } from '@angular/material/sort';

/** Sortierung einer Liste: Spalte und Richtung. */
export interface ListSort {
  readonly active: string;
  readonly direction: 'asc' | 'desc';
}

const TITLE = 'title';
/** Serverseitig ohne Rücksicht auf Groß- und Kleinschreibung sortierte Spalten. */
const TEXT_COLUMNS: ReadonlySet<string> = new Set([TITLE, 'filePath', 'directoryPath']);

export const SORT_BY_TITLE: ListSort = { active: TITLE, direction: 'asc' };

export function listSortOf(sort: Sort): ListSort {
  return { active: sort.active, direction: sort.direction === 'desc' ? 'desc' : 'asc' };
}

/** Ergänzt den Sortier-Parameter. Bei gleichen Werten entscheidet der Titel, sonst springen Einträge zwischen den Seiten. */
export function withSort(params: HttpParams, sort: ListSort): HttpParams {
  const sorted = params.append('sort', sortParam(sort));
  return sort.active === TITLE ? sorted : sorted.append('sort', sortParam(SORT_BY_TITLE));
}

/** Sortiert eine vollständig geladene Liste; fehlende Werte stehen immer am Ende. */
export function sortedBy<T>(
  items: readonly T[],
  sort: ListSort,
  value: (item: T, column: string) => string | null,
): T[] {
  const direction = sort.direction === 'asc' ? 1 : -1;
  return [...items].sort((left, right) => {
    const leftValue = value(left, sort.active);
    const rightValue = value(right, sort.active);
    if (leftValue === null || rightValue === null) {
      return Number(leftValue === null) - Number(rightValue === null);
    }
    return direction * leftValue.localeCompare(rightValue, undefined, { sensitivity: 'base' });
  });
}

function sortParam(sort: ListSort): string {
  const ignoreCase = TEXT_COLUMNS.has(sort.active) ? ',ignorecase' : '';
  return `${sort.active},${sort.direction}${ignoreCase}`;
}
