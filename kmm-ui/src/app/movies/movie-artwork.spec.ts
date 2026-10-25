import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CONFIRM } from '../shared/confirm';
import { ArtworkCandidate, ArtworkInfo } from './artwork';
import { MovieArtwork } from './movie-artwork';
import { MOVIES_URL } from './movie.service';

describe('MovieArtwork', () => {
  const artworkUrl = `${MOVIES_URL}/5/artwork`;
  const poster: ArtworkInfo = { type: 'POSTER', exists: true, path: '/m/Heat-poster.jpg', modifiedAt: '2026-10-25T12:00:00Z' };
  const fanart: ArtworkInfo = { type: 'FANART', exists: false, path: null, modifiedAt: null };
  const candidate: ArtworkCandidate = {
    scraperId: 'fanarttv',
    scraperName: 'fanart.tv',
    type: 'FANART',
    url: 'https://assets.fanart.tv/fanart/bg.jpg',
    previewUrl: 'https://assets.fanart.tv/preview/bg.jpg',
    width: 1920,
    height: 1080,
    language: null,
    rating: 6,
  };

  let fixture: ComponentFixture<MovieArtwork>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [MovieArtwork],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
        { provide: CONFIRM, useValue: () => confirmAnswer },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovieArtwork);
    fixture.componentRef.setInput('movieId', 5);
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function tile(type: string): HTMLElement {
    return element().querySelector<HTMLElement>(`[data-type="${type}"]`)!;
  }

  async function load(artwork: ArtworkInfo[]): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: artworkUrl }).flush(artwork);
    await fixture.whenStable();
  }

  async function reloadWith(artwork: ArtworkInfo[]): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: artworkUrl }).flush(artwork);
    await fixture.whenStable();
  }

  function expectCandidates(type: string): TestRequest {
    return httpTesting.expectOne({ method: 'GET', url: `${artworkUrl}/${type}/candidates` });
  }

  it('shows existing images and marks missing ones', async () => {
    await load([poster, fanart]);

    expect(tile('POSTER').querySelector('img')?.getAttribute('src')).toBe(
      `${artworkUrl}/POSTER/image?v=2026-10-25T12%3A00%3A00Z`,
    );
    expect(tile('FANART').querySelector('.missing')?.textContent).toBe('fehlt');
    expect(tile('FANART').querySelector('.change-button')?.textContent).toContain('Suchen');
    expect(tile('FANART').querySelector('.remove-button')).toBeNull();
  });

  it('lists candidates with source, language and size and applies the chosen one', async () => {
    await load([poster, fanart]);

    tile('FANART').querySelector<HTMLButtonElement>('.change-button')!.click();
    fixture.detectChanges();
    expect(element().querySelector('.loading-candidates')).not.toBeNull();
    expectCandidates('FANART').flush({ candidates: [candidate], warnings: [] });
    await fixture.whenStable();

    const option = element().querySelector<HTMLButtonElement>('.candidate')!;
    expect(option.querySelector('img')?.getAttribute('src')).toBe(candidate.previewUrl);
    expect(option.textContent?.replace(/\s+/g, ' ')).toContain('fanart.tv · ohne Text · 1920×1080');

    option.click();
    const request = httpTesting.expectOne({ method: 'PUT', url: `${artworkUrl}/FANART` });
    expect(request.request.body).toEqual({ url: candidate.url });
    request.flush({ ...fanart, exists: true, path: '/m/Heat-fanart.jpg', modifiedAt: '2026-10-25T13:00:00Z' });
    await reloadWith([poster, { ...fanart, exists: true, modifiedAt: '2026-10-25T13:00:00Z' }]);

    expect(snackBarMessages).toEqual(['Fanart von fanart.tv gespeichert.']);
    expect(element().querySelector('.selection')).toBeNull();
    expect(tile('FANART').querySelector('img')).not.toBeNull();
  });

  it('shows warnings and a hint when nothing was found', async () => {
    await load([poster]);

    tile('POSTER').querySelector<HTMLButtonElement>('.change-button')!.click();
    expectCandidates('POSTER').flush({ candidates: [], warnings: ['Für fanart.tv fehlt ein API-Key.'] });
    await fixture.whenStable();

    expect(element().querySelector('.warning')?.textContent).toContain('fanart.tv');
    expect(element().querySelector('.no-candidates')).not.toBeNull();
  });

  it('explains why candidates cannot be searched', async () => {
    await load([poster]);

    tile('POSTER').querySelector<HTMLButtonElement>('.change-button')!.click();
    expectCandidates('POSTER').flush(
      { detail: 'Für die Bildsuche wird eine TMDB- oder IMDb-ID benötigt.' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await fixture.whenStable();

    expect(element().querySelector('.selection .error')?.textContent).toContain('TMDB- oder IMDb-ID');
  });

  it('closes the selection when the same type is chosen again or via the close button', async () => {
    await load([poster]);

    tile('POSTER').querySelector<HTMLButtonElement>('.change-button')!.click();
    expectCandidates('POSTER').flush({ candidates: [], warnings: [] });
    tile('POSTER').querySelector<HTMLButtonElement>('.change-button')!.click();
    await fixture.whenStable();
    expect(element().querySelector('.selection')).toBeNull();

    tile('POSTER').querySelector<HTMLButtonElement>('.change-button')!.click();
    expectCandidates('POSTER').flush({ candidates: [], warnings: [] });
    await fixture.whenStable();
    element().querySelector<HTMLButtonElement>('.close-button')!.click();
    await fixture.whenStable();
    expect(element().querySelector('.selection')).toBeNull();
  });

  it('reports a failed download and keeps the selection open', async () => {
    await load([fanart]);
    tile('FANART').querySelector<HTMLButtonElement>('.change-button')!.click();
    expectCandidates('FANART').flush({ candidates: [candidate], warnings: [] });
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('.candidate')!.click();
    httpTesting
      .expectOne({ method: 'PUT', url: `${artworkUrl}/FANART` })
      .flush({ detail: 'Bild konnte nicht geladen werden' }, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Bild konnte nicht geladen werden']);
    expect(element().querySelector('.selection')).not.toBeNull();
  });

  it('deletes an image after confirmation', async () => {
    await load([poster]);

    tile('POSTER').querySelector<HTMLButtonElement>('.remove-button')!.click();
    httpTesting.expectOne({ method: 'DELETE', url: `${artworkUrl}/POSTER` }).flush(null);
    await reloadWith([{ ...poster, exists: false, modifiedAt: null }]);

    expect(snackBarMessages).toEqual(['Poster gelöscht.']);
  });

  it('keeps the image when deletion is not confirmed', async () => {
    await load([poster]);
    confirmAnswer = false;

    tile('POSTER').querySelector<HTMLButtonElement>('.remove-button')!.click();

    httpTesting.expectNone({ method: 'DELETE' });
  });

  it('reports a failed deletion', async () => {
    await load([poster]);

    tile('POSTER').querySelector<HTMLButtonElement>('.remove-button')!.click();
    httpTesting
      .expectOne({ method: 'DELETE', url: `${artworkUrl}/POSTER` })
      .flush({ detail: 'Schreibrechte fehlen' }, { status: 422, statusText: 'Unprocessable' });

    expect(snackBarMessages).toEqual(['Schreibrechte fehlen']);
  });

  it('shows an error when the artwork cannot be loaded', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(artworkUrl).flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
