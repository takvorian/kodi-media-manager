import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { localDateTime } from './date-time';
import { switchLanguage } from './testing';
import { activeUiLocale, injectUiLocale, translatedLabels } from './ui-locale';

describe('UI locale', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('follows the active UI language', async () => {
    const locale = TestBed.runInInjectionContext(() => injectUiLocale());
    expect(activeUiLocale()).toBe('de-DE');
    expect(locale()).toBe('de-DE');

    await switchLanguage('en');

    expect(activeUiLocale()).toBe('en-US');
    expect(locale()).toBe('en-US');
  });

  it('translates a label table when it is read', async () => {
    const labels = translatedLabels({ yes: 'common.yes', no: 'common.no' });
    expect(labels.yes).toBe('ja');

    await switchLanguage('en');

    expect(labels.yes).toBe('yes');
    expect(Object.keys(labels)).toEqual(['yes', 'no']);
  });

  it('formats date and time in the UI language', async () => {
    const timestamp = '2026-10-25T12:00:00Z';
    const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
    expect(localDateTime(timestamp)).toBe(new Date(timestamp).toLocaleString('de-DE', options));

    await switchLanguage('en');

    expect(localDateTime(timestamp)).toBe(new Date(timestamp).toLocaleString('en-US', options));
    expect(localDateTime(null)).toBe('–');
  });
});
