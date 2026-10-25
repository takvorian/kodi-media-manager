package de.tiktak.kmm.settings;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import lombok.RequiredArgsConstructor;

/**
 * Meldet sich mit den Zugangsdaten aus den Einstellungen am Proxy an. Anfragen von Zielservern
 * (nicht vom Proxy) werden nicht beantwortet.
 */
@RequiredArgsConstructor
public class SettingsProxyAuthenticator extends Authenticator {

    private static final char[] NO_PASSWORD = {};

    private final SettingsService settingsService;

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        ProxySettings proxy = settingsService.current().proxy();
        if (getRequestorType() != RequestorType.PROXY || proxy == null || !proxy.requiresAuthentication()) {
            return null;
        }
        char[] password = proxy.password() == null ? NO_PASSWORD : proxy.password().toCharArray();
        return new PasswordAuthentication(proxy.username(), password);
    }
}
