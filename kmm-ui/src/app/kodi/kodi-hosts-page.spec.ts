import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { CONFIRM } from '../shared/confirm';
import { MediaSource } from '../sources/media-source';
import { SOURCES_URL } from '../sources/media-source.service';
import { KodiHost } from './kodi';
import { KodiHostsPage } from './kodi-hosts-page';
import { KODI_HOSTS_URL } from './kodi.service';
import { literal } from '../i18n/localized-text';

describe('KodiHostsPage', () => {
  const livingRoom: KodiHost = {
    id: 3,
    name: 'Wohnzimmer',
    address: 'kodi.local',
    port: 8080,
    username: 'kodi',
    passwordSet: true,
    realtimeSync: true,
    pathMappings: [{ localPath: '/media/filme', kodiPath: 'smb://nas/filme/' }],
    lastSync: {
      hostId: 3,
      host: 'Wohnzimmer',
      outcome: 'REFRESHED',
      message: literal('„Heat“ aktualisiert.'),
      at: '2026-10-25T20:00:00Z',
    },
  };
  const bedroom: KodiHost = {
    ...livingRoom,
    id: 4,
    name: 'Schlafzimmer',
    username: null,
    passwordSet: false,
    realtimeSync: false,
    pathMappings: [],
    lastSync: {
      hostId: 4,
      host: 'Schlafzimmer',
      outcome: 'FAILED',
      message: literal('nicht erreichbar.'),
      at: '2026-10-25T20:00:00Z',
    },
  };
  const films: MediaSource = {
    id: 1,
    name: 'Filme',
    path: '/media/filme',
    mediaType: 'MOVIE',
    createdAt: '2026-10-25T10:00:00Z',
    lastScannedAt: null,
    metadataLanguage: null,
  };

  let fixture: ComponentFixture<KodiHostsPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      imports: [KodiHostsPage],
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
    fixture = TestBed.createComponent(KodiHostsPage);
  });

  afterEach(() => httpTesting.verify());

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texts(selector: string): (string | undefined)[] {
    return Array.from(element().querySelectorAll(selector)).map((item) => item.textContent?.trim());
  }

  function field(name: string): HTMLInputElement {
    return element().querySelector<HTMLInputElement>(`[name="${name}"]`)!;
  }

  function buttons(selector: string): HTMLButtonElement[] {
    return Array.from(element().querySelectorAll<HTMLButtonElement>(selector));
  }

  async function type(name: string, value: string): Promise<void> {
    field(name).value = value;
    field(name).dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function loadHosts(hosts: KodiHost[]): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: KODI_HOSTS_URL }).flush(hosts);
    await fixture.whenStable();
  }

  async function answerReload(hosts: KodiHost[]): Promise<void> {
    fixture.detectChanges();
    await loadHosts(hosts);
  }

  async function openNewHost(sources: MediaSource[]): Promise<void> {
    buttons('.add-button')[0].click();
    httpTesting.expectOne(SOURCES_URL).flush(sources);
    await fixture.whenStable();
  }

  it('lists the hosts with their mappings', async () => {
    await loadHosts([livingRoom, bedroom]);

    expect(texts('.host-name')).toEqual(['Wohnzimmer', 'Schlafzimmer']);
    expect(texts('.host-address')[0]).toBe('kodi.local:8080 · Benutzer kodi');
    expect(texts('.host-address')[1]).toBe('kodi.local:8080');
    expect(texts('.realtime')).toEqual([
      'Echtzeit-Sync: Kodi wird nach jeder Änderung aktualisiert.',
      'Kein Echtzeit-Sync.',
    ]);
    expect(texts('.mappings li')).toEqual(['/media/filme → smb://nas/filme/']);
    expect(element().querySelectorAll('.no-mappings')).toHaveLength(1);
    expect(texts('.last-sync')[0]).toMatch(/^Letzter Abgleich .+: „Heat“ aktualisiert\.$/);
    expect(element().querySelectorAll('.last-sync.error')).toHaveLength(1);
  });

  it('switches the texts to English at runtime', async () => {
    await loadHosts([livingRoom, bedroom]);

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();
    buttons('.test-button')[0].click();
    httpTesting.expectOne(`${KODI_HOSTS_URL}/3/test`).flush({ version: 'Kodi 21.2' });
    await fixture.whenStable();

    expect(texts('.host-address')[0]).toBe('kodi.local:8080 · User kodi');
    expect(texts('.realtime')).toEqual([
      'Real-time sync: Kodi is updated after every change.',
      'No real-time sync.',
    ]);
    expect(texts('.last-sync')[0]).toMatch(/^Last sync .+: „Heat“ aktualisiert\.$/);
    expect(texts('.connection-result')).toEqual(['Connected to Kodi 21.2']);
    expect(texts('.add-button')).toEqual(['Add Kodi device']);
  });

  it('shows an empty list and a load error', async () => {
    await loadHosts([]);
    expect(element().querySelector('.empty')?.textContent).toContain('Noch kein Kodi-Gerät');

    fixture = TestBed.createComponent(KodiHostsPage);
    fixture.detectChanges();
    httpTesting
      .expectOne(KODI_HOSTS_URL)
      .flush({ detail: 'kaputt' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(element().querySelector('.error')?.textContent).toContain(
      'konnten nicht geladen werden',
    );
  });

  it('creates a host with the source directories as suggestions', async () => {
    await loadHosts([]);
    await openNewHost([films]);

    expect(field('localPath0').value).toBe('/media/filme');
    expect(field('port').value).toBe('8080');
    await type('name', ' Wohnzimmer ');
    await type('address', 'kodi.local');
    await type('username', 'kodi');
    await type('password', 'geheim');
    await type('kodiPath0', 'smb://nas/filme/');
    buttons('.save-button')[0].click();

    const request = httpTesting.expectOne({ method: 'POST', url: KODI_HOSTS_URL });
    expect(request.request.body).toEqual({
      name: 'Wohnzimmer',
      address: 'kodi.local',
      port: 8080,
      username: 'kodi',
      password: 'geheim',
      realtimeSync: true,
      pathMappings: [{ localPath: '/media/filme', kodiPath: 'smb://nas/filme/' }],
    });
    request.flush(livingRoom);
    await answerReload([livingRoom]);

    expect(snackBarMessages).toEqual(['Kodi-Gerät „Wohnzimmer“ gespeichert.']);
    expect(element().querySelector('.editor')).toBeNull();
  });

  it('opens an empty editor when the sources cannot be loaded', async () => {
    await loadHosts([]);
    buttons('.add-button')[0].click();
    httpTesting.expectOne(SOURCES_URL).flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(element().querySelector('.editor')).not.toBeNull();
    expect(field('localPath0')).toBeNull();
  });

  it('does not save an invalid host', async () => {
    await loadHosts([]);
    await openNewHost([films]);
    await type('name', 'Wohnzimmer');
    await type('address', 'http://kodi.local');

    buttons('.save-button')[0].click();

    httpTesting.expectNone({ method: 'POST' });
  });

  it('edits a host and keeps the password when left empty', async () => {
    await loadHosts([livingRoom]);
    buttons('.edit-button')[0].click();
    await fixture.whenStable();

    expect(field('name').value).toBe('Wohnzimmer');
    expect(element().querySelector('mat-hint')?.textContent).toContain('gespeicherte Passwort');
    buttons('.add-mapping-button')[0].click();
    await fixture.whenStable();
    await type('localPath1', '/media/kinder');
    await type('kodiPath1', 'nfs://nas/kinder/');
    buttons('.remove-mapping-button')[0].click();
    await fixture.whenStable();
    element().querySelector<HTMLInputElement>('mat-checkbox input')!.click();
    await fixture.whenStable();
    buttons('.save-button')[0].click();

    const request = httpTesting.expectOne({ method: 'PUT', url: `${KODI_HOSTS_URL}/3` });
    expect(request.request.body.password).toBeNull();
    expect(request.request.body.realtimeSync).toBe(false);
    expect(request.request.body.pathMappings).toEqual([
      { localPath: '/media/kinder', kodiPath: 'nfs://nas/kinder/' },
    ]);
    request.flush({ detail: 'Name bereits vergeben' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(snackBarMessages).toEqual(['Name bereits vergeben']);
    expect(element().querySelector('.editor')).not.toBeNull();
  });

  it('cancels editing', async () => {
    await loadHosts([livingRoom]);
    buttons('.edit-button')[0].click();
    await fixture.whenStable();

    buttons('.cancel-button')[0].click();
    await fixture.whenStable();

    expect(element().querySelector('.editor')).toBeNull();
  });

  it('tests the connection and shows the result', async () => {
    await loadHosts([livingRoom, bedroom]);

    buttons('.test-button')[0].click();
    buttons('.test-button')[1].click();
    fixture.detectChanges();
    expect(buttons('.test-button')[0].textContent).toContain('Teste');
    httpTesting.expectOne(`${KODI_HOSTS_URL}/3/test`).flush({ version: 'Kodi 21.2' });
    httpTesting
      .expectOne(`${KODI_HOSTS_URL}/4/test`)
      .flush(
        { detail: 'Kodi unter kodi.local ist nicht erreichbar.' },
        { status: 502, statusText: 'Bad Gateway' },
      );
    await fixture.whenStable();

    expect(texts('.connection-result')).toEqual([
      'Verbunden mit Kodi 21.2',
      'Kodi unter kodi.local ist nicht erreichbar.',
    ]);
    expect(element().querySelectorAll('.connection-result.ok')).toHaveLength(1);
    expect(element().querySelector('.connection-warning')).toBeNull();
  });

  it('warns about mapped paths without a source in Kodi', async () => {
    await loadHosts([livingRoom]);

    buttons('.test-button')[0].click();
    httpTesting.expectOne(`${KODI_HOSTS_URL}/3/test`).flush({
      version: 'Kodi 21.2',
      warning: literal('Kodi hat keine Quelle für a:\\filme.'),
    });
    await fixture.whenStable();

    expect(texts('.connection-result')).toEqual(['Verbunden mit Kodi 21.2']);
    expect(texts('.connection-warning')).toEqual(['Kodi hat keine Quelle für a:\\filme.']);
  });

  it('deletes a host after confirmation', async () => {
    await loadHosts([livingRoom]);
    confirmAnswer = false;
    buttons('.delete-button')[0].click();
    httpTesting.expectNone({ method: 'DELETE' });

    confirmAnswer = true;
    buttons('.delete-button')[0].click();
    httpTesting.expectOne({ method: 'DELETE', url: `${KODI_HOSTS_URL}/3` }).flush(null);
    await answerReload([]);

    expect(snackBarMessages).toEqual(['Kodi-Gerät „Wohnzimmer“ entfernt.']);
  });

  it('reports a failed deletion', async () => {
    await loadHosts([livingRoom]);

    buttons('.delete-button')[0].click();
    httpTesting
      .expectOne({ method: 'DELETE', url: `${KODI_HOSTS_URL}/3` })
      .flush({ detail: 'nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(snackBarMessages).toEqual(['nicht gefunden']);
  });
});
