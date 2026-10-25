package de.tiktak.kmm.plugin.tmdb;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt die Antwort von {@code /movie/{id}/images} in Bild-Kandidaten. TMDB liefert Poster,
 * Hintergründe (Kodi: Fanart) und Logos (Kodi: Clearlogo).
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
                        candidates(images.path("posters"), ImageType.POSTER, POSTER_PREVIEW_SIZE),
                        candidates(images.path("backdrops"), ImageType.FANART, BACKDROP_PREVIEW_SIZE),
                        candidates(images.path("logos"), ImageType.CLEARLOGO, LOGO_PREVIEW_SIZE))
                .flatMap(stream -> stream)
                .toList();
    }

    private Stream<ImageCandidate> candidates(JsonNode images, ImageType type, String previewSize) {
        return JsonNodes.elements(images)
                .filter(image -> isSupported(image.path("file_path").asString()))
                .map(image -> toCandidate(image, type, previewSize));
    }

    private ImageCandidate toCandidate(JsonNode image, ImageType type, String previewSize) {
        String filePath = image.path("file_path").asString();
        return ImageCandidate.builder()
                .type(type)
                .url(settings.imageUrl(ORIGINAL_SIZE, filePath))
                .previewUrl(settings.imageUrl(previewSize, filePath))
                .width(positive(image.path("width").asInt(0)))
                .height(positive(image.path("height").asInt(0)))
                .language(language(image))
                .rating(image.path("vote_average").isNumber() ? image.path("vote_average").asDouble() : null)
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
