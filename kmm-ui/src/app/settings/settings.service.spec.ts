import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { KmmSettings, SettingsView } from './settings';
import { SETTINGS_URL, SettingsService } from './settings.service';

describe('SettingsService', () => {
  const view: SettingsView = {
    metadataLanguage: 'de-DE',
    artworkLanguage: 'de',
    videoExtensions: ['mkv'],
    excludedPaths: [],
    proxy: null,
    tv: { metadataLanguage: 'de-DE', artworkLanguage: 'de', episodeOrdering: 'AIRED' },
    mediaInfo: { ffprobePath: 'ffprobe', probeOnScrape: true, probeNewFiles: true },
    overridden: [],
  };

  let service: SettingsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SettingsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the settings', () => {
    let received: SettingsView | undefined;

    service.load().subscribe((settings) => (received = settings));
    httpTesting.expectOne({ method: 'GET', url: SETTINGS_URL }).flush(view);

    expect(received).toEqual(view);
  });

  it('saves the settings', () => {
    const settings: KmmSettings = { ...view, proxy: null };

    service.save(settings).subscribe();
    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    request.flush(view);

    expect(request.request.body).toEqual(settings);
  });

  it('loads and saves API keys', () => {
    service.apiKeys().subscribe();
    service.saveApiKeys({ TMDB_API_KEY: 'neu', TVDB_PIN: '' }).subscribe();

    httpTesting.expectOne({ method: 'GET', url: `${SETTINGS_URL}/api-keys` }).flush([]);
    const request = httpTesting.expectOne({ method: 'PUT', url: `${SETTINGS_URL}/api-keys` });
    expect(request.request.body).toEqual({ TMDB_API_KEY: 'neu', TVDB_PIN: '' });
    request.flush([]);
  });
});
