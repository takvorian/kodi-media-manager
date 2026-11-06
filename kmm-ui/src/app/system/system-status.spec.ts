import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { provideI18nTesting } from '../i18n/testing';
import { switchLanguage } from '../shared/testing';
import { ChangelogVersion, SystemInfo } from './system-info';
import { SystemInfoService } from './system-info.service';
import { SystemStatus } from './system-status';

describe('SystemStatus', () => {
  const systemInfo: SystemInfo = {
    applicationName: 'Kodi Media Manager',
    version: '0.1.0-SNAPSHOT',
    javaVersion: '25.0.5',
    activeProfiles: ['standalone'],
    database: 'H2',
  };

  let fixture: ComponentFixture<SystemStatus>;

  async function render(
    systemInfo$: Observable<SystemInfo>,
    changelog$: Observable<ChangelogVersion[]> = of([]),
  ): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [SystemStatus],
      providers: [
        provideI18nTesting(),
        {
          provide: SystemInfoService,
          useValue: { load: () => systemInfo$, changelog: () => changelog$ },
        },
      ],
    });
    fixture = TestBed.createComponent(SystemStatus);
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a loading hint until the system info arrives', async () => {
    const element = await render(new Subject<SystemInfo>());

    expect(element.querySelector('.status')?.textContent).toContain('Lade Systeminformationen');
  });

  it('shows the loaded system info', async () => {
    const element = await render(of(systemInfo));

    expect(element.querySelector('.version')?.textContent).toBe('0.1.0-SNAPSHOT');
    expect(element.querySelector('.database')?.textContent).toBe('H2');
  });

  it('shows the changes of every version, newest first', async () => {
    const element = await render(
      of(systemInfo),
      of([
        { version: 'v0.2', date: '2026-11-02', changes: ['Zweite (def5678)', 'Dritte (abc1234)'] },
        { version: 'v0.1', date: '', changes: ['Erste (0123abc)'] },
      ]),
    );

    const releases = element.querySelectorAll('.release');
    expect(releases).toHaveLength(2);
    expect(releases[0].querySelector('h3')?.textContent).toContain('v0.2');
    expect(releases[0].querySelector('.release-date')?.textContent).toBe('2026-11-02');
    expect(releases[0].querySelectorAll('li')).toHaveLength(2);
    expect(releases[1].querySelector('.release-date')).toBeNull();
  });

  it('says when no version has been released yet', async () => {
    const element = await render(of(systemInfo));

    expect(element.querySelector('.no-release')?.textContent).toContain('Noch keine Version');
  });

  it('reports a changelog that cannot be loaded', async () => {
    const element = await render(
      of(systemInfo),
      throwError(() => new Error('offline')),
    );

    expect(element.querySelector('.changelog .error')?.textContent).toContain('nicht geladen');
  });

  it('shows an error when the backend is unreachable', async () => {
    const element = await render(throwError(() => new Error('offline')));

    expect(element.querySelector('.error')?.textContent).toContain('nicht erreichbar');
  });

  describe('clearing the database', () => {
    let clearDatabase: ReturnType<typeof vi.fn>;

    async function renderClearCard(result: Observable<void> = of(undefined)) {
      clearDatabase = vi.fn(() => result);
      TestBed.configureTestingModule({
        imports: [SystemStatus],
        providers: [
          provideI18nTesting(),
          {
            provide: SystemInfoService,
            useValue: { load: () => of(systemInfo), changelog: () => of([]), clearDatabase },
          },
        ],
      });
      const fixture = TestBed.createComponent(SystemStatus);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const element = fixture.nativeElement as HTMLElement;
      return {
        fixture,
        element,
        button: element.querySelector<HTMLButtonElement>('.clear-button')!,
        type(word: string) {
          const input = element.querySelector<HTMLInputElement>('.clear-confirmation input')!;
          input.value = word;
          input.dispatchEvent(new Event('input'));
          fixture.detectChanges();
        },
        click() {
          element.querySelector<HTMLButtonElement>('.clear-button')!.click();
          fixture.detectChanges();
        },
        toggleKeepSettings() {
          element.querySelector<HTMLInputElement>('.keep-settings input')!.click();
          fixture.detectChanges();
        },
      };
    }

    it('stays disabled until the exact word is typed', async () => {
      const card = await renderClearCard();

      expect(card.button.disabled).toBe(true);
      card.type('leeren');
      expect(card.button.disabled).toBe(true);
      card.type('LEEREN');
      expect(card.button.disabled).toBe(false);
    });

    it('keeps the settings by default and confirms the result', async () => {
      const card = await renderClearCard();

      card.type('LEEREN');
      card.click();

      expect(clearDatabase).toHaveBeenCalledWith(true);
      expect(card.element.querySelector('.cleared')?.textContent).toContain('geleert');
      expect(card.button.disabled).toBe(true);
    });

    it('deletes the settings only when the checkbox is cleared', async () => {
      const card = await renderClearCard();

      card.toggleKeepSettings();
      card.type('LEEREN');
      card.click();

      expect(clearDatabase).toHaveBeenCalledWith(false);
    });

    it('asks for the confirmation word of the UI language', async () => {
      const card = await renderClearCard();
      await switchLanguage('en');
      await card.fixture.whenStable();

      expect(card.element.querySelector('.clear-confirmation mat-label')?.textContent).toBe(
        'Type CLEAR to confirm',
      );
      card.type('LEEREN');
      expect(card.button.disabled).toBe(true);
      card.type('CLEAR');
      expect(card.button.disabled).toBe(false);
      card.click();
      expect(card.element.querySelector('.cleared')?.textContent).toBe(
        'The database has been cleared.',
      );
    });

    it('shows why the database could not be cleared', async () => {
      const problem = new HttpErrorResponse({
        status: 409,
        error: { detail: 'Scan „Filme“ läuft bereits.' },
      });
      const card = await renderClearCard(throwError(() => problem));

      card.type('LEEREN');
      card.click();

      expect(card.element.querySelector('.clear-error')?.textContent).toContain('läuft bereits');
      expect(card.element.querySelector('.cleared')).toBeNull();
    });
  });

  it('switches its texts to English at runtime', async () => {
    const element = await render(of(systemInfo));
    expect(element.querySelector('.page-title')?.textContent).toBe('System');

    await switchLanguage('en');
    await fixture.whenStable();

    expect(element.querySelector('.clear-database mat-card-title')?.textContent).toBe(
      'Clear database',
    );
    expect(element.querySelector('.no-release')?.textContent).toBe('No version released yet.');
    expect(Array.from(element.querySelectorAll('dt')).map((term) => term.textContent)).toEqual([
      'Version',
      'Java',
      'Database',
      'Profiles',
    ]);
  });
});
