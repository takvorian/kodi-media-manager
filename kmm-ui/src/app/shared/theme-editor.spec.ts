import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CONFIRM } from './confirm';
import { provideI18nTesting } from '../i18n/testing';
import { NO_THEME, switchLanguage } from './testing';
import { ThemeInfo, ThemeOption } from './theme';
import { ThemeEditor } from './theme-editor';

describe('ThemeEditor', () => {
  const endpoint = '/api/v1/tvshows/3/theme';
  const saved: ThemeInfo = {
    exists: true,
    path: '/serien/ALF/theme.mp3',
    sizeBytes: 1103792,
    modifiedAt: '2026-10-29T12:00:00Z',
  };
  const main: ThemeOption = {
    name: 'Main Theme',
    subject: 'ALF',
    source: 'http://televisiontunes.com/uploads/audio/Alf.mp3',
    previewUrl: 'http://televisiontunes.com/uploads/audio/Alf.mp3',
    webUrl: 'http://televisiontunes.com/Alf_2.html',
    durationSeconds: 31,
    scraperId: 'televisiontunes',
    scraperName: 'TelevisionTunes',
    sameTitle: true,
  };
  const video: ThemeOption = {
    ...main,
    name: 'ALF Intro (HD)',
    subject: null,
    source: 'https://www.youtube.com/watch?v=abc',
    previewUrl: null,
    durationSeconds: null,
    scraperId: 'youtube',
    scraperName: 'YouTube',
    sameTitle: false,
  };

  let fixture: ComponentFixture<ThemeEditor>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [ThemeEditor],
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
    fixture = TestBed.createComponent(ThemeEditor);
    fixture.componentRef.setInput('endpoint', endpoint);
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string): HTMLButtonElement {
    return element().querySelector<HTMLButtonElement>(selector)!;
  }

  async function show(info: ThemeInfo): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: endpoint }).flush(info);
    await fixture.whenStable();
  }

  it('plays an existing theme and says when there is none', async () => {
    await show(saved);

    expect(element().querySelector('audio.theme-player')?.getAttribute('src')).toBe(
      `${endpoint}/file?v=2026-10-29T12%3A00%3A00Z`,
    );
    expect(element().querySelector('.theme-size')?.textContent).toContain('1.078 KB');

    fixture.componentRef.setInput('endpoint', '/api/v1/movies/5/theme');
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/movies/5/theme').flush(NO_THEME);
    await fixture.whenStable();
    expect(element().querySelector('.no-theme')).not.toBeNull();
    expect(element().querySelector('.remove-theme-button')).toBeNull();
  });

  it('lists suggestions and saves one, asking before replacing an existing theme', async () => {
    await show(saved);

    button('.find-themes-button').click();
    httpTesting
      .expectOne(`${endpoint}/candidates`)
      .flush({ candidates: [main, video], warnings: ['YouTube: yt-dlp fehlt'] });
    await fixture.whenStable();

    const candidates = element().querySelectorAll('.theme-candidate');
    expect(candidates.length).toBe(2);
    expect(
      candidates[0].querySelector('.candidate-name')?.textContent?.replace(/\s+/g, ' ').trim(),
    ).toBe('ALF – Main Theme');
    expect(candidates[0].textContent).toContain('gleicher Titel');
    expect(candidates[0].querySelector('audio')?.getAttribute('src')).toBe(main.previewUrl);
    expect(candidates[1].querySelector('audio')).toBeNull();
    expect(candidates[1].querySelector('.candidate-name')?.textContent?.trim()).toBe(
      'ALF Intro (HD)',
    );
    expect(element().querySelector('.theme-warning')?.textContent).toBe('YouTube: yt-dlp fehlt');

    confirmAnswer = false;
    candidates[0].querySelector<HTMLButtonElement>('.apply-candidate-button')!.click();
    httpTesting.expectNone({ method: 'PUT', url: endpoint });

    confirmAnswer = true;
    candidates[0].querySelector<HTMLButtonElement>('.apply-candidate-button')!.click();
    const apply = httpTesting.expectOne({ method: 'PUT', url: endpoint });
    expect(apply.request.body).toEqual(main);
    apply.flush({ ...saved, modifiedAt: '2026-10-29T13:00:00Z' });
    await fixture.whenStable();

    expect(element().querySelector('.theme-candidate')).toBeNull();
    expect(snackBarMessages).toEqual(['Titelmusik als theme.mp3 gespeichert.']);
  });

  it('reports failed searches and downloads and closes empty results', async () => {
    await show(NO_THEME);

    button('.find-themes-button').click();
    httpTesting
      .expectOne(`${endpoint}/candidates`)
      .flush(
        { detail: 'TelevisionTunes ist nicht erreichbar.' },
        { status: 502, statusText: 'Bad Gateway' },
      );
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['TelevisionTunes ist nicht erreichbar.']);

    button('.find-themes-button').click();
    httpTesting.expectOne(`${endpoint}/candidates`).flush({ candidates: [video], warnings: [] });
    await fixture.whenStable();
    button('.apply-candidate-button').click();
    httpTesting
      .expectOne({ method: 'PUT', url: endpoint })
      .flush({ detail: 'yt-dlp fehlt.' }, { status: 502, statusText: 'Bad Gateway' });
    await fixture.whenStable();
    expect(snackBarMessages).toContain('yt-dlp fehlt.');

    button('.close-candidates-button').click();
    fixture.detectChanges();
    expect(element().querySelector('.theme-candidate')).toBeNull();

    button('.find-themes-button').click();
    httpTesting.expectOne(`${endpoint}/candidates`).flush({ candidates: [], warnings: [] });
    await fixture.whenStable();
    expect(element().querySelector('.no-candidates')).not.toBeNull();
  });

  it('deletes the theme after confirmation', async () => {
    await show(saved);

    confirmAnswer = false;
    button('.remove-theme-button').click();
    httpTesting.expectNone({ method: 'DELETE', url: endpoint });

    confirmAnswer = true;
    button('.remove-theme-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: endpoint }).flush(NO_THEME);
    await fixture.whenStable();

    expect(element().querySelector('.no-theme')).not.toBeNull();
    expect(snackBarMessages).toEqual(['Titelmusik gelöscht.']);
  });

  it('says when the theme cannot be checked', async () => {
    fixture.detectChanges();
    httpTesting.expectOne(endpoint).flush({}, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element().querySelector('.theme-error')).not.toBeNull();
  });

  it('switches its texts and number format to English at runtime', async () => {
    await show(saved);
    expect(element().querySelector('.kodi-hint')?.textContent).toContain('Add-on wie TvTunes');

    await switchLanguage('en');
    await fixture.whenStable();

    expect(element().querySelector('.theme-size')?.textContent).toContain('1,078 KB');
    expect(button('.remove-theme-button').textContent?.trim()).toBe('Delete');
    expect(button('.find-themes-button').textContent?.trim()).toBe('Find suggestions');
    expect(element().querySelector('.kodi-hint')?.textContent).toBe(
      'Kodi plays theme music only with an add-on such as TvTunes.',
    );

    button('.remove-theme-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: endpoint }).flush(NO_THEME);
    await fixture.whenStable();
    expect(snackBarMessages).toEqual(['Theme music deleted.']);
    expect(element().querySelector('.no-theme')?.textContent).toBe(
      'No theme music (theme.mp3) yet.',
    );
  });
});
