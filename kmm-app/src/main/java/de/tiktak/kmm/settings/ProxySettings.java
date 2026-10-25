package de.tiktak.kmm.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP-Proxy für Anfragen an Online-Dienste.
 *
 * @param username Benutzername oder {@code null} ohne Anmeldung
 * @param password Passwort; in Änderungen heißt {@code null} „unverändert lassen“
 */
public record ProxySettings(
        @NotBlank @Size(max = MAX_TEXT_LENGTH) String host,
        @Min(1) @Max(MAX_PORT) int port,
        @Size(max = MAX_TEXT_LENGTH) String username,
        @Size(max = MAX_TEXT_LENGTH) String password) {

    static final int MAX_PORT = 65_535;
    static final int MAX_TEXT_LENGTH = 255;

    public ProxySettings {
        host = blankToNull(host);
        username = blankToNull(username);
    }

    boolean requiresAuthentication() {
        return username != null;
    }

    ProxySettings withPassword(String newPassword) {
        return new ProxySettings(host, port, username, newPassword);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
