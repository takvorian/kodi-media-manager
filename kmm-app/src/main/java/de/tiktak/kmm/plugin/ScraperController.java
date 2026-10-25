package de.tiktak.kmm.plugin;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScraperController {

    public static final String METADATA_SCRAPERS_PATH = "/api/v1/scrapers/metadata";

    private final ScraperRegistry scraperRegistry;

    @GetMapping(METADATA_SCRAPERS_PATH)
    public List<ScraperInfo> metadataScrapers() {
        return scraperRegistry.metadataScrapers().stream()
                .map(ScraperInfo::from)
                .sorted(Comparator.comparing(ScraperInfo::displayName))
                .toList();
    }
}
