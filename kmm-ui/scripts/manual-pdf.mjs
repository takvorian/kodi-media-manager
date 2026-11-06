// Builds a PDF of the manual per language from docs/manual: all chapters in one HTML file, printed with
// headless Chrome. Fails if a link or an image points nowhere, which also checks the manual itself.
// Usage: npm run manual:pdf; output: target/manual/KMM-Handbuch.pdf (de) and KMM-Manual.pdf (en);
// Chrome via CHROME_BIN if it is not found.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Marked } from 'marked';

const manual = new URL('../../docs/manual/', import.meta.url);
const target = new URL('../target/manual/', import.meta.url);
const chapters = JSON.parse(readFileSync(new URL('chapters.json', manual), 'utf8'));
const slugs = new Set(chapters.map((chapter) => chapter.slug));
const CHROME_CANDIDATES = [
  process.env.CHROME_BIN,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);
const EXPLICIT_ANCHOR = /<a id="([a-z0-9-]+)"><\/a>/g;

const LANGUAGES = {
  de: { file: 'KMM-Handbuch.pdf', subtitle: 'Handbuch', contents: 'Inhalt' },
  en: { file: 'KMM-Manual.pdf', subtitle: 'Manual', contents: 'Contents' },
};

// Same as headingAnchor in src/app/help/help.ts, where the same anchors apply to the help page.
function headingAnchor(text) {
  return text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s/g, '-');
}

function escapeHtml(text) {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** Renders all chapters of one language and collects the problems. */
function renderLanguage(language) {
  const source = new URL(`${language}/`, manual);
  const anchors = new Set();
  const links = [];
  const problems = [];

  function render(chapter) {
    const marked = new Marked({
      renderer: {
        heading({ tokens, depth, text }) {
          const id = depth === 1 ? chapter.slug : `${chapter.slug}--${headingAnchor(text)}`;
          anchors.add(id);
          return `<h${depth} id="${id}">${this.parser.parseInline(tokens)}</h${depth}>\n`;
        },
        // Stable anchors the application links to, e.g. <a id="batch-edit"></a> before a heading.
        html({ text }) {
          return text.replace(EXPLICIT_ANCHOR, (_, id) => {
            anchors.add(`${chapter.slug}--${id}`);
            return `<a id="${chapter.slug}--${id}"></a>`;
          });
        },
        link({ href, tokens }) {
          const internal = /^([a-z-]+)\.md(?:#(.*))?$/.exec(href);
          if (!internal) {
            if (!/^https?:/.test(href)) {
              problems.push(`${language}/${chapter.slug}: unknown link ${href}`);
            }
            return `<a href="${escapeHtml(href)}">${this.parser.parseInline(tokens)}</a>`;
          }
          const [, slug, anchor] = internal;
          if (!slugs.has(slug)) {
            problems.push(`${language}/${chapter.slug}: there is no chapter ${slug}`);
          }
          const id = anchor ? `${slug}--${anchor}` : slug;
          links.push({ from: chapter.slug, id });
          return `<a href="#${id}">${this.parser.parseInline(tokens)}</a>`;
        },
        image({ href, text }) {
          const file = new URL(href, source);
          if (!existsSync(file)) {
            problems.push(`${language}/${chapter.slug}: image ${href} is missing`);
          }
          return `<img src="${file.href}" alt="${escapeHtml(text)}" />`;
        },
      },
    });
    const file = new URL(`${chapter.slug}.md`, source);
    if (!existsSync(file)) {
      problems.push(`${language}/${chapter.slug}: chapter file is missing`);
      return '';
    }
    return marked.parse(readFileSync(file, 'utf8'));
  }

  const body = chapters.map((chapter) => `<section>${render(chapter)}</section>`).join('\n');
  for (const { from, id } of links) {
    if (!anchors.has(id)) {
      problems.push(`${language}/${from}: there is no anchor ${id}`);
    }
  }
  return { body, problems };
}

function page(language, body) {
  const texts = LANGUAGES[language];
  const contents = chapters
    .map(
      (chapter) => `<li><a href="#${chapter.slug}">${escapeHtml(chapter.title[language])}</a></li>`,
    )
    .join('');
  return `<!doctype html>
<html lang="${language}"><head><meta charset="utf-8"><title>Kodi Media Manager – ${texts.subtitle}</title>
<style>
  @page { size: A4; margin: 20mm 18mm; }
  body { font: 11pt/1.45 Arial, Helvetica, sans-serif; color: #1a1a1a; }
  .cover { height: 240mm; display: flex; flex-direction: column; justify-content: center; }
  .cover h1 { font-size: 30pt; margin: 0 0 8pt; }
  .cover p { font-size: 14pt; color: #555; margin: 0; }
  .contents { page-break-after: always; }
  .contents h2 { margin-top: 0; }
  .contents ol { line-height: 1.9; }
  section { page-break-before: always; }
  h1 { font-size: 22pt; margin: 0 0 10pt; }
  h2 { font-size: 15pt; margin: 18pt 0 6pt; }
  h3 { font-size: 12pt; margin: 14pt 0 4pt; }
  h2, h3 { page-break-after: avoid; }
  a { color: #0b57d0; text-decoration: none; }
  img { display: block; max-width: 100%; margin: 8pt 0; border: 1px solid #ccc; page-break-inside: avoid; }
  code { font: 9.5pt Consolas, 'DejaVu Sans Mono', monospace; background: #f2f2f2; padding: 0 2pt; overflow-wrap: anywhere; }
  pre { background: #f2f2f2; padding: 8pt; white-space: pre-wrap; }
  pre code { padding: 0; }
  table { border-collapse: collapse; width: 100%; margin: 8pt 0; }
  tr { page-break-inside: avoid; }
  th, td { border: 1px solid #bbb; padding: 3pt 6pt; text-align: left; vertical-align: top; }
  th { background: #f2f2f2; }
</style></head><body>
<div class="cover"><h1>Kodi Media Manager</h1><p>${texts.subtitle}</p></div>
<div class="contents"><h2>${texts.contents}</h2><ol>${contents}</ol></div>
${body}
</body></html>`;
}

function print(chrome, html, file) {
  const htmlFile = new URL(file.replace(/\.pdf$/, '.html'), target);
  const pdf = new URL(file, target);
  rmSync(pdf, { force: true });
  writeFileSync(htmlFile, html);
  execFileSync(
    chrome,
    [
      '--headless',
      '--disable-gpu',
      '--no-sandbox',
      '--no-pdf-header-footer',
      `--user-data-dir=${fileURLToPath(new URL('chrome-profile/', target))}`,
      `--print-to-pdf=${fileURLToPath(pdf)}`,
      htmlFile.href,
    ],
    { stdio: 'ignore', timeout: 120000 },
  );
  if (!existsSync(pdf)) {
    console.error(`Chrome did not create ${file}.`);
    process.exit(1);
  }
  console.log(fileURLToPath(pdf));
}

const rendered = Object.keys(LANGUAGES).map((language) => ({
  language,
  ...renderLanguage(language),
}));
const problems = rendered.flatMap((result) => result.problems);
if (problems.length > 0) {
  console.error(problems.join('\n'));
  process.exit(1);
}

const chrome = CHROME_CANDIDATES.find((candidate) => existsSync(candidate));
if (!chrome) {
  console.error(`Chrome not found, set CHROME_BIN. Looked for: ${CHROME_CANDIDATES.join(', ')}`);
  process.exit(1);
}
mkdirSync(target, { recursive: true });
for (const { language, body } of rendered) {
  print(chrome, page(language, body), LANGUAGES[language].file);
}
