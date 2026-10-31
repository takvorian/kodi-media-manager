import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/** Deutsche Beschriftung der Seitenleiste. */
@Injectable()
export class GermanPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = 'Einträge pro Seite:';
  override firstPageLabel = 'Erste Seite';
  override previousPageLabel = 'Vorherige Seite';
  override nextPageLabel = 'Nächste Seite';
  override lastPageLabel = 'Letzte Seite';

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0) {
      return '0 von 0';
    }
    const first = page * pageSize + 1;
    const last = Math.min((page + 1) * pageSize, length);
    return `${first} – ${last} von ${length}`;
  };
}
