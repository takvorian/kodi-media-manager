import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { provideI18nTesting } from '../../i18n/testing';
import { CONFIRM } from '../../shared/confirm';
import { SCRAPER_PLUGINS_URL } from './scraper-admin.service';
import { ScraperListPage } from './scraper-list-page';
import { ScraperPage } from './scraper-page';
import { ScraperPlugin, ScraperPluginDetails } from './scraper-plugin';

const TVDB: ScraperPlugin = {
  id: 'tvdb',
  displayName: 'TheTVDB',
  version: '1.0.0',
  description: 'TV shows from TheTVDB',
  enabled: true,
  capabilities: [
    { kind: 'METADATA', mediaTypes: ['TV_SHOW'] },
    { kind: 'ARTWORK', mediaTypes: ['TV_SHOW', 'TV_SEASON'] },
  ],
  missingConfiguration: { key: 'scraper.apiKeyMissing', args: { provider: 'TVDB' } },
  attribution: null,
};

const TRAKT: ScraperPlugin = {
  ...TVDB,
  id: 'trakt',
  displayName: 'trakt',
  enabled: false,
  capabilities: [],
  missingConfiguration: null,
};

const DETAILS: ScraperPluginDetails = {
  scraper: TVDB,
  settings: [
    {
      name: 'api-key',
      type: 'SECRET',
      label: { key: 'scraper.setting.tvdb.apiKey', args: {} },
      value: null,
      defaultValue: null,
      set: true,
      hint: '…cdef',
    },
    {
      name: 'pin',
      type: 'SECRET',
      label: { key: 'scraper.setting.tvdb.pin', args: {} },
      value: null,
      defaultValue: null,
      set: false,
      hint: null,
    },
    {
      name: 'certification-country',
      type: 'TEXT',
      label: { key: 'scraper.setting.tvdb.certificationCountry', args: {} },
      value: 'AT',
      defaultValue: 'DE',
      set: true,
      hint: null,
    },
  ],
};

describe('scraper settings pages', () => {
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    TestBed.configureTestingModule({
      providers: [
        provideI18nTesting(),
        provideRouter([]),
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
  });

  afterEach(() => httpTesting.verify());

  function text(fixture: ComponentFixture<unknown>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  describe('ScraperListPage', () => {
    it('lists what each scraper delivers and whether it is ready', async () => {
      const fixture = TestBed.createComponent(ScraperListPage);
      fixture.detectChanges();
      httpTesting.expectOne(SCRAPER_PLUGINS_URL).flush([TVDB, TRAKT]);
      await fixture.whenStable();
      fixture.detectChanges();

      const entries = (fixture.nativeElement as HTMLElement).querySelectorAll('.scraper-entry');
      expect(entries).toHaveLength(2);
      expect(entries[0].getAttribute('href')).toBe('/tvdb');
      expect(entries[0].textContent).toContain('Serien, Staffeln');
      expect(entries[0].textContent).toContain('Einstellung fehlt');
      expect(entries[1].textContent).toContain('ausgeschaltet');
    });

    it('reports when the scrapers cannot be loaded', async () => {
      const fixture = TestBed.createComponent(ScraperListPage);
      fixture.detectChanges();
      httpTesting.expectOne(SCRAPER_PLUGINS_URL).flush(null, { status: 500, statusText: 'Error' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(text(fixture)).toContain('Die Scraper konnten nicht geladen werden.');
    });
  });

  describe('ScraperPage', () => {
    let fixture: ComponentFixture<ScraperPage>;

    async function load(details: ScraperPluginDetails = DETAILS): Promise<void> {
      fixture = TestBed.createComponent(ScraperPage);
      fixture.componentRef.setInput('id', details.scraper.id);
      fixture.detectChanges();
      httpTesting.expectOne(`${SCRAPER_PLUGINS_URL}/${details.scraper.id}`).flush(details);
      await fixture.whenStable();
      fixture.detectChanges();
    }

    function field(name: string): HTMLInputElement {
      return (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
        `input[name="${name}"]`,
      )!;
    }

    function saveButton(): HTMLButtonElement {
      return (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
        '.save-settings-button',
      )!;
    }

    async function type(name: string, value: string): Promise<void> {
      field(name).value = value;
      field(name).dispatchEvent(new Event('input'));
      await fixture.whenStable();
      fixture.detectChanges();
    }

    it('shows keys only as stored and other settings with their default', async () => {
      await load();

      expect(field('api-key').type).toBe('password');
      expect(field('api-key').value).toBe('');
      expect(field('api-key').placeholder).toBe('gespeichert …cdef');
      expect(field('pin').placeholder).toBe('nicht gesetzt');
      expect(field('certification-country').value).toBe('AT');
      expect(text(fixture)).toContain('Standard: DE');
      expect(text(fixture)).toContain('Für TVDB fehlt ein API-Key');
      expect(saveButton().disabled).toBe(true);
    });

    it('sends only changed settings, removed keys as empty', async () => {
      await load();
      await type('pin', ' 4711 ');
      await type('certification-country', '');
      (fixture.nativeElement as HTMLElement)
        .querySelector<HTMLButtonElement>('.remove-setting-button')!
        .click();
      fixture.detectChanges();
      expect(field('api-key').placeholder).toBe('wird entfernt');

      saveButton().click();

      const request = httpTesting.expectOne({
        method: 'PUT',
        url: `${SCRAPER_PLUGINS_URL}/tvdb/settings`,
      });
      expect(request.request.body).toEqual({
        'api-key': '',
        pin: '4711',
        'certification-country': '',
      });
      request.flush({ ...DETAILS, scraper: { ...TVDB, missingConfiguration: null } });
      await fixture.whenStable();
      expect(snackBarMessages).toEqual(['Einstellungen von „TheTVDB“ gespeichert.']);
      expect(field('pin').value).toBe('');
    });

    it('reports a rejected setting', async () => {
      await load();
      await type('pin', 'x');
      saveButton().click();

      httpTesting
        .expectOne({ method: 'PUT', url: `${SCRAPER_PLUGINS_URL}/tvdb/settings` })
        .flush({ detail: 'Zu lang' }, { status: 422, statusText: 'Unprocessable' });

      expect(snackBarMessages).toEqual(['Zu lang']);
    });

    it('switches a scraper off only after asking', async () => {
      await load();
      const toggle = fixture.debugElement.query(By.directive(MatSlideToggle))
        .componentInstance as MatSlideToggle;

      confirmAnswer = false;
      toggle.toggle();
      toggle.change.emit({ source: toggle, checked: false });
      httpTesting.expectNone(`${SCRAPER_PLUGINS_URL}/tvdb/disable`);
      expect(toggle.checked).toBe(true);

      confirmAnswer = true;
      toggle.change.emit({ source: toggle, checked: false });
      httpTesting
        .expectOne({ method: 'POST', url: `${SCRAPER_PLUGINS_URL}/tvdb/disable` })
        .flush({ scraper: { ...TVDB, enabled: false, capabilities: [] }, settings: [] });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(snackBarMessages).toEqual(['„TheTVDB“ ist ausgeschaltet.']);
      expect(text(fixture)).toContain('sobald der Scraper eingeschaltet ist');
    });

    it('switches a scraper on without asking', async () => {
      await load({ scraper: TRAKT, settings: [] });
      const toggle = fixture.debugElement.query(By.directive(MatSlideToggle))
        .componentInstance as MatSlideToggle;

      toggle.change.emit({ source: toggle, checked: true });

      httpTesting
        .expectOne({ method: 'POST', url: `${SCRAPER_PLUGINS_URL}/trakt/enable` })
        .flush({ scraper: { ...TRAKT, enabled: true }, settings: [] });
      expect(snackBarMessages).toEqual(['„trakt“ ist eingeschaltet.']);
    });
  });
});
