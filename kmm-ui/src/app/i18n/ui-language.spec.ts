import { normalizeLanguage } from './ui-language';

describe('normalizeLanguage', () => {
  it('accepts plain language codes in any case', () => {
    expect(normalizeLanguage('de')).toBe('de');
    expect(normalizeLanguage('EN')).toBe('en');
    expect(normalizeLanguage(' de ')).toBe('de');
  });

  it('accepts locales with region, separated by hyphen or underscore', () => {
    expect(normalizeLanguage('de_DE')).toBe('de');
    expect(normalizeLanguage('de-AT')).toBe('de');
    expect(normalizeLanguage('en-GB')).toBe('en');
    expect(normalizeLanguage('en_EN')).toBe('en');
    expect(normalizeLanguage('en-US')).toBe('en');
  });

  it('rejects unsupported, malformed and missing values', () => {
    expect(normalizeLanguage('fr')).toBeNull();
    expect(normalizeLanguage('fr_FR')).toBeNull();
    expect(normalizeLanguage('deutsch')).toBeNull();
    expect(normalizeLanguage('de_')).toBeNull();
    expect(normalizeLanguage('')).toBeNull();
    expect(normalizeLanguage(null)).toBeNull();
    expect(normalizeLanguage(undefined)).toBeNull();
  });
});
