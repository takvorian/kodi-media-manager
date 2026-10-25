import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CONFIRM } from '../shared/confirm';
import { MediaSource } from './media-source';
import { SOURCES_URL } from './media-source.service';
import { SourceList } from './source-list';

describe('SourceList', () => {
  const films: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
  };
  const series: MediaSource = { ...films, id: 2, name: 'Serien', path: '/media/serien', mediaType: 'TV_SHOW' };

  let fixture: ComponentFixture<SourceList>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [SourceList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
        { provide: CONFIRM, useValue: () => confirmAnswer },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SourceList);
  });

  afterEach(() => httpTesting.verify());

  async function loadSources(sources: MediaSource[]): Promise<void> {
    fixture.detectChanges();
    await answerReload(sources);
  }

  /** Beantwortet das (Neu-)Laden der Liste, das ein Resource-Reload beim nächsten Rendern auslöst. */
  async function answerReload(sources: MediaSource[]): Promise<void> {
    fixture.detectChanges();
    expectList().flush(sources);
    await fixture.whenStable();
  }

  function expectList(): TestRequest {
    return httpTesting.expectOne({ method: 'GET', url: SOURCES_URL });
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(selector: string, row = 0): HTMLButtonElement {
    return element().querySelectorAll<HTMLButtonElement>(selector)[row];
  }

  async function fillForm(name: string, path: string): Promise<void> {
    for (const [field, value] of [['name', name], ['path', path]]) {
      const input = element().querySelector<HTMLInputElement>(`input[name="${field}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
  }

  it('lists the sources', async () => {
    await loadSources([films, series]);

    expect(Array.from(element().querySelectorAll('.source-name')).map((cell) => cell.textContent)).toEqual([
      'Filme',
      'Serien',
    ]);
  });

  it('offers scanning only for movie sources', async () => {
    await loadSources([films, series]);

    expect(element().querySelectorAll('.scan-button')).toHaveLength(1);
  });

  it('shows a hint without sources', async () => {
    await loadSources([]);

    expect(element().querySelector('.empty')?.textContent).toContain('Noch keine Quellen');
  });

  it('shows an error when the sources cannot be loaded', async () => {
    fixture.detectChanges();
    expectList().flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });

  it('creates a source and reloads the list', async () => {
    await loadSources([]);
    await fillForm('Filme', '/media/filme');

    button('.create-button').click();
    const request = httpTesting.expectOne({ method: 'POST', url: SOURCES_URL });
    expect(request.request.body).toEqual({ name: 'Filme', path: '/media/filme', mediaType: 'MOVIE' });
    request.flush(films);
    await answerReload([films]);

    expect(snackBarMessages).toEqual(['Quelle „Filme“ angelegt.']);
    expect(element().querySelector('.source-name')?.textContent).toBe('Filme');
  });

  it('keeps the create button disabled while the form is incomplete', async () => {
    await loadSources([]);
    await fillForm('Filme', '');

    expect(button('.create-button').disabled).toBe(true);
  });

  it('shows the problem detail when creating fails', async () => {
    await loadSources([]);
    await fillForm('Filme', '/fehlt');

    button('.create-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: SOURCES_URL })
      .flush({ detail: "'/fehlt' ist kein erreichbares Verzeichnis." }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(["'/fehlt' ist kein erreichbares Verzeichnis."]);
    expect(button('.create-button').disabled).toBe(false);
  });

  it('scans a source and reports the result', async () => {
    await loadSources([films]);

    button('.scan-button').click();
    fixture.detectChanges();
    expect(button('.scan-button').textContent).toContain('Scanne');
    httpTesting
      .expectOne({ method: 'POST', url: `${SOURCES_URL}/1/scan` })
      .flush({ sourceId: 1, added: 3, removed: 1, unchanged: 7 });
    await answerReload([{ ...films, lastScannedAt: '2026-10-25T12:00:00Z' }]);

    expect(snackBarMessages).toEqual(['„Filme“ gescannt: 3 neu, 1 entfernt, 7 unverändert.']);
    expect(button('.scan-button').textContent).toContain('Scannen');
  });

  it('reports a failed scan', async () => {
    await loadSources([films]);

    button('.scan-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: `${SOURCES_URL}/1/scan` })
      .flush({ detail: 'nicht erreichbar' }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['nicht erreichbar']);
    expect(button('.scan-button').disabled).toBe(false);
  });

  it('deletes a source after confirmation', async () => {
    await loadSources([films]);

    button('.delete-button').click();
    httpTesting.expectOne({ method: 'DELETE', url: `${SOURCES_URL}/1` }).flush(null);
    await answerReload([]);

    expect(snackBarMessages).toEqual(['Quelle „Filme“ entfernt.']);
  });

  it('keeps the source when deletion is not confirmed', async () => {
    await loadSources([films]);
    confirmAnswer = false;

    button('.delete-button').click();

    httpTesting.expectNone({ method: 'DELETE' });
  });

  it('reports a failed deletion', async () => {
    await loadSources([films]);

    button('.delete-button').click();
    httpTesting
      .expectOne({ method: 'DELETE', url: `${SOURCES_URL}/1` })
      .flush({ title: 'Quelle nicht gefunden' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Quelle nicht gefunden']);
  });
});
