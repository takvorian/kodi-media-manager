package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.SearchResult;
import de.tiktak.kmm.plugin.api.SeasonResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt TVDB-Antworten in die Typen der Plugin-API. Texte kommen bevorzugt aus der Übersetzung
 * in der eingestellten Sprache, sonst in der Originalsprache.
 */
class TvdbMapper {

    static final String CONTINUING = "Continuing";
    static final String ENDED = "Ended";
    private static final String ACTOR = "Actor";
    private static final String IMDB_SOURCE = "IMDB";
    private static final String TMDB_SOURCE = "TheMovieDB.com";
    private static final int YEAR_LENGTH = 4;
    private static final String AGE_SUFFIX = "+";

    private final TvdbSettings settings;

    TvdbMapper(TvdbSettings settings) {
        this.settings = settings;
    }

    SearchResult toSearchResult(JsonNode series) {
        String name = JsonNodes.text(series, "name").orElse("");
        return new SearchResult(
                JsonNodes.text(series, "tvdb_id").orElseGet(() -> JsonNodes.text(series, "id").orElse("")),
                JsonNodes.text(series.path("translations"), settings.language()).orElse(name),
                name,
                year(series).orElse(null),
                JsonNodes.text(series.path("overviews"), settings.language())
                        .or(() -> JsonNodes.text(series, "overview"))
                        .orElse(null),
                JsonNodes.text(series, "image_url").flatMap(TvdbMapper::uri).orElse(null));
    }

    TvShowResult toShow(JsonNode series, Optional<JsonNode> translation, List<JsonNode> episodes,
            List<JsonNode> translatedEpisodes) {
        Optional<LocalDate> firstAired = date(series, "firstAired");
        String originalName = JsonNodes.text(series, "name").orElse("");
        Map<String, JsonNode> translated = translatedEpisodes.stream()
                .collect(Collectors.toMap(episode -> episode.path("id").asString(), Function.identity(),
                        (first, second) -> first));
        return TvShowResult.builder()
                .title(translation.flatMap(text -> JsonNodes.text(text, "name")).orElse(originalName))
                .originalTitle(originalName.isEmpty() ? null : originalName)
                .year(firstAired.map(LocalDate::getYear).or(() -> year(series)).orElse(null))
                .premiered(firstAired.orElse(null))
                .plot(translation.flatMap(text -> JsonNodes.text(text, "overview"))
                        .or(() -> JsonNodes.text(series, "overview"))
                        .orElse(null))
                .runtime(positive(series, "averageRuntime"))
                .certification(certification(series).orElse(null))
                .status(status(series).orElse(null))
                .genres(names(series.path("genres")))
                .studios(network(series).stream().toList())
                .actors(actors(series))
                .uniqueIds(showIds(series))
                .seasons(seasons(series))
                .episodes(episodes.stream()
                        .map(episode -> toEpisode(episode, translated.get(episode.path("id").asString())))
                        .toList())
                .build();
    }

    private EpisodeResult toEpisode(JsonNode episode, JsonNode translation) {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put(TvdbApi.TVDB, episode.path("id").asString());
        return EpisodeResult.builder()
                .season(episode.path("seasonNumber").asInt())
                .episode(episode.path("number").asInt())
                .title(translatedText(translation, episode, "name").orElse(null))
                .plot(translatedText(translation, episode, "overview").orElse(null))
                .aired(date(episode, "aired").orElse(null))
                .runtime(positive(episode, "runtime"))
                .uniqueIds(ids)
                .thumb(JsonNodes.text(episode, "image").flatMap(TvdbMapper::uri).orElse(null))
                .build();
    }

    /** Staffeln der eingestellten Reihenfolge; Staffeltitel nur, wenn TVDB einen kennt. */
    private List<SeasonResult> seasons(JsonNode series) {
        return JsonNodes.elements(series.path("seasons"))
                .filter(season -> settings.seasonType().equals(season.path("type").path("type").asString()))
                .map(season -> new SeasonResult(season.path("number").asInt(),
                        JsonNodes.text(season, "name").orElse(null), null, null))
                .collect(Collectors.toMap(SeasonResult::number, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparingInt(SeasonResult::number))
                .toList();
    }

    private static List<CastMember> actors(JsonNode series) {
        return JsonNodes.elements(series.path("characters"))
                .filter(character -> ACTOR.equals(character.path("peopleType").asString()))
                .filter(character -> JsonNodes.text(character, "personName").isPresent())
                .sorted(Comparator.comparingInt(character -> character.path("sort").asInt(Integer.MAX_VALUE)))
                .map(character -> new CastMember(
                        JsonNodes.text(character, "personName").orElseThrow(),
                        JsonNodes.text(character, "name").orElse(null),
                        JsonNodes.text(character, "personImgURL")
                                .or(() -> JsonNodes.text(character, "image"))
                                .flatMap(TvdbMapper::uri)
                                .orElse(null)))
                .toList();
    }

    private Optional<String> certification(JsonNode series) {
        return JsonNodes.elements(series.path("contentRatings"))
                .filter(rating -> settings.certificationCountry().equalsIgnoreCase(rating.path("country").asString()))
                .map(rating -> JsonNodes.text(rating, "name"))
                .flatMap(Optional::stream)
                .findFirst()
                .map(rating -> settings.certificationPrefix() + stripSuffix(rating));
    }

    /** TVDB schreibt deutsche Freigaben teils als „16+“; in der NFO steht „FSK 16“. */
    private static String stripSuffix(String rating) {
        return rating.endsWith(AGE_SUFFIX) ? rating.substring(0, rating.length() - AGE_SUFFIX.length()) : rating;
    }

    /** Kodi kennt nur „Continuing“ und „Ended“; angekündigte Serien gelten als fortlaufend. */
    private static Optional<String> status(JsonNode series) {
        return JsonNodes.text(series.path("status"), "name").map(status -> ENDED.equalsIgnoreCase(status) ? ENDED
                : CONTINUING);
    }

    private static Optional<String> network(JsonNode series) {
        return JsonNodes.text(series.path("originalNetwork"), "name")
                .or(() -> JsonNodes.text(series.path("latestNetwork"), "name"));
    }

    private static Map<String, String> showIds(JsonNode series) {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put(TvdbApi.TVDB, series.path("id").asString());
        JsonNodes.elements(series.path("remoteIds")).forEach(remote -> {
            String source = remote.path("sourceName").asString();
            JsonNodes.text(remote, "id").ifPresent(id -> {
                if (IMDB_SOURCE.equals(source)) {
                    ids.putIfAbsent(TvdbApi.IMDB, id);
                } else if (TMDB_SOURCE.equals(source)) {
                    ids.putIfAbsent(TvdbApi.TMDB, id);
                }
            });
        });
        return ids;
    }

    private static Optional<String> translatedText(JsonNode translation, JsonNode original, String field) {
        return Optional.ofNullable(translation).flatMap(text -> JsonNodes.text(text, field))
                .or(() -> JsonNodes.text(original, field));
    }

    private static List<String> names(JsonNode array) {
        return JsonNodes.elements(array)
                .map(entry -> JsonNodes.text(entry, "name"))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<Integer> year(JsonNode node) {
        return JsonNodes.text(node, "year")
                .filter(year -> year.length() == YEAR_LENGTH && year.chars().allMatch(Character::isDigit))
                .map(Integer::valueOf);
    }

    private static Optional<LocalDate> date(JsonNode node, String field) {
        return JsonNodes.text(node, field).flatMap(value -> {
            try {
                return Optional.of(LocalDate.parse(value));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    private static Integer positive(JsonNode node, String field) {
        int value = node.path(field).asInt(0);
        return value > 0 ? value : null;
    }

    private static Optional<URI> uri(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute() ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
