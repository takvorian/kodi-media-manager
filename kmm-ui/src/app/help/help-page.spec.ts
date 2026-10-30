import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { HELP_CHAPTERS } from './help';
import { HelpPage } from './help-page';

describe('HelpPage', () => {
  let fixture: ComponentFixture<HelpPage>;
  let httpTesting: HttpTestingController;
  let element: HTMLElement;

  async function showChapter(slug: string, markdown: string): Promise<void> {
    fixture.componentRef.setInput('chapter', slug);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    httpTesting.expectOne(`help/${slug}.md`).flush(markdown);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HelpPage);
    element = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => httpTesting.verify());

  it('shows the chapter and all chapters in the index', async () => {
    await showChapter('filme', '# Filme\n\nDie **Liste**.');

    expect(element.querySelector('.help-content h1')?.textContent).toBe('Filme');
    expect(element.querySelector('.help-content strong')?.textContent).toBe('Liste');
    expect(element.querySelectorAll('.help-chapters a')).toHaveLength(HELP_CHAPTERS.length);
    expect(element.querySelector('.help-chapters a.active')?.textContent).toBe('Filme');
  });

  it('keeps anchors, images and external links through the sanitizer', async () => {
    await showChapter('filme', '## Abschnitt\n\n![Bild](bilder/a.png) [Kodi](https://kodi.tv)');

    expect(element.querySelector('.help-content h2')?.id).toBe('abschnitt');
    expect(element.querySelector('.help-content img')?.getAttribute('src')).toBe(
      'help/bilder/a.png',
    );
    expect(element.querySelector('.help-content a')?.getAttribute('target')).toBe('_blank');
  });

  it('links to the neighbouring chapters', async () => {
    await showChapter('filme', '# Filme');

    expect(element.querySelector('.help-previous')?.textContent).toContain('Quellen und Scan');
    expect(element.querySelector('.help-next')?.textContent).toContain('Serien und Episoden');
  });

  it('has no neighbours at the ends of the manual', async () => {
    await showChapter(HELP_CHAPTERS[0].slug, '# Anfang');

    expect(element.querySelector('.help-previous')).toBeNull();
    expect(element.querySelector('.help-next')).not.toBeNull();
  });

  it('follows links between chapters inside the application', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await showChapter('filme', '[Serien](serien.md#staffeln)');

    const link = element.querySelector<HTMLAnchorElement>('.help-content a')!;
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    link.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/help/serien#staffeln');
  });

  it('leaves external links to the browser', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await showChapter('filme', '[Kodi](https://kodi.tv)');

    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    element
      .querySelector('.help-content a')!
      .addEventListener('click', (click) => click.preventDefault());
    element.querySelector('.help-content a')!.dispatchEvent(event);

    expect(navigate).not.toHaveBeenCalled();
  });

  it('reports a chapter that cannot be loaded', async () => {
    fixture.componentRef.setInput('chapter', 'gibt-es-nicht');
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    httpTesting
      .expectOne('help/gibt-es-nicht.md')
      .flush('', { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('.error')?.textContent).toContain('nicht geladen');
    expect(element.querySelector('.help-next')).toBeNull();
  });

  it('searches all chapters and shows the hits instead of the index', async () => {
    await showChapter('filme', '# Filme');

    const input = element.querySelector<HTMLInputElement>('.help-search input')!;
    input.value = 'pfad-zuordnung';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));

    for (const chapter of HELP_CHAPTERS) {
      const requests = httpTesting.match(`help/${chapter.slug}.md`);
      requests.forEach((request) =>
        request.flush(
          chapter.slug === 'kodi' ? '# Kodi\n\n## Pfade\n\nDie Pfad-Zuordnung.' : '# Anderes',
        ),
      );
    }
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('.help-chapters')).toBeNull();
    expect(element.querySelectorAll('.help-hits a')).toHaveLength(1);
    expect(element.querySelector('.help-hits strong')?.textContent).toBe('Kodi');
    expect(element.querySelector('.help-section')?.textContent).toBe('Pfade');
  });

  it('says when the search finds nothing and ignores a single letter', async () => {
    await showChapter('filme', '# Filme');
    const input = element.querySelector<HTMLInputElement>('.help-search input')!;

    input.value = 'x';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element.querySelector('.help-chapters')).not.toBeNull();

    input.value = 'xyz';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    for (const chapter of HELP_CHAPTERS) {
      httpTesting.match(`help/${chapter.slug}.md`).forEach((request) => request.flush('# Anderes'));
    }
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('.help-no-hits')?.textContent).toContain('Nichts gefunden');
  });
});
