package de.tiktak.kmm.plugin.api;

import java.net.URI;
import java.util.Objects;

import lombok.Builder;

/**
 * An image found by an image scraper.
 *
 * @param url        image in full resolution; downloaded when it is applied
 * @param previewUrl small preview for the selection; equal to {@code url} without a preview of its own
 * @param language   ISO 639-1 language code or {@code null} for language-neutral images
 * @param rating     popularity at the provider (greater is better) or {@code null}
 * @param season     season the image belongs to (0 for specials), or {@code null} for images of the whole movie
 *                   or TV show
 */
@Builder
public record ImageCandidate(
        ImageType type,
        URI url,
        URI previewUrl,
        Integer width,
        Integer height,
        String language,
        Double rating,
        Integer season) {

    public ImageCandidate {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(url, "url must not be null");
        previewUrl = previewUrl == null ? url : previewUrl;
    }
}
