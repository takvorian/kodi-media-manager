package de.tiktak.kmm.plugin.tvdb;

import java.time.Clock;
import java.util.List;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Bilder von TheTVDB für Serien: Poster, Fanart, Banner, Clearlogo, Clearart sowie Staffelposter,
 * -banner und -fanart.
 */
@Extension
public class TvdbImageScraper extends AbstractTvdbScraper implements ImageScraper {

    public TvdbImageScraper() {
        this(Clock.systemUTC());
    }

    TvdbImageScraper(Clock clock) {
        super(clock);
    }

    @Override
    public List<ImageCandidate> findImages(ScrapeRequest request) {
        TvdbApi tvdb = api();
        if (!supports(request.mediaType())) {
            return List.of();
        }
        return tvdb.seriesIdOf(request.uniqueIds())
                .flatMap(tvdb::series)
                .map(new TvdbImageMapper()::toCandidates)
                .orElse(List.of());
    }
}
