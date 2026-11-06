package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Turns TMDB responses on TV shows, seasons and episodes into the types of the plugin API.
 */
@RequiredArgsConstructor
class TmdbTvMapper {

    static final String RATING_SOURCE = "themoviedb";
    static final String CONTINUING = "Continuing";
    static final String ENDED = "Ended";
    private static final int RATING_MAX = 10;
    private static final String SEARCH_POSTER_SIZE = "w185";
    private static final String PROFILE_SIZE = "w185";
    private static final String STILL_SIZE = "original";
    private static final String DIRECTOR = "Director";
    private static final String WRITER = "Writer";
    private static final String RETURNING_SERIES = "Returning Series";
    private static final String IN_PRODUCTION = "In Production";
    /** TMDB names seasons without a name of their own "Staffel 2", "Season 2" or "Specials"; that is no title. */
    private static final Pattern GENERIC_SEASON_NAME = Pattern.compile("(staffel|season|specials)( \\d+)?");

    private final TmdbSettings settings;

    SearchResult toSearchResult(JsonNode show) {
        return new SearchResult(
                show.path("id").asString(),
                title(show),
                text(show, "original_name").orElse(null),
                firstAirDate(show).map(LocalDate::getYear).orElse(null),
                text(show, "overview").orElse(null),
                text(show, "poster_path").map(path -> settings.imageUrl(SEARCH_POSTER_SIZE, path)).orElse(null));
    }

    TvShowResult toShow(JsonNode show, List<JsonNode> seasons) {
        Optional<LocalDate> firstAirDate = firstAirDate(show);
        return TvShowResult.builder()
                .title(title(show))
                .originalTitle(text(show, "original_name").orElse(null))
                .year(firstAirDate.map(LocalDate::getYear).orElse(null))
                .premiered(firstAirDate.orElse(null))
                .plot(text(show, "overview").orElse(null))
                .runtime(runtime(show))
                .certification(certification(show).orElse(null))
                .status(status(show).orElse(null))
                .genres(names(show.path("genres")))
                .studios(names(show.path("networks")))
                .actors(cast(show.path("credits").path("cast")))
                .ratings(rating(show).stream().toList())
                .uniqueIds(showIds(show))
                .seasons(seasons.stream().map(TmdbTvMapper::toSeason).toList())
                .episodes(seasons.stream()
                        .flatMap(season -> JsonNodes.elements(season.path("episodes")))
                        .map(this::toEpisode)
                        .toList())
                .build();
    }

    private static SeasonResult toSeason(JsonNode season) {
        int number = season.path("season_number").asInt();
        String name = text(season, "name")
                .filter(text -> !GENERIC_SEASON_NAME.matcher(text.toLowerCase(Locale.ROOT)).matches())
                .orElse(null);
        return new SeasonResult(number, name, text(season, "overview").orElse(null),
                date(season, "air_date").orElse(null));
    }

    private EpisodeResult toEpisode(JsonNode episode) {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put(TmdbApi.TMDB, episode.path("id").asString());
        return EpisodeResult.builder()
                .season(episode.path("season_number").asInt())
                .episode(episode.path("episode_number").asInt())
                .title(text(episode, "name").orElse(null))
                .plot(text(episode, "overview").orElse(null))
                .aired(date(episode, "air_date").orElse(null))
                .runtime(positiveNumber(episode, "runtime"))
                .directors(crew(episode, DIRECTOR))
                .writers(crew(episode, WRITER))
                .guestStars(cast(episode.path("guest_stars")))
                .ratings(rating(episode).stream().toList())
                .uniqueIds(ids)
                .thumb(text(episode, "still_path").map(path -> settings.imageUrl(STILL_SIZE, path)).orElse(null))
                .build();
    }

    /** Kodi only knows "Continuing" and "Ended". */
    private static Optional<String> status(JsonNode show) {
        return text(show, "status").map(status -> RETURNING_SERIES.equals(status) || IN_PRODUCTION.equals(status)
                ? CONTINUING : ENDED);
    }

    private Optional<String> certification(JsonNode show) {
        return JsonNodes.elements(show.path("content_ratings").path("results"))
                .filter(rating -> settings.certificationCountry().equalsIgnoreCase(rating.path("iso_3166_1").asString()))
                .map(rating -> rating.path("rating").asString().strip())
                .filter(rating -> !rating.isEmpty())
                .findFirst()
                .map(rating -> settings.certificationPrefix() + rating);
    }

    private static Integer runtime(JsonNode show) {
        return JsonNodes.elements(show.path("episode_run_time"))
                .map(JsonNode::asInt)
                .filter(minutes -> minutes > 0)
                .findFirst()
                .orElse(null);
    }

    private static Optional<ProviderRating> rating(JsonNode node) {
        double average = node.path("vote_average").asDouble(0);
        int votes = node.path("vote_count").asInt(0);
        return average > 0 ? Optional.of(new ProviderRating(RATING_SOURCE, average, RATING_MAX, votes))
                : Optional.empty();
    }

    private static Map<String, String> showIds(JsonNode show) {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put(TmdbApi.TMDB, show.path("id").asString());
        JsonNode external = show.path("external_ids");
        text(external, "tvdb_id").ifPresent(id -> ids.put(TmdbApi.TVDB, id));
        text(external, "imdb_id").ifPresent(id -> ids.put(TmdbApi.IMDB, id));
        return ids;
    }

    private List<CastMember> cast(JsonNode cast) {
        return JsonNodes.elements(cast)
                .filter(person -> text(person, "name").isPresent())
                .map(person -> new CastMember(text(person, "name").orElseThrow(),
                        text(person, "character").orElse(null),
                        text(person, "profile_path").map(this::profileUrl).orElse(null)))
                .toList();
    }

    private static List<String> crew(JsonNode episode, String job) {
        return JsonNodes.elements(episode.path("crew"))
                .filter(person -> job.equals(person.path("job").asString()))
                .map(person -> text(person, "name"))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private static List<String> names(JsonNode array) {
        return JsonNodes.elements(array)
                .map(entry -> text(entry, "name"))
                .flatMap(Optional::stream)
                .toList();
    }

    private URI profileUrl(String path) {
        return settings.imageUrl(PROFILE_SIZE, path);
    }

    private static String title(JsonNode show) {
        return text(show, "name").orElse(text(show, "original_name").orElse(""));
    }

    private static Optional<LocalDate> firstAirDate(JsonNode show) {
        return date(show, "first_air_date");
    }

    private static Optional<LocalDate> date(JsonNode node, String field) {
        return text(node, field).flatMap(value -> {
            try {
                return Optional.of(LocalDate.parse(value));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    private static Integer positiveNumber(JsonNode node, String field) {
        int value = node.path(field).asInt(0);
        return value > 0 ? value : null;
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
