package de.tiktak.kmm.settings;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * State of an API key for the user interface; the key itself is never returned.
 *
 * @param label name of the key for the user
 * @param set   whether a key is stored
 * @param hint  the last characters of the key for recognizing it, or {@code null}
 */
public record ApiKeyView(ApiKey key, String pluginId, LocalizedText label, boolean set, String hint) {
}
