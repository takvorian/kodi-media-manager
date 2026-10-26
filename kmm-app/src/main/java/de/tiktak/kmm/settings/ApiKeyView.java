package de.tiktak.kmm.settings;

/**
 * Zustand eines API-Keys für die Oberfläche; der Key selbst wird nie ausgegeben.
 *
 * @param set  ob ein Key gespeichert ist
 * @param hint die letzten Zeichen des Keys zum Wiedererkennen oder {@code null}
 */
public record ApiKeyView(ApiKey key, String pluginId, String label, boolean set, String hint) {
}
