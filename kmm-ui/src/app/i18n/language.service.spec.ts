import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

import { LANGUAGE_STORAGE_KEY, LanguageService } from './language.service';
import { provideI18nTesting } from './testing';

describe('LanguageService', () => {
  /** Sets the address the app was opened with; jsdom keeps it in `document.location`. */
  function openWith(search: string): void {
    window.history.replaceState(null, '', `/${search}`);
  }

  beforeEach(() => {
    openWith('');
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideI18nTesting()],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  async function startLanguage(): Promise<string> {
    const service = TestBed.inject(LanguageService);
    await service.init();
    return service.current();
  }

  it('starts with the language from the URL parameter', async () => {
    openWith('?lang=de_DE');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');

    expect(await startLanguage()).toBe('de');
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('de');
    expect(document.documentElement.lang).toBe('de');
  });

  it('remembers the language from the URL parameter', async () => {
    openWith('?lang=de');

    await startLanguage();

    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('de');
  });

  it('uses the stored choice without a URL parameter', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'de');

    expect(await startLanguage()).toBe('de');
  });

  it('ignores an unsupported URL parameter in favour of the stored choice', async () => {
    openWith('?lang=fr');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'de');

    expect(await startLanguage()).toBe('de');
  });

  it('falls back to English', async () => {
    openWith('?lang=en_EN');
    expect(await startLanguage()).toBe('en');

    openWith('');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'xx');
    expect(await startLanguage()).toBe('en');
  });

  it('works without browser storage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    const service = TestBed.inject(LanguageService);

    await service.init();
    await service.use('de');

    expect(service.current()).toBe('de');
  });

  it('switches at runtime and remembers the choice', async () => {
    const service = TestBed.inject(LanguageService);
    await service.init();
    const transloco = TestBed.inject(TranslocoService);

    await service.use('de');

    expect(service.current()).toBe('de');
    expect(transloco.translate('nav.movies')).toBe('Filme');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('de');

    await service.use('en');

    expect(transloco.translate('nav.movies')).toBe('Movies');
  });

  it('updates the language parameter in the URL only if there is one', async () => {
    const router = TestBed.inject(Router);
    const service = TestBed.inject(LanguageService);

    await router.navigateByUrl('/movies?lang=en&page=2');
    await service.use('de');
    expect(router.url).toBe('/movies?lang=de&page=2');

    await router.navigateByUrl('/movies?page=2');
    await service.use('en');
    expect(router.url).toBe('/movies?page=2');
  });
});
