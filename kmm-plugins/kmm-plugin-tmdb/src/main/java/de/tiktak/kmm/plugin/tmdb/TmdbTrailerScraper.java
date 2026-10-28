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
 * Trailer von The Movie Database (TMDB). TMDB verweist auf YouTube-Videos; Kodi spielt sie über das YouTube-Addon
 * ab, wie Embers „Kodi-Trailer-Format“. Andere Seiten (z. B. Vimeo) werden übergangen.
 */
@Extension
public class TmdbTrailerScraper extends AbstractTmdbScraper implements TrailerScraper {

    static final String KODI_YOUTUBE = "plugin://plugin.video.youtube/?action=play_video&videoid=";
    private static final String YOUTUBE = "YouTube";
    private static final String WATCH = "https://www.youtube.com/watch?v=";
    private static final String THUMBNAIL = "https://img.youtube.com/vi/%s/mqdefault.jpg";
    /** YouTube-IDs bestehen aus Buchstaben, Ziffern, {@code -} und {@code _}. */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");
    private static final int DATE_LENGTH = 10;

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE);
    }

    @Override
    public List<TrailerCandidate> findTrailers(ScrapeRequest request) {
        if (request.mediaType() != MediaType.MOVIE) {
            return List.of();
        }
        TmdbApi api = api();
        return api.tmdbIdOf(request.uniqueIds())
                .flatMap(api::movieVideos)
                .map(videos -> JsonNodes.elements(videos.path("results"))
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
