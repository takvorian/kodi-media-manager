package de.tiktak.kmm.settings;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.SettingDefinition;

/**
 * A plugin setting for the user interface. Keys and passwords are never returned, only whether one is stored and its
 * last characters.
 *
 * @param label        name of the setting for the user
 * @param value        the stored value, or {@code null} if the configuration applies; always {@code null} for a key
 * @param defaultValue value that applies without a stored one (configuration or the plugin's default), or
 *                     {@code null}; always {@code null} for a key
 * @param set          whether a value is stored
 * @param hint         the last characters of a key for recognizing it, or {@code null}
 */
public record PluginSettingView(String name, SettingDefinition.Type type, LocalizedText label, String value,
        String defaultValue, boolean set, String hint) {
}
