import { routes } from '../app.routes';
import {
  chapterForUrl,
  DEFAULT_HELP_CHAPTER,
  HELP_CHAPTERS,
  headingAnchor,
  searchChapter,
} from './help';
import { renderHelp } from './help-markdown';

const CHAPTER = { slug: 'quellen', title: 'Quellen und Scan', routes: [] };

describe('help', () => {
  it('finds a chapter for every page of the application', () => {
    const pages = routes
      .map((route) => route.path ?? '')
      .filter((path) => path !== '' && path !== '**' && !path.startsWith('help'))
      .map((path) => `/${path.replace(/:[a-z]+/g, '1')}`);

    expect(pages).not.toHaveLength(0);
    for (const page of pages) {
      expect(chapterForUrl(page), page).not.toBe(DEFAULT_HELP_CHAPTER);
    }
  });

  it('prefers the most specific route and ignores query and fragment', () => {
    expect(chapterForUrl('/sources')).toBe('quellen');
    expect(chapterForUrl('/sources/3/rename')).toBe('umbenennen');
    expect(chapterForUrl('/sources/3/tv-rename?x=1')).toBe('umbenennen');
    expect(chapterForUrl('/movies/12#top')).toBe('filme');
    expect(chapterForUrl('/episodes/5')).toBe('serien');
  });

  it('falls back to the first chapter for unknown pages', () => {
    expect(chapterForUrl('/unbekannt')).toBe(HELP_CHAPTERS[0].slug);
  });

  it('builds anchors like GitHub', () => {
    expect(headingAnchor('Zugriff aus dem Heimnetz')).toBe('zugriff-aus-dem-heimnetz');
    expect(headingAnchor('Scrapen: Daten, Bilder')).toBe('scrapen-daten-bilder');
    expect(headingAnchor('Größe prüfen')).toBe('größe-prüfen');
  });

  it('renders headings with anchors and keeps links in the application', () => {
    const html = renderHelp(
      '## Erste Hilfe\n\n[Filme](filme.md#liste) und [Kodi](https://kodi.tv)',
    );

    expect(html).toContain('<h2 id="erste-hilfe">Erste Hilfe</h2>');
    expect(html).toContain('<a href="/help/filme#liste">Filme</a>');
    expect(html).toContain('<a href="https://kodi.tv" target="_blank" rel="noopener">Kodi</a>');
  });

  it('serves images from the help files and escapes their text', () => {
    expect(renderHelp('![Liste "A"](bilder/a.png)')).toContain(
      '<img src="help/bilder/a.png" alt="Liste &quot;A&quot;" loading="lazy" />',
    );
  });

  it('renders tables', () => {
    expect(renderHelp('| A |\n|---|\n| b |')).toContain('<td>b</td>');
  });

  it('finds the section and an excerpt of a match', () => {
    const hit = searchChapter(
      CHAPTER,
      '# Quellen\n\n## Der Bericht\n\nBleibt beim **Scrapen** etwas offen.',
      'scrapen',
    );

    expect(hit).toEqual({
      slug: 'quellen',
      chapter: 'Quellen und Scan',
      section: 'Der Bericht',
      anchor: 'der-bericht',
      snippet: 'Bleibt beim Scrapen etwas offen.',
    });
  });

  it('shortens long excerpts', () => {
    const hit = searchChapter(CHAPTER, `${'a'.repeat(100)} treffer ${'b'.repeat(100)}`, 'treffer');

    expect(hit?.snippet.startsWith('… ')).toBe(true);
    expect(hit?.snippet.endsWith(' …')).toBe(true);
  });

  it('requires all words and ignores case', () => {
    expect(searchChapter(CHAPTER, 'Der Scan läuft', 'SCAN LÄUFT')).not.toBeNull();
    expect(searchChapter(CHAPTER, 'Der Scan läuft', 'scan steht')).toBeNull();
    expect(searchChapter(CHAPTER, 'Der Scan läuft', '   ')).toBeNull();
  });
});
