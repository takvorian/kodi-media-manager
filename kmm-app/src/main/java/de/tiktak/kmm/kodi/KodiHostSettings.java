package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Objects;

import de.tiktak.kmm.kodi.path.KodiPathMapping;

/**
 * Einstellungen eines Kodi-Geräts.
 *
 * @param name         Anzeigename, eindeutig
 * @param address      Rechnername oder IP-Adresse
 * @param port         Port des Kodi-Webservers
 * @param username     Benutzername oder {@code null} ohne Anmeldung
 * @param password     Passwort; {@code null} behält beim Ändern das gespeicherte
 * @param realtimeSync ob KMM Kodi nach jeder Änderung automatisch aktualisiert
 * @param pathMappings Zuordnung der KMM-Verzeichnisse zu Kodi-Pfaden
 */
public record KodiHostSettings(
        String name,
        String address,
        int port,
        String username,
        String password,
        boolean realtimeSync,
        List<KodiPathMapping> pathMappings) {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_ADDRESS_LENGTH = 255;
    public static final int MAX_USERNAME_LENGTH = 128;
    public static final int MAX_PASSWORD_LENGTH = 255;
    public static final int MAX_PATH_LENGTH = 1024;

    public KodiHostSettings {
        name = Objects.requireNonNull(name, "name must not be null").strip();
        address = Objects.requireNonNull(address, "address must not be null").strip();
        username = username == null || username.isBlank() ? null : username.strip();
        pathMappings = List.copyOf(pathMappings);
    }

    /** Ohne Benutzer gibt es kein Passwort. */
    boolean withoutCredentials() {
        return username == null;
    }
}
