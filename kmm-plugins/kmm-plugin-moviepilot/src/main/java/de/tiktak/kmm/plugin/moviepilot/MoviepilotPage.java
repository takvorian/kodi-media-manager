package de.tiktak.kmm.plugin.moviepilot;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.HtmlText;
import de.tiktak.kmm.plugin.api.ScraperException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the data of a Moviepilot movie page from the JSON embedded in the page.
 */
final class MoviepilotPage {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Pattern PAGE_DATA = Pattern.compile("(?s)<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>");
    private static final Pattern PLOT_HEADING = Pattern.compile("(?s)^.*?<strong>\\s*Handlung[^<]*</strong>");
    private static final String NEXT_HEADING = "<strong>";
    private static final String CERTIFICATION_PREFIX = "FSK ";
    private static final String COUNTRY_GROUP = "Produktionsland";
    private static final String DIRECTOR = "director";
    private static final String WRITER_ROLE = "Drehbuch";
    private static final LocalizedText UNKNOWN_FORMAT = LocalizedText.of("scraper.moviepilot.unknownPageFormat");

    private final JsonNode data;

    private MoviepilotPage(JsonNode data) {
        this.data = data;
    }

    /** @throws ScraperException if the page does not contain the expected data, i.e. it was rebuilt */
    static MoviepilotPage parse(String html) {
        Matcher matcher = PAGE_DATA.matcher(html);
        if (!matcher.find()) {
            throw new ScraperException(UNKNOWN_FORMAT);
        }
        try {
            return new MoviepilotPage(JSON.readTree(matcher.group(1)).path("props").path("initialProps")
                    .path("pageProps"));
        } catch (JacksonException e) {
            throw new ScraperException(UNKNOWN_FORMAT, e);
        }
    }

    String imdbId() {
        return data.path("imdbId").asString("");
    }

    /** Without genres: under "Genre" Moviepilot lists keywords such as "Hard SF" or "Mafiafilm". */
    Enrichment enrichment() {
        JsonNode item = data.path("listOfLists").path("item");
        return new Enrichment(List.of(), plot(), positive(item.path("runtime")), premiered(item), certification(item),
                List.of(), people("crew", person -> DIRECTOR.equals(person.path("itemprop").asString(""))),
                people("crew", person -> person.path("titleSubline").asString("").startsWith(WRITER_ROLE)),
                people("cast", person -> true), tags(COUNTRY_GROUP));
    }

    /** Only the section "Handlung" (plot): editorial background follows on the page. */
    private String plot() {
        String description = data.path("description").asString("");
        String text = PLOT_HEADING.matcher(description).replaceFirst("");
        int next = text.indexOf(NEXT_HEADING);
        String plot = HtmlText.plain(next < 0 ? text : text.substring(0, next));
        return plot.isEmpty() ? null : plot;
    }

    private static Integer positive(JsonNode number) {
        int value = number.asInt(0);
        return value > 0 ? value : null;
    }

    private static LocalDate premiered(JsonNode item) {
        return date(item.path("premiereDate")) != null ? date(item.path("premiereDate"))
                : date(item.path("cinemaStartDate"));
    }

    private static LocalDate date(JsonNode value) {
        try {
            return value.isString() ? LocalDate.parse(value.asString()) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String certification(JsonNode item) {
        JsonNode rated = item.path("rated");
        return rated.isNumber() && rated.asInt() >= 0 ? CERTIFICATION_PREFIX + rated.asInt() : null;
    }

    private List<String> tags(String group) {
        return data.path("tagGroups").valueStream()
                .filter(tagGroup -> group.equals(tagGroup.path("title").asString("")))
                .flatMap(tagGroup -> tagGroup.path("content").valueStream())
                .map(tag -> tag.path("name").asString("").strip())
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> people(String list, Predicate<JsonNode> wanted) {
        return data.path(list).valueStream()
                .filter(wanted)
                .map(person -> person.path("title").asString("").strip())
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }
}
