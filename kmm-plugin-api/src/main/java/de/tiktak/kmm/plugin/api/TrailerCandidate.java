package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

/**
 * A trailer to choose from.
 *
 * @param name       title of the video
 * @param kodiUrl    link Kodi plays and that goes into the NFO, e.g.
 *                   {@code plugin://plugin.video.youtube/?action=play_video&videoid=…}
 * @param webUrl     address to watch it in the browser
 * @param previewUrl preview image or {@code null}
 * @param language   language (ISO 639-1) or {@code null}
 * @param type       type according to the provider, e.g. {@code Trailer} or {@code Teaser}
 * @param official   whether the provider marks the video as official
 * @param height     picture height, e.g. 1080, or {@code null}
 * @param published  publication as ISO date or {@code null}
 */
public record TrailerCandidate(
        String name,
        String kodiUrl,
        URI webUrl,
        URI previewUrl,
        String language,
        String type,
        boolean official,
        Integer height,
        String published) {

    public TrailerCandidate {
        Objects.requireNonNull(kodiUrl, "kodiUrl must not be null");
    }
}
