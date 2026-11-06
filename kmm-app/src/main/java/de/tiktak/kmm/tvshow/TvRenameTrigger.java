package de.tiktak.kmm.tvshow;

import java.util.function.Predicate;

import de.tiktak.kmm.settings.RenameSettings;

/**
 * When TV shows are renamed automatically; whether they are is set in the settings.
 */
public enum TvRenameTrigger {
    /** After saving edited metadata of a TV show or episode. */
    EDIT(RenameSettings::tvOnEdit),
    /** After automatic scraping. */
    SCRAPE(RenameSettings::tvOnScrape),
    /** After a scan that found new episodes. */
    SCAN(RenameSettings::episodesOnScan);

    private final Predicate<RenameSettings> enabled;

    TvRenameTrigger(Predicate<RenameSettings> enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled(RenameSettings settings) {
        return enabled.test(settings);
    }
}
