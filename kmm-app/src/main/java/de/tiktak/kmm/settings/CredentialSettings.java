package de.tiktak.kmm.settings;

/**
 * Recognizes entries of the settings store that contain credentials: plugin API keys and the proxy password.
 */
public final class CredentialSettings {

    private static final String PLUGIN_PREFIX = "plugin.";

    private CredentialSettings() {
    }

    public static boolean isCredential(String settingKey) {
        return settingKey.startsWith(PLUGIN_PREFIX) || SettingsStore.PROXY_PASSWORD.equals(settingKey);
    }
}
