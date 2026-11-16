package de.tiktak.kmm.plugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.plugin.api.Scraper;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScraperController {

    public static final String SCRAPERS_PATH = "/api/v1/scrapers";
    public static final String METADATA_SCRAPERS_PATH = SCRAPERS_PATH + "/metadata";
    public static final String TV_SHOW_SCRAPERS_PATH = SCRAPERS_PATH + "/tvshows";
    public static final String PLUGIN_PATH = SCRAPERS_PATH + "/plugins/{pluginId}";

    private final ScraperRegistry scraperRegistry;
    private final ScraperOverviewService overviewService;

    @GetMapping(METADATA_SCRAPERS_PATH)
    public List<ScraperInfo> metadataScrapers() {
        return sorted(scraperRegistry.metadataScrapers());
    }

    @GetMapping(TV_SHOW_SCRAPERS_PATH)
    public List<ScraperInfo> tvShowScrapers() {
        return sorted(scraperRegistry.tvShowScrapers());
    }

    /** All scraper plugins with what they deliver, including switched off ones. */
    @GetMapping(SCRAPERS_PATH + "/plugins")
    public List<ScraperOverview> scraperPlugins() {
        return overviewService.scrapers();
    }

    @GetMapping(PLUGIN_PATH)
    public ScraperDetails scraperPlugin(@PathVariable String pluginId) {
        return overviewService.details(pluginId);
    }

    /** A value replaces a setting, an empty value deletes it; settings not mentioned remain. */
    @PutMapping(PLUGIN_PATH + "/settings")
    public ScraperDetails changeSettings(@PathVariable String pluginId, @RequestBody Map<String, String> changes) {
        return overviewService.changeSettings(pluginId, changes);
    }

    @PostMapping(PLUGIN_PATH + "/enable")
    public ScraperDetails enable(@PathVariable String pluginId) {
        return overviewService.enable(pluginId);
    }

    @PostMapping(PLUGIN_PATH + "/disable")
    public ScraperDetails disable(@PathVariable String pluginId) {
        return overviewService.disable(pluginId);
    }

    private static List<ScraperInfo> sorted(List<? extends Scraper> scrapers) {
        return scrapers.stream()
                .map(ScraperInfo::from)
                .sorted(Comparator.comparing(ScraperInfo::displayName))
                .toList();
    }
}
