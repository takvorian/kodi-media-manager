package de.tiktak.kmm.plugin.omdb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import tools.jackson.databind.JsonNode;

/**
 * Bewertungen und englische Angaben von OMDb (omdbapi.com) über die IMDb-ID: IMDb-Bewertung mit Stimmen, Rotten
 * Tomatoes und Metacritic sowie Handlung, Laufzeit, Erstaufführung, Freigabe, Genres, Regie, Drehbuch, Darsteller und
 * Länder für leere Felder. Ohne IMDb-ID wird nicht gesucht, damit keine falschen Treffer entstehen.
 */
@Extension
public class OmdbEnrichmentScraper implements EnrichmentScraper {

    static final String ID = "omdb";
    static final String IMDB = "imdb";
    static final String ROTTEN_TOMATOES = "tomatometerallcritics";
    static final String METACRITIC = "metacritic";
    static final String MISSING_API_KEY = "OMDb: API-Key fehlt (Einstellungen → API-Keys).";
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");
    private static final Pattern NUMBER = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PARENTHESES = Pattern.compile("\\s*\\([^)]*\\)");
    private static final DateTimeFormatter RELEASED = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String NOT_AVAILABLE = "N/A";
    private static final int IMDB_MAX = 10;
    private static final int PERCENT_MAX = 100;

    private OmdbSettings settings;
    private OmdbClient client;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "OMDb";
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Override
    public Set<String> ratingSources() {
        return Set.of(IMDB, ROTTEN_TOMATOES, METACRITIC);
    }

    @Override
    public boolean providesDetails() {
        return true;
    }

    @Override
    public void initialize(PluginContext context) {
        settings = OmdbSettings.from(context);
        client = new OmdbClient(context.httpClient(), settings, context.userAgent());
    }

    @Override
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    @Override
    public Optional<Enrichment> findEnrichment(ScrapeRequest request) {
        if (client == null) {
            throw new IllegalStateException("OMDb scraper has not been initialized");
        }
        String imdbId = request.uniqueIds().get(IMDB);
        if (missingConfiguration().isPresent() || !supports(request.mediaType()) || imdbId == null
                || !IMDB_ID.matcher(imdbId).matches()) {
            return Optional.empty();
        }
        return client.byImdbId(imdbId).map(OmdbEnrichmentScraper::toEnrichment);
    }

    private static Enrichment toEnrichment(JsonNode movie) {
        return new Enrichment(
                ratings(movie),
                text(movie, "Plot"),
                number(text(movie, "Runtime")).map(Double::intValue).orElse(null),
                date(text(movie, "Released")),
                text(movie, "Rated"),
                names(movie, "Genre"),
                names(movie, "Director"),
                names(movie, "Writer"),
                names(movie, "Actors"),
                names(movie, "Country"));
    }

    private static List<ProviderRating> ratings(JsonNode movie) {
        List<ProviderRating> ratings = new ArrayList<>();
        Optional<Double> imdb = number(text(movie, "imdbRating"));
        Optional<Double> votes = number(Optional.ofNullable(text(movie, "imdbVotes"))
                .map(value -> value.replace(",", "")).orElse(null));
        imdb.ifPresent(value -> ratings.add(new ProviderRating(IMDB, value, IMDB_MAX,
                votes.map(Double::intValue).orElse(null))));
        for (JsonNode rating : movie.path("Ratings").valueStream().toList()) {
            String source = rating.path("Source").asString("");
            Optional<Double> value = number(rating.path("Value").asString(NOT_AVAILABLE));
            switch (source) {
                case "Rotten Tomatoes" -> value.ifPresent(percent -> ratings.add(
                        new ProviderRating(ROTTEN_TOMATOES, percent, PERCENT_MAX, null)));
                case "Metacritic" -> value.ifPresent(score -> ratings.add(
                        new ProviderRating(METACRITIC, score, PERCENT_MAX, null)));
                default -> {
                    // Die IMDb-Bewertung steht mit Stimmenzahl schon in imdbRating.
                }
            }
        }
        return ratings;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString("").strip();
        return value.isEmpty() || NOT_AVAILABLE.equals(value) ? null : value;
    }

    private static Optional<Double> number(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER.matcher(text);
        return matcher.find() ? Optional.of(Double.parseDouble(matcher.group(1))) : Optional.empty();
    }

    private static LocalDate date(String text) {
        try {
            return text == null ? null : LocalDate.parse(text, RELEASED);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Kommagetrennte Namen ohne Zusätze in Klammern, etwa „(novel)“, jeder einmal. */
    private static List<String> names(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return List.of();
        }
        return List.copyOf(Stream.of(value.split(","))
                .map(name -> PARENTHESES.matcher(name).replaceAll("").strip())
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
