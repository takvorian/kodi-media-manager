package de.tiktak.kmm.plugin.testdata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MetadataResult;
import de.tiktak.kmm.plugin.api.MetadataScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.SearchResult;

/**
 * Returns the requested title as the result for every movie. Only serves to test the plugin loading mechanism
 * end to end.
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

    /** The title also serves as the provider ID. */
    @Override
    public List<SearchResult> search(SearchQuery query) {
        if (!supports(query.mediaType())) {
            return List.of();
        }
        return List.of(new SearchResult(query.title(), query.title(), query.title(), query.year(), null, null));
    }

    @Override
    public Optional<MetadataResult> fetch(String providerId) {
        return Optional.of(result(providerId, null));
    }

    @Override
    public Optional<MetadataResult> scrape(ScrapeRequest request) {
        if (!supports(request.mediaType())) {
            return Optional.empty();
        }
        return Optional.of(result(request.title(), request.year()));
    }

    private static MetadataResult result(String title, Integer year) {
        return MetadataResult.builder()
                .title(title)
                .originalTitle(title)
                .year(year)
                .uniqueIds(Map.of(UNIQUE_ID_KEY, title))
                .build();
    }
}
