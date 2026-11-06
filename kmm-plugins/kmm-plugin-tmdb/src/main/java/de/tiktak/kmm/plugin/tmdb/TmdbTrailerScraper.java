package de.tiktak.kmm.plugin.tmdb;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.TrailerCandidate;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import tools.jackson.databind.JsonNode;

/**
 * Trailers of movies and TV shows from The Movie Database (TMDB). TMDB refers to YouTube videos; Kodi plays them via
 * the YouTube add-on, like Ember's "Kodi trailer format". Other sites (e.g. Vimeo) are skipped.
 */
@Extension
public class TmdbTrailerScraper extends AbstractTmdbScraper implements TrailerScraper {

    static final String KODI_YOUTUBE = "plugin://plugin.video.youtube/?action=play_video&videoid=";
    private static final String YOUTUBE = "YouTube";
    private static final String WATCH = "https://www.youtube.com/watch?v=";
    private static final String THUMBNAIL = "https://img.youtube.com/vi/%s/mqdefault.jpg";
    /** YouTube IDs consist of letters, digits, {@code -} and {@code _}. */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");
    private static final int DATE_LENGTH = 10;

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Override
    public List<TrailerCandidate> findTrailers(ScrapeRequest request) {
        TmdbApi api = api();
        Optional<JsonNode> videos = switch (request.mediaType()) {
            case MOVIE -> api.tmdbIdOf(request.uniqueIds()).flatMap(api::movieVideos);
            case TV_SHOW -> api.tvShowIdOf(request.uniqueIds()).flatMap(api::tvVideos);
            default -> Optional.empty();
        };
        return videos
                .map(response -> JsonNodes.elements(response.path("results"))
                        .map(TmdbTrailerScraper::toCandidate)
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }

    private static Optional<TrailerCandidate> toCandidate(JsonNode video) {
        String key = video.path("key").asString("");
        if (!YOUTUBE.equals(video.path("site").asString("")) || !VIDEO_ID.matcher(key).matches()) {
            return Optional.empty();
        }
        String language = video.path("iso_639_1").asString("");
        String published = video.path("published_at").asString("");
        int size = video.path("size").asInt(0);
        return Optional.of(new TrailerCandidate(
                video.path("name").asString(""),
                KODI_YOUTUBE + key,
                URI.create(WATCH + key),
                URI.create(THUMBNAIL.formatted(key)),
                language.isEmpty() ? null : language,
                video.path("type").asString(""),
                video.path("official").asBoolean(false),
                size > 0 ? size : null,
                published.length() >= DATE_LENGTH ? published.substring(0, DATE_LENGTH) : null));
    }
}
