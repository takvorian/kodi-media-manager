package de.tiktak.kmm.plugin.tvdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Die von KMM genutzten TVDB-Endpunkte. Prüft IDs streng, bevor sie im URL-Pfad landen.
 */
class TvdbApi {

    static final String TVDB = "tvdb";
    static final String TMDB = "tmdb";
    static final String IMDB = "imdb";
    /** Schutz vor endlosem Blättern bei fehlerhaften Antworten; 50 Seiten à 500 Episoden reichen für jede Serie. */
    static final int MAX_EPISODE_PAGES = 50;
    private static final Pattern NUMERIC_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");

    private final TvdbClient client;
    private final TvdbSettings settings;

    TvdbApi(TvdbClient client, TvdbSettings settings) {
        this.client = client;
        this.settings = settings;
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
        // Ohne "short", denn nur die vollständige Antwort enthält die Darsteller.
        return client.get("/series/" + tvdbId + "/extended", Map.of())
                .map(response -> response.path("data"));
    }

    /** Name und Beschreibung in der eingestellten Sprache; leer, wenn es keine Übersetzung gibt. */
    Optional<JsonNode> seriesTranslation(String tvdbId) {
        return client.get("/series/" + tvdbId + "/translations/" + settings.language(), Map.of())
                .map(response -> response.path("data"));
    }

    /** Alle Episoden in der eingestellten Reihenfolge, ohne bzw. mit Übersetzung. */
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

    /** TVDB-ID einer Serie aus bekannten IDs: direkt, über die IMDb- oder die TMDB-ID. */
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
