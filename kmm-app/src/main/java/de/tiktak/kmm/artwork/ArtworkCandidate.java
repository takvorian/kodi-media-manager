package de.tiktak.kmm.artwork;

import java.net.URI;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;

/**
 * An image that a scraper offers for selection.
 *
 * @param season season of the image or {@code null} for images of the whole medium
 */
public record ArtworkCandidate(
        String scraperId,
        String scraperName,
        ImageType type,
        URI url,
        URI previewUrl,
        Integer width,
        Integer height,
        String language,
        Double rating,
        Integer season) {

    static ArtworkCandidate from(ImageScraper scraper, ImageCandidate image) {
        return new ArtworkCandidate(scraper.id(), scraper.displayName(), image.type(), image.url(),
                image.previewUrl(), image.width(), image.height(), image.language(), image.rating(),
                image.season());
    }
}
