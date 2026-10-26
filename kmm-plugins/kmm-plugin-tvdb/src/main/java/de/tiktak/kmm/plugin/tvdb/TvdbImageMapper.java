package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt die Artworks einer Serie ({@code /series/{id}/extended}) in Bild-Kandidaten. Staffelbilder
 * sind bei TVDB über die ID der Staffel zugeordnet; KMM braucht die Staffelnummer.
 */
class TvdbImageMapper {

    /** TVDB-Artwork-Typen (siehe {@code /artwork/types}) für die ganze Serie. */
    private static final Map<Integer, ImageType> SERIES_TYPES = Map.of(
            1, ImageType.BANNER,
            2, ImageType.POSTER,
            3, ImageType.FANART,
            22, ImageType.CLEARART,
            23, ImageType.CLEARLOGO);
    /** TVDB-Artwork-Typen für Staffeln. */
    private static final Map<Integer, ImageType> SEASON_TYPES = Map.of(
            6, ImageType.BANNER,
            7, ImageType.POSTER,
            8, ImageType.FANART);
    /** TVDB nennt Sprachen nach ISO 639-2 ({@code eng}), KMM nach ISO 639-1 ({@code en}). */
    private static final Map<String, String> ISO1_BY_ISO3 = iso1ByIso3();

    List<ImageCandidate> toCandidates(JsonNode series) {
        Map<String, Integer> seasonNumbers = new HashMap<>();
        JsonNodes.elements(series.path("seasons"))
                .forEach(season -> seasonNumbers.put(season.path("id").asString(), season.path("number").asInt()));
        return JsonNodes.elements(series.path("artworks"))
                .map(artwork -> toCandidate(artwork, seasonNumbers))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ImageCandidate> toCandidate(JsonNode artwork, Map<String, Integer> seasonNumbers) {
        int type = artwork.path("type").asInt();
        Optional<URI> url = JsonNodes.text(artwork, "image").flatMap(TvdbImageMapper::uri);
        if (url.isEmpty()) {
            return Optional.empty();
        }
        Integer season = null;
        ImageType imageType = SERIES_TYPES.get(type);
        if (imageType == null) {
            imageType = SEASON_TYPES.get(type);
            season = JsonNodes.text(artwork, "seasonId").map(seasonNumbers::get).orElse(null);
            if (imageType == null || season == null) {
                return Optional.empty();
            }
        }
        return Optional.of(ImageCandidate.builder()
                .type(imageType)
                .url(url.get())
                .previewUrl(JsonNodes.text(artwork, "thumbnail").flatMap(TvdbImageMapper::uri).orElse(null))
                .width(positive(artwork, "width"))
                .height(positive(artwork, "height"))
                .language(JsonNodes.text(artwork, "language").map(TvdbImageMapper::iso1).orElse(null))
                .rating(artwork.path("score").isNumber() ? artwork.path("score").asDouble() : null)
                .season(season)
                .build());
    }

    private static String iso1(String iso3) {
        return ISO1_BY_ISO3.getOrDefault(iso3.toLowerCase(Locale.ROOT), iso3);
    }

    private static Map<String, String> iso1ByIso3() {
        Map<String, String> languages = new HashMap<>();
        for (String iso1 : Locale.getISOLanguages()) {
            try {
                languages.put(Locale.of(iso1).getISO3Language(), iso1);
            } catch (MissingResourceException e) {
                // Sprachen ohne dreistelligen Code kommen bei TVDB nicht vor.
            }
        }
        return Map.copyOf(languages);
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
