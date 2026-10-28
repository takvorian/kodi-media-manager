package de.tiktak.kmm.movie;

import java.util.function.Predicate;

import de.tiktak.kmm.settings.RenameSettings;

/**
 * Wann Filme automatisch umbenannt werden; ob, steht in den Einstellungen.
 */
public enum RenameTrigger {
    /** Nach dem Speichern bearbeiteter Metadaten. */
    EDIT(RenameSettings::movieOnEdit),
    /** Nach dem automatischen Scrapen. */
    SCRAPE(RenameSettings::movieOnScrape);

    private final Predicate<RenameSettings> enabled;

    RenameTrigger(Predicate<RenameSettings> enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled(RenameSettings settings) {
        return enabled.test(settings);
    }
}
