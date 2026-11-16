package de.tiktak.kmm.plugin;

import java.util.List;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;

/**
 * A scraper plugin for the overview in the settings.
 *
 * @param id                   ID of the plugin
 * @param displayName          name of the provider; the plugin ID while the plugin is switched off
 * @param enabled              whether the plugin is switched on and started
 * @param capabilities         what the plugin delivers for which media types; empty while it is switched off
 * @param missingConfiguration hint about what is missing for use, or {@code null} if ready
 * @param attribution          notice about the data source required by the provider, or {@code null}
 */
public record ScraperOverview(String id, String displayName, String version, String description, boolean enabled,
        List<Capability> capabilities, LocalizedText missingConfiguration, Attribution attribution) {

    public ScraperOverview {
        capabilities = List.copyOf(capabilities);
    }

    /** One kind of data a plugin delivers, for these media types. */
    public record Capability(ScraperKind kind, Set<MediaType> mediaTypes) {

        public Capability {
            mediaTypes = Set.copyOf(mediaTypes);
        }
    }
}
