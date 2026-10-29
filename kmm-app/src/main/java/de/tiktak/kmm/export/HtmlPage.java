package de.tiktak.kmm.export;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Die HTML-Liste des Exports: eine eigenständige Seite mit Suchfeld, die ohne KMM und ohne Internet funktioniert.
 */
final class HtmlPage {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
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

    /** @param posters relativer Pfad des Posters im ZIP je ID */
    static String render(ExportTable table, Map<Long, String> posters, LocalDate exportedOn) {
        StringBuilder html = new StringBuilder(64 * 1024);
        html.append("<!doctype html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(escape(table.title())).append(" – Kodi Media Manager</title>\n")
                .append("<style>\n").append(STYLE).append("</style>\n</head>\n<body>\n")
                .append("<h1>").append(escape(table.title())).append("</h1>\n")
                .append("<p><span id=\"count\">").append(table.rows().size()).append("</span> von ")
                .append(table.rows().size()).append(" Einträgen · exportiert am ")
                .append(DATE.format(exportedOn)).append("</p>\n")
                .append("<input id=\"search\" type=\"search\" placeholder=\"Suchen …\" aria-label=\"Suchen\">\n")
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
            row.values().forEach(value -> html.append("<td>").append(escape(ExportWriter.text(value))).append("</td>"));
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table></div>\n<script>\n").append(SCRIPT).append("</script>\n</body>\n</html>\n");
        return html.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
