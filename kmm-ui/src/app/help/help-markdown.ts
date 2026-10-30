import { Marked } from 'marked';

import { headingAnchor } from './help';

const INTERNAL_LINK = /^([a-z-]+)\.md(#.*)?$/;
const EXTERNAL_LINK = /^https?:/;

function escapeHtml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function helpHref(href: string): string {
  return href.replace(
    INTERNAL_LINK,
    (_, slug: string, anchor?: string) => `/help/${slug}${anchor ?? ''}`,
  );
}

const MARKED = new Marked({
  renderer: {
    heading({ tokens, depth, text }) {
      return `<h${depth} id="${headingAnchor(text)}">${this.parser.parseInline(tokens)}</h${depth}>\n`;
    },
    link({ href, tokens }) {
      const target = EXTERNAL_LINK.test(href) ? ' target="_blank" rel="noopener"' : '';
      return `<a href="${escapeHtml(helpHref(href))}"${target}>${this.parser.parseInline(tokens)}</a>`;
    },
    image({ href, text }) {
      return `<img src="help/${escapeHtml(href)}" alt="${escapeHtml(text)}" loading="lazy" />`;
    },
  },
});

/** Wandelt ein Kapitel in HTML um: Links auf andere Kapitel führen zur Hilfeseite, Bilder zu den Hilfedateien. */
export function renderHelp(markdown: string): string {
  return MARKED.parse(markdown, { async: false });
}
