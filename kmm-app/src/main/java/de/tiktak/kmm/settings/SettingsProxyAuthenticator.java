package de.tiktak.kmm.settings;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import lombok.RequiredArgsConstructor;

/**
 * Authenticates at the proxy with the credentials from the settings. Requests from target servers
 * (not from the proxy) are not answered.
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
