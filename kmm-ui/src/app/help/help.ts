import chapterList from '../../../../docs/handbuch/kapitel.json';

/** Ein Kapitel des Handbuchs aus `docs/handbuch/kapitel.json`; `routes` sind die Seiten, zu denen es gehört. */
export interface HelpChapter {
  slug: string;
  title: string;
  routes: string[];
}

/** Ein Suchtreffer: das Kapitel, der Abschnitt und ein Ausschnitt um die Fundstelle. */
export interface HelpHit {
  slug: string;
  chapter: string;
  section: string;
  anchor: string;
  snippet: string;
}

export const HELP_CHAPTERS: readonly HelpChapter[] = chapterList;
export const DEFAULT_HELP_CHAPTER = HELP_CHAPTERS[0].slug;

const SNIPPET_RADIUS = 60;
const HEADING = /^#{1,6}\s+(.*)$/;
const MARKDOWN_SYNTAX = /!?\[([^\]]*)\]\([^)]*\)|[*`|]/g;

/** Das Kapitel zu einer Seite der Anwendung; Seiten ohne eigenes Kapitel führen zum Anfang. */
export function chapterForUrl(url: string): string {
  const path = url.split(/[?#]/)[0];
  let best: { slug: string; length: number } | null = null;
  for (const chapter of HELP_CHAPTERS) {
    for (const route of chapter.routes) {
      if (matchesRoute(route, path) && (!best || route.length > best.length)) {
        best = { slug: chapter.slug, length: route.length };
      }
    }
  }
  return best?.slug ?? DEFAULT_HELP_CHAPTER;
}

function matchesRoute(route: string, path: string): boolean {
  const expected = route.split('/');
  const actual = path.split('/');
  return (
    expected.length <= actual.length &&
    expected.every((segment, index) => segment === '*' || segment === actual[index])
  );
}

/** Anker einer Überschrift, gebildet wie auf GitHub, damit Links in beiden Ansichten gelten. */
export function headingAnchor(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

/** Sucht alle Wörter der Anfrage im Kapitel, Groß- und Kleinschreibung zählen nicht. */
export function searchChapter(
  chapter: HelpChapter,
  markdown: string,
  query: string,
): HelpHit | null {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  const text = markdown.toLowerCase();
  if (words.length === 0 || !words.every((word) => text.includes(word))) {
    return null;
  }
  let section = chapter.title;
  let anchor = '';
  for (const line of markdown.split('\n')) {
    const heading = HEADING.exec(line);
    if (heading) {
      section = heading[1];
      anchor = headingAnchor(heading[1]);
    }
    const plain = line.replace(HEADING, '$1').replace(MARKDOWN_SYNTAX, '$1');
    const position = plain.toLowerCase().indexOf(words[0]);
    if (position >= 0) {
      return {
        slug: chapter.slug,
        chapter: chapter.title,
        section,
        anchor,
        snippet: snippet(plain, position),
      };
    }
  }
  return {
    slug: chapter.slug,
    chapter: chapter.title,
    section: chapter.title,
    anchor: '',
    snippet: '',
  };
}

function snippet(text: string, position: number): string {
  const start = Math.max(0, position - SNIPPET_RADIUS);
  const end = Math.min(text.length, position + SNIPPET_RADIUS);
  return `${start > 0 ? '… ' : ''}${text.substring(start, end).trim()}${end < text.length ? ' …' : ''}`;
}
