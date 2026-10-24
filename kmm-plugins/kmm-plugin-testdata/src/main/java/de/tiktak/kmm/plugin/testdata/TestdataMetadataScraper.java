package de.tiktak.kmm.plugin.testdata;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Liefert für jeden Film den angefragten Titel als Ergebnis zurück. Dient nur dazu, den
 * Plugin-Lademechanismus Ende zu Ende zu testen.
 */
@Extension
public class TestdataMetadataScraper implements MetadataScraper {

    static final String ID = "testdata";
    static final String DISPLAY_NAME = "Testdaten";
    static final String UNIQUE_ID_KEY = "testdata";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE);
    }

    @Override
    public Optional<MetadataResult> scrape(ScrapeRequest request) {
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return Optional.of(new MetadataResult(
                request.title(),
                request.title(),
                request.year(),
                null,
                Map.of(UNIQUE_ID_KEY, request.title())));
    }
}
