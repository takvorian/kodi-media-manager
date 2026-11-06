import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { ApiKeyView, SettingsView } from './settings';
import { SettingsPage } from './settings-page';
import { SETTINGS_URL } from './settings.service';

describe('SettingsPage', () => {
  const stored: SettingsView = {
    metadataLanguage: 'de-DE',
    artworkLanguage: 'de',
    videoExtensions: ['mkv', 'avi'],
    excludedPaths: ['/media/privat'],
    proxy: { host: 'proxy.local', port: 3128, username: 'user', passwordSet: true },
    tv: { metadataLanguage: 'de-DE', artworkLanguage: 'de', episodeOrdering: 'AIRED' },
    mediaInfo: { ffprobePath: 'ffprobe', probeOnScrape: true, probeNewFiles: true },
    rename: {
      movieFolder: '{title}[ ({year})]',
      movieFile: '{title}[.{source}]',
      tvShowFolder: '{show}',
      seasonFolder: 'Season {season:2}',
      episodeFile: '{show} - S{season:2}E{episode:2}[ - {title}]',
      movieOnEdit: false,
      movieOnScrape: false,
      tvOnEdit: false,
      tvOnScrape: false,
      episodesOnScan: false,
    },
    movieSets: {
      flatImages: true,
      flatImagesFolder: '/media/filme/_filmsets',
      kodiImages: false,
      kodiImagesFolder: null,
      assignOnScrape: true,
    },
    overridden: [],
  };
  const keys: ApiKeyView[] = [
    { key: 'TMDB_API_KEY', pluginId: 'tmdb', label: 'TMDB-API-Key', set: true, hint: '…cdef' },
    { key: 'TVDB_API_KEY', pluginId: 'tvdb', label: 'TVDB-API-Key (v4)', set: false, hint: null },
    { key: 'TVDB_PIN', pluginId: 'tvdb', label: 'TVDB-Abonnenten-PIN', set: true, hint: null },
  ];

  let fixture: ComponentFixture<SettingsPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [
        provideI18nTesting(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsPage);
  });

  afterEach(() => httpTesting.verify());

  async function load(view: SettingsView): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: SETTINGS_URL }).flush(view);
    await renderPendingChanges();
    httpTesting.expectOne({ method: 'GET', url: `${SETTINGS_URL}/api-keys` }).flush(keys);
    await fixture.whenStable();
  }

  /** Renders until the API key card has sent its request after the settings were loaded. */
  async function renderPendingChanges(): Promise<void> {
    const renderPasses = 2;
    for (let pass = 0; pass < renderPasses; pass++) {
      await Promise.resolve();
      fixture.detectChanges();
    }
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function field(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  async function type(name: string, value: string): Promise<void> {
    field(name).value = value;
    field(name).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  it('shows the stored settings', async () => {
    await load(stored);

    expect(field('metadataLanguage').value).toBe('de-DE');
    expect(field('videoExtensions').value).toBe('mkv, avi');
    expect(field('excludedPaths').value).toBe('/media/privat');
    expect(field('proxyHost').value).toBe('proxy.local');
    expect(field('proxyPassword').placeholder).toBe('unverändert');
    expect(button('.save-button').disabled).toBe(true);
  });

  it('switches the texts to English at runtime and keeps the metadata language', async () => {
    await load(stored);

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    expect(element().querySelector('.page-title')?.textContent).toBe('Settings');
    expect(field('proxyPassword').placeholder).toBe('unchanged');
    expect(field('TMDB_API_KEY').placeholder).toBe('stored …cdef');
    expect(field('metadataLanguage').value).toBe('de-DE');
    expect(element().querySelector('.hint code')?.textContent).toBe('{ }');
    expect(element().querySelector('.save-keys-button')?.textContent?.trim()).toBe('Save keys');
  });

  it('saves changes and keeps the password when it is left empty', async () => {
    await load(stored);
    await type('videoExtensions', '.MKV, iso,');
    await type('excludedPaths', '/media/privat\n\n/media/tmp ');

    button('.save-button').click();
    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    request.flush({ ...stored, videoExtensions: ['mkv', 'iso'] });
    await fixture.whenStable();

    expect(request.request.body).toEqual({
      metadataLanguage: 'de-DE',
      artworkLanguage: 'de',
      videoExtensions: ['.MKV', 'iso'],
      excludedPaths: ['/media/privat', '/media/tmp'],
      proxy: { host: 'proxy.local', port: 3128, username: 'user', password: null },
      tv: { metadataLanguage: 'de-DE', artworkLanguage: 'de', episodeOrdering: 'AIRED' },
      mediaInfo: { ffprobePath: 'ffprobe', probeOnScrape: true, probeNewFiles: true },
      rename: stored.rename,
      movieSets: stored.movieSets,
    });
    expect(snackBarMessages).toEqual(['Einstellungen gespeichert.']);
    expect(field('videoExtensions').value).toBe('mkv, iso');
  });

  it('saves separate settings for TV shows', async () => {
    await load({
      ...stored,
      tv: { metadataLanguage: 'en-US', artworkLanguage: 'en', episodeOrdering: 'DVD' },
    });
    expect(field('tvMetadataLanguage').value).toBe('en-US');

    await type('tvArtworkLanguage', 'de');
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.tv).toEqual({
      metadataLanguage: 'en-US',
      artworkLanguage: 'de',
      episodeOrdering: 'DVD',
    });
    request.flush(stored);
  });

  it('saves the rename settings', async () => {
    await load(stored);
    expect(field('movieFolder').value).toBe('{title}[ ({year})]');

    await type('movieFile', ' {title} ');
    element().querySelector<HTMLInputElement>('[name="movieOnScrape"] input')!.click();
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.rename).toEqual({
      ...stored.rename,
      movieFile: '{title}',
      movieOnScrape: true,
    });
    request.flush(stored);
  });

  it('saves the movie set settings and requires a folder for an enabled storage', async () => {
    await load(stored);
    expect(field('flatImagesFolder').value).toBe('/media/filme/_filmsets');

    element().querySelector<HTMLInputElement>('[name="kodiImages"] input')!.click();
    await fixture.whenStable();
    button('.save-button').click();
    httpTesting.expectNone({ method: 'PUT', url: SETTINGS_URL });

    await type('kodiImagesFolder', ' /media/sets ');
    await type('flatImagesFolder', '');
    element().querySelector<HTMLInputElement>('[name="flatImages"] input')!.click();
    await fixture.whenStable();
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.movieSets).toEqual({
      flatImages: false,
      flatImagesFolder: null,
      kodiImages: true,
      kodiImagesFolder: '/media/sets',
      assignOnScrape: true,
    });
    request.flush(stored);
  });

  it('saves the ffprobe settings', async () => {
    await load(stored);

    await type('ffprobePath', ' C:\ffmpeg\bin\ffprobe.exe ');
    element().querySelector<HTMLInputElement>('[name="probeNewFiles"] input')!.click();
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.mediaInfo).toEqual({
      ffprobePath: 'C:\ffmpeg\bin\ffprobe.exe',
      probeOnScrape: true,
      probeNewFiles: false,
    });
    request.flush(stored);
  });

  it('changes and removes API keys without showing them', async () => {
    await load(stored);
    expect(field('TMDB_API_KEY').placeholder).toBe('gespeichert …cdef');
    expect(field('TVDB_API_KEY').placeholder).toBe('nicht gesetzt');
    expect(button('.save-keys-button').disabled).toBe(true);

    await type('TVDB_API_KEY', ' 0000-1111 ');
    const removeButtons = element().querySelectorAll<HTMLButtonElement>('.remove-key-button');
    expect(removeButtons).toHaveLength(2);
    removeButtons[1].click();
    fixture.detectChanges();
    expect(field('TVDB_PIN').placeholder).toBe('wird entfernt');
    button('.save-keys-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${SETTINGS_URL}/api-keys` });
    expect(request.request.body).toEqual({ TVDB_PIN: '', TVDB_API_KEY: '0000-1111' });
    request.flush(keys);
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['API-Keys gespeichert.']);
    expect(field('TVDB_API_KEY').value).toBe('');
  });

  it('reports failures around API keys', async () => {
    await load(stored);
    await type('TMDB_API_KEY', 'neu');

    button('.save-keys-button').click();
    httpTesting
      .expectOne({ method: 'PUT', url: `${SETTINGS_URL}/api-keys` })
      .flush({ detail: 'Zu lang' }, { status: 400, statusText: 'Bad Request' });
    expect(snackBarMessages).toEqual(['Zu lang']);

    fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: SETTINGS_URL }).flush(stored);
    await renderPendingChanges();
    httpTesting
      .expectOne({ method: 'GET', url: `${SETTINGS_URL}/api-keys` })
      .flush(null, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    expect(element().querySelector('.api-keys .error')).not.toBeNull();
  });

  it('sends no proxy when it is switched off', async () => {
    await load(stored);

    element().querySelector<HTMLInputElement>('[name="proxyEnabled"] input')!.click();
    await fixture.whenStable();
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.proxy).toBeNull();
    request.flush({ ...stored, proxy: null });
  });

  it('requires a proxy host when the proxy is switched on', async () => {
    await load({ ...stored, proxy: null });

    element().querySelector<HTMLInputElement>('[name="proxyEnabled"] input')!.click();
    await fixture.whenStable();
    button('.save-button').click();
    fixture.detectChanges();

    httpTesting.expectNone({ method: 'PUT' });
    expect(element().querySelector('mat-error')?.textContent).toContain(
      'Der Host ist erforderlich.',
    );
  });

  it('locks settings fixed by the configuration', async () => {
    await load({ ...stored, overridden: ['METADATA_LANGUAGE', 'PROXY'] });

    expect(field('metadataLanguage').disabled).toBe(true);
    expect(field('artworkLanguage').disabled).toBe(false);
    expect(field('proxyHost').disabled).toBe(true);
    expect(element().querySelector('.proxy-overridden')).not.toBeNull();
  });

  it('rejects an invalid language without saving', async () => {
    await load(stored);
    await type('metadataLanguage', 'deutsch');

    button('.save-button').click();

    httpTesting.expectNone({ method: 'PUT' });
  });

  it('reports a failed save and discards changes on request', async () => {
    await load(stored);
    await type('artworkLanguage', 'en');

    button('.save-button').click();
    httpTesting
      .expectOne({ method: 'PUT', url: SETTINGS_URL })
      .flush({ detail: 'Ungültig' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    button('.reset-button').click();
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Ungültig']);
    expect(field('artworkLanguage').value).toBe('de');
  });

  it('shows an error when the settings cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting
      .expectOne({ method: 'GET', url: SETTINGS_URL })
      .flush('boom', { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
