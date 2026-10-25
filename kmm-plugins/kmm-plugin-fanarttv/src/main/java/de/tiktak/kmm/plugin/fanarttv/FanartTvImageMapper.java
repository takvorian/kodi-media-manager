package de.tiktak.kmm.plugin.fanarttv;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import tools.jackson.databind.JsonNode;

/**
 * Übersetzt die Antwort von {@code /movies/{id}} in Bild-Kandidaten nach Kodi-Bildarten.
 */
class FanartTvImageMapper {

    /** fanart.tv-Feld → Kodi-Bildart; HD-Varianten zuerst, damit sie bei gleicher Bewertung vorne stehen. */
    private static final Map<String, ImageType> FIELDS = fields();
    /** fanart.tv kennzeichnet Bilder ohne Sprache mit „00“. */
    private static final String NO_LANGUAGE = "00";
    private static final String FULL_IMAGE_PATH = "/fanart/";
    private static final String PREVIEW_PATH = "/preview/";

    List<ImageCandidate> toCandidates(JsonNode movie) {
        return FIELDS.entrySet().stream()
                .flatMap(field -> StreamSupport.stream(movie.path(field.getKey()).spliterator(), false)
                        .filter(image -> !image.path("url").asString().isBlank())
                        .map(image -> toCandidate(image, field.getValue())))
                .toList();
    }

    private static ImageCandidate toCandidate(JsonNode image, ImageType type) {
        String url = image.path("url").asString();
        return ImageCandidate.builder()
                .type(type)
                .url(URI.create(url))
                .previewUrl(URI.create(url.replace(FULL_IMAGE_PATH, PREVIEW_PATH)))
                .language(language(image.path("lang").asString()))
                .rating(likes(image.path("likes").asString()))
                .build();
    }

    private static String language(String language) {
        return language.isBlank() || NO_LANGUAGE.equals(language) ? null : language;
    }

    private static Double likes(String likes) {
        try {
            return Double.valueOf(likes);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, ImageType> fields() {
        Map<String, ImageType> fields = new LinkedHashMap<>();
        fields.put("movieposter", ImageType.POSTER);
        fields.put("moviebackground", ImageType.FANART);
        fields.put("hdmovielogo", ImageType.CLEARLOGO);
        fields.put("movielogo", ImageType.CLEARLOGO);
        fields.put("hdmovieclearart", ImageType.CLEARART);
        fields.put("movieart", ImageType.CLEARART);
        fields.put("moviedisc", ImageType.DISCART);
        fields.put("moviebanner", ImageType.BANNER);
        fields.put("moviethumb", ImageType.LANDSCAPE);
        return fields;
    }
}
