package de.tiktak.kmm.movie;

import java.util.function.Predicate;

import de.tiktak.kmm.settings.RenameSettings;

/**
 * When movies are renamed automatically; whether they are is set in the settings.
 */
public enum RenameTrigger {
    /** After saving edited metadata. */
    EDIT(RenameSettings::movieOnEdit),
    /** After automatic scraping. */
    SCRAPE(RenameSettings::movieOnScrape);

    private final Predicate<RenameSettings> enabled;

    RenameTrigger(Predicate<RenameSettings> enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled(RenameSettings settings) {
        return enabled.test(settings);
    }
}
