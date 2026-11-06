import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { isLocalizedText, literal, localize } from './localized-text';
import { provideI18nTesting } from './testing';

describe('localize', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('translates backend keys in the active language', () => {
    const title = { key: 'migration.import.dryRunTitle', args: {} };

    expect(localize(title)).toBe('Ember-Import (Probelauf)');
    TestBed.inject(TranslocoService).setActiveLang('en');
    expect(localize(title)).toBe('Ember import (dry run)');
  });

  it('shows literals as they are', () => {
    expect(localize(literal('Heat (1995).mkv'))).toBe('Heat (1995).mkv');
    expect(localize({ key: 'literal', args: {} })).toBe('');
  });

  it('translates nested texts in the arguments', () => {
    TestBed.inject(TranslocoService).setTranslation(
      { server: { job: { running: '{job} läuft bereits.' } } },
      'de',
      { merge: true },
    );

    expect(
      localize({ key: 'job.running', args: { job: { key: 'backup.import.title', args: {} } } }),
    ).toBe('Sicherung einspielen läuft bereits.');
  });

  it('returns an empty text for a missing text', () => {
    expect(localize(null)).toBe('');
    expect(localize(undefined)).toBe('');
  });

  it('recognizes texts from the backend', () => {
    expect(isLocalizedText(literal('x'))).toBe(true);
    expect(isLocalizedText({ key: 'a' })).toBe(false);
    expect(isLocalizedText('a')).toBe(false);
    expect(isLocalizedText(null)).toBe(false);
  });
});
