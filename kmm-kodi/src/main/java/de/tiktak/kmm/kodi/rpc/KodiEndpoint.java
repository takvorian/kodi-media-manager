package de.tiktak.kmm.kodi.rpc;

import java.net.URI;
import java.util.Objects;

/**
 * Access to the JSON-RPC interface of a Kodi device. In Kodi, Settings → Services → Control → "Allow remote
 * control via HTTP" must be enabled for this.
 *
 * @param address  host name or IP address, e.g. {@code firetv.fritz.box}
 * @param port     port of the Kodi web server, 8080 by default
 * @param username user name, or {@code null} if Kodi requires no login
 * @param password password or {@code null}
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

    /** Without the password so it does not end up in logs. */
    @Override
    public String toString() {
        return "KodiEndpoint[%s:%d]".formatted(address, port);
    }
}
