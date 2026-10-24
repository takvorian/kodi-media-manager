package de.tiktak.kmm.plugin;

import org.pf4j.PluginDescriptor;
import org.pf4j.PluginWrapper;

/**
 * Darstellung eines installierten Plugins für die REST-API.
 */
public record PluginInfo(String id, String version, String description, String provider, String state) {

    static PluginInfo from(PluginWrapper plugin) {
        PluginDescriptor descriptor = plugin.getDescriptor();
        return new PluginInfo(
                descriptor.getPluginId(),
                descriptor.getVersion(),
                descriptor.getPluginDescription(),
                descriptor.getProvider(),
                plugin.getPluginState().name());
    }
}
