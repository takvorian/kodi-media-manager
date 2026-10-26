package de.tiktak.kmm.plugin.tmdb;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt die Antworten von {@code /movie/{id}/images} und {@code /tv/{id}/images} in Bild-Kandidaten.
 * TMDB liefert Poster, Hintergründe (Kodi: Fanart) und Logos (Kodi: Clearlogo), für Staffeln nur Poster.
 */
@RequiredArgsConstructor
class TmdbImageMapper {

    private static final String ORIGINAL_SIZE = "original";
    private static final String POSTER_PREVIEW_SIZE = "w342";
    private static final String BACKDROP_PREVIEW_SIZE = "w780";
    private static final String LOGO_PREVIEW_SIZE = "w300";
    /** Kodi kann keine Vektorgrafiken anzeigen; TMDB liefert manche Logos als SVG. */
    private static final String UNSUPPORTED_EXTENSION = ".svg";

    private final TmdbSettings settings;

    List<ImageCandidate> toCandidates(JsonNode images) {
        return Stream.of(
                        candidates(images.path("posters"), ImageType.POSTER, POSTER_PREVIEW_SIZE, null),
                        candidates(images.path("backdrops"), ImageType.FANART, BACKDROP_PREVIEW_SIZE, null),
                        candidates(images.path("logos"), ImageType.CLEARLOGO, LOGO_PREVIEW_SIZE, null))
                .flatMap(stream -> stream)
                .toList();
    }

    List<ImageCandidate> toSeasonCandidates(JsonNode images, int season) {
        return candidates(images.path("posters"), ImageType.POSTER, POSTER_PREVIEW_SIZE, season).toList();
    }

    private Stream<ImageCandidate> candidates(JsonNode images, ImageType type, String previewSize, Integer season) {
        return JsonNodes.elements(images)
                .filter(image -> isSupported(image.path("file_path").asString()))
                .map(image -> toCandidate(image, type, previewSize, season));
    }

    private ImageCandidate toCandidate(JsonNode image, ImageType type, String previewSize, Integer season) {
        String filePath = image.path("file_path").asString();
        return ImageCandidate.builder()
                .type(type)
                .url(settings.imageUrl(ORIGINAL_SIZE, filePath))
                .previewUrl(settings.imageUrl(previewSize, filePath))
                .width(positive(image.path("width").asInt(0)))
                .height(positive(image.path("height").asInt(0)))
                .language(language(image))
                .rating(image.path("vote_average").isNumber() ? image.path("vote_average").asDouble() : null)
                .season(season)
                .build();
    }

    private static boolean isSupported(String filePath) {
        return !filePath.isBlank() && !filePath.toLowerCase(Locale.ROOT).endsWith(UNSUPPORTED_EXTENSION);
    }

    private static String language(JsonNode image) {
        JsonNode language = image.path("iso_639_1");
        return language.isNull() || language.isMissingNode() || language.asString().isBlank()
                ? null
                : language.asString();
    }

    private static Integer positive(int value) {
        return value > 0 ? value : null;
    }
}
