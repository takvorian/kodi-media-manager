package de.tiktak.kmm.plugin.api;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Macht aus HTML-Ausschnitten von Webseiten ohne API lesbaren Text.
 */
public final class HtmlText {

    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>|</p>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x{00A0}]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\s*\\n\\s*(\\n\\s*)*");
    private static final int HEX = 16;
    private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("amp", "&"), Map.entry("lt", "<"),
            Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("auml", "ä"), Map.entry("ouml", "ö"), Map.entry("uuml", "ü"), Map.entry("Auml", "Ä"),
            Map.entry("Ouml", "Ö"), Map.entry("Uuml", "Ü"), Map.entry("szlig", "ß"), Map.entry("hellip", "…"),
            Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("bdquo", "„"), Map.entry("ldquo", "“"),
            Map.entry("rdquo", "”"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("sbquo", "‚"));

    private HtmlText() {
    }

    /** Text ohne Tags und Entities; Absätze und Zeilenumbrüche werden zu Zeilen, leere Zeilen entfallen. */
    public static String plain(String html) {
        if (html == null) {
            return "";
        }
        String text = TAG.matcher(LINE_BREAK.matcher(html).replaceAll("\n")).replaceAll("");
        Matcher entities = ENTITY.matcher(text);
        String decoded = entities.replaceAll(entity -> Matcher.quoteReplacement(decode(entity.group(1),
                entity.group())));
        return BLANK_LINES.matcher(SPACES.matcher(decoded).replaceAll(" ")).replaceAll("\n").strip();
    }

    private static String decode(String entity, String original) {
        if (entity.charAt(0) != '#') {
            return NAMED.getOrDefault(entity, original);
        }
        boolean hex = entity.charAt(1) == 'x' || entity.charAt(1) == 'X';
        try {
            int codePoint = hex ? Integer.parseInt(entity.substring(2), HEX) : Integer.parseInt(entity.substring(1));
            return Character.isValidCodePoint(codePoint) ? Character.toString(codePoint) : original;
        } catch (NumberFormatException e) {
            return original;
        }
    }
}
