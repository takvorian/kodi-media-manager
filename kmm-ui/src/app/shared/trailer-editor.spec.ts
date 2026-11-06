import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { provideI18nTesting } from '../i18n/testing';
import { CONFIRM } from './confirm';
import { switchLanguage } from './testing';
import { TrailerOption, WithTrailer } from './trailer';
import { TrailerEditor } from './trailer-editor';
import { literal } from '../i18n/localized-text';

describe('TrailerEditor', () => {
  const trailerUrl = '/api/v1/tvshows/5/trailer';
  const youtube = 'plugin://plugin.video.youtube/?action=play_video&videoid=2GfZl4kuVNI';
  const german: TrailerOption = {
    name: 'HEAT | Trailer | Deutsch',
    url: youtube,
    webUrl: 'https://www.youtube.com/watch?v=2GfZl4kuVNI',
    previewUrl: 'https://img.youtube.com/vi/2GfZl4kuVNI/mqdefault.jpg',
    language: 'de',
    type: 'Trailer',
    official: true,
    height: 1080,
    published: '2015-06-01',
    scraperName: 'TMDB',
  };
  const teaser: TrailerOption = {
    ...german,
    name: 'Heat - Teaser',
    url: 'plugin://plugin.video.youtube/?action=play_video&videoid=14oNcFxiVaQ',
    previewUrl: null,
    language: null,
    type: 'Teaser',
    official: false,
    height: null,
    published: null,
  };

  let fixture: ComponentFixture<TrailerEditor>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  let emitted: (string | null)[];

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    emitted = [];
    TestBed.configureTestingModule({
      imports: [TrailerEditor],
      providers: [
        provideI18nTesting(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        { provide: CONFIRM, useValue: () => confirmAnswer },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TrailerEditor);
    fixture.componentRef.setInput('endpoint', trailerUrl);
    fixture.componentInstance.changed.subscribe((trailer) => emitted.push(trailer));
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  function details(trailer: string | null): WithTrailer {
    return { trailer };
  }

  function show(trailer: string | null): void {
    fixture.componentRef.setInput('trailer', trailer);
    fixture.detectChanges();
  }

  it('shows the link and where to watch it', () => {
    show(youtube);

    expect(element().querySelector('.trailer-link')?.textContent).toBe(youtube);
    expect(element().querySelector('.watch-link')?.getAttribute('href')).toBe(
      'https://www.youtube.com/watch?v=2GfZl4kuVNI',
    );

    show('https://example.org/heat.mp4');
    expect(element().querySelector('.watch-link')?.getAttribute('href')).toBe(
      'https://example.org/heat.mp4',
    );

    show('smb://nas/trailer/heat.mkv');
    expect(element().querySelector('.watch-link')).toBeNull();

    show(null);
    expect(element().querySelector('.no-trailer')?.textContent).toContain('Kein Trailer');
    expect(element().querySelector('.remove-trailer-button')).toBeNull();
  });

  it('lists the candidates and applies one', async () => {
    show(null);

    button('.find-trailers-button').click();
    httpTesting
      .expectOne({ method: 'GET', url: `${trailerUrl}/candidates` })
      .flush({ candidates: [german, teaser], warnings: [literal('YouTube: Key fehlt')] });
    await fixture.whenStable();

    const candidates = element().querySelectorAll('.trailer-candidate');
    expect(candidates.length).toBe(2);
    expect(candidates[0].textContent).toContain('1080p');
    expect(candidates[0].textContent).toContain('offiziell');
    expect(candidates[0].querySelector('img')?.getAttribute('src')).toBe(german.previewUrl);
    expect(candidates[1].textContent).toContain('ohne Sprache');
    expect(candidates[1].querySelector('img')).toBeNull();
    expect(element().querySelector('.trailer-warning')?.textContent).toBe('YouTube: Key fehlt');

    candidates[0].querySelector<HTMLButtonElement>('.apply-candidate-button')!.click();
    const apply = httpTesting.expectOne({ method: 'PUT', url: trailerUrl });
    expect(apply.request.body).toEqual({ url: youtube });
    apply.flush(details(youtube));
    await fixture.whenStable();

    expect(emitted).toEqual([youtube]);
    expect(element().querySelector('.trailer-candidate')).toBeNull();
    expect(snackBarMessages).toEqual(['Trailer übernommen und in die NFO geschrieben.']);
  });

  it('marks the current trailer among the candidates and says when there are none', async () => {
    show(youtube);

    button('.find-trailers-button').click();
    httpTesting.expectOne(`${trailerUrl}/candidates`).flush({ candidates: [german], warnings: [] });
    await fixture.whenStable();
    expect(button('.apply-candidate-button').disabled).toBe(true);
    expect(button('.apply-candidate-button').textContent).toContain('Eingetragen');

    button('.close-candidates-button').click();
    fixture.detectChanges();
    expect(element().querySelector('.trailer-candidate')).toBeNull();

    button('.find-trailers-button').click();
    httpTesting.expectOne(`${trailerUrl}/candidates`).flush({ candidates: [], warnings: [] });
    await fixture.whenStable();
    expect(element().querySelector('.no-candidates')).not.toBeNull();
  });

  it('applies an own link and reports rejected ones', async () => {
    show(null);
    const field = element().querySelector<HTMLInputElement>('input[name="trailerUrl"]')!;
    expect(button('.apply-link-button').disabled).toBe(true);

    field.value = ' ftp://nas/heat.mkv ';
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button('.apply-link-button').click();
    const rejected = httpTesting.expectOne({ method: 'PUT', url: trailerUrl });
    expect(rejected.request.body).toEqual({ url: 'ftp://nas/heat.mkv' });
    rejected.flush(
      { detail: 'Kein abspielbarer Link.' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Kein abspielbarer Link.']);
    expect(emitted).toEqual([]);
    expect(button('.apply-link-button').disabled).toBe(false);
  });

  it('removes the trailer after confirmation', async () => {
    show(youtube);
    confirmAnswer = false;
    button('.remove-trailer-button').click();
    httpTesting.expectNone(trailerUrl);

    confirmAnswer = true;
    button('.remove-trailer-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: trailerUrl }).flush(details(null));
    await fixture.whenStable();

    expect(emitted).toEqual([null]);
    expect(snackBarMessages).toEqual(['Trailer entfernt.']);
  });

  it('reports candidates that cannot be searched', async () => {
    show(null);

    button('.find-trailers-button').click();
    httpTesting
      .expectOne(`${trailerUrl}/candidates`)
      .flush(
        { detail: 'Bitte zuerst „Online suchen“ nutzen.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Bitte zuerst „Online suchen“ nutzen.']);
    expect(button('.find-trailers-button').disabled).toBe(false);
  });

  it('switches its texts to English at runtime', async () => {
    show(null);
    await switchLanguage('en');
    await fixture.whenStable();

    expect(element().querySelector('.no-trailer')?.textContent).toBe('No trailer set.');
    expect(element().querySelector('.manual-link mat-label')?.textContent).toBe('Custom link');

    button('.find-trailers-button').click();
    httpTesting
      .expectOne({ method: 'GET', url: `${trailerUrl}/candidates` })
      .flush({ candidates: [german, teaser], warnings: [] });
    await fixture.whenStable();

    const candidates = element().querySelectorAll('.trailer-candidate');
    expect(candidates[0].textContent).toContain('official');
    expect(candidates[1].textContent).toContain('no language');
    expect(candidates[0].querySelector('.apply-candidate-button')?.textContent?.trim()).toBe(
      'Apply',
    );

    candidates[0].querySelector<HTMLButtonElement>('.apply-candidate-button')!.click();
    httpTesting.expectOne({ method: 'PUT', url: trailerUrl }).flush(details(youtube));
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['Trailer applied and written to the NFO.']);
  });
});
