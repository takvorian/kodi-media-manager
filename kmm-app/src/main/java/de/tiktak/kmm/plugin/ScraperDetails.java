package de.tiktak.kmm.plugin;

import java.util.List;

import de.tiktak.kmm.settings.PluginSettingView;

/**
 * A scraper plugin with its settings for its page in the settings.
 *
 * @param settings the plugin's settings; empty while it is switched off, since only a started plugin declares them
 */
public record ScraperDetails(ScraperOverview scraper, List<PluginSettingView> settings) {

    public ScraperDetails {
        settings = List.copyOf(settings);
    }
}
