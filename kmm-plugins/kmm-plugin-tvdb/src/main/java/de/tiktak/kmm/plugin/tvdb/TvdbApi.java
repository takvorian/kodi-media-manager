package de.tiktak.kmm.plugin.tvdb;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * The TVDB endpoints KMM uses. Checks IDs strictly before they end up in the URL path.
 */
class TvdbApi {

    static final String TVDB = "tvdb";
    static final String TMDB = "tmdb";
    static final String IMDB = "imdb";
    /** Guards against endless paging on faulty responses; 50 pages of 500 episodes are enough for any TV show. */
    static final int MAX_EPISODE_PAGES = 50;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");

    private final TvdbClient client;
    private final TvdbSettings settings;
    private final Clock clock;

    TvdbApi(TvdbClient client, TvdbSettings settings, Clock clock) {
        this.client = client;
        this.settings = settings;
        this.clock = clock;
    }

    List<JsonNode> searchSeries(String title, Integer year) {
        Map<String, String> parameters = new HashMap<>(Map.of("query", title, "type", "series"));
        if (year != null) {
            parameters.put("year", year.toString());
        }
        return client.get("/search", parameters)
                .map(response -> JsonNodes.elements(response.path("data")).toList())
                .orElse(List.of());
    }

    Optional<JsonNode> series(String tvdbId) {
        if (!isNumericId(tvdbId)) {
            return Optional.empty();
        }
        // Without "short" because only the full response contains the actors.
        return client.get("/series/" + tvdbId + "/extended", Map.of())
                .map(response -> response.path("data"));
    }

    /** Name and description in the configured language; empty if there is no translation. */
    Optional<JsonNode> seriesTranslation(String tvdbId) {
        return client.get("/series/" + tvdbId + "/translations/" + settings.language(), Map.of())
                .map(response -> response.path("data"));
    }

    /** All episodes in the configured order, without or with translation. */
    List<JsonNode> episodes(String tvdbId, boolean translated) {
        String path = "/series/" + tvdbId + "/episodes/" + settings.seasonType()
                + (translated ? "/" + settings.language() : "");
        List<JsonNode> episodes = new ArrayList<>();
        for (int page = 0; page < MAX_EPISODE_PAGES; page++) {
            Optional<JsonNode> response = client.get(path, Map.of("page", Integer.toString(page)));
            if (response.isEmpty()) {
                break;
            }
            JsonNodes.elements(response.get().path("data").path("episodes")).forEach(episodes::add);
            if (JsonNodes.text(response.get().path("links"), "next").isEmpty()) {
                break;
            }
        }
        return episodes;
    }

    /** Details of an episode with directors, writers and guest stars; cached. */
    Optional<JsonNode> episode(String tvdbId) {
        if (!isNumericId(tvdbId)) {
            return Optional.empty();
        }
        String key = settings.baseUrl() + "|" + tvdbId;
        Optional<JsonNode> cached = TvdbEpisodeCache.get(key, clock);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<JsonNode> details = client.get("/episodes/" + tvdbId + "/extended", Map.of())
                .map(response -> response.path("data"));
        details.ifPresent(data -> TvdbEpisodeCache.put(key, data, clock));
        return details;
    }

    /** TVDB ID of a TV show from known IDs: directly, via the IMDb or the TMDB ID. */
    Optional<String> seriesIdOf(Map<String, String> uniqueIds) {
        Optional<String> known = Optional.ofNullable(uniqueIds.get(TVDB)).filter(TvdbApi::isNumericId);
        if (known.isPresent()) {
            return known;
        }
        return Optional.ofNullable(uniqueIds.get(IMDB)).filter(id -> IMDB_ID.matcher(id).matches())
                .or(() -> Optional.ofNullable(uniqueIds.get(TMDB)).filter(TvdbApi::isNumericId))
                .flatMap(this::findByRemoteId);
    }

    private Optional<String> findByRemoteId(String remoteId) {
        return client.get("/search/remoteid/" + remoteId, Map.of())
                .flatMap(response -> JsonNodes.elements(response.path("data"))
                        .map(match -> match.path("series"))
                        .filter(series -> !series.isMissingNode() && !series.isNull())
                        .findFirst())
                .flatMap(series -> JsonNodes.text(series, "id"));
    }

    private static boolean isNumericId(String id) {
        return NUMERIC_ID.matcher(id).matches();
    }
}
