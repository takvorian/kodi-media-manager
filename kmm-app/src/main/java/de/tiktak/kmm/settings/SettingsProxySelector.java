package de.tiktak.kmm.settings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wählt den Proxy bei jeder Verbindung anhand der aktuellen Einstellungen, damit Änderungen ohne
 * Neustart wirken.
 */
@Slf4j
@RequiredArgsConstructor
public class SettingsProxySelector extends ProxySelector {

    private final SettingsService settingsService;

    @Override
    public List<Proxy> select(URI uri) {
        ProxySettings proxy = settingsService.current().proxy();
        if (proxy == null) {
            return List.of(Proxy.NO_PROXY);
        }
        return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxy.host(), proxy.port())));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        log.warn("Connection to {} via proxy {} failed: {}", uri, address, failure.getMessage());
    }
}
