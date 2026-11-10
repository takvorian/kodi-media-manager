import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { KodiConnectionTest, KodiHost, KodiHostCommand, KodiSyncResult } from './kodi';
import { KODI_HOSTS_URL, KodiService } from './kodi.service';

describe('KodiService', () => {
  const command: KodiHostCommand = {
    name: 'Wohnzimmer',
    address: 'kodi.local',
    port: 8080,
    username: null,
    password: null,
    realtimeSync: true,
    pathMappings: [{ localPath: '/media/filme', kodiPath: 'smb://nas/filme/' }],
  };
  const host: KodiHost = { ...command, id: 3, passwordSet: false, enabled: true, lastSync: null };

  let service: KodiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(KodiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('loads the hosts', () => {
    let received: KodiHost[] | undefined;

    service.hosts().subscribe((hosts) => (received = hosts));
    httpTesting.expectOne({ method: 'GET', url: KODI_HOSTS_URL }).flush([host]);

    expect(received).toEqual([host]);
  });

  it('creates, updates and deletes hosts', () => {
    service.create(command).subscribe();
    service.update(3, command).subscribe();
    service.delete(3).subscribe();

    const created = httpTesting.expectOne({ method: 'POST', url: KODI_HOSTS_URL });
    expect(created.request.body).toEqual(command);
    created.flush(host);
    httpTesting.expectOne({ method: 'PUT', url: `${KODI_HOSTS_URL}/3` }).flush(host);
    httpTesting.expectOne({ method: 'DELETE', url: `${KODI_HOSTS_URL}/3` }).flush(null);
  });

  it('switches a host on and off', () => {
    service.setEnabled(3, false).subscribe();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${KODI_HOSTS_URL}/3/enabled` });
    expect(request.request.body).toEqual({ enabled: false });
    request.flush({ ...host, enabled: false });
  });

  it('refreshes a movie and scans a source on all hosts', () => {
    let refreshed: KodiSyncResult[] | undefined;

    service.refreshMovie(5).subscribe((results) => (refreshed = results));
    service.scanSource(1).subscribe();
    service.refreshTvShow(3).subscribe();
    service.refreshSeason(3, 0).subscribe();
    service.scanTvShow(3).subscribe();
    service.scanSeason(3, 2).subscribe();
    service.scanTvShows([3, 4]).subscribe();
    service.refreshEpisode(12).subscribe();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/refresh' }).flush([]);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/scan' }).flush([]);
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/refresh' }).flush({});
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/scan' }).flush({});
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/seasons/2/scan' })
      .flush({});
    expect(
      httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/scan' }).request.body,
    ).toEqual({ showIds: [3, 4] });
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/tvshows/3/seasons/0/refresh' })
      .flush({});
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/episodes/12/refresh' }).flush([]);

    expect(refreshed).toEqual([]);
  });

  it('imports the watched status from Kodi', () => {
    service.importSourcePlayback(1).subscribe();
    service.importMoviePlayback(5).subscribe();
    service.importEpisodePlayback(12).subscribe();

    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/sources/1/playback' }).flush({});
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/movies/5/playback' }).flush({});
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/episodes/12/playback' }).flush({});
  });

  it('tests the connection', () => {
    let received: KodiConnectionTest | undefined;

    service.testConnection(3).subscribe((result) => (received = result));
    httpTesting
      .expectOne({ method: 'POST', url: `${KODI_HOSTS_URL}/3/test` })
      .flush({ version: 'Kodi 21.2' });

    expect(received?.version).toBe('Kodi 21.2');
  });
});
