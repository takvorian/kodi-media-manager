package de.tiktak.kmm.settings;

/**
 * Erkennt Einträge des Einstellungsspeichers, die Zugangsdaten enthalten: API-Keys der Plugins und das Proxy-Passwort.
 */
public final class CredentialSettings {

    private static final String PLUGIN_PREFIX = "plugin.";

    private CredentialSettings() {
    }

    public static boolean isCredential(String settingKey) {
        return settingKey.startsWith(PLUGIN_PREFIX) || SettingsStore.PROXY_PASSWORD.equals(settingKey);
    }
}
