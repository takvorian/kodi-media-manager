package de.tiktak.kmm.plugin;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.plugin.api.Scraper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScraperController {

    public static final String METADATA_SCRAPERS_PATH = "/api/v1/scrapers/metadata";
    public static final String TV_SHOW_SCRAPERS_PATH = "/api/v1/scrapers/tvshows";

    private final ScraperRegistry scraperRegistry;

    @GetMapping(METADATA_SCRAPERS_PATH)
    public List<ScraperInfo> metadataScrapers() {
        return sorted(scraperRegistry.metadataScrapers());
    }

    @GetMapping(TV_SHOW_SCRAPERS_PATH)
    public List<ScraperInfo> tvShowScrapers() {
        return sorted(scraperRegistry.tvShowScrapers());
    }

    private static List<ScraperInfo> sorted(List<? extends Scraper> scrapers) {
        return scrapers.stream()
                .map(ScraperInfo::from)
                .sorted(Comparator.comparing(ScraperInfo::displayName))
                .toList();
    }
}
