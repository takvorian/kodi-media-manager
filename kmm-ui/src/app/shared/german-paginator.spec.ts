import { GermanPaginatorIntl } from './german-paginator';

describe('GermanPaginatorIntl', () => {
  const intl = new GermanPaginatorIntl();

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
});
