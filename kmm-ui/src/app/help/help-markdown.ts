import { Marked } from 'marked';

import { headingAnchor } from './help';

const INTERNAL_LINK = /^([a-z-]+)\.md(#.*)?$/;
const EXTERNAL_LINK = /^https?:/;
/** Images are shared by all languages, next to the language folders. */
const IMAGE_FOLDER = /^\.\.\/images\//;

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
      const source = `help/${href.replace(IMAGE_FOLDER, 'images/')}`;
      return `<img src="${escapeHtml(source)}" alt="${escapeHtml(text)}" loading="lazy" />`;
    },
  },
});

/** Turns a chapter into HTML: links to other chapters lead to the help page, images to the help files. */
export function renderHelp(markdown: string): string {
  return MARKED.parse(markdown, { async: false });
}
