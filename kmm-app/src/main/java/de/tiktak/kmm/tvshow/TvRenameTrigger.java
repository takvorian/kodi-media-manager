package de.tiktak.kmm.tvshow;

import java.util.function.Predicate;

import de.tiktak.kmm.settings.RenameSettings;

/**
 * Wann Serien automatisch umbenannt werden; ob, steht in den Einstellungen.
 */
public enum TvRenameTrigger {
    /** Nach dem Speichern bearbeiteter Metadaten einer Serie oder Episode. */
    EDIT(RenameSettings::tvOnEdit),
    /** Nach dem automatischen Scrapen. */
    SCRAPE(RenameSettings::tvOnScrape),
    /** Nach einem Scan, der neue Episoden gefunden hat. */
    SCAN(RenameSettings::episodesOnScan);

    private final Predicate<RenameSettings> enabled;

    TvRenameTrigger(Predicate<RenameSettings> enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled(RenameSettings settings) {
        return enabled.test(settings);
    }
}
