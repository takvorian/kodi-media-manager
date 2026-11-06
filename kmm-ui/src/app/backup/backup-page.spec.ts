import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { vi } from 'vitest';

import { LanguageService } from '../i18n/language.service';
import { provideI18nTesting } from '../i18n/testing';
import { FakeJobEvents, job } from '../jobs/fake-job-events';
import { Job } from '../jobs/job';
import { CONFIRM } from '../shared/confirm';
import { BackupHeader, BackupImportReport } from './backup';
import { BackupPage } from './backup-page';
import { BACKUP_URL } from './backup.service';

describe('BackupPage', () => {
  const header: BackupHeader = {
    schemaVersion: '20',
    applicationVersion: 'v0.4',
    createdAt: '2026-10-31T12:00:00Z',
    credentials: false,
    counts: { movie: 1170, episode: 8401, media_source: 2, movie_credit: 44000 },
    sources: [
      { name: 'filme', path: 'a:\\medien\\filme', mediaType: 'MOVIE' },
      { name: 'serien', path: 'a:\\medien\\serien', mediaType: 'TV_SHOW' },
    ],
  };
  const report: BackupImportReport = {
    dryRun: false,
    mode: 'REPLACE',
    results: [{ table: 'movie', created: 1170, updated: 2, skipped: 3 }],
    unmappedPaths: 4,
    credentialsKept: true,
    safetyCopy: '/config/backup/vor-import.json.gz',
    warnings: ['Das Verzeichnis ist nicht erreichbar.'],
  };
  const importJob = (overrides: Partial<Job> = {}): Job =>
    job({ id: 'job-9', key: 'backup-import', title: 'Sicherung einspielen', ...overrides });

  let fixture: ComponentFixture<BackupPage>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let confirmAnswer: boolean;
  let events: FakeJobEvents;

  beforeEach(() => {
    snackBarMessages = [];
    confirmAnswer = true;
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      imports: [BackupPage],
      providers: [
        provideI18nTesting(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
        { provide: CONFIRM, useValue: () => confirmAnswer },
        events.provider(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BackupPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
    vi.restoreAllMocks();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function query<T extends HTMLElement = HTMLButtonElement>(selector: string): T {
    return element().querySelector<T>(selector)!;
  }

  function type(selector: string, value: string): void {
    const input = query<HTMLInputElement>(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  /**
   * Switches like the language menu does. Without `init()` the service still holds its fallback language, so it is
   * first set to the German of the test setup; otherwise the switch to English would not be a change.
   */
  async function switchToEnglish(): Promise<void> {
    const language = TestBed.inject(LanguageService);
    await language.use('de');
    await language.use('en');
  }

  async function uploaded(contents: BackupHeader = header): Promise<void> {
    const picker = query<HTMLInputElement>('.file-picker');
    Object.defineProperty(picker, 'files', {
      value: [new File(['x'], 'sicherung.json.gz')],
      configurable: true,
    });
    picker.dispatchEvent(new Event('change'));
    httpTesting.expectOne(`${BACKUP_URL}/upload`).flush(contents);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('leaves credentials out of the export by default and warns when they are included', () => {
    expect(query('.credentials-warning')).toBeNull();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    query('.export-button').click();
    const first = httpTesting.expectOne((request) => request.url === `${BACKUP_URL}/export`);
    expect(first.request.params.get('credentials')).toBe('false');
    first.flush(new Blob(['x']));

    query<HTMLInputElement>('.credentials-checkbox input').click();
    fixture.detectChanges();
    expect(query('.credentials-warning').textContent).toContain('Klartext');

    query('.export-button').click();
    const second = httpTesting.expectOne((request) => request.url === `${BACKUP_URL}/export`);
    expect(second.request.params.get('credentials')).toBe('true');
    second.flush(new Blob(['x']));
  });

  it('updates by itself when the choices change', async () => {
    query<HTMLInputElement>('.credentials-checkbox input').click();
    await fixture.whenStable();
    expect(query('.credentials-warning')).not.toBeNull();

    await uploaded();
    expect(query('.import-button').disabled).toBe(true);
    query<HTMLInputElement>('.mode-merge input').click();
    await fixture.whenStable();
    expect(query('.replace-confirmation')).toBeNull();
    expect(query('.import-button').disabled).toBe(false);
  });

  it('reports a failed export', () => {
    query('.export-button').click();
    httpTesting
      .expectOne((request) => request.url === `${BACKUP_URL}/export`)
      .flush(new Blob(), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(snackBarMessages).toHaveLength(1);
    expect(query('.export-button').disabled).toBe(false);
  });

  it('shows what the file contains and offers its sources as mappings', async () => {
    await uploaded();

    expect(query('.created').textContent).toContain('v0.4');
    expect(query('.contents').textContent).toContain('1170 Filme');
    expect(query('.contents').textContent).toContain('2 Quellen');
    expect(query('.contents').textContent).not.toContain('movie_credit');
    expect(query('.credentials').textContent).toContain('nicht enthalten');
    expect(element().querySelectorAll('.mapping')).toHaveLength(2);
  });

  it('switches the texts, the date format and the confirmation word at runtime', async () => {
    await uploaded();

    await switchToEnglish();
    await fixture.whenStable();

    expect(query('.page-title').textContent).toBe('Backup');
    expect(query('.contents').textContent).toContain('1170 Movies');
    expect(query('.created').textContent).toContain(
      new Date(header.createdAt!).toLocaleString('en-US'),
    );
    expect(query('.replace-confirmation mat-label').textContent).toContain('Enter REPLACE');
    type('.replace-confirmation input', 'ERSETZEN');
    expect(query('.import-button').disabled).toBe(true);
    type('.replace-confirmation input', 'REPLACE');
    expect(query('.import-button').disabled).toBe(false);
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('en');
  });

  it('forgets the file when the upload is refused', async () => {
    const picker = query<HTMLInputElement>('.file-picker');
    Object.defineProperty(picker, 'files', {
      value: [new File(['x'], 'kaputt.gz')],
      configurable: true,
    });
    picker.dispatchEvent(new Event('change'));
    httpTesting
      .expectOne(`${BACKUP_URL}/upload`)
      .flush(
        { detail: 'Die Datei ist keine gültige KMM-Sicherung.' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(snackBarMessages).toEqual(['Die Datei ist keine gültige KMM-Sicherung.']);
    expect(query('.contents')).toBeNull();
  });

  it('asks for the word before replacing everything', async () => {
    await uploaded();

    expect(query('.import-button').disabled).toBe(true);
    type('.replace-confirmation input', 'ersetzen');
    expect(query('.import-button').disabled).toBe(true);
    type('.replace-confirmation input', 'ERSETZEN');
    expect(query('.import-button').disabled).toBe(false);

    query('.import-button').click();
    const request = httpTesting.expectOne(`${BACKUP_URL}/import`);
    expect(request.request.body).toEqual({ mode: 'REPLACE', pathMappings: [], dryRun: false });
    request.flush(importJob());
  });

  it('runs a dry run without the word and sends only changed paths', async () => {
    await uploaded();
    const targets = element().querySelectorAll<HTMLInputElement>('input[formControlName="to"]');
    targets[0].value = ' /media/filme ';
    targets[0].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    query('.dry-run-button').click();

    const request = httpTesting.expectOne(`${BACKUP_URL}/import`);
    expect(request.request.body).toEqual({
      mode: 'REPLACE',
      pathMappings: [{ from: 'a:\\medien\\filme', to: '/media/filme' }],
      dryRun: true,
    });
    request.flush(importJob({ title: 'Sicherung einspielen (Probelauf)' }));
  });

  it('refuses an empty target path', async () => {
    await uploaded();
    const target = element().querySelector<HTMLInputElement>('input[formControlName="to"]')!;
    target.value = '';
    target.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    query('.dry-run-button').click();

    httpTesting.expectNone(`${BACKUP_URL}/import`);
  });

  it('merges only after a confirmation', async () => {
    await uploaded();
    query<HTMLInputElement>('.mode-merge input').click();
    fixture.detectChanges();
    expect(query('.replace-confirmation')).toBeNull();

    confirmAnswer = false;
    query('.import-button').click();
    httpTesting.expectNone(`${BACKUP_URL}/import`);

    confirmAnswer = true;
    query('.import-button').click();
    const request = httpTesting.expectOne(`${BACKUP_URL}/import`);
    expect(request.request.body.mode).toBe('MERGE');
    request.flush(importJob());
  });

  it('shows the report and forgets the file after a real import', async () => {
    await uploaded();
    events.emit(importJob());
    events.emit(importJob({ status: 'SUCCEEDED', result: report }));
    fixture.detectChanges();

    expect(query('.result').textContent).toContain('1170 neu, 2 aktualisiert, 3 übersprungen');
    expect(query('.unmapped').textContent).toContain('4');
    expect(query('.credentials-kept')).not.toBeNull();
    expect(query('.safety-copy').textContent).toContain('vor-import');
    expect(query('.warnings').textContent).toContain('nicht erreichbar');
    expect(query('.contents')).toBeNull();
  });

  it('keeps the file after a dry run', async () => {
    await uploaded();
    events.emit(importJob());
    events.emit(
      importJob({
        title: 'Sicherung einspielen (Probelauf)',
        status: 'SUCCEEDED',
        result: { ...report, dryRun: true, safetyCopy: null },
      }),
    );
    fixture.detectChanges();

    expect(element().querySelector('.report mat-card-title')?.textContent).toContain('Probelauf');
    expect(query('.contents')).not.toBeNull();
    expect(query('.safety-copy')).toBeNull();
  });

  it('reports a failed import', async () => {
    await uploaded();
    events.emit(importJob());
    events.emit(importJob({ status: 'FAILED', message: 'Der Import ist fehlgeschlagen' }));
    fixture.detectChanges();

    expect(snackBarMessages.at(-1)).toContain('Der Import ist fehlgeschlagen');
    expect(query('.report')).toBeNull();
  });

  it('shows the progress of a running import and can cancel it', async () => {
    await uploaded();
    events.emit(
      importJob({ status: 'RUNNING', message: 'Daten werden übernommen', done: 1, total: 2 }),
    );
    fixture.detectChanges();

    expect(query('.import-button')).toBeNull();
    query('.cancel-button').click();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/v1/jobs/job-9/cancel' })
      .flush(importJob({ status: 'RUNNING' }));
  });

  it('discards the uploaded file on request', async () => {
    await uploaded();

    query('.discard-button').click();
    httpTesting.expectOne(`${BACKUP_URL}/upload`).flush(null);
    fixture.detectChanges();

    expect(query('.contents')).toBeNull();
  });
});
