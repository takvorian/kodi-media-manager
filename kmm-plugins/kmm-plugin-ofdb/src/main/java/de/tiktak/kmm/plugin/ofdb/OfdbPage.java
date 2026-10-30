package de.tiktak.kmm.plugin.ofdb;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.tiktak.kmm.plugin.api.HtmlText;

/**
 * Liest die Angaben aus den HTML-Seiten von OFDb.
 */
final class OfdbPage {

    private static final Pattern IMDB_ID = Pattern.compile("imdb\\.com/title/(tt[0-9]{7,10})");
    private static final Pattern GENRE = Pattern.compile("itemprop=\"genre\">([^<]*)");
    private static final Pattern COUNTRY = Pattern.compile("itemprop=\"countryOfOrigin\">([^<]*)");
    private static final Pattern DIRECTOR = person("director");
    private static final Pattern ACTOR = person("actor");
    private static final Pattern RELEASE = Pattern.compile("Freigabe: FSK ([^<\"]*)");
    private static final Pattern AGE = Pattern.compile("[0-9]{1,2}");
    private static final Pattern PLOT_LINK = Pattern.compile("/film/([0-9]+),([0-9]+),[^\"/]*/plot/");
    private static final Pattern PLOT = Pattern.compile(
            "(?s)flagline-lg\">Inhaltsangabe</h5>.*?<h4 class=\"mb15\">(.*?)</h4>\\s*<p>(.*?)</p>");
    private static final String NO_AGE_LIMIT = "o.A";
    private static final String ELLIPSIS = "...";
    private static final String CERTIFICATION_PREFIX = "FSK ";

    private OfdbPage() {
    }

    private static Pattern person(String role) {
        return Pattern.compile("itemprop=\"%s\"[^>]*>\\s*<a[^>]*>\\s*<span itemprop=\"name\">([^<]*)".formatted(role));
    }

    static String imdbId(String film) {
        Matcher matcher = IMDB_ID.matcher(film);
        return matcher.find() ? matcher.group(1) : "";
    }

    static List<String> genres(String film) {
        return values(GENRE, film);
    }

    static List<String> countries(String film) {
        return values(COUNTRY, film);
    }

    static List<String> directors(String film) {
        return values(DIRECTOR, film);
    }

    static List<String> actors(String film) {
        return values(ACTOR, film);
    }

    /** Die FSK der angebotenen Fassungen, aber nur wenn alle dieselbe haben: Geschnittene Fassungen weichen ab. */
    static String certification(String film) {
        Set<String> ages = RELEASE.matcher(film).results().map(release -> age(release.group(1)))
                .flatMap(Optional::stream).collect(Collectors.toSet());
        return ages.size() == 1 ? CERTIFICATION_PREFIX + ages.iterator().next() : null;
    }

    private static Optional<String> age(String release) {
        if (release.contains(NO_AGE_LIMIT)) {
            return Optional.of("0");
        }
        Matcher matcher = AGE.matcher(release);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    /** @return ID der Inhaltsangabe, falls der Film eine hat */
    static Optional<Integer> plotId(String film) {
        Matcher matcher = PLOT_LINK.matcher(film);
        return matcher.find() ? Optional.of(Integer.valueOf(matcher.group(2))) : Optional.empty();
    }

    /** Die Inhaltsangabe; OFDb teilt ihren ersten Satz oft mit „…“ auf Überschrift und Text auf. */
    static String plot(String page) {
        Matcher matcher = PLOT.matcher(page);
        if (!matcher.find()) {
            return null;
        }
        String head = HtmlText.plain(matcher.group(1));
        String body = HtmlText.plain(matcher.group(2));
        if (head.endsWith(ELLIPSIS) && body.startsWith(ELLIPSIS)) {
            head = head.substring(0, head.length() - ELLIPSIS.length()).strip();
            body = body.substring(ELLIPSIS.length()).strip();
        }
        String plot = (head + " " + body).strip();
        return plot.isEmpty() ? null : plot;
    }

    private static List<String> values(Pattern pattern, String html) {
        return pattern.matcher(html).results().map(value -> HtmlText.plain(value.group(1)))
                .filter(value -> !value.isEmpty()).distinct().toList();
    }
}
