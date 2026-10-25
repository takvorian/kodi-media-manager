package de.tiktak.kmm.artwork;

import java.net.URI;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Ein Bild, das ein Scraper zur Auswahl anbietet.
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
        Double rating) {

    static ArtworkCandidate from(ImageScraper scraper, ImageCandidate image) {
        return new ArtworkCandidate(scraper.id(), scraper.displayName(), image.type(), image.url(),
                image.previewUrl(), image.width(), image.height(), image.language(), image.rating());
    }
}
