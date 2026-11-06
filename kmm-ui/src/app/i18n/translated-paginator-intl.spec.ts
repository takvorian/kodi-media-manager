import { TestBed } from '@angular/core/testing';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from './testing';

describe('TranslatedPaginatorIntl', () => {
  let intl: MatPaginatorIntl;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    intl = TestBed.inject(MatPaginatorIntl);
  });

  it('labels the controls in German', () => {
    expect(intl.itemsPerPageLabel).toBe('Einträge pro Seite:');
    expect(intl.nextPageLabel).toBe('Nächste Seite');
    expect(intl.previousPageLabel).toBe('Vorherige Seite');
  });

  it('describes the visible range', () => {
    expect(intl.getRangeLabel(0, 50, 1170)).toBe('1 – 50 von 1170');
    expect(intl.getRangeLabel(23, 50, 1170)).toBe('1151 – 1170 von 1170');
    expect(intl.getRangeLabel(0, 50, 3)).toBe('1 – 3 von 3');
    expect(intl.getRangeLabel(0, 50, 0)).toBe('0 von 0');
  });

  it('follows a language switch and tells the paginator to re-render', () => {
    let changes = 0;
    intl.changes.subscribe(() => changes++);

    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(intl.itemsPerPageLabel).toBe('Items per page:');
    expect(intl.getRangeLabel(0, 50, 1170)).toBe('1 – 50 of 1170');
    expect(changes).toBeGreaterThan(0);
  });
});
