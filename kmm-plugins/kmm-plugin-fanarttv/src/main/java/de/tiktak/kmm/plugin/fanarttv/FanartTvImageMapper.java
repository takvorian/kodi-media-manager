package de.tiktak.kmm.plugin.fanarttv;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageType;
import tools.jackson.databind.JsonNode;

/**
 * Turns the responses of {@code /movies/{id}} and {@code /tv/{id}} into image candidates by Kodi image type. Season
 * images carry the season number; images for all seasons are dropped.
 */
class FanartTvImageMapper {

    /** fanart.tv field → Kodi image type; HD variants first so they come first at equal rating. */
    private static final Map<String, ImageType> MOVIE_FIELDS = movieFields();
    private static final Map<String, ImageType> TV_FIELDS = tvFields();
    private static final Map<String, ImageType> SEASON_FIELDS = seasonFields();
    /** fanart.tv marks images without language with "00". */
    private static final String NO_LANGUAGE = "00";
    private static final String FULL_IMAGE_PATH = "/fanart/";
    private static final String PREVIEW_PATH = "/preview/";

    List<ImageCandidate> toCandidates(JsonNode movie) {
        return candidates(movie, MOVIE_FIELDS, false).toList();
    }

    List<ImageCandidate> toTvCandidates(JsonNode show) {
        return Stream.concat(candidates(show, TV_FIELDS, false), candidates(show, SEASON_FIELDS, true)).toList();
    }

    private static Stream<ImageCandidate> candidates(JsonNode response, Map<String, ImageType> fields,
            boolean perSeason) {
        return fields.entrySet().stream()
                .flatMap(field -> StreamSupport.stream(response.path(field.getKey()).spliterator(), false)
                        .filter(image -> !perSeason || season(image) != null)
                        .map(image -> toCandidate(image, field.getValue(), perSeason ? season(image) : null))
                        .flatMap(Optional::stream));
    }

    /** Season number of a season image; {@code null} for "all" or invalid values. */
    private static Integer season(JsonNode image) {
        try {
            int season = Integer.parseInt(image.path("season").asString());
            return season >= 0 ? season : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<ImageCandidate> toCandidate(JsonNode image, ImageType type, Integer season) {
        String url = image.path("url").asString().strip();
        Optional<URI> full = uri(url);
        if (full.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ImageCandidate.builder()
                .type(type)
                .url(full.get())
                .previewUrl(uri(url.replace(FULL_IMAGE_PATH, PREVIEW_PATH)).orElse(null))
                .language(language(image.path("lang").asString()))
                .rating(likes(image.path("likes").asString()))
                .season(season)
                .build());
    }

    /** fanart.tv supplies file names with spaces, e.g. {@code A_78020 (0).jpg}. */
    private static Optional<URI> uri(String url) {
        if (url.isEmpty()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.replace(" ", "%20"));
            return uri.isAbsolute() ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
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

    private static Map<String, ImageType> tvFields() {
        Map<String, ImageType> fields = new LinkedHashMap<>();
        fields.put("tvposter", ImageType.POSTER);
        fields.put("showbackground", ImageType.FANART);
        fields.put("hdtvlogo", ImageType.CLEARLOGO);
        fields.put("clearlogo", ImageType.CLEARLOGO);
        fields.put("hdclearart", ImageType.CLEARART);
        fields.put("clearart", ImageType.CLEARART);
        fields.put("tvbanner", ImageType.BANNER);
        fields.put("tvthumb", ImageType.LANDSCAPE);
        fields.put("characterart", ImageType.CHARACTERART);
        return fields;
    }

    private static Map<String, ImageType> seasonFields() {
        Map<String, ImageType> fields = new LinkedHashMap<>();
        fields.put("seasonposter", ImageType.POSTER);
        fields.put("seasonbanner", ImageType.BANNER);
        fields.put("seasonthumb", ImageType.LANDSCAPE);
        return fields;
    }

    private static Map<String, ImageType> movieFields() {
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
