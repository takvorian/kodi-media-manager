package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Objects;

import de.tiktak.kmm.kodi.path.KodiPathMapping;

/**
 * Settings of a Kodi device.
 *
 * @param name         display name, unique
 * @param address      host name or IP address
 * @param port         port of the Kodi web server
 * @param username     user name, or {@code null} without login
 * @param password     password; {@code null} keeps the stored one when changing
 * @param realtimeSync whether KMM updates Kodi automatically after every change
 * @param pathMappings mapping of the KMM directories to Kodi paths
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

    /** Without a user there is no password. */
    boolean withoutCredentials() {
        return username == null;
    }
}
