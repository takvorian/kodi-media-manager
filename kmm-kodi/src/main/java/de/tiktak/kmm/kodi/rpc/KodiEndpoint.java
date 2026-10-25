package de.tiktak.kmm.kodi.rpc;

import java.net.URI;
import java.util.Objects;

/**
 * Zugang zur JSON-RPC-Schnittstelle eines Kodi-Geräts. In Kodi muss dafür unter Einstellungen →
 * Dienste → Steuerung „Steuerung über HTTP erlauben“ aktiviert sein.
 *
 * @param address  Rechnername oder IP-Adresse, z. B. {@code firetv.fritz.box}
 * @param port     Port des Kodi-Webservers, standardmäßig 8080
 * @param username Benutzername oder {@code null}, wenn Kodi keine Anmeldung verlangt
 * @param password Passwort oder {@code null}
 */
public record KodiEndpoint(String address, int port, String username, String password) {

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65_535;
    private static final String PATH = "/jsonrpc";

    public KodiEndpoint {
        Objects.requireNonNull(address, "address must not be null");
        if (address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("port must be between %d and %d".formatted(MIN_PORT, MAX_PORT));
        }
        address = address.strip();
    }

    public URI uri() {
        String host = address.contains(":") ? "[" + address + "]" : address;
        return URI.create("http://%s:%d%s".formatted(host, port, PATH));
    }

    boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /** Ohne Passwort, damit es nicht in Logs landet. */
    @Override
    public String toString() {
        return "KodiEndpoint[%s:%d]".formatted(address, port);
    }
}
