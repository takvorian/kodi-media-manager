package de.tiktak.kmm.export;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;

/**
 * The HTML list of the export: a standalone page with a search field that works without KMM and without internet.
 */
final class HtmlPage {

    private static final LocalizedText SEARCH = LocalizedText.of("export.html.search");
    private static final LocalizedText SEARCH_LABEL = LocalizedText.of("export.html.searchLabel");
    private static final String SUMMARY = "export.html.summary";
    /** Stands in for the visible count while the summary is escaped; replaced by the element the search updates. */
    private static final String COUNT_PLACEHOLDER = "\u0000count\u0000";
    private static final String STYLE = """
            :root { color-scheme: light dark; --line: #d0d7de; --muted: #59636e; --head: #f6f8fa; }
            @media (prefers-color-scheme: dark) { :root { --line: #3d444d; --muted: #9198a1; --head: #151b23; } }
            body { font-family: system-ui, sans-serif; margin: 16px; }
            h1 { font-size: 1.4rem; margin: 0 0 4px; }
            p { color: var(--muted); margin: 0 0 12px; }
            input { font: inherit; padding: 6px 8px; width: min(100%, 360px); margin-bottom: 12px; }
            .scroll { overflow-x: auto; }
            table { border-collapse: collapse; font-size: 0.9rem; }
            th, td { border-bottom: 1px solid var(--line); padding: 4px 8px; text-align: left; vertical-align: top; }
            th { position: sticky; top: 0; background: var(--head); }
            img { width: 60px; border-radius: 3px; }
            """;
    private static final String SCRIPT = """
            const search = document.getElementById('search');
            const rows = Array.from(document.querySelectorAll('tbody tr'));
            search.addEventListener('input', () => {
              const term = search.value.toLowerCase();
              let shown = 0;
              rows.forEach(row => {
                const match = row.textContent.toLowerCase().includes(term);
                row.hidden = !match;
                shown += match ? 1 : 0;
              });
              document.getElementById('count').textContent = shown;
            });
            """;

    private HtmlPage() {
    }

    /** @param posters relative path of the poster in the ZIP per ID */
    static String render(ExportTable table, Map<Long, String> posters, LocalDate exportedOn) {
        Locale locale = table.locale();
        StringBuilder html = new StringBuilder(64 * 1024);
        html.append("<!doctype html>\n<html lang=\"").append(escape(locale.getLanguage()))
                .append("\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(escape(table.title())).append(" – Kodi Media Manager</title>\n")
                .append("<style>\n").append(STYLE).append("</style>\n</head>\n<body>\n")
                .append("<h1>").append(escape(table.title())).append("</h1>\n")
                .append("<p>").append(summary(table, exportedOn)).append("</p>\n")
                .append("<input id=\"search\" type=\"search\" placeholder=\"")
                .append(escape(Messages.render(SEARCH, locale))).append("\" aria-label=\"")
                .append(escape(Messages.render(SEARCH_LABEL, locale))).append("\">\n")
                .append("<div class=\"scroll\"><table>\n<thead><tr><th></th>");
        table.columns().forEach(column -> html.append("<th>").append(escape(column.label())).append("</th>"));
        html.append("</tr></thead>\n<tbody>\n");
        for (ExportTable.Row row : table.rows()) {
            html.append("<tr><td>");
            String poster = posters.get(row.id());
            if (poster != null) {
                html.append("<img loading=\"lazy\" alt=\"\" src=\"").append(escape(poster)).append("\">");
            }
            html.append("</td>");
            row.values().forEach(value -> html.append("<td>").append(escape(ExportWriter.text(value, locale)))
                    .append("</td>"));
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table></div>\n<script>\n").append(SCRIPT).append("</script>\n</body>\n</html>\n");
        return html.toString();
    }

    /** "2 of 2 entries · exported on …" with the count in an element the search script updates. */
    private static String summary(ExportTable table, LocalDate exportedOn) {
        int total = table.rows().size();
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(table.locale())
                .format(exportedOn);
        String text = Messages.render(LocalizedText.of(SUMMARY).with("count", COUNT_PLACEHOLDER).with("total", total)
                .with("date", date), table.locale());
        return escape(text).replace(COUNT_PLACEHOLDER, "<span id=\"count\">" + total + "</span>");
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
