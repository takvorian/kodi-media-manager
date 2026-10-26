import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { EpisodeDetail } from './episode-detail';
import { EpisodeDetails } from './tvshow';
import { flushWhenRequested } from '../shared/testing';
import { EPISODES_URL } from './tvshow.service';

describe('EpisodeDetail', () => {
  const actors = Array.from({ length: 20 }, (_, index) => ({ name: `Gast ${index}`, role: null, thumb: null }));
  const gestatten: EpisodeDetails = {
    id: 12,
    tvShowId: 3,
    showTitle: 'ALF',
    filePath: '/media/serien/ALF/S01E16E17.avi',
    season: 1,
    episode: 16,
    addedAt: '2026-10-26T10:00:00Z',
    metadata: {
      title: 'Gestatten – Teil 1',
      originalTitle: null,
      plot: null,
      aired: '1987-02-09',
      runtime: 30,
      tvdbId: '110001',
      tmdbId: null,
      imdbId: null,
      userRating: null,
    },
    extras: {
      directors: ['Tom Patchett'],
      writers: ['Bob Bendetson'],
      actors: [{ name: 'Bill Daily', role: 'Dr. Larry Dykstra', thumb: null }, ...actors],
      ratings: [],
      playCount: 1,
      lastPlayed: '2014-05-24T23:21:47Z',
      streams: { videos: [], audios: [{ codec: 'ac3', language: null, channels: 1, bitrate: null }], subtitles: [] },
    },
    nfo: { path: '/media/serien/ALF/S01E16E17.nfo', exists: true, modifiedAt: '2026-10-26T09:00:00Z' },
  };

  let fixture: ComponentFixture<EpisodeDetail>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      imports: [EpisodeDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EpisodeDetail);
    fixture.componentRef.setInput('id', '12');
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function input(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  async function load(details: EpisodeDetails): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: `${EPISODES_URL}/12` }).flush(details);
    await flushWhenRequested(fixture, httpTesting, `${EPISODES_URL}/12/artwork`, []);
    await fixture.whenStable();
  }

  it('shows the episode with crew, viewing state and a shortened cast', async () => {
    await load(gestatten);

    expect(element().querySelector('h2')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('S01E16 Gestatten – Teil 1');
    expect(element().querySelector('.back-link')?.getAttribute('href')).toBe('/tvshows/3');
    expect(element().querySelector('.directors')?.textContent).toBe('Tom Patchett');
    expect(element().querySelector('.writers')?.textContent).toBe('Bob Bendetson');
    expect(element().querySelector('.play-count')?.textContent).toContain('1×');
    expect(element().querySelectorAll('.actors li')).toHaveLength(15);
    element().querySelector<HTMLButtonElement>('.toggle-actors')!.click();
    await fixture.whenStable();
    expect(element().querySelectorAll('.actors li')).toHaveLength(21);
    expect(input('aired').value).toBe('1987-02-09');
    expect(element().querySelector('.audios')?.textContent?.trim()).toBe('Dolby Digital · Mono');
  });

  it('refreshes the episode in Kodi on request', async () => {
    await load(gestatten);

    element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/v1/kodi/episodes/12/refresh' }).flush([]);
    element().querySelector<HTMLButtonElement>('.kodi-refresh-button')!.click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/kodi/episodes/12/refresh' })
      .flush({ detail: 'Kodi nicht erreichbar' }, { status: 502, statusText: 'Bad Gateway' });

    expect(snackBarMessages).toEqual(['Kein Kodi-Gerät eingerichtet – unter „Kodi“ anlegen.', 'Kodi nicht erreichbar']);
  });

  it('saves the metadata', async () => {
    await load({ ...gestatten, nfo: { ...gestatten.nfo, exists: false, modifiedAt: null } });
    expect(element().querySelector('.nfo-status')?.textContent).toContain('Noch keine NFO');

    input('plot').value = 'Alf verliert sein Gedächtnis.';
    input('plot').dispatchEvent(new Event('input'));
    input('imdbId').value = '';
    input('imdbId').dispatchEvent(new Event('input'));
    await fixture.whenStable();
    element().querySelector<HTMLButtonElement>('.save-button')!.click();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${EPISODES_URL}/12/metadata` });
    expect(request.request.body).toEqual({ ...gestatten.metadata, plot: 'Alf verliert sein Gedächtnis.' });
    request.flush(gestatten);
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Gespeichert und in /media/serien/ALF/S01E16E17.nfo geschrieben.']);
  });

  it('validates, discards and reports failures', async () => {
    await load(gestatten);
    input('userRating').value = '11';
    input('userRating').dispatchEvent(new Event('input'));
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('.save-button')!.click();
    httpTesting.expectNone({ method: 'PUT' });
    element().querySelector<HTMLButtonElement>('.reset-button')!.click();
    await fixture.whenStable();
    expect(input('userRating').value).toBe('');

    input('title').value = 'Neu';
    input('title').dispatchEvent(new Event('input'));
    await fixture.whenStable();
    element().querySelector<HTMLButtonElement>('.save-button')!.click();
    httpTesting
      .expectOne({ method: 'PUT', url: `${EPISODES_URL}/12/metadata` })
      .flush({ detail: 'Keine Schreibrechte' }, { status: 422, statusText: 'Unprocessable' });
    expect(snackBarMessages).toEqual(['Keine Schreibrechte']);
  });

  it('shows an error when the episode cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(`${EPISODES_URL}/12`).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
