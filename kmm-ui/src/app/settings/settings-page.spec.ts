import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { SettingsView } from './settings';
import { SettingsPage } from './settings-page';
import { SETTINGS_URL } from './settings.service';

describe('SettingsPage', () => {
  const stored: SettingsView = {
    metadataLanguage: 'de-DE',
    artworkLanguage: 'de',
    videoExtensions: ['mkv', 'avi'],
    excludedPaths: ['/media/privat'],
    proxy: { host: 'proxy.local', port: 3128, username: 'user', passwordSet: true },
    overridden: [],
  };

  let fixture: ComponentFixture<SettingsPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];

  beforeEach(() => {
    snackBarMessages = [];
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open: (message: string) => snackBarMessages.push(message) } },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsPage);
  });

  afterEach(() => httpTesting.verify());

  async function load(view: SettingsView): Promise<void> {
    fixture.detectChanges();
    httpTesting.expectOne({ method: 'GET', url: SETTINGS_URL }).flush(view);
    await fixture.whenStable();
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
    });
    expect(snackBarMessages).toEqual(['Einstellungen gespeichert.']);
    expect(field('videoExtensions').value).toBe('mkv, iso');
  });

  it('sends no proxy when it is switched off', async () => {
    await load(stored);

    element().querySelector<HTMLInputElement>('mat-checkbox input')!.click();
    await fixture.whenStable();
    button('.save-button').click();

    const request = httpTesting.expectOne({ method: 'PUT', url: SETTINGS_URL });
    expect(request.request.body.proxy).toBeNull();
    request.flush({ ...stored, proxy: null });
  });

  it('requires a proxy host when the proxy is switched on', async () => {
    await load({ ...stored, proxy: null });

    element().querySelector<HTMLInputElement>('mat-checkbox input')!.click();
    await fixture.whenStable();
    button('.save-button').click();
    fixture.detectChanges();

    httpTesting.expectNone({ method: 'PUT' });
    expect(element().querySelector('mat-error')?.textContent).toContain('Der Host ist erforderlich.');
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
    httpTesting.expectOne({ method: 'GET', url: SETTINGS_URL }).flush('boom', { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    expect(element().querySelector('.error')).not.toBeNull();
  });
});
