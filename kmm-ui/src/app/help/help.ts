import chapterList from '../../../../docs/manual/chapters.json';
import { UiLanguage } from '../i18n/ui-language';

/**
 * A chapter of the manual from `docs/manual/chapters.json`; `routes` are the pages it belongs to. The slug names
 * the file in every language (`docs/manual/<language>/<slug>.md`).
 */
export interface HelpChapter {
  slug: string;
  title: Readonly<Record<UiLanguage, string>>;
  routes: string[];
}

/** A search hit: the chapter, the section and an excerpt around the match. */
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

/** The chapter for a page of the application; pages without their own chapter lead to the start. */
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

/** Anchor of a heading, built as on GitHub so links work in both views. */
export function headingAnchor(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

/** Searches the chapter for all words of the query, ignoring case. */
export function searchChapter(
  chapter: HelpChapter,
  markdown: string,
  query: string,
  language: UiLanguage,
): HelpHit | null {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  const text = markdown.toLowerCase();
  if (words.length === 0 || !words.every((word) => text.includes(word))) {
    return null;
  }
  const title = chapter.title[language];
  let section = title;
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
        chapter: title,
        section,
        anchor,
        snippet: snippet(plain, position),
      };
    }
  }
  return {
    slug: chapter.slug,
    chapter: title,
    section: title,
    anchor: '',
    snippet: '',
  };
}

function snippet(text: string, position: number): string {
  const start = Math.max(0, position - SNIPPET_RADIUS);
  const end = Math.min(text.length, position + SNIPPET_RADIUS);
  return `${start > 0 ? '… ' : ''}${text.substring(start, end).trim()}${end < text.length ? ' …' : ''}`;
}
