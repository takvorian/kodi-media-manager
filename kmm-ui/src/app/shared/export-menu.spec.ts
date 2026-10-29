import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';

import { ExportMenu } from './export-menu';

describe('ExportMenu', () => {
  const endpoint = '/api/v1/movies/export';
  const filter = { matchAll: true, conditions: [], search: null };

  let fixture: ComponentFixture<ExportMenu>;
  let httpTesting: HttpTestingController;
  let snackBarMessages: string[];
  let downloads: { name: string; href: string }[];

  beforeEach(() => {
    snackBarMessages = [];
    downloads = [];
    TestBed.configureTestingModule({
      imports: [ExportMenu],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatSnackBar,
          useValue: { open: (message: string) => snackBarMessages.push(message) },
        },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ExportMenu);
    fixture.componentRef.setInput('endpoint', endpoint);
    fixture.componentRef.setInput('filter', filter);
    fixture.componentRef.setInput('fallbackName', 'kmm-filme');
    fixture.detectChanges();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloads.push({ name: this.download, href: this.href });
    });
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:export');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.restoreAllMocks();
  });

  function choose(selector: string): void {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('.export-button')!
      .click();
    fixture.detectChanges();
    document.querySelector<HTMLButtonElement>(selector)!.click();
  }

  it('downloads the filtered list under the name the server gives', async () => {
    choose('.export-csv');

    const request = httpTesting.expectOne((req) => req.url === endpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('format')).toBe('CSV');
    expect(request.request.body).toEqual(filter);
    request.flush(new Blob(['Titel']), {
      headers: { 'Content-Disposition': 'attachment; filename="kmm-filme-2026-10-29.csv"' },
    });
    await fixture.whenStable();

    expect(downloads).toEqual([{ name: 'kmm-filme-2026-10-29.csv', href: 'blob:export' }]);
  });

  it('names the file itself without a server name and reports failures', async () => {
    choose('.export-html');
    httpTesting.expectOne((req) => req.params.get('format') === 'HTML').flush(new Blob(['zip']));
    await fixture.whenStable();
    expect(downloads.map((download) => download.name)).toEqual(['kmm-filme.zip']);

    choose('.export-json');
    httpTesting
      .expectOne((req) => req.params.get('format') === 'JSON')
      .flush(new Blob(['{}']), { status: 500, statusText: 'Fehler' });
    await fixture.whenStable();
    expect(snackBarMessages.length).toBe(1);
  });
});
